# Mario 64 in Matrix3 / Revision 830

## Goal

Bring an actual Mario character experience into Matrix3/revision-830: imported Mario-compatible visual assets plus Mario 64-style movement/action behavior, implemented through Matrix3-native client/server ownership while using the SM64 decomp as the behavior reference.

## Canonical Main-Goal Status

This table is the authoritative user-facing status table for this workstream across chats.

| Main-goal area | Status |
| --- | --- |
| Character asset pipeline | ❌ Not started |
| Alternate movement/controller foundation | 🔵 In Progress |
| Mario 64 movement/action parity | ❌ Not started |
| Platform collision / level interaction | ❌ Not started |
| Multiplayer / server authority integration | ❌ Not started |

## Scope

### In scope

- Matrix3-native alternate player/controller seams for Mario-style behavior.
- Mario model/texture/skeleton/animation conversion and revision-830 presentation once the asset path is investigated.
- Movement/action behavior translated from the SM64 decomp instead of guessed from gameplay footage.
- Progressive support for jump families, running/acceleration, air control, long jump, backflip, side flip, triple jump, wall kick, ground pound, ledges and other selected SM64 actions.
- Platform collision/interaction only when the movement foundation proves it is required.
- Correct client/server authority and multiplayer synchronization after local game feel is established.

### Out of scope for the current POC

- Importing the Mario model or animations.
- SM64 action parity beyond one basic vertical jump.
- Wall/ceiling/platform collision.
- Jumping over RuneScape clipping or landing on roofs/objects.
- Server-authoritative Mario physics or multiplayer replication.
- Replacing normal RuneScape movement globally.

## Architecture / ownership

- Matrix3 remains the engine and architecture authority.
- Revision-830 cache/data remains the asset/data authority where applicable.
- The SM64 decomp is a behavior/reference source, not a second runtime engine embedded into Matrix3.
- Local presentation uses Matrix3's existing player transform and live viewport tick.
- Normal RuneScape X/Z movement, plane, pathfinding, clipping and server position authority remain untouched by the first POC.
- Future Mario control should grow as an isolated alternate-controller path rather than scattering Mario-specific branches throughout `Player`/renderer code.

## Verified foundation

### VERIFIED

- None yet for Mario behavior in Matrix3. The first POC still requires runtime acceptance.

### verified-static

- Local player is `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976`, typed `Player`.
- `Class456.method5394().aClass240_2647` exposes the current player scene transform.
- `Class456.method5395(float,float,float)` writes entity translation and invalidates dependent transform caches.
- `Class611.method7272(...)` is one movement interpolation path that updates X/Z while preserving the current Y value.
- `Class343.method4302(...)` is an established live viewport/render update seam and already drives `ConstructionBuildCamera.tick()`.
- Matrix3 keyboard state is available through `Class108.aClass549_1426.method6514(...)`; `KeyEvent.VK_SPACE` maps to internal key `83` in `Class549_Sub1.anIntArray8901`.
- The current client coordinate convention supports higher altitude through a smaller scene-Y value; stock camera math uses terrain height minus camera height.
- The old 718 jump is reference-only and demonstrated the same high-level pattern: terrain baseline plus temporary vertical displacement without changing plane.

## Unknown / research needed

### HYPOTHESIS

- A clean alternate-character controller can remain largely outside the decompiled player renderer by using established input/tick/transform seams.
- The SM64 action state machine can be translated incrementally into Matrix3 units while retaining recognizable Mario 64 feel.

### UNKNOWN

- Whether every Matrix3 movement/grounding path preserves or cleanly coexists with a temporary custom player Y offset.
- Exact revision-830 model/skeleton/animation conversion path for the Mario asset set.
- Which collision owner is the best long-term foundation for walls, ceilings, ledges and moving platforms.
- Final client/server reconciliation model for responsive multiplayer Mario movement.

## Dependencies

- Matrix3 client scene/player transform ownership.
- Matrix3 keyboard/input state.
- Revision-830 model/animation/cache tooling for later asset work.
- SM64 decomp for behavior/action reference.
- User runtime testing for visual movement/game-feel acceptance.

