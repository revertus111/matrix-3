# Ocarina of Time in Matrix3

## Goal

Add Link as an imported character in Matrix3 with Ocarina of Time movement, actions, animations, items, and combat feel. Matrix3 remains the host world, camera, renderer, and server authority. Link's combat progression uses RuneScape skills and NPC systems. The OoT-style inventory menu is local to Link; the Matrix world continues while it is open.

## Accepted design

- Manual action combat: sword/shield input, targeting, dodging, and weapon contact; no click-to-attack control.
- RuneScape stats govern accuracy, damage, equipment requirements, NPC health, XP, and progression.
- Weapon speed affects Link's attack timing and recovery. Weapon reach and attack animation determine whether the swing connects.
- Items and equipment can be tested incrementally, then organized into an OoT-style inventory.
- Keep ROM bytes and ROM-extracted assets out of the Matrix3 repository.
- Adult Link is the active playable/fitting target for the current Matrix integration slice.
- Preserve OoT body proportions with uniform character scaling; adapt 830 equipment to Link later instead of stretching Link independently on X/Y/Z.
- OoT texture pixels remain local/runtime-only. Do not import ROM-derived texture files into the revision-830 cache or Git history.

## Current status

**Phase 1 — Playable adult Link foundation: ACTIVE**

The user-provided 32 MiB ROM matches the NTSC-U 1.2 profile (MD5 `57a9719ad547c516342e1a15d5c28c3d`). The Matrix-owned bridge under `native/oot-bridge/` pins liboot and the matching zeldaret source revision, applies the tracked NTSC-U 1.2 compatibility patch, regenerates asset/animation metadata locally, and keeps dependency checkouts/build outputs under ignored local directories.

Phase 0.2/0.3 are runtime verified under MSYS2 UCRT64 against the exact local ROM. The probe passed engine creation, static collision, adult/child Link creation/equipment/skeleton/geometry, movement, animation-state advancement, simulation ticks, and clean destruction. It reported 757 adult triangles and 717 child triangles and ended with `[OoT NTSC12] RESULT: PASS`.

### Phase 0.4 provenance boundary

Phase 0.4 review is complete for local experimental development:

- liboot original code is AGPL-3.0-or-later.
- selected `src/decomp/` material derives from pinned `zeldaret/oot` and is not relicensed by liboot.
- redistribution/shipping of the native dependency/decompilation material remains a separate legal/provenance review requirement.
- ROM bytes and ROM-extracted assets remain local and untracked.

This is an engineering boundary, not legal advice or distribution clearance.

## Phase 1 runtime proof

The first Matrix Link renderer is **runtime observed**. A user screenshot verified recognizable adult OoT Link geometry inside the 830 scene at the local-player transform.

The native sidecar explicitly calls `oot_engine_link_set_age(..., OOT_AGE_ADULT)`; current presentation and future equipment fitting remain adult-Link targets.

Current integration seams:

- `native/oot-bridge/oot_bridge.c` — persistent 20 Hz NTSC-U 1.2 sidecar.
- `OotBridgeSession` — Java 8 process/session owner and immutable frame publisher.
- `PlayerControllerMode` — `LINK` on Ctrl+L; Mario remains separate on Ctrl+M.
- `AlternateCharacterController` — shared alternate-character input dispatch.
- `LinkController` — camera-relative WASD/A/B/Z input while Matrix/server walking remains X/Z authority.
- `LinkCharacterFit` — adult Link world-scale/floor calibration against the live 830 player.
- `LinkVisualRenderer` — animated Link -> Matrix `Model` conversion.
- `LinkTextureRegistry` — runtime-only OoT RGBA -> Matrix GPU/material bridge.
- `Class578.method6834(...)` — established Matrix preview/render submission seam.

No Link combat authority is connected yet. F/B can drive liboot's sword/action state but must not issue Matrix NPC damage/XP until hit-window/contact integration is implemented.

## Adult Link character-fit V1 — RUNTIME ACCEPTED

The second runtime screenshot verified the auto-fit is working:

- adult Link now occupies approximately the same world-scale envelope as the visible 830 player;
- feet are close to the same Matrix ground plane;
- overall head/character height is close enough for the imported-character equipment-fit architecture;
- the user considers Link slightly large and OoT Link's head proportionally large, but neither is a blocker while equipment is adapted to Link rather than deforming Link into RuneScape proportions.

Fit architecture:

- live 830 height comes from the local player's persisted rendered minimum-Y bound (`Player.method8310((byte) 0)` -> stored `Model.method1382()` minimum Y);
- adult Link standing floor/top use a trimmed native geometry span relative to Link's root;
- one uniform scale preserves OoT proportions;
- calibrated standing floor maps to Matrix local Y=0 while native root-height deltas remain visible;
- native adult-Link height/width/depth are retained as groundwork for helmet/body/glove/boot/cape fitting;
- `matrix3.oot.modelScale` remains a forced diagnostic override and `matrix3.oot.fitMultiplier` remains optional fine tuning.

Do not spend the next slice deforming Link's head/body. Future 830 gear should fit Link through body dimensions + stable skeleton sockets.

## OoT material fidelity V2 — IMPLEMENTATION STAGED

The original V1 presentation used one averaged RuneScape face colour per OoT triangle. That made the low-poly topology visually obvious even though the geometry itself was valid.

