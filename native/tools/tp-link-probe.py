import argparse
import json
import shutil
import struct
from pathlib import Path


LEFT_HAND_JOINT = 0x09
LEFT_WEAPON_JOINT = 0x0A
RIGHT_HAND_JOINT = 0x0E
RIGHT_WEAPON_JOINT = 0x0F


def be_u16(data, off):
    if off < 0 or off + 2 > len(data):
        raise ValueError(f"read u16 out of range at 0x{off:X}")
    return struct.unpack_from(">H", data, off)[0]


def be_u32(data, off):
    if off < 0 or off + 4 > len(data):
        raise ValueError(f"read u32 out of range at 0x{off:X}")
    return struct.unpack_from(">I", data, off)[0]


def c_string(data, off):
    if off < 0 or off >= len(data):
        raise ValueError(f"string offset out of range at 0x{off:X}")
    end = data.find(b"\x00", off)
    if end < 0:
        raise ValueError(f"unterminated string at 0x{off:X}")
    return data[off:end].decode("shift_jis", errors="replace")


def find_section(data, tag):
    if len(tag) != 4:
        raise ValueError("section tag must be 4 bytes")
    pos = 0x20
    while pos + 8 <= len(data):
        section_tag = data[pos : pos + 4]
        size = be_u32(data, pos + 4)
        if section_tag == tag:
            if size < 8 or pos + size > len(data):
                raise ValueError(f"{tag.decode()} section has invalid size {size}")
            return pos, size
        if size < 8:
            break
        pos += size
    return None


def parse_name_table(data, table_off):
    count = be_u16(data, table_off)
    entries = table_off + 4
    names = []
    for i in range(count):
        entry = entries + i * 4
        string_rel = be_u16(data, entry + 2)
        names.append(c_string(data, table_off + string_rel))
    return names


def parse_bmd(path):
    data = path.read_bytes()
    magic = data[:8].decode("ascii", errors="replace")
    if magic not in ("J3D1bmd3", "J3D2bmd3"):
        raise ValueError(f"unexpected BMD magic {magic!r}")

    section = find_section(data, b"JNT1")
    if not section:
        raise ValueError("JNT1 section not found")

    base, size = section
    joint_count = be_u16(data, base + 8)
    names_rel = be_u32(data, base + 0x14)
    names = parse_name_table(data, base + names_rel)
    if len(names) != joint_count:
        raise ValueError(f"JNT1 name count {len(names)} != joint count {joint_count}")

    return {
        "file": str(path),
        "magic": magic,
        "bytes": len(data),
        "jnt1_bytes": size,
        "joint_count": joint_count,
        "joint_names": names,
    }


def parse_bck(path):
    data = path.read_bytes()
    magic = data[:8].decode("ascii", errors="replace")
    if magic not in ("J3D1bck1", "J3D2bck1"):
        raise ValueError(f"unexpected BCK magic {magic!r}")

    section = find_section(data, b"ANK1")
    if not section:
        raise ValueError("ANK1 section not found")

    base, size = section
    return {
        "file": str(path),
        "magic": magic,
        "bytes": len(data),
        "ank1_bytes": size,
        "loop_mode": data[base + 8],
        "rotation_decimal_shift": data[base + 9],
        "duration_frames": be_u16(data, base + 0x0A),
        "joint_count": be_u16(data, base + 0x0C),
    }


def normalize_name(name):
    return "".join(ch.lower() for ch in name if ch.isalnum())


def find_named_file(root, wanted):
    wanted_lower = wanted.lower()
    matches = [p for p in root.rglob("*") if p.is_file() and p.name.lower() == wanted_lower]
    if not matches:
        raise FileNotFoundError(f"{wanted} was not found under {root}")
    return sorted(matches, key=lambda p: str(p).lower())[0]


def find_bck_candidates(root):
    return sorted(
        [p for p in root.rglob("*") if p.is_file() and p.suffix.lower() == ".bck"],
        key=lambda p: str(p).lower(),
    )


def choose_by_family(candidates, exact_names, prefixes):
    by_name = {p.name.lower(): p for p in candidates}
    for name in exact_names:
        if name in by_name:
            return by_name[name]
    for prefix in prefixes:
        for path in candidates:
            if path.stem.lower().startswith(prefix):
                return path
    return None


def choose_idle(candidates):
    return choose_by_family(
        candidates,
        ["wait.bck", "waita.bck", "waitb.bck", "waitatos.bck"],
        ["wait"],
    )


def choose_walk(candidates):
    return choose_by_family(
        candidates,
        ["walk.bck", "walka.bck", "walkb.bck", "dasha.bck", "dashb.bck"],
        ["walk", "dash"],
    )


def choose_sword(candidates):
    return choose_by_family(
        candidates,
        ["cutl.bck", "cutr.bck", "cutu.bck", "cutt.bck", "cuta.bck"],
        ["cut"],
    )


def copy_named(src, dest_dir, out_name=None):
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / (out_name or src.name)
    shutil.copy2(src, dest)
    return dest


def assert_reaches_weapon(label, info):
    if info["joint_count"] <= RIGHT_WEAPON_JOINT:
        raise RuntimeError(
            f"{label} BCK has only {info['joint_count']} joints; it does not reach joint 0x{RIGHT_WEAPON_JOINT:X}"
        )


def require_joint(model, index, expected):
    if model["joint_count"] <= index:
        raise RuntimeError(
            f"al.bmd has only {model['joint_count']} joints; expected joint 0x{index:X}"
        )
    name = model["joint_names"][index]
    if expected not in normalize_name(name):
        raise RuntimeError(
            f"joint 0x{index:X} is {name!r}, expected a {expected!r} joint"
        )
    return name