## Development plan

### Phase 1 - Alternate Controller Foundation

**Purpose:** Prove Matrix3 can support local player behavior that temporarily departs from normal ground-bound RuneScape presentation without replacing the engine.

**Status:** NEEDS TEST

**Exit conditions:**

- Basic jump visibly raises and lands the actual local player model.
- Normal X/Z movement remains usable.
- Slopes/terrain updates do not create persistent height drift.
- Holding Space does not auto-bunny-hop without a release/repress.
- Normal movement remains intact after landing and after relog.

#### Bundle 1.1 - Vertical Jump POC

**Purpose:** Smallest possible proof that the revision-830 client can render the local player above the terrain baseline with time-based gravity.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Trace Matrix3 local-player transform owner. `verified-static`
- [x] Trace live viewport tick and held-key input seam. `verified-static`
- [x] Add isolated `MarioJumpController` with Space edge detection, time-based velocity/gravity and local Y displacement.
- [x] Tick the controller from the established `Class343.method4302(...)` live viewport seam.
- [ ] Runtime verify stationary jump, movement while airborne, slopes, hold/repress behavior and post-landing normal movement.

**Runtime tests:** See `docs/mario/TESTLIST.md`.

#### Bundle 1.2 - Controller Mode Boundary

**Purpose:** After the jump proof is accepted, prevent Mario mechanics from becoming global RuneScape-player behavior and establish a deliberate alternate-controller activation boundary.

**Status:** PLANNED

**Checklist / patches:**

- [ ] Define Matrix3-native controller activation/lifecycle ownership.
- [ ] Add clean reset/transition behavior between normal RuneScape and Mario control.
- [ ] Decide camera ownership required for platforming without replacing vanilla camera behavior outside Mario mode.

### Phase 2 - Mario Character Asset Pipeline

**Purpose:** Render the actual Mario-compatible character asset and its animations through revision-830/Matrix3 presentation.

**Status:** PLANNED

**Checklist / patches:**

- [ ] Trace existing Matrix3/revision-830 custom NPC/model/animation import path.
- [ ] Convert/import the Mario model, textures and skeleton/rig representation.
- [ ] Prove idle/run/jump animation playback on the imported character.
- [ ] Establish repeatable asset conversion documentation/tooling only where the real import exposes a need.

### Phase 3 - SM64 Movement / Action State Machine

**Purpose:** Translate selected SM64 movement/action behavior into the established Matrix3 alternate controller.

**Status:** PLANNED

**Checklist / patches:**

- [ ] Ground acceleration/deceleration and facing.
- [ ] Air control and normal jump.
- [ ] Double/triple jump chain.
- [ ] Long jump, backflip and side flip.
- [ ] Ground pound and landing states.
- [ ] Wall kick and other actions only after collision ownership is established.

### Phase 4 - Platform Collision / Interaction

**Purpose:** Support the world interactions required by real platforming rather than visual-only vertical displacement.

**Status:** PLANNED

**Checklist / patches:**

- [ ] Establish floor/wall/ceiling collision ownership.
- [ ] Slopes and collision normals.
- [ ] Ledges and platform landing.
- [ ] Moving platforms/object interactions where required.
- [ ] Preserve RuneScape clipping/pathing ownership outside Mario control.

### Phase 5 - Multiplayer / Server Authority

**Purpose:** Keep responsive Mario movement while restoring authoritative multiplayer/gameplay validation.

**Status:** PLANNED

**Checklist / patches:**

- [ ] Define movement state sent to/validated by the server.
- [ ] Remote-player replication/interpolation.
- [ ] Reconciliation/correction behavior.
- [ ] Combat/world-interaction authority boundaries.

## Current execution state

