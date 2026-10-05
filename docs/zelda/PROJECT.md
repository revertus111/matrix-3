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

**Phase 0 — NTSC-U 1.2 native compatibility and bridge proof: ACTIVE**

The user-provided 32 MiB ROM matches the NTSC-U 1.2 profile (MD5 `57a9719ad547c516342e1a15d5c28c3d`). The official `zeldaret/oot` decompilation has an `ntsc-1.2` build target for that revision. This gives us the source revision needed to adapt the candidate engine-neutral Link SDK, `Cycl0o0/liboot`.

A Matrix-owned compatibility harness exists under `native/oot-bridge/`. It pins liboot and the matching zeldaret source revision, applies a tracked downstream patch that makes the liboot source revision configurable, selects `NTSC_1_2` / revision `2`, and supplies the NTSC-U 1.2 `gameplay_keep` offsets derived from the pinned zeldaret XML. The bridge regenerates asset/animation metadata locally and keeps dependency checkouts under ignored `.deps/` directories.

The NTSC offset derivation, build wiring, and ROM-backed bridge proof are now runtime verified on Windows under MSYS2 UCRT64. The exact local ROM was identified as NTSC-U 1.2 / revision 2 with the expected MD5. In one acceptance run, engine creation, static collision, adult Link creation/equipment, adult skeleton and animated geometry, movement, animation-state advancement, child age/equipment switching, child skeleton and geometry, simulation-tick advancement, and clean destruction all passed. The probe reported 757 adult triangles, 717 child triangles, and finished with `[OoT NTSC12] RESULT: PASS`.

Phase 0.2 and 0.3 are therefore complete. The Windows UCRT64 build/probe path is proven; Phase 0.4 remains open for dependency license/provenance/distribution review before Matrix gameplay integration.

The SDK is not integrated into Matrix3 gameplay yet. Its project is AGPL-3.0-or-later and includes selected vendored decompilation files without a repository-wide license declaration. Review dependency and distribution implications before vendoring or redistributing it.

## Matrix3 implementation seam

- `AlternateCharacterController` already centralizes imported-character input and viewport dispatch, but currently registers only Mario.
- `PlayerControllerMode` currently exposes only RuneScape and Mario modes.
- `AlternateCharacterCombatBridge` currently sends a stock melee NPC attack intent; it does not report a sword collision window or calculate damage.
- `MarioJumpController` and `MarioVisualRenderer` are tied to libsm64 state and geometry. Link needs a distinct native session/driver and an OoT geometry presentation path, while reusing Matrix camera/input ownership where the contracts fit.
- The Mario native bridge is a pinned, reproducible sidecar build. Use a separate OoT bridge contract rather than sharing Mario-specific binary state.

These Matrix seam findings are `verified-static`; Link source integration is not runtime verified.

## Phases and bundles

### Phase 0 — NTSC-U 1.2 native compatibility and bridge proof

- [x] 0.1 Identify the local ROM as NTSC-U 1.2 and locate the matching `zeldaret/oot` build target.
- [x] 0.2 Generate/adapt NTSC-U 1.2 asset symbols and build settings for liboot from the matching decompilation. Runtime verified in MSYS2 UCRT64 against the exact local ROM.
- [x] 0.3 Build a bounded native bridge proof that creates Link, advances the OoT fixed-step update, and returns Link state plus animated geometry using the exact local ROM. Runtime acceptance PASS: adult/child Link, geometry, movement, animation-state advancement, simulation ticks, and clean destroy verified.
- [ ] 0.4 Confirm the native dependency's license/provenance and distribution implications before adding it to Matrix3. The reproducible Windows UCRT64 build/probe path is already verified.

### Phase 1 — Playable Link foundation

- [ ] Add Link as a driver under the shared alternate-character controller.
- [ ] Give Link mode exclusive movement input while preserving Matrix camera controls that remain intentionally available.
- [ ] Render animated Link geometry through Matrix3's renderer and provide reliable activation, exit, failure fallback, and local-player restoration.
- [ ] Prove movement, turn, idle, and one jump/action animation in one runtime session.

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

Phase 0.2 and 0.3 are runtime verified against the exact NTSC-U 1.2 ROM. Continue with Phase 0.4: document the liboot/zeldaret license and provenance boundary, confirm what may be built/distributed versus what must remain local, and preserve the proven MSYS2 UCRT64 build path. Do not add a Link driver to Matrix3 gameplay until the Phase 0.4 gate is complete and the next implementation bundle has explicit AAA approval.
