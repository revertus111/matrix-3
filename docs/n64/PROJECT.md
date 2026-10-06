# N64 / Imported Character Project Architecture

Status: active architecture authority for Mario, OoT Link and future imported playable characters.

## Current checkpoint - universal horizontal controller, 2026-10-05

The user's AAA-approved horizontal consolidation is implemented / verified-static. AlternateCharacterController owns one sampled control frame, native profile encoding and exactly one horizontal movement state. Both adapters delegate free/clipped motion, pending tile approval, mode/toggle restoration and resets. They supply native position/scale and retain native action/vertical/render evidence only.

Java 8-targeted focused checks pass: 110,902 shared-input/native-axis checks, 6,089 complete production-driver checks including matching clipped traces/switches/jump height/target basis, and 112 OoT interpolation samples. An ownership guard rejects the pre-consolidation drivers. The newer shared combat API/contact gate is preserved; LinkCombatController is not reintroduced.

Resume Here: pull current main, refresh/clean/build Client and Server, restart/login, then run the consolidated universal-controller session in docs/n64/TESTLIST.md plus the shared-combat checks in SHARED_CONTROLLERS_TESTLIST.md. Full client/native gameplay remains pending. Free motion is local presentation; optional clipping remains stock tile authority, not continuous RuneScape world collision. No native rebuild required.

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
- a declared native input profile/scale consumed by the shared movement encoder;
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
| Camera-relative world intent / native-axis encoding | `AlternateCharacterController` | request the registered native input profile; supply optional shared combat basis |
| Free/clipped horizontal routing, restoration and resets | `AlternateCharacterController` + its single `AlternateCharacterFreeMovement` state | supply native position/scale; delegate apply/restore/reset |
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
- Both reach the same `AlternateCharacterCombatPacketBridge` server authority.
- RuneScape equipment/stats remain authoritative for weapon type, speed/cooldown, accuracy, damage, XP, NPC HP/death and drops.
- Native engines own presentation/action state and provide the contact moment into the shared combat owner.
- Stock RuneScape repeating click-to-attack is **not** the imported-character action-combat owner.
- One physical F keypress may arm at most one native swing/contact attempt. Holding F must not repeat attacks.
- One native contact may execute at most one server combat cycle. A second attack requires a second keypress and a second native contact.
- Manual imported-character combat must never call `PlayerCombatNew.start()` because its stock `process()` path may follow, add walk steps or apply diagonal route correction.
- The shared server bridge prevalidates range, then invokes only one `PlayerCombatNew.processWithDelay()` cycle without installing that object into `ActionManager`.
- If the target is out of legal melee range at contact, the swing produces no hit and no movement toward the target.
- Any existing repeating `PlayerCombatNew` action is stopped before a manual imported-character contact so stale RuneScape auto-combat cannot continue behind Mario/Link.

Link supplies liboot action/animation/frame data to the shared native-contact gate. Mario supplies libsm64 protocol-v3 slash state and its native normalized contact time to that same shared gate. Native timing differs by adapter; targeting, one-keypress lifecycle, zero-follow policy, combat intent and server authority do not.

## Movement contract

There is one Matrix3 input/camera/movement policy, not separate Mario and Link keyboard systems.

- `AlternateCharacterController.ControlState` is the common movement vocabulary.
- Camera-relative intent is resolved once by the shared controller.
- Shared `movementInput` resolves the native profile and optional combat basis. Both native input and clipped movement consume the same resolved world intent.
- All drivers delegate `applyHorizontalMovement`, `restoreHorizontalMovement` and `resetHorizontalMovement`; they do not instantiate their own mover, compute stick signs or send walk packets.
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

- Shared input/camera/native encoding and single horizontal free/clipped owner: `verified-static`; focused production checks pass, rendered native gameplay acceptance pending.
- Shared Mario/Link combat target + packet ownership: `verified-static`; strict one-keypress/one-contact/zero-follow server path implemented, Eclipse/client/server runtime acceptance pending.
- Link exact sword-contact frame: `HYPOTHESIS` until runtime accepted.
- Mario native slash visual profile remains tracked in `docs/n64/CUSTOM_COMBAT.md` and does not own server combat.

Runtime checklist for this architecture cleanup: `docs/n64/SHARED_CONTROLLERS_TESTLIST.md`.
