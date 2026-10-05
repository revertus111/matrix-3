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

The first Matrix Link renderer is **runtime observed**. User screenshots verified recognizable adult OoT Link geometry inside the 830 scene at the local-player transform.

The native sidecar explicitly calls `oot_engine_link_set_age(..., OOT_AGE_ADULT)`; current presentation and future equipment fitting remain adult-Link targets.

Current integration seams:

- `native/oot-bridge/oot_bridge.c` — persistent 20 Hz NTSC-U 1.2 sidecar, protocol V2.
- `OotBridgeSession` — Java 8 process/session owner and immutable frame/texture publisher.
- `PlayerControllerMode` — `LINK` on Ctrl+L; Mario remains separate on Ctrl+M.
- `AlternateCharacterController` — shared alternate-character input dispatch.
- `LinkController` — screen-relative WASD/A/B/Z input while Matrix/server walking remains X/Z authority.
- `LinkCharacterFit` — adult Link world-scale/floor calibration against the live 830 player.
- `LinkVisualRenderer` — animated Link -> Matrix `Model` conversion, OoT RGBA micro-bake, and strict fail-open replacement readiness.
- `LinkTextureRegistry` — retained experimental synthetic Matrix material adapter; no longer used by the active Link render path after runtime rejection of that approach.
- `Player.method10696(...)` — existing local RuneScape appearance suppression seam, reached through the shared `MarioVisualRenderer.shouldSuppressLocalPlayer(...)` gate for both Mario and Link.
- `Class578.method6834(...)` — established Matrix preview/render submission seam.

No Link combat authority is connected yet. F/B can drive liboot's sword/action state but must not issue Matrix NPC damage/XP until hit-window/contact integration is implemented.

## Adult Link character-fit V1 — RUNTIME ACCEPTED

The second runtime screenshot verified the auto-fit is working:

- adult Link now occupies approximately the same world-scale envelope as the 830 player;
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

## OoT material fidelity V2 — NATIVE/PROTOCOL VERIFIED, MATRIX DIRECT-MATERIAL PATH REJECTED

Protocol V2 is now locally built and running. The user's Native Builder log proved the correct OoT bridge folder, MSYS2 UCRT64 toolchain, CMake generation/build, fresh `dist/oot_bridge.exe`, and `OOT BUILD SUCCESS`.

V2 successfully carries the data required for a faithful host renderer:

- positions;
- original vertex normals;
- vertex colours / lighting tint;
- normalized UVs;
- per-triangle liboot texture index;
- local-ROM RGBA8 texture updates with dimensions/wrap/revision;
- Java-retained texture catalog across frames.

The first Matrix implementation attempted to register OoT pixels as synthetic runtime Matrix materials and use `Class159` direct UV mode. Runtime screenshot rejected that path: Link rendered and the 830 body was correctly suppressed, but Link remained broad flat green/white/gray polygons with a nearly blank white face. That is a material binding failure, not acceptable OoT fidelity.

Do not treat that screenshot as an N64-quality limitation. It proved:

- OoT geometry: working;
- adult fit: working;
- fail-open 830 replacement: working;
- V2 native build: working;
- synthetic Matrix runtime-material/direct-UV presentation: not working visibly.

## OoT RGBA micro-bake V3 — IMPLEMENTATION STAGED

The active Link renderer now bypasses the rejected synthetic runtime-material seam and uses the same general strategy already proven for Mario's imported texture presentation.

For every textured OoT source triangle:

1. resolve the retained liboot texture by `triTexture` index;
2. subdivide the source triangle (default 4x -> up to 16 micro-faces);
3. barycentrically interpolate original OoT position, normal, colour/tint, and UV;
4. bilinearly sample the actual local-ROM RGBA texture with liboot repeat/mirror/clamp wrap behavior;
5. multiply sampled texture RGB by liboot's interpolated vertex RGB, matching liboot's documented host-render rule;
6. bake the resulting colour into ordinary Matrix face colour;
7. share transformed vertices only when position + quantized liboot normal agree so intended smooth surfaces can share Matrix normal accumulation without welding hard edges.

