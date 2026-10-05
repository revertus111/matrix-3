import argparse
import json
import sys
import tempfile
from pathlib import Path


HAND_JOINT = 0x0E
WEAPON_JOINT = 0x0F
HEAD_JOINT = 0x04
CENTER_JOINT = 0x00


def normalize_name(name):
    return "".join(ch.lower() for ch in name if ch.isalnum())


def require_file(path, label):
    path = Path(path)
    if not path.is_file():
        raise FileNotFoundError(f"{label} was not found: {path}")
    return path


def find_named_file(root, wanted):
    root = Path(root)
    matches = sorted(
        [p for p in root.rglob(wanted) if p.is_file()],
        key=lambda p: str(p).lower(),
    )
    if not matches:
        raise FileNotFoundError(f"{wanted} was not found under {root}")
    return matches[0]


def choose_body_joint(body_model, index, expected):
    if index >= len(body_model.joints):
        raise RuntimeError(
            f"body has {len(body_model.joints)} joints; required joint 0x{index:X} is missing"
        )
    name = body_model.joints[index]["name"]
    if expected not in normalize_name(name):
        raise RuntimeError(
            f"joint 0x{index:X} is {name!r}; expected a {expected!r} joint"
        )
    return name


def build_aliases(body_model, part_models):
    body_by_norm = {normalize_name(j["name"]): j["name"] for j in body_model.joints}
    aliases = {}
    diagnostics = {}

    semantic_aliases = {
        "headroot": "head",
        "faceroot": "head",
        "alhandsl": "handl",
        "alhandsr": "handr",
        "worldroot": "center",
    }

    for part_name, model in part_models.items():
        rows = []
        for joint in model.joints:
            source = joint["name"]
            source_norm = normalize_name(source)
            target = body_by_norm.get(source_norm)
            if target is None:
                wanted = semantic_aliases.get(source_norm)
                if wanted is not None:
                    target = body_by_norm.get(wanted)
            if target is not None and target != source:
                aliases[source] = target
            rows.append({"source": source, "body_target": target})
        diagnostics[part_name] = rows

    return aliases, diagnostics


def import_demake(demake_root):
    pipeline = Path(demake_root).resolve() / "pipeline"
    if not (pipeline / "demake" / "bmd.py").is_file():
        raise FileNotFoundError(
            f"pinned demake-engine pipeline was not found under {pipeline}"
        )
    sys.path.insert(0, str(pipeline))
    from demake import bck, bmd, dmk, preview, texture

    return bck, bmd, dmk, preview, texture


def project_point(preview, point, positions, size, yaw, pitch):
    import numpy as np

    bbox_min = positions.min(axis=0)
    bbox_max = positions.max(axis=0)
    center = (bbox_min + bbox_max) / 2
    extent = float(np.max(bbox_max - bbox_min))
    if extent == 0:
        extent = 1.0

    rot = preview.rotation_x(pitch) @ preview.rotation_y(yaw)
    view = (point - center) @ rot.T
    view[2] += extent * 2.5
    if view[2] <= 1e-6:
        return None

    width, height = size
    focal = height * 1.2
    x = width / 2 + focal * view[0] / view[2]
    y = height / 2 - focal * view[1] / view[2]
    return float(x), float(y)


def draw_marker(draw, marker_xy, label, color, radius, cross):
    if marker_xy is None:
        return

    x, y = marker_xy
    draw.ellipse(
        (x - radius, y - radius, x + radius, y + radius),
        outline=color,
        width=3,
    )
    if cross:
        draw.line((x - 14, y, x + 14, y), fill=color, width=2)
        draw.line((x, y - 14, x, y + 14), fill=color, width=2)
    draw.text((x + 14, y - 7), label, fill=color)