Protocol/material V2 now stages the real OoT rendering data:

- bridge protocol upgraded from V1 to **V2**;
- native frame stream now includes Link positions, original vertex normals, vertex colours, normalized UVs, and per-triangle liboot texture indices;
- only textures referenced by the current Link frame are queried from liboot's texture cache;
- texture revision tracking sends RGBA8 pixels only when a referenced texture is new/changed;
- Java retains the local texture catalog across frames so warm-up frames cannot drop the first material uploads;
- `LinkTextureRegistry` appends a runtime-only synthetic material block to the live Matrix material table and uploads RGBA pixels through Matrix's existing GPU texture creation/cache path;
- synthetic material IDs are kept below the signed-short boundary used by Matrix models;
- runtime OoT textures are re-uploaded if Matrix's LRU texture cache evicts them;
- no OoT texture pixels are persisted to the cache or repository;
- `LinkVisualRenderer` uses Matrix `Class159` direct-UV mode (`faceTextureIndexes = 32766`) with the original normalized liboot UVs;
- Link vertices are shared when transformed position + quantized liboot normal + UV + material agree, allowing Matrix's normal accumulation to smooth intended surfaces rather than treating every triangle as isolated geometry;
- untextured or unavailable-material faces retain the safe vertex-colour fallback.

Known V2 approximation: liboot wrap mode `mirror` currently degrades to Matrix repeat at this adapter seam; repeat and clamp are mapped directly. If a visible Link material exposes this limitation, add an explicit mirror emulation pass rather than importing texture files.

Status: material V2 is **verified-static only**. Native compilation and in-game texture/UV orientation are `UNKNOWN` until the next runtime test. A native rebuild is mandatory because protocol V1 and V2 intentionally reject each other.

## Matrix3 implementation seam

- Matrix remains host renderer/world/server authority.
- liboot owns Link simulation/pose/action state.
- Link native state remains separate from Mario/libsm64 state.
- Matrix stock walking remains the temporary actual X/Z movement/collision path until Phase 2.
- Future 830 equipment fitting should consume stable Link skeleton/socket transforms plus `LinkCharacterFit` dimensions.
- Runtime OoT materials are presentation-only and must fail back to vertex colours if the hardware texture seam is unavailable.

## Phases and bundles

### Phase 0 — NTSC-U 1.2 native compatibility and bridge proof

- [x] 0.1 Identify exact NTSC-U 1.2 ROM and matching zeldaret target.
- [x] 0.2 Adapt/generate NTSC-U 1.2 liboot bindings. Runtime verified.
- [x] 0.3 Native Link state + animated geometry bridge proof. Runtime PASS.
- [x] 0.4 Record local-development provenance boundary and reproducible Windows build path.

### Phase 1 — Playable Link foundation

- [x] Add Link under the shared alternate-character controller.
- [x] Render recognizable animated adult Link geometry inside Matrix3.
- [x] Auto-fit adult Link height/floor to the live 830 player; runtime screenshot accepted as close enough for the equipment-fit architecture.
- [ ] Runtime-verify OoT material V2: real texture pixels + UVs and reduced faceted/triangled appearance.
- [ ] Prove movement/turn, one action/jump, and B/sword animation in one consolidated runtime session.
- [ ] Give Link mode fully accepted exclusive movement input while preserving intended Matrix camera controls.
- [ ] Add fail-open local RuneScape model suppression/restoration after Link presentation is stable.
- [ ] Expose stable Link skeleton/socket transforms for 830 equipment fitting.

### Phase 2 — OoT movement and world interaction

- [ ] Adapt Matrix terrain and relevant objects to OoT collision.
- [ ] Add Z-targeting, face-target movement, shield positioning, rolling, and contextual actions.
- [ ] Keep world/camera ownership stable while Link inventory is open.

### Phase 3 — Combat and RuneScape progression

- [ ] Connect sword hit windows/contact to Matrix NPC targets.
- [ ] Use Matrix Attack, Strength, Defence, Constitution, equipment, NPC health, XP, and drops as progression authority.
- [ ] Make weapon speed/reach/damage/recovery data-driven.
- [ ] Add ranged and magic styles through the same authority boundary.

### Phase 4 — Equipment, items, and inventory

- [ ] Add generic imported-character equipment fit layer built on body dimensions + stable skeleton sockets.
- [ ] Start Link adaptation with helmet, sword, shield, gloves, boots, amulet, and cape before torso/legs fitting.
- [ ] Add Link equipment slots and visible gear state.
- [ ] Add OoT item groups: bow, bombs, hookshot, boomerang, hammer, magic.
- [ ] Add non-pausing OoT-style inventory/equipment pages.

## Resume Here

Run the **material V2** checklist in `docs/zelda/TESTLIST.md`.

1. Pull `main`.
2. Rebuild `native/oot-bridge` because the binary protocol is now V2.
3. Refresh/clean the Eclipse client.
4. Enter Link mode with Ctrl+L.
5. Preserve the console lines for protocol/material activation and capture a screenshot.

Immediate acceptance target: adult Link remains correctly fitted/animated, but now shows recognizably mapped OoT textures and substantially less obvious triangle-by-triangle shading. Do not suppress the visible RuneScape reference player yet; keep it for this material proof. If V2 passes, the next presentation slice is fail-open RS-player suppression, then stable adult-Link skeleton sockets for 830 equipment fitting.