This intentionally avoids dependence on Matrix synthetic material registration while still using the real OoT texture pixels. It also reduces the old crystalline appearance because one large OoT polygon becomes multiple smaller Matrix faces carrying varying texture samples.

Safety/limits:

- no ROM-derived pixels are written into the cache or Git;
- untextured/unavailable texture faces retain vertex-colour fallback;
- subdivisions automatically reduce if the generated model would exceed Matrix's 65,535-vertex / signed-short-safe face budget;
- `-Dmatrix3.oot.textureSubdivisions=0..4` is available for diagnostics, default 4;
- the V2 native protocol is unchanged by this recovery patch, so a user who already rebuilt the V2 sidecar does **not** need another native rebuild for the Java-only micro-bake test.

Status: V3 is **verified-static only** until Eclipse compile + runtime screenshot. The first runtime diagnostic must report `textureCatalog > 0`, `texturedSource > 0`, and `material=oot-rgba-micro-v3`.

## Fail-open local-player replacement — RUNTIME ACCEPTED

The latest screenshot verified the RuneScape local-player body is removed while Link is healthy. Link remained visible alone, proving the replacement gate is functioning.

Replacement policy remains:

- Link must successfully draw through Matrix before suppression can activate;
- the latest liboot frame must remain fresh (<= 500 ms);
- the latest successful Matrix Link draw must also remain fresh (<= 500 ms);
- Link mode, bridge readiness, usable geometry, and replacement readiness are all required;
- any mode exit, bridge loss, stale frame, failed fit, failed model build, invalid player transform, or render exception fails open to the normal RuneScape body;
- only the local player's RuneScape appearance is suppressed; remote players are untouched;
- the actual RuneScape appearance/equipment object is not deleted, nulled, or rewritten.

Implementation deliberately reuses the existing `Player.method10696(...) -> MarioVisualRenderer.shouldSuppressLocalPlayer(...)` presentation seam. The Mario gate delegates to `LinkVisualRenderer.shouldSuppressLocalPlayer(...)` first, avoiding a second invasive edit to the decompiled Player renderer.

## Matrix3 implementation seam

- Matrix remains host renderer/world/server authority.
- liboot owns Link simulation/pose/action state.
- Link native state remains separate from Mario/libsm64 state.
- Matrix stock walking remains the temporary actual X/Z movement/collision path until Phase 2.
- Future 830 equipment fitting should consume stable Link skeleton/socket transforms plus `LinkCharacterFit` dimensions.
- OoT texture fidelity currently uses runtime CPU sampling/micro-baking into Matrix face colours rather than persistent cache assets.

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
- [x] Runtime-prove protocol V2 native sidecar and identify direct Matrix runtime-material presentation as visually failed.
- [ ] Runtime-verify OoT RGBA micro-bake V3: recognizable real texture detail + reduced faceted/triangled appearance.
- [ ] Prove movement/turn, one action/jump, and B/sword animation in one consolidated runtime session.
- [ ] Give Link mode fully accepted exclusive movement input while preserving intended Matrix camera controls.
- [x] Fail-open local RuneScape model suppression/restoration for Link; Link-only screenshot runtime accepted.
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

Run the **OoT RGBA micro-bake V3** checklist in `docs/zelda/TESTLIST.md`.

1. Pull `main`.
2. Do **not** rebuild native if the local bridge already reports protocol V2; the user's latest Native Builder run already succeeded.
3. Refresh/clean the Eclipse client.
4. Enter Link mode with Ctrl+L.
5. Preserve the new `[OoT Visual]` diagnostic line.
6. Capture a close front/three-quarter screenshot of Link alone.

Immediate acceptance target: `textureCatalog > 0`, `texturedSource > 0`, `matrixTriangles > triangles`, and visibly recognizable OoT face/tunic/equipment texture detail with substantially less triangle-by-triangle shading. If the pixels are present but vertically flipped or mirrored, fix only the UV/wrap convention next; do not reopen the native compatibility or auto-fit work.