- Phase: 1 - Alternate Controller Foundation
- Phase status: NEEDS TEST
- Bundle: 1.1 - Vertical Jump POC
- Bundle status: NEEDS TEST
- Approval state: AAA approved for Jump POC #1
- Current checklist item: Runtime acceptance
- Current objective: Prove the actual local player can jump above terrain and return cleanly without breaking normal movement.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix3 transform/input/tick trace | 1 | 1.1 | DONE | `verified-static`; no runtime claim. |
| Basic vertical jump controller | 1 | 1.1 | NEEDS TEST | Implemented client-side; runtime acceptance pending. |
| Controller mode/lifecycle | 1 | 1.2 | READY | Start only after POC acceptance. |
| Mario asset import | 2 | 2.x | READY | Not started; trace actual 830 asset path first. |

## Decisions / new ideas

### Decision log

- Use Matrix3 as host architecture; SM64 decomp supplies behavior reference.
- Do not embed/run the SM64 C engine inside Matrix3.
- Prove local vertical movement before importing the character or translating the full action state machine.
- Preserve normal RuneScape X/Z movement, plane, clipping and server authority during the first POC.
- Keep Mario work as a separate workstream; Construction Revamp remains the repository's current main workstream unless priority is explicitly changed.

## Testing

### Quick/high-value checks

1. Press/release Space while standing still: player model rises and returns to the same ground baseline.
2. Hold Space through landing: no repeated jump until Space is released and pressed again.
3. Click-walk/run during the jump: normal X/Z movement still works and player lands cleanly.
4. Jump while traversing an uphill/downhill slope: no permanent burial, hovering or cumulative height drift.
5. After landing, normal movement/camera/input remain unchanged.

### Deeper checks

1. Logout/relog after using the jump; normal player state returns without stale airborne state.
2. Repeat jumps at different terrain heights.
3. Verify no plane/floor transition is caused by the POC.
4. Check chat/text entry: Space remains usable; current POC may visually jump while typing because activation gating is intentionally deferred to Bundle 1.2.

### Smoke/regression checks

- Relevant `docs/rs3/SMOKE_TEST.md`: Eclipse Java 8 clean/build, client startup/login, normal movement, logout/relog.

## Carryover / blockers

### CARRYOVER

- Task: Mario model/animations
- Phase/bundle: Phase 2
- Current state: Not investigated in Matrix3 yet.
- Remaining work: Trace the actual revision-830 asset import/render path before choosing a conversion format.
- Next action: Begin after the movement POC and controller boundary are accepted, unless user explicitly reprioritizes.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Static implementation of Jump POC #1: `MarioJumpController` plus one call from the established viewport tick.

**Current phase:**

- Phase 1 - Alternate Controller Foundation (`NEEDS TEST`).

**Active bundle:**

- Bundle 1.1 - Vertical Jump POC (`NEEDS TEST`).

**Next checklist item:**

- Runtime-verify the jump acceptance list.

**Current state / next action:**

- Pull/build/launch; press Space and run the short jump/movement/slope checks. If accepted, mark Bundle 1.1 complete and start Bundle 1.2 controller-mode boundary.

**Files/systems already inspected:**

- `Client/src/main/java/game/Class456.java`
- `Client/src/main/java/game/Class611.java`
- `Client/src/main/java/game/Player.java`
- `Client/src/main/java/game/Entity.java`
- `Client/src/main/java/game/Class343.java`
- `Client/src/main/java/game/Class549_Sub1.java`
- `Client/src/main/java/game/ConstructionBuildCamera.java`
- Old 718 `FPSJump` implementation as reference only.

**Do not re-scan without new evidence:**

- Local-player transform ownership (`Class611` -> `Player` -> `Class456`).
- Live viewport tick (`Class343.method4302`).
- Held-key state and Space mapping (`Class549_Sub1`, internal key 83).

**Pending runtime verification:**

- Jump direction/height/landing.
- X/Z movement while airborne.
- Slope baseline adaptation.
- Space hold/repress behavior.
- Post-landing and logout/relog regression.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Other Matrix3 movement/grounding paths may overwrite Y differently at runtime; the POC deliberately detects external Y writes and treats them as a new terrain baseline, but this must be observed in-game.

## Next recommended work

Runtime-accept Jump POC #1, then establish the explicit Mario controller-mode/lifecycle boundary before expanding movement mechanics.
