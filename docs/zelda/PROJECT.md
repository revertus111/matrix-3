# Ocarina of Time in Matrix3

## Goal

Run Adult Link from Ocarina of Time as a playable alternate character inside Matrix3 revision 830. liboot owns Link simulation/pose/action state; Matrix3 remains host world, camera, renderer, server and RuneScape progression authority.

## Accepted design

- Adult Link is the active Matrix integration target.
- Preserve OoT body proportions with one uniform world-scale fit.
- Adult Link intentionally occupies roughly the same height/floor envelope as the 830 player so revision-830 equipment can remain near native Matrix scale.
- Do **not** deform Link into the RuneScape skeleton just to make gear fit.
- Do **not** reuse Mario's geometry-envelope equipment architecture for Link.
- Link equipment should attach to liboot's real animated skeleton/socket data.
- Small per-item scale/offset/rotation corrections are acceptable after the base socket system is proven.
- Manual Zelda-style combat stays separate from RuneScape progression authority: Matrix stats/NPC health/XP/drops remain authoritative once hit-contact integration is enabled.
- ROM bytes and ROM-derived textures stay local and untracked.

## Current status

**Phase 1 foundation: runtime proven. Phase 4 equipment foundation: ACTIVE.**

Runtime-proven baseline:

- NTSC-U 1.2 ROM compatibility probe passes.
- Adult Link geometry renders inside Matrix3.
- Adult Link is auto-fitted into the 830 character-scale envelope.
- local RuneScape body suppression is fail-open and runtime accepted.
- OoT RGBA texture data is visibly reaching the Matrix presentation path.
- the synthetic Matrix runtime-material path was rejected; the active renderer uses CPU texture micro-baking.
- V4 filtering/smoothing did not visibly improve the OoT look enough to keep tuning right now.
- the V4 signed-short vertex overflow was fixed by returning to the verified-safe subdivision budget.
- current priority is revision-830 equipment, not further base-Link texture polishing.

No Matrix NPC damage/XP authority is connected to Link B/sword yet.

## Native/provenance boundary

Pinned local native baseline:

- `Cycl0o0/liboot` commit `25208734c8ca388638f0ce63841e166fac9e5acb`
- `zeldaret/oot` commit `269d03016cd0e3d7a0b8925e02b97a319c1d0e8d`
- user ROM: NTSC-U 1.2, 32 MiB, MD5 `57a9719ad547c516342e1a15d5c28c3d`

liboot original code is AGPL-3.0-or-later; selected vendored/decomp material has separate provenance. Local experimental integration may continue, but redistribution/shipping remains a separate review requirement. ROM bytes/assets remain local-only.

## Adult Link world fit — RUNTIME ACCEPTED

`LinkCharacterFit` measures the live 830 local-player rendered height and uniformly scales Adult Link to that envelope.

Key rules:

- one uniform scale preserves OoT proportions;
- calibrated Link floor maps to Matrix local ground;
- native root-height motion remains visible;
- Link's comparatively large OoT head is not a blocker;
- Link is intentionally large because 830 gear should begin close to native Matrix size.

Do not reopen body/head deformation before the equipment socket proof.

## Link renderer baseline

Current renderer stack:

- `native/oot-bridge/oot_bridge.c` — persistent native sidecar;
- `OotBridgeSession` — Java 8 bridge/session owner;
- `LinkCharacterFit` — Adult Link 830-scale calibration;
- `LinkVisualRenderer` — OoT geometry/texture presentation + fail-open replacement readiness;
- `Player.method10696(...)` — existing local body-suppression seam;
- `Class578.method6834(...)` — direct Matrix presentation seam.

The current CPU RGBA bake is visually recognizable but still looks like low-poly OoT rendered through Matrix face colours. User explicitly chose to move on to equipment rather than keep spending time polishing this renderer now.

## Adult Link skeleton/socket V1 — STAGED

This is the equipment architecture for Link.

liboot exports `OoTSkeletonPose` as:

- up to 21 world-space joints;
- a parent index for each joint;
- `jointPos[21][3]` world-space positions;
- semantic indexing deliberately stored as `PLAYER_LIMB_* - 1` for host attachments.

Relevant Adult Link socket indices:

```text
ROOT       0
WAIST      1
LOWER      2
R_THIGH    3
R_SHIN     4
R_FOOT     5
L_THIGH    6
L_SHIN     7
L_FOOT     8
UPPER      9
HEAD      10
HAT       11
COLLAR    12
L_SHOULDER 13
L_FOREARM 14
L_HAND    15
R_SHOULDER 16
R_FOREARM 17
R_HAND    18
SHEATH    19
TORSO     20
```

### Protocol V3

The Matrix sidecar protocol is upgraded from V2 -> V3 for equipment sockets.

Every frame now carries:

1. existing Link state;
2. skeleton availability;
3. skeleton joint count;
4. skeleton parent table;
5. world-space skeleton joint positions;
6. existing geometry/material payload.

The Java bridge validates the joint count/parents and publishes the skeleton arrays inside the immutable `LinkFrame`.

