# Ocarina of Time in Matrix3

## Goal

Add Link as an imported character in Matrix3 with Ocarina of Time movement, actions, animations, items, and combat feel. Matrix3 remains the host world, camera, renderer, and server authority. Link's combat progression uses RuneScape skills and NPC systems. The OoT-style inventory menu is local to Link; the Matrix world continues while it is open.

## Accepted design

- Manual action combat: sword/shield input, targeting, dodging, and weapon contact; no click-to-attack control.
- RuneScape stats govern accuracy, damage, equipment requirements, NPC health, XP, and progression.
- Weapon speed affects Link's attack timing and recovery. Weapon reach and attack animation determine whether the swing connects.
- Items and equipment can be tested incrementally, then organized into an OoT-style inventory.
- Keep ROM bytes and ROM-extracted assets out of the Matrix3 repository.

## Current status

**Phase 1 — Playable Link foundation: ACTIVE**

The user-provided 32 MiB ROM matches the NTSC-U 1.2 profile (MD5 `57a9719ad547c516342e1a15d5c28c3d`). The official `zeldaret/oot` decompilation has an `ntsc-1.2` build target for that revision. The Matrix-owned bridge under `native/oot-bridge/` pins liboot and the matching zeldaret source revision, applies the tracked NTSC-U 1.2 compatibility patch, regenerates asset/animation metadata locally, and keeps dependency checkouts/build outputs under ignored local directories.

Phase 0.2/0.3 are runtime verified on Windows under MSYS2 UCRT64 against the exact local ROM. The acceptance run passed engine creation, static collision, adult/child Link creation/equipment/skeleton/geometry, movement, animation-state advancement, simulation ticks, and clean destruction. It reported 757 adult triangles and 717 child triangles and ended with `[OoT NTSC12] RESULT: PASS`.

### Phase 0.4 provenance boundary

Phase 0.4 review is complete for this local-development workstream:

- liboot's original code is published as AGPL-3.0-or-later.
- liboot's selected `src/decomp/` material is derived from the pinned `zeldaret/oot` source and is explicitly not relicensed by liboot.
- At the pinned upstream state, that selected decompilation material did not have a repository-wide license declaration suitable for treating redistribution as automatically cleared.
- ROM bytes and ROM-extracted assets remain local and untracked; `native/oot-bridge/.gitignore` now explicitly ignores common N64 ROM extensions.
- Engineering decision: local build/integration may proceed for this experiment, but redistribution/shipping of the native dependency/decompilation material remains a separate legal/provenance review requirement. This project record is not legal advice or a distribution clearance.

### Phase 1 implementation now staged

The first Matrix3 Link slice is implemented but **not runtime verified yet**:

- `native/oot-bridge/oot_bridge.c` provides a persistent 20 Hz binary sidecar using the already-proven NTSC-U 1.2 liboot setup.
- `OotBridgeSession` owns the Java 8 sidecar process and publishes immutable Link geometry/state frames.
- `PlayerControllerMode` adds `LINK`, toggled with **Ctrl+L**; Ctrl+M remains Mario.
- `AlternateCharacterController` registers Link beside Mario and keeps shared camera-relative input ownership.
- `LinkController` feeds WASD/A/B/Z-style input into liboot while Matrix's existing walk packet/server collision path remains X/Z authority.
- `LinkVisualRenderer` converts liboot animated Link triangles/vertex colours into a Matrix `Model` at the local player's transform.
- `Class578.method6834(...)` submits the Link model through the same established Matrix preview-render seam used by Mario and other developer previews.
- Failures return Link mode to RuneScape control instead of leaving alternate input ownership active.

This first proof intentionally does **not** suppress the normal RuneScape local-player model. The RuneScape model remains visible until Link scale/orientation/render stability is proven in-game, giving the test a fail-safe visual baseline. OoT texture/material fidelity is also deferred: V1 uses liboot vertex colours only.

No Link combat authority is connected yet. F/B can drive liboot's sword/action state, but it does not send a Matrix NPC attack packet. Sword hit windows/contact must be proven before RuneScape damage/XP intent is connected.

