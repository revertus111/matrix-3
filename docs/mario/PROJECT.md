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

### Out of scope for the current controller-foundation slice

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
- Normal RuneScape X/Z movement, plane, pathfinding, clipping and server position authority remain untouched by the current client-side proof.
- `PlayerControllerMode` owns deliberate local controller selection; RuneScape is the default and Mario is opt-in.
- `MarioJumpController` owns only the current Mario vertical-jump proof and lifecycle/reset behavior.
- Future Mario control should continue behind this alternate-controller boundary instead of scattering Mario-specific branches throughout `Player`/renderer code.

## Verified foundation

### VERIFIED

- User runtime testing on 2026-10-03 confirmed the actual revision-830 local player visibly leaves RuneScape terrain with the Matrix3-native jump proof.
- This runtime result confirms the existing local-player transform can present temporary vertical displacement without changing RuneScape plane ownership.
- Deeper movement/slope/hold/relog regression checks remain pending and are not implied by the visual proof.

### verified-static

- Local player is `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976`, typed `Player`.
- `Class456.method5394().aClass240_2647` exposes the current player scene transform.
- `Class456.method5395(float,float,float)` writes entity translation and invalidates dependent transform caches.
- `Class611.method7272(...)` is one movement interpolation path that updates X/Z while preserving the current Y value.
- `Class343.method4302(...)` is an established live viewport/render update seam and drives `MarioJumpController.tick()`.
- Matrix3 keyboard state is available through `Class108.aClass549_1426.method6514(...)`; Space maps to internal key `83`, Ctrl to `82`, and M to `70` in `Class549_Sub1.anIntArray8901`.
- The current client coordinate convention supports higher altitude through a smaller scene-Y value; stock camera math uses terrain height minus camera height.
- `PlayerControllerMode` defaults to `RUNESCAPE`, toggles `MARIO` with Ctrl+M using rising-edge detection, and exposes an explicit mode API for later UI/tool activation.
- `MarioJumpController` resets controller/jump state when the local `Player` object changes and restores the tracked ground baseline when Mario mode is disabled midair.
- The old 718 jump remains reference-only; Matrix3 owns the current implementation.

## Unknown / research needed

### HYPOTHESIS

- The SM64 action state machine can be translated incrementally into Matrix3 units while retaining recognizable Mario 64 feel.
- The current controller-mode boundary can remain the local activation owner while later movement behaviors are split into dedicated Mario action/state components.

### UNKNOWN

- Whether all remaining Matrix3 movement/grounding paths coexist cleanly with custom airborne Y while walking/running across slopes.
- Exact revision-830 model/skeleton/animation conversion path for the Mario asset set.
- Which camera ownership seam is best for platforming without changing vanilla RuneScape camera behavior outside Mario mode.
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

**Status:** ACTIVE

**Exit conditions:**

- Basic jump visibly raises and lands the actual local player model.
- Normal X/Z movement remains usable.
- Slopes/terrain updates do not create persistent height drift.
- Holding Space does not auto-bunny-hop without a release/repress.
- Normal movement remains intact after landing and after relog.
- Mario behavior is opt-in behind an explicit controller-mode lifecycle boundary.

#### Bundle 1.1 - Vertical Jump POC

**Purpose:** Smallest possible proof that the revision-830 client can render the local player above the terrain baseline with time-based gravity.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Trace Matrix3 local-player transform owner. `verified-static`
- [x] Trace live viewport tick and held-key input seam. `verified-static`
- [x] Add isolated `MarioJumpController` with Space edge detection, time-based velocity/gravity and local Y displacement.
- [x] Tick the controller from the established `Class343.method4302(...)` live viewport seam.
- [x] Runtime-confirm the core visual proof: actual local player visibly leaves terrain. `VERIFIED`
- [ ] Finish regression acceptance: movement while airborne, slopes, hold/repress behavior, post-landing movement and relog.

**Runtime tests:** See `docs/mario/TESTLIST.md`.

#### Bundle 1.2 - Controller Mode Boundary

**Purpose:** Prevent Mario mechanics from becoming global RuneScape-player behavior and establish a deliberate alternate-controller activation/lifecycle boundary.

**Status:** ACTIVE

**Checklist / patches:**

- [x] Define Matrix3-native controller activation/lifecycle ownership with `PlayerControllerMode`.
- [x] Default to `RUNESCAPE`; add developer Ctrl+M rising-edge toggle for `MARIO` mode.
- [x] Gate Space-driven Mario jump behavior behind Mario mode.
- [x] Add clean midair disable reset and local-player lifecycle reset to RuneScape mode.
- [ ] Runtime verify mode gating, midair disable and relog lifecycle.
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
- Phase status: ACTIVE
- Bundle: 1.2 - Controller Mode Boundary
- Bundle status: ACTIVE
- Approval state: `SAP AAA` approved for the next controller-boundary slice on 2026-10-03.
- Current checklist item: Runtime verify controller-mode gating/lifecycle, then decide Mario-mode camera ownership.
- Current objective: Keep normal RuneScape behavior default while proving Mario mechanics can be deliberately entered/exited without stale airborne state.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix3 transform/input/tick trace | 1 | 1.1 | DONE | Static ownership established; transform jump now has runtime visual proof. |
| Basic vertical jump controller | 1 | 1.1 | NEEDS TEST | Core visual lift is `VERIFIED`; movement/slope/hold/relog regression remains. |
| Controller mode/lifecycle | 1 | 1.2 | NEEDS TEST | `PlayerControllerMode`, Ctrl+M gate, midair reset and player-lifecycle reset implemented statically. |
| Mario-mode camera ownership | 1 | 1.2 | READY | Decide after controller-mode runtime acceptance. |
| Mario asset import | 2 | 2.x | READY | Not started; trace actual 830 asset path first. |