A native rebuild is required once after pulling this protocol change.

### `LinkSkeletonSockets`

`LinkSkeletonSockets` is intentionally independent from Mario equipment code.

It converts liboot world-space joints into the same Matrix-local coordinate system used by `LinkVisualRenderer`/`LinkCharacterFit`.

The first HEAD socket uses:

- `HEAD` for the primary skull anchor;
- `HAT` for animated head-depth/head-pose information;
- `COLLAR` for the head-up chain;
- left/right shoulder joints for a stable fallback plane;
- OoT actor yaw only to resolve axis sign when the positional basis is ambiguous.

This is a real skeleton attachment path, not a geometry bounding-box guess.

## Revision-830 helmet proof — STAGED

`LinkEquipmentAdapter` is the first consumer of the skeleton socket system.

Behavior:

- reads the local RuneScape player's currently equipped visible hat-slot item;
- loads that item's normal revision-830 worn raw model;
- recenters the equipment mesh only;
- keeps default equipment scale at **1.0**;
- attaches the model to Adult Link's live OoT HEAD socket;
- follows the animated skeleton pose while Link moves/acts;
- renders only while Link's own fail-open replacement is healthy;
- does not call `MarioEquipmentAdapter`, `MarioEquipmentWorkbench`, or Mario head-envelope calibration.

First-proof calibration properties:

```text
matrix3.oot.helmetScale=1.0
matrix3.oot.helmetOffsetX=0
matrix3.oot.helmetOffsetY=0
matrix3.oot.helmetOffsetZ=0
matrix3.oot.helmetPitchDegrees=0
matrix3.oot.helmetYawDegrees=180
matrix3.oot.helmetRollDegrees=0
matrix3.oot.headSocketHatBlend=0.35
```

These are diagnostic/calibration knobs, not the intended permanent user workflow. Preserve the first runtime screenshot before tuning them.

## Equipment roadmap

Order after HEAD socket runtime acceptance:

1. helmet / head;
2. right hand -> revision-830 weapon;
3. left hand/forearm -> revision-830 shield;
4. boots -> foot sockets;
5. cape/back item -> sheath/upper-back socket;
6. gloves -> hand sockets;
7. amulet -> collar/upper socket;
8. torso and legs last, using multi-joint fit rather than one rigid socket.

Long-term rule:

```text
Adult Link stays OoT-shaped + 830-sized
        +
830 equipment stays near native Matrix scale
        +
real OoT skeleton sockets drive animation
        +
small saved corrections only where needed
```

## Phases

### Phase 0 — native compatibility

- [x] Exact NTSC-U 1.2 ROM/profile identified.
- [x] NTSC-U 1.2 liboot adaptation/probe.
- [x] Adult/child Link state, geometry, skeleton and animation native proof.
- [x] Local-development provenance boundary recorded.

### Phase 1 — playable Adult Link foundation

- [x] Ctrl+L Link mode.
- [x] Recognizable animated Adult Link in Matrix3.
- [x] Adult Link 830-scale auto-fit.
- [x] fail-open local RuneScape body replacement.
- [x] real OoT texture data visibly reaches Link presentation.
- [x] V4 signed-short renderer overflow corrected.
- [ ] Runtime-prove protocol V3 skeleton stream.
- [ ] Runtime-prove stable HEAD socket during idle/turn/action.

### Phase 2 — OoT movement/world interaction

- [ ] Feed Matrix terrain/objects into OoT collision.
- [ ] Z-targeting/roll/contextual interactions.
- [ ] Preserve Matrix world progression while Link inventory is open.

### Phase 3 — combat/progression

- [ ] Sword swing windows/contact -> Matrix NPC target.
- [ ] RuneScape Attack/Strength/Defence/Constitution authority.
- [ ] data-driven weapon speed/reach/recovery.
- [ ] ranged/magic under same authority boundary.

### Phase 4 — equipment/items/inventory

- [ ] Runtime-accept first revision-830 helmet on real OoT HEAD socket.
- [ ] Weapon socket.
- [ ] Shield socket.
- [ ] Boots/gloves/cape/amulet.
- [ ] Multi-joint torso/legs fitting.
- [ ] Visible Link equipment state + OoT-style non-pausing equipment UI.

## Resume Here

Run the **Adult Link skeleton + 830 helmet proof** in `docs/zelda/TESTLIST.md`.

1. `git pull origin main`.
2. Run root `Native Builder.bat` -> **BUILD OOT** once because protocol is now V3.
3. Equip a normal revision-830 helmet/head-slot item before entering Link mode.
4. Eclipse Refresh -> Clean -> Run.
5. Press Ctrl+L.
6. Preserve the protocol READY lines and the first `[OoT Equipment]` line.
7. Capture a front/three-quarter screenshot before changing any calibration property.

Immediate acceptance target: `protocol v3`, `skeletonJoints=21`, a revision-830 helmet visibly attached near Adult Link's head at `scale=1.0`, and the helmet following Link's animated head/socket instead of remaining at the hidden RuneScape avatar origin.