## Matrix3 implementation seam

- `AlternateCharacterController` is now the shared Mario/Link input and driver dispatch owner.
- `PlayerControllerMode` exposes RuneScape, Mario, and Link developer modes.
- `AlternateCharacterCombatBridge` remains Mario/legacy combat plumbing; Link does not use it in Phase 1 V1.
- Link has a distinct `OotBridgeSession`, `LinkController`, and `LinkVisualRenderer`; OoT native state is not mixed into Mario/libsm64 state.
- Matrix's stock walk request remains the temporary Link X/Z collision/authority path until Phase 2 adds Matrix-world collision to the OoT simulation.

The new Phase 1 source integration is `verified-static`; compilation and in-game behavior are `UNKNOWN` until the runtime checklist passes.

## Phases and bundles

### Phase 0 — NTSC-U 1.2 native compatibility and bridge proof

- [x] 0.1 Identify the local ROM as NTSC-U 1.2 and locate the matching `zeldaret/oot` build target.
- [x] 0.2 Generate/adapt NTSC-U 1.2 asset symbols and build settings for liboot from the matching decompilation. Runtime verified in MSYS2 UCRT64 against the exact local ROM.
- [x] 0.3 Build a bounded native bridge proof that creates Link, advances the OoT fixed-step update, and returns Link state plus animated geometry using the exact local ROM. Runtime acceptance PASS.
- [x] 0.4 Document the liboot/zeldaret provenance boundary and preserve the reproducible Windows path. Local-development engineering gate complete; redistribution/shipping remains explicitly uncleared pending separate legal/provenance review.

### Phase 1 — Playable Link foundation

- [ ] Add Link as a driver under the shared alternate-character controller. Implementation staged; runtime verification pending.
- [ ] Give Link mode exclusive movement input while preserving intended Matrix camera controls. Implementation staged; runtime verification pending.
- [ ] Render animated Link geometry through Matrix3's renderer and provide reliable activation/exit/failure fallback. Implementation staged; first proof deliberately leaves the RuneScape model visible.
- [ ] Prove idle, movement/turn, one action/jump, and B/sword animation in one runtime session.
- [ ] After visible Link render is accepted, add fail-open local RuneScape model suppression/restoration.
- [ ] After geometry presentation is stable, add OoT texture/material fidelity.

### Phase 2 — OoT movement and world interaction

- [ ] Adapt Matrix terrain and relevant objects to OoT collision.
- [ ] Add Z-targeting, face-target movement, shield positioning, rolling, and contextual actions.
- [ ] Keep world and camera ownership stable when opening and closing Link's inventory.

### Phase 3 — Combat and RuneScape progression

- [ ] Connect sword hit windows/contact to Matrix NPC targets.
- [ ] Use Matrix Attack, Strength, Defence, Constitution, equipment, NPC health, XP, and drops as progression authority.
- [ ] Make weapon speed, reach, damage, and recovery data-driven so light and heavy weapons feel different.
- [ ] Add ranged and magic styles through the same combat/progression ownership.

### Phase 4 — Equipment, items, and inventory

- [ ] Add Link equipment slots and visible sword/shield/gear state.
- [ ] Add OoT items in testable groups: bow, bombs, hookshot, boomerang, hammer, and magic.
- [ ] Add the local OoT-style inventory/equipment pages; opening the menu does not pause the Matrix world.

## Resume Here

Run the Phase 1 first-live checklist in `docs/zelda/TESTLIST.md`: pull `main`, build `native/oot-bridge` in MSYS2 UCRT64, launch Matrix3 normally, then use **Ctrl+L** to enter Link mode. Do not mark Phase 1 runtime-verified from compilation alone. The first acceptance target is a visible animated Link geometry overlay that follows the Matrix player, camera-relative WASD still uses Matrix/server walking, A/B/Z input changes native action state without a Matrix combat packet, and Ctrl+L cleanly exits back to RuneScape control.
