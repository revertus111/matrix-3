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

**Phase 0 — native OoT compatibility and bridge proof: BLOCKED ON COMPATIBILITY EVIDENCE**

A local identity check of the user-provided 32 MiB ROM matches the upstream `oot-ntsc-us-1.2` profile (MD5 `57a9719ad547c516342e1a15d5c28c3d`). The candidate upstream SDK is `Cycl0o0/liboot`: its documentation describes an engine-neutral C API for Link movement, equipment, items, animation geometry, and host-world collision. Its compatibility matrix marks NTSC-U 1.2 as **identification only, not gameplay validated**; PAL 1.1 is its compiled and ROM-backed tested target. Recognition of this ROM must not be treated as runtime compatibility.

The SDK is not integrated into Matrix3. Its project is AGPL-3.0-or-later and includes selected vendored decompilation files without a repository-wide license declaration. Review dependency and distribution implications before vendoring or redistributing it.

## Matrix3 implementation seam

- `AlternateCharacterController` already centralizes imported-character input and viewport dispatch, but currently registers only Mario.
- `PlayerControllerMode` currently exposes only RuneScape and Mario modes.
- `AlternateCharacterCombatBridge` currently sends a stock melee NPC attack intent; it does not report a sword collision window or calculate damage.
- `MarioJumpController` and `MarioVisualRenderer` are tied to libsm64 state and geometry. Link needs a distinct native session/driver and an OoT geometry presentation path, while reusing Matrix camera/input ownership where the contracts fit.
- The Mario native bridge is a pinned, reproducible sidecar build. Use a separate OoT bridge contract rather than sharing Mario-specific binary state.

These findings are `verified-static`; the source integration is not runtime verified.

## Phases and bundles

### Phase 0 — Native OoT compatibility and bridge proof

- [ ] 0.1 Validate the exact NTSC-U 1.2 ROM against a supported liboot gameplay build, or establish a supported build for this revision.
- [ ] 0.2 Build a bounded native bridge proof that creates Link, advances the OoT fixed-step update, and returns Link state plus animated geometry.
- [ ] 0.3 Confirm the native dependency's license/provenance and reproducible Windows build path before adding it to Matrix3.

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

No Matrix3 source or ROM data has been changed. The exact ROM revision is identified, but upstream gameplay compatibility for NTSC-U 1.2 is not established. Next: decide the compatibility path, then complete Phase 0 before adding a Link driver. Do not reuse Mario-specific native state or claim ROM support from an identity match alone.