def annotate_frame(
    image,
    clip_name,
    hand_name,
    weapon_name,
    hand_xy,
    weapon_xy,
):
    from PIL import ImageDraw

    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, 365, 58), fill=(0, 0, 0))
    draw.text((18, 16), f"TP Link - {clip_name}", fill=(255, 255, 255))
    draw.text(
        (18, 36),
        "REAL BCK / body + head + hands + face + al_swb",
        fill=(210, 210, 210),
    )

    draw_marker(
        draw,
        hand_xy,
        f"0xE {hand_name}",
        (64, 208, 255),
        radius=7,
        cross=False,
    )
    draw_marker(
        draw,
        weapon_xy,
        f"0xF {weapon_name}",
        (255, 64, 64),
        radius=9,
        cross=True,
    )


def render_clip(
    data,
    anim,
    preview,
    output,
    hand_name,
    weapon_name,
    size=(640, 560),
    yaw=0.58,
    pitch=0.22,
    max_frames=36,
):
    from PIL import Image

    frames = anim["frames"]
    fps = float(anim["fps"])
    frame_count = len(frames)
    if frame_count <= 0 or fps <= 0:
        raise RuntimeError(
            f"animation {anim['name']!r} has invalid frame_count/fps "
            f"({frame_count}, {fps})"
        )

    sample_count = min(max_frames, frame_count)
    step = frame_count / sample_count
    frame_duration_ms = max(20, int(round((1000.0 / fps) * step)))

    original_anims = data["anims"]
    data["anims"] = [anim]
    rendered = []
    marker_samples = []

    try:
        with tempfile.TemporaryDirectory(prefix="matrix3-tp-link-") as tmp:
            tmp_dir = Path(tmp)
            for i in range(sample_count):
                frame_index = i * step
                t = frame_index / fps
                frame_path = tmp_dir / f"frame-{i:03d}.png"
                preview.render(
                    data,
                    frame_path,
                    size=size,
                    yaw=yaw,
                    pitch=pitch,
                    time=t,
                )
                image = Image.open(frame_path).convert("RGB")

                pose = preview.sample_pose(anim, t)
                if WEAPON_JOINT >= len(pose):
                    raise RuntimeError(
                        f"animation {anim['name']!r} only has {len(pose)} joints; "
                        "weapon joint 0xF is missing"
                    )

                hand_world = pose[HAND_JOINT].reshape(3, 4)[:, 3]
                weapon_world = pose[WEAPON_JOINT].reshape(3, 4)[:, 3]
                positions = preview.skinned_positions(data, t)
                hand_xy = project_point(
                    preview,
                    hand_world,
                    positions,
                    size=size,
                    yaw=yaw,
                    pitch=pitch,
                )
                weapon_xy = project_point(
                    preview,
                    weapon_world,
                    positions,
                    size=size,
                    yaw=yaw,
                    pitch=pitch,
                )
                annotate_frame(
                    image,
                    anim["name"],
                    hand_name,
                    weapon_name,
                    hand_xy,
                    weapon_xy,
                )
                rendered.append(image.copy())
                marker_samples.append(
                    {
                        "frame": float(frame_index),
                        "time_seconds": float(t),
                        "hand_world": [float(v) for v in hand_world],
                        "hand_screen": None
                        if hand_xy is None
                        else [float(hand_xy[0]), float(hand_xy[1])],
                        "weapon_world": [float(v) for v in weapon_world],
                        "weapon_screen": None
                        if weapon_xy is None
                        else [float(weapon_xy[0]), float(weapon_xy[1])],
                    }
                )
    finally:
        data["anims"] = original_anims

    output.parent.mkdir(parents=True, exist_ok=True)
    rendered[0].save(
        output,
        save_all=True,
        append_images=rendered[1:],
        duration=frame_duration_ms,
        loop=0,
        optimize=False,
    )

    return {
        "name": anim["name"],
        "frames": frame_count,
        "fps": fps,
        "sampled_frames": sample_count,
        "gif_frame_ms": frame_duration_ms,
        "output": str(output),
        "marker_samples": marker_samples,
    }, rendered