## Decisions / new ideas

### Decision log

- Use Matrix3 as host architecture; SM64 decomp supplies behavior reference.
- Do not embed/run the SM64 C engine inside Matrix3.
- Prove local vertical movement before importing the character or translating the full action state machine.
- Preserve normal RuneScape X/Z movement, plane, clipping and server authority during the controller foundation.
- RuneScape control is the default; Mario behavior is opt-in rather than global.
- Ctrl+M is a developer activation seam for the current foundation, not a final player-facing UX commitment.
- Keep Mario work as a separate workstream; Construction Revamp remains the repository's current main workstream unless priority is explicitly changed.

## Testing

### Quick/high-value checks

1. Default/login state: Space does not Mario-jump.
2. Ctrl+M -> `MARIO`; Space performs the already-proven vertical jump.
3. Ctrl+M again -> `RUNESCAPE`; Space no longer Mario-jumps.
4. Disable Mario mode while airborne: player returns to the tracked ground baseline cleanly.
5. Logout/relog after Mario use: controller starts in RuneScape mode with no stale airborne state.
6. Finish Bundle 1.1 carryover checks for airborne X/Z movement, slopes and hold/repress behavior during the same session.

### Deeper checks

1. Repeat jumps at multiple terrain elevations.
2. Verify no plane/floor transition is caused by the POC.
3. Verify normal camera/interactions remain unchanged in RuneScape mode.

### Smoke/regression checks

- Relevant `docs/rs3/SMOKE_TEST.md`: Eclipse Java 8 clean/build, client startup/login, normal movement, logout/relog.

## Carryover / blockers

### CARRYOVER

- Task: Jump POC deeper regression
- Phase/bundle: Phase 1 / Bundle 1.1
- Current state: Core visual lift is runtime-confirmed.
- Remaining work: hold/repress, X/Z movement while airborne, slopes, post-landing movement, plane and relog checks.
- Next action: Consolidate with Bundle 1.2 runtime test session.

- Task: Mario model/animations
- Phase/bundle: Phase 2
- Current state: Not investigated in Matrix3 yet.
- Remaining work: Trace the actual revision-830 asset import/render path before choosing a conversion format.
- Next action: Begin after the controller foundation/camera boundary is accepted unless the user explicitly reprioritizes.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Runtime-confirmed the core vertical jump proof.
- Implemented `PlayerControllerMode` plus Ctrl+M Mario-mode gating, midair reset and local-player lifecycle reset.

**Current phase:**

- Phase 1 - Alternate Controller Foundation (`ACTIVE`).

**Active bundle:**

- Bundle 1.2 - Controller Mode Boundary (`ACTIVE`).

**Next checklist item:**

- Runtime-verify Ctrl+M gating/reset/lifecycle and finish the short Bundle 1.1 regression carryover in the same launch.

**Current state / next action:**

- Pull/build/launch. Confirm Space is inert in RuneScape mode, Ctrl+M enables Mario jumping, disabling midair restores ground, and relog returns to RuneScape mode. If accepted, decide the Mario-mode camera boundary next.

**Files/systems already inspected:**

- `Client/src/main/java/game/Class456.java`
- `Client/src/main/java/game/Class611.java`
- `Client/src/main/java/game/Player.java`
- `Client/src/main/java/game/Entity.java`
- `Client/src/main/java/game/Class343.java`
- `Client/src/main/java/game/Class549_Sub1.java`
- `Client/src/main/java/game/ConstructionBuildCamera.java`
- `Client/src/main/java/game/MarioJumpController.java`
- `Client/src/main/java/game/PlayerControllerMode.java`
- Old 718 `FPSJump` implementation as reference only.

**Do not re-scan without new evidence:**

- Local-player transform ownership (`Class611` -> `Player` -> `Class456`).
- Live viewport tick (`Class343.method4302`).
- Held-key state and normalized Space/Ctrl/M mappings (`Class549_Sub1`).
- Basic vertical-displacement feasibility in revision 830; the actual player visibly left terrain at runtime.

**Pending runtime verification:**

- Ctrl+M mode gating and rising-edge behavior.
- Midair Mario-mode disable baseline restore.
- Player-lifecycle/relog reset to RuneScape mode.
- Bundle 1.1 airborne movement/slope/hold/repress regression carryover.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Sloped-terrain movement may expose a different Y writer while airborne; this remains a focused runtime regression question rather than a reason to reopen the transform trace.

## Next recommended work

Runtime-accept the controller-mode boundary, then decide Mario-mode camera ownership before expanding into the character asset pipeline or full SM64 action controller.
