# N64 / Imported Character Project Architecture

Status: active architecture authority for Mario, OoT Link and future imported playable characters.

## Mandatory shared-controller rule

Mario, Link and every future imported character use **one shared Matrix3 controller architecture** for common gameplay systems. Do not implement the same gameplay system once for Mario and again for Link.

Shared ownership includes:

- keyboard/input sampling and key ownership;
- camera-relative movement intent and common movement policy;
- clipping-mode policy and common movement handoff rules;
- target acquisition and lock-on state;
- melee target selection and forward fallback;
- attack-edge / one-shot lifecycle;
- combat intent and packet routing;
- RuneScape server combat handoff;
- shared mode/reset lifecycle where the behavior is not native-engine-specific.

The intended shape is:

```text
AlternateCharacterController
    |
    +-- shared input / camera / movement policy
    +-- shared AlternateCharacterCombatBridge
    |       +-- target lock
    |       +-- melee target resolution
    |       +-- attack lifecycle
    |       +-- common manual-melee server intent
    |
    +-- Mario native adapter
    |       +-- libsm64 physics/action/animation state
    |       +-- Mario render/socket/presentation conversion
    |
    +-- Link native adapter
            +-- liboot physics/action/animation state
            +-- Link render/socket/presentation conversion
```

### Character-specific code is an adapter, not a second controller

Per-character code may contain only behavior that genuinely comes from that native engine or asset format, for example:

- libsm64 vs liboot physics/state feeds;
- native stick-axis/sign conversion;
- native animation/action/frame identifiers;
- character-specific render/model/skeleton/socket data;
- coordinate/scale conversion required by that native runtime;
- native attack/contact evidence supplied to the shared combat owner.

A native adapter may **feed data into** the shared controller. It must not create its own duplicate NPC targeting, lock retention, melee packet path, RuneScape damage formula, attack cooldown system, movement-input owner or camera owner.

If Mario and Link both need a behavior, the default decision is: **put it in the shared owner first**. An exception requires an explicit architecture decision in this file before code is added.

## Current owners

| System | Owner | Character adapters may do |
| --- | --- | --- |
| Input/key sampling | `AlternateCharacterController` | consume `ControlState` |
| Camera-relative movement intent | `AlternateCharacterController` | translate shared intent to native engine axes |
| Free-position helper | `AlternateCharacterFreeMovement` | supply native position/scale |
| Combat/targeting | `AlternateCharacterCombatBridge` | supply native attack/contact state |
| Server manual melee authority | `AlternateCharacterCombatPacketBridge` | none; character identity is irrelevant |
| Mario native runtime | `MarioJumpController` / libsm64 bridge | Mario-only native adaptation |
| Link native runtime | `LinkController` / liboot bridge | Link-only native adaptation |

`MarioJumpController` is a legacy-named Mario native adapter, not a second global movement/controller owner. `LinkController` is the equivalent liboot adapter. Shared behavior belongs above both of them.

The server file `LinkCombatPacketBridge` is retained only as a temporary compatibility shim because the existing NPC packet router references that historical name. It contains no combat behavior and delegates immediately to `AlternateCharacterCombatPacketBridge`. Do not add logic to the shim.

## Combat contract

Mario and Link do **not** have separate combat systems.

- Both resolve NPCs through `AlternateCharacterCombatBridge`.
- Both send the same manual-melee intent.
- Both reach the same server validation and one-shot `PlayerCombatNew` path.
- RuneScape equipment/stats remain authoritative for weapon type, speed/cooldown, accuracy, damage, XP, NPC HP/death and drops.
- Native engines own only presentation/action state and may provide the moment an attack should be considered at contact.
- Stock RuneScape repeating click-to-attack is not the imported-character action-combat owner.

Link currently supplies liboot action/animation/frame data to the shared native-contact gate. Mario's established native slash adapter emits a discrete attack edge into that same shared combat owner. Native contact timing can differ by adapter; targeting, combat intent and server authority do not.

## Movement contract

There is one Matrix3 input/camera/movement policy, not separate Mario and Link keyboard systems.

- `AlternateCharacterController.ControlState` is the common movement vocabulary.
- Camera-relative intent is resolved once by the shared controller.
- Character adapters translate that shared intent into their native engine's axis/sign conventions only.
- Native physics/presentation may differ because libsm64 and liboot are different engines; that difference does not authorize duplicate Matrix input, camera, clipping-policy or world-authority systems.
- Shared movement bugs must be fixed at the shared owner when the bug is common. Adapter-only bugs are fixed only at the affected native boundary.

## Rule for every new imported character

Adding another character must mean:

1. register the character with the shared controller;
2. provide a native adapter for movement/action/render state;
3. feed native attack/contact evidence into the shared combat controller;
4. reuse the same Matrix targeting, packet and server-combat path.

Do **not** add `SonicCombatController`, `BanjoCombatController`, another NPC target scanner, another manual-melee packet, or another copy of camera-relative WASD logic.

## Verification status - 2026-10-05

- Shared input/camera ownership: `verified-static`; existing runtime movement results are tracked separately.
- Shared Mario/Link combat target + packet ownership refactor: `verified-static`; Eclipse/client/server runtime acceptance pending.
- Link exact sword-contact frame: `HYPOTHESIS` until runtime accepted.
- Mario native slash visual profile remains tracked in `docs/n64/CUSTOM_COMBAT.md` and does not own server combat.

Runtime checklist for this architecture cleanup: `docs/n64/SHARED_CONTROLLERS_TESTLIST.md`.