def main():
    parser = argparse.ArgumentParser(
        description="Build the first visible Twilight Princess Link animation/socket proof."
    )
    parser.add_argument("--demake-root", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()

    manifest_path = require_file(args.manifest, "asset manifest")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    selected = manifest.get("selected_files") or {}

    body_path = require_file(selected.get("body"), "al.bmd")
    head_path = require_file(selected.get("head"), "al_head.bmd")
    hands_path = require_file(selected.get("hands"), "al_hands.bmd")
    face_path = require_file(selected.get("face"), "al_face.bmd")
    sword_model_path = find_named_file(args.out.resolve() / "raw" / "Kmdl", "al_swb.bmd")
    idle_path = require_file(selected.get("idle"), "idle BCK")
    walk_path = require_file(selected.get("walk"), "walk/run BCK")
    sword_path = require_file(selected.get("sword"), "sword BCK")

    bck, bmd, dmk, preview, texture = import_demake(args.demake_root)

    body_data = body_path.read_bytes()
    body_model = bmd.BmdModel(body_data)
    head_model = bmd.BmdModel(head_path.read_bytes())
    hands_model = bmd.BmdModel(hands_path.read_bytes())
    face_model = bmd.BmdModel(face_path.read_bytes())
    sword_model = bmd.BmdModel(sword_model_path.read_bytes())

    right_hand_name = choose_body_joint(body_model, HAND_JOINT, "handr")
    right_weapon_name = choose_body_joint(body_model, WEAPON_JOINT, "weaponr")
    head_joint_name = choose_body_joint(body_model, HEAD_JOINT, "head")
    center_joint_name = body_model.joints[CENTER_JOINT]["name"]

    if len(sword_model.joints) != 1:
        raise RuntimeError(
            f"al_swb.bmd has {len(sword_model.joints)} joints; expected the known rigid one-joint weapon model"
        )

    part_models = {
        "head": head_model,
        "hands": hands_model,
        "face": face_model,
    }
    aliases, alias_diagnostics = build_aliases(body_model, part_models)

    attachments = [
        (head_path.read_bytes(), head_joint_name),
        (hands_path.read_bytes(), center_joint_name),
        (face_path.read_bytes(), head_joint_name),
        (sword_model_path.read_bytes(), right_weapon_name),
    ]

    mesh_data = bmd.build_mesh(
        body_data,
        mode="atlas",
        flip_v=False,
        attachments=attachments,
        with_skin=True,
        joint_aliases=aliases,
    )
    model = mesh_data.pop("model")
    texture_data = None
    if mesh_data["texture"] is not None:
        texture_data = texture.prepare(mesh_data["texture"], max_size=256)

    skeleton = bmd.skeleton_data(model)
    clip_paths = [
        ("idle", idle_path),
        ("walk", walk_path),
        ("sword", sword_path),
    ]
    anims = []
    for role, path in clip_paths:
        clip = bck.parse(path.read_bytes())
        anim = bck.to_anim(clip, model, role)
        if anim["frames"].shape[1] <= WEAPON_JOINT:
            raise RuntimeError(
                f"{role} animation has {anim['frames'].shape[1]} joints; "
                "weapon joint 0xF is missing"
            )
        anims.append(anim)

    out = args.out.resolve()
    visual_dir = out / "visual"
    visual_dir.mkdir(parents=True, exist_ok=True)
    dmk_path = visual_dir / "tp-link-proof.dmk"
    dmk.write(
        dmk_path,
        mesh_data,
        texture_data,
        skeleton=skeleton,
        anims=anims,
        anim_format="f32",
    )

    data = dmk.read(dmk_path)
    if data.get("skeleton") is None or data.get("skin") is None:
        raise RuntimeError("visual conversion produced no skeleton/skin")
    if len(data["skeleton"]["parents"]) <= WEAPON_JOINT:
        raise RuntimeError("visual conversion dropped weapon joint 0xF")
    if len(data["anims"]) != 3:
        raise RuntimeError(
            f"visual conversion wrote {len(data['anims'])} animations; expected 3"
        )

    clip_results = {}
    combined_frames = []
    for role, anim in zip(("idle", "walk", "sword"), data["anims"]):
        gif_path = visual_dir / f"{role}.gif"
        result, rendered = render_clip(
            data,
            anim,
            preview,
            gif_path,
            right_hand_name,
            right_weapon_name,
        )
        clip_results[role] = result
        combined_frames.extend(rendered)
        if rendered:
            combined_frames.extend([rendered[-1].copy()] * 4)

    combined_path = visual_dir / "tp-link-proof.gif"
    combined_frames[0].save(
        combined_path,
        save_all=True,
        append_images=combined_frames[1:],
        duration=80,
        loop=0,
        optimize=False,
    )

    visual_manifest = {
        "status": "NEEDS_VISUAL_ACCEPTANCE",
        "converter": {
            "repo": "https://github.com/snuri00/demake-engine",
            "pin": "a134ff49cc74585c6b11f881293796e45c973c75",
            "local_root": str(args.demake_root.resolve()),
        },
        "parts": {
            "body": str(body_path),
            "head": str(head_path),
            "hands": str(hands_path),
            "face": str(face_path),
            "sword_model": str(sword_model_path),
        },
        "attachment_aliases": aliases,
        "attachment_diagnostics": alias_diagnostics,
        "mesh": {
            "vertices": int(data["mesh"]["vertex_count"]),
            "triangles": int(data["mesh"]["vertex_count"] // 3),
            "joints": int(len(data["skeleton"]["parents"])),
        },
        "right_hand": {
            "index": HAND_JOINT,
            "name": right_hand_name,
            "marker": "cyan circle rendered from each sampled BCK world-space hand pose",
        },
        "weapon_socket": {
            "index": WEAPON_JOINT,
            "name": right_weapon_name,
            "marker": "red cross/circle rendered from each sampled BCK world-space item pose",
            "proof_attachment": str(sword_model_path),
        },
        "animations": clip_results,
        "outputs": {
            "dmk": str(dmk_path),
            "combined_gif": str(combined_path),
        },
    }
    visual_manifest_path = visual_dir / "visual-proof.json"
    visual_manifest_path.write_text(
        json.dumps(visual_manifest, indent=2),
        encoding="utf-8",
    )

    summary_path = visual_dir / "visual-summary.txt"
    summary = [
        "Matrix3 Twilight Princess Link VISUAL proof: GENERATED - NEEDS VISUAL ACCEPTANCE",
        "",
        f"Body: {body_path.name}",
        f"Attached: {head_path.name}, {hands_path.name}, {face_path.name}, {sword_model_path.name}",
        f"Mesh: {visual_manifest['mesh']['triangles']} triangles / "
        f"{visual_manifest['mesh']['vertices']} vertices",
        f"Skeleton: {visual_manifest['mesh']['joints']} joints",
        f"Right hand: 0x{HAND_JOINT:X} {right_hand_name}",
        f"Weapon socket: 0x{WEAPON_JOINT:X} {right_weapon_name}",
        "Animations:",
    ]
    for role in ("idle", "walk", "sword"):
        item = clip_results[role]
        summary.append(
            f"  {role}: {item['frames']} source frames @ {item['fps']:g} fps -> "
            f"{Path(item['output']).name}"
        )
    summary += [
        "",
        f"Combined proof: {combined_path}",
        f"Visual manifest: {visual_manifest_path}",
        "",
        "Cyan circle = animated body right-hand joint 0xE.",
        "Red cross/circle = animated body right-item/weapon joint 0xF.",
        "al_swb.bmd is rigidly attached to body joint 0xF for this diagnostic proof.",
        "A successful renderer exit only means the artifacts were generated; visual PASS still requires user acceptance.",
        "It does not yet prove TP Link rendering inside the Matrix3 client.",
    ]
    summary_path.write_text("\n".join(summary) + "\n", encoding="utf-8")

    print("TP LINK VISUAL PROOF GENERATED")
    print(f"Combined GIF: {combined_path}")
    print(f"Right hand: 0x{HAND_JOINT:X} {right_hand_name}")
    print(f"Weapon joint: 0x{WEAPON_JOINT:X} {right_weapon_name}")
    print(f"Attached proof sword: {sword_model_path.name}")
    print("Visual acceptance: REQUIRED")
    print(f"Summary: {summary_path}")


if __name__ == "__main__":
    main()