def main():
    parser = argparse.ArgumentParser(description="Probe extracted Twilight Princess Link J3D assets.")
    parser.add_argument("--kmdl", required=True, type=Path)
    parser.add_argument("--alanm", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    kmdl = args.kmdl.resolve()
    alanm = args.alanm.resolve()
    out = args.out.resolve()
    selected = out / "selected"
    selected.mkdir(parents=True, exist_ok=True)

    model_files = {}
    model_info = {}
    for name in ("al.bmd", "al_head.bmd", "al_hands.bmd", "al_face.bmd"):
        path = find_named_file(kmdl, name)
        model_files[name] = path
        model_info[name] = parse_bmd(path)
        copy_named(path, selected)

    model = model_info["al.bmd"]
    left_hand = require_joint(model, LEFT_HAND_JOINT, "handl")
    left_weapon = require_joint(model, LEFT_WEAPON_JOINT, "weaponl")
    right_hand = require_joint(model, RIGHT_HAND_JOINT, "handr")
    right_weapon = require_joint(model, RIGHT_WEAPON_JOINT, "weaponr")

    bcks = find_bck_candidates(alanm)
    if not bcks:
        raise FileNotFoundError(f"no .bck files found under {alanm}")

    idle_path = choose_idle(bcks)
    walk_path = choose_walk(bcks)
    sword_path = choose_sword(bcks)
    if not idle_path:
        raise FileNotFoundError("no WAIT-family BCK animation found")
    if not walk_path:
        raise FileNotFoundError("no WALK/DASH-family BCK animation found")
    if not sword_path:
        raise FileNotFoundError("no CUT-family BCK animation found")

    idle = parse_bck(idle_path)
    walk = parse_bck(walk_path)
    sword = parse_bck(sword_path)
    assert_reaches_weapon("idle", idle)
    assert_reaches_weapon("walk/run", walk)
    assert_reaches_weapon("sword", sword)

    idle_copy = copy_named(idle_path, selected, f"idle_{idle_path.name}")
    walk_copy = copy_named(walk_path, selected, f"walk_{walk_path.name}")
    sword_copy = copy_named(sword_path, selected, f"sword_{sword_path.name}")

    manifest = {
        "status": "PASS",
        "model": model,
        "model_parts": model_info,
        "socket_contract": {
            "active_sword_side": "left",
            "active_sword_hand_index": LEFT_HAND_JOINT,
            "active_sword_hand_name": left_hand,
            "active_sword_weapon_index": LEFT_WEAPON_JOINT,
            "active_sword_weapon_name": left_weapon,
            "right_hand_index": RIGHT_HAND_JOINT,
            "right_hand_name": right_hand,
            "right_weapon_index": RIGHT_WEAPON_JOINT,
            "right_weapon_name": right_weapon,
        },
        "animations": {
            "idle": idle,
            "walk": walk,
            "sword": sword,
            "total_bck_files": len(bcks),
        },
        "selected_files": {
            "body": str(selected / "al.bmd"),
            "head": str(selected / "al_head.bmd"),
            "hands": str(selected / "al_hands.bmd"),
            "face": str(selected / "al_face.bmd"),
            "idle": str(idle_copy),
            "walk": str(walk_copy),
            "sword": str(sword_copy),
        },
    }

    manifest_path = out / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    summary = [
        "Matrix3 Twilight Princess Link asset proof: PASS",
        "",
        f"Body model: {model_files['al.bmd']}",
        f"Joints: {model['joint_count']}",
        f"Left hand 0x{LEFT_HAND_JOINT:X}: {left_hand}",
        f"Left weapon 0x{LEFT_WEAPON_JOINT:X}: {left_weapon} (active GZ2E01 sword socket)",
        f"Right hand 0x{RIGHT_HAND_JOINT:X}: {right_hand}",
        f"Right weapon 0x{RIGHT_WEAPON_JOINT:X}: {right_weapon}",
        f"Idle clip: {idle_path.name} ({idle['duration_frames']} frames, {idle['joint_count']} joints)",
        f"Walk/run clip: {walk_path.name} ({walk['duration_frames']} frames, {walk['joint_count']} joints)",
        f"Sword clip: {sword_path.name} ({sword['duration_frames']} frames, {sword['joint_count']} joints)",
        f"BCK clips discovered: {len(bcks)}",
        "",
        f"Selected proof files: {selected}",
        f"Manifest: {manifest_path}",
        "",
        "This proves authentic TP Link model/skeleton + real idle/locomotion/sword BCK assets + both item socket identities.",
        "For GZ2E01 GameCube Link, the visible sword proof uses the left hand/item pair 0x9/0xA.",
        "The visual probe is the next gate; Matrix3 in-client rendering is still not proven.",
    ]
    (out / "summary.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")

    print("TP LINK ASSET PROOF PASS")
    print(f"Body: {model_files['al.bmd']}")
    print(f"Joints: {model['joint_count']}")
    print(f"Left hand 0x{LEFT_HAND_JOINT:X}: {left_hand}")
    print(f"Left weapon 0x{LEFT_WEAPON_JOINT:X}: {left_weapon} - active GC sword socket")
    print(f"Right hand 0x{RIGHT_HAND_JOINT:X}: {right_hand}")
    print(f"Right weapon 0x{RIGHT_WEAPON_JOINT:X}: {right_weapon}")
    print(f"Idle: {idle_path.name} - {idle['duration_frames']} frames")
    print(f"Walk/run: {walk_path.name} - {walk['duration_frames']} frames")
    print(f"Sword: {sword_path.name} - {sword['duration_frames']} frames")
    print(f"Manifest: {manifest_path}")


if __name__ == "__main__":
    main()
