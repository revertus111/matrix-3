# Matrix3 Construction Revamp

## Goal

Turn Construction into a persistent freeform settlement/factory-style system built natively on Matrix3, while preserving Matrix3 core ownership for maps, objects, persistence, NPCs, combat, interfaces, and cache data.

The player should be able to build a settlement wall-by-wall, recruit and train flexible workers, gather/haul/process resources, automate logistics, expand the settlement, and later connect the system to overworld Construction projects and a settlement economy.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Freeform settlement building foundation | 🔵 In Progress |
| Starter resource loop and shelter milestone | ❌ Not started |
| First worker, Allowed Jobs, gathering and hauling | ❌ Not started |
| Worker needs, storage and settlement recovery | ❌ Not started |
| Persistence and Construction XP ownership | ❌ Not started |
| Population, processing and logistics expansion | ❌ Not started |
| Settlement Wealth, offline production and economy | ❌ Not started |
| Overworld Construction integration | ❌ Not started |

## Current status

- Repository authority: `revertus111/matrix-3`, branch `main`.
- Runtime foundation: protected Matrix3 baseline `e86851b95e1d2927d58463b67f600153b9166f6a` plus the restored pre-reset feature stack.
- State: ACTIVE — Construction Editor and custom Construction palette/selected-piece/hover foundation are runtime verified; the direct-render 3D ghost, white/translucent styling and preview-vs-placed alpha isolation are runtime verified. Legacy Orb and generic-renderer camera attempts are runtime-rejected. Runtime CAM DEBUG plus source tracing have now identified Matrix3's real detached developer camera as the Class24/Class411 path, and Construction Free Build has been reimplemented by reusing that owner; runtime acceptance is pending.
- Construction Editor implementation: `09cd35fec87defd0f49ef8000f49eca3523f112e`.
- Custom Construction palette foundation implementation: `a2ce37439896d77d257d0966463104fcb962803f`.
- Visible 3D ghost base-render fix is runtime verified after `014a133f02c6e72c3bac08ea26f5c6bd98ebeb3d`; white/translucent styling and the `0x100` private-alpha isolation fix are also runtime verified after a full client restart.
- Construction no longer invokes Matrix3's legacy Orb-of-Oculus interface path because the current client/cache rejects legacy root/component `475/57` with `ArrayIndexOutOfBoundsException: 57` during `SET_INTERFACE` processing.
- Construction Free Build now activates/reuses `Class24.aClass411_Sub1_158`, the same detached Class411 camera used by Matrix3's developer freecam. Construction adds WASD, Shift/Ctrl speed modifiers and Q/E vertical movement inside the existing `Class24.method711()` update path while preserving engine mouse-look and freecam ownership.
- The current Client Console Construction Editor remains a developer/debug harness; the custom in-game palette is the intended player-facing selection direction.
- The prior 718/legacy Construction implementation is reference material only and must not be transplanted as architecture.
- First playable target: Phase 1 MVP vertical slice.

## Vision

Construction becomes both a RuneScape skill and a persistent settlement-management system.

The intended loop is:

`Build -> recruit/train workers -> gather -> haul -> process -> automate -> expand -> trade -> optimize -> unlock world/settlement milestones`

The system should remain useful long-term instead of becoming a short furniture-training path.

---

# 1. Core Building System

## Accepted — B01-B25

- B01: Start with a mostly empty instanced settlement plot.
- B02: Players place walls one piece at a time instead of selecting predefined rooms.
- B03: Wooden walls, wooden floors and a basic doorway are available from Construction level 1.
- B04: Placed pieces can be rotated before placement.
- B05: A ghost/blueprint preview shows placement before confirmation.
- B06: Building consumes real materials such as logs, planks, stone, bars, cloth or piece-specific materials.
- B07: Removing a structure returns only part of its materials when appropriate.
- B08: Exact layouts persist, including walls, furniture, tracks, machines and other placed pieces.
- B09: Usable plot size expands through Construction progression/upgrades.
- B10: Additional terrain/floor sections can eventually unlock inside the same settlement.
- B11: Workstations are physical placeable objects, not menu-only upgrades.
- B12: A workstation functions only when its required inputs, storage and assigned worker are available.
- B13: Production buildings can use physical input/output storage.
- B14: Workers physically move between resource nodes, storage, workstations and transport points.
- B15: Early production is manual and inefficient; automation improves throughput rather than replacing the system instantly.
- B16: Wood chain can progress from hand-cut logs -> hauling -> saw/crafting station -> processed building materials.
- B17: Mining chain can progress from hand mining -> hauling -> cart loading -> smelting -> bars -> stronger structures.
- B18: Food chain can progress from fishing/hunting/farming -> raw storage -> cooking -> dining/food storage.
- B19: Settlement-produced materials feed back into settlement construction and expansion.
- B20: Higher Construction levels unlock better materials/infrastructure rather than predefined layouts.
- B21: Decorative objects remain optional and do not block functional progression.
- B22: Doors, windows, fences, gates, stairs and roof pieces use the same placement system as walls.
- B23: Rooms are detected from player-built walls/floors rather than selected room templates.
- B24: Room purpose can emerge from placed contents, e.g. beds -> housing, cooking stations -> kitchen/food hall.
- B25: Players can build decorative homes, industrial factories or hybrids on the same plot.

---

# 2. Worker Needs, Traits, Skills and Logistics

## Accepted — W01-W20

- Workers have visible traits that influence behavior without locking professions.
- Traits should usually be tradeoffs rather than simple good/bad rolls.
- Workers gain supported RuneScape-style skill levels by doing those jobs.
- A Hauling/Logistics proficiency may improve carrying performance.
- Worker progression persists through reassignment.
- Workers can be manually assigned to jobs, work areas or resource nodes.
- Later automation can let workers fill critically understaffed jobs automatically.
- Food has tiers; cooked food is substantially better than emergency raw food.
- Water starts simple and may later progress into wells, barrels, tanks, pipes or similar infrastructure.
- Better beds/housing restore energy faster and reduce downtime.
- Workers physically travel to needs, workstations and storage.
- Storage has real capacity.
- Workers carry resources manually before transport automation is unlocked.
- Minecarts use physical loading/unloading points.
- Track routes/junctions can later support more complex logistics.
- Automation frees labor rather than eliminating NPC workers.
- A later management panel exposes shortages, idle workers and bottlenecks.

### Needs behavior

- Workers automatically eat, drink and sleep.
- Needs do not routinely kill workers.
- Unsatisfied critical needs reduce productivity and eventually stop work.
- When supplies recover, workers automatically resume valid work.
- Complete supply-chain collapse must remain recoverable through active player gathering.

---

# 3. Worker AI and Progression

## Accepted

- Each worker has permanent personal skill progression.
- Workers gain XP by performing jobs.
- Higher skill improves job effectiveness where appropriate.
- Workers are never permanently profession-locked.
- Long-term training naturally makes workers more valuable at jobs they practice.
- Traits can affect needs, movement, learning, carrying, work duration and routines.
- Rare positive traits may exist but are never required for a viable settlement.
- Workers use an **Allowed Jobs checklist** instead of rigid professions.
  - All checked -> worker may perform any valid job.
  - One checked -> worker effectively specializes in that job.
  - Any subset is valid.
- Idle workers automatically seek valid work from their allowed jobs.
- Global settlement emergencies may override normal work where appropriate.
- Workers finish sensible current actions before switching jobs.
- AI should avoid constant job thrashing.
- Workers may have a small carried-resource inventory.
- Blocked workers seek another valid destination where possible.
- Idle/waiting reasons should be readable: No Food, No Storage, No Ore, No Path, Needs Rest, etc.
- Management UI can group workers by status.
- Workers can be renamed.
- Useful tools/equipment can improve job performance.
- Routine failures do not permanently destroy trained worker progression.
- Dangerous activity may temporarily injure/disable workers.
- Recovery occurs through rest, food or later healing infrastructure.
- Worker capacity grows through housing/Construction/settlement progression.

## Later

- Multiple simultaneous worker traits.
- Cosmetic task-based worker clothing/equipment.

## Cut / replaced

- Per-worker ordered job priority list is replaced by Allowed Jobs + global/emergency logic.
- No fixed requirement that every recruited worker must begin weak.

---

# 4. Population, Housing and Settlement Growth

## Accepted

- Start with a very small workforce.
- New workers are recruited; Construction levels do not simply spawn workers.
- Some workers may arrive naturally once the settlement is developed enough.
- Special workers may later come from quests, bosses, achievements or exploration.
- Workers remain generalists even when traits/background differ.
- Housing creates real population capacity.
- Basic huts provide early housing; better housing improves recovery/comfort.
- Housing is freeform construction, not a predefined Worker House room.
- Beds can directly contribute capacity.
- Dormitory-style housing is valid.
- Population capacity depends on both housing and progression.
- Settlement Rating can reflect housing, supplies, storage and infrastructure and influence recruitment opportunities.
- Morale/happiness, if added, must remain lightweight.
- Repeated shortages may reduce productivity/recruitment attraction but do not routinely delete workers.
- Players may dismiss workers with a clear progression-loss warning.
- Settlement upgrades enlarge actual physical build area.
- Long-term progression: tiny wooden shelter -> working camp -> settlement -> industrial compound.

---

# 5. Jobs, Gathering and Production Chains

## Accepted

- Allowed Jobs is the core worker permission model.
- Mining -> ores/resources.
- Woodcutting -> logs.
- Fishing -> fish.
- Hunting -> meat/hunting resources.
- Farming -> planted/maintained/harvested crops.
- Cooking -> raw ingredients to prepared food.
- Smithing -> ore/bars to processed materials.
- Crafting -> wood/cloth/leather/misc production.
- Construction/Building -> consumes produced materials to build/upgrade placed structures.
- Hauling -> moves resources between nodes, storage, workstations, carts and outputs.
- Workers can gather and haul their own output when no dedicated hauler exists, but specialized logistics becomes more efficient later.
- Resource nodes can have work areas/linked destinations.
- Storage can filter accepted resource types.
- Workers choose nearby valid storage if no explicit destination exists.
- Production stations can link input/output storage.
- Production stations expose recipes/allowed outputs.
- Workers repeat selected recipes while inputs/capacity allow.
- Multi-ingredient recipes are valid.
- Prefer existing RuneScape resources/assets before inventing custom materials.
- Workers can use normal tools such as pickaxes, axes and fishing equipment.
- Central tool storage may allow automatic appropriate-tool selection later.

## Settlement economy boundary

- No normal player-bank dump into settlement storage to bypass progression.
- Settlement resources are not generally withdrawable into normal RuneScape gameplay.
- The player may personally gather resources inside the settlement ecosystem.

---

# 6. Settlement Start, Offline Progress and Recovery

## Accepted

- Player begins alone with only minimum starter infrastructure.
- Player performs early gathering/building personally before automation scales up.
- Settlement storage is separate from the normal bank.
- Resource nodes regenerate.
- Wood, food, basic stone and basic ore become available early enough that the settlement cannot be permanently bricked.
- Emergency food must always be recoverable through active play.
- UI should expose why a production chain stopped.
- Workers continue operating while the player is elsewhere in the game world.
- Offline progress exists and is capped.
- Offline production consumes real food, water, materials and capacity.
- Chains stop when a real required input runs out.
- Workers do not starve/die/disappear while offline.
- Workers with unmet offline needs become inactive until support returns.
- Login summary can show production, consumption and bottlenecks.
- Player can disable offline production/pause settlement.
- Worker skill XP can continue during valid offline work, subject to caps.
- Major progression still requires active player building/unlocks.

### Settlement Wealth / Production Rating shortcut

Do not literally simulate every NPC movement while offline.

Use Settlement Wealth / Production Rating as a throughput shortcut, while real stored inputs remain the hard limit.

Example: theoretical production supports 12 hours, but food/raw inputs support only 3 hours -> only 3 hours are produced.

---

# 7. Construction XP and Long-Term Progression

## Accepted

- Productive workers generate modest Construction XP.
- Idle workers generate no passive Construction XP.
- More productive workers can increase passive XP with diminishing returns.
- Active building and upgrades grant direct Construction XP.
- Settlement land expansions grant large one-time XP rewards.
- Automation, production, population and overworld milestones can grant achievement XP.
- First completion of an overworld project is worth more than repeat repairs.
- Offline Construction XP follows valid offline production and stops with the underlying chain.
- Offline XP remains weaker than active play over the same broad period.
- Construction is intentionally slow and long-term.
- Post-99 settlement progression can continue through achievements, Settlement Wealth, expansions and unlocks without requiring the normal skill cap to exceed 99.
- Exact rates/weights remain configurable and are tuned through testing.

---

# 8. Settlement Wealth / Production Rating

- Visible development/capability score; not GP and not spendable.
- Can consider infrastructure, workforce, worker skills, housing, storage, production capacity and logistics.
- Used as an offline throughput shortcut, never as a source of free resources.
- Real inputs and storage remain hard constraints.
- Exact formula, displayed categories, diminishing returns and offline cap remain configurable/open until testing.

---

# 9. Settlement Economy, Gold and Exchange

## Settlement Gold

- Settlement-produced goods can be sold for Settlement Gold.
- Settlement Gold is separate from normal RuneScape GP.
- It is used for workers, upgrades, blueprints, tools, logistics, market purchases and settlement progression.
- Normal GP cannot be converted into Settlement Gold.
- Settlement Gold may convert one-way into normal GP with configurable fee and daily/weekly cap.

## Settlement Exchange

- Dedicated GE-style interface separate from the normal GE.
- Uses Settlement Gold and settlement resources.
- Supports Buy, Sell, My Offers, Trade History, Market Prices and later Delivery/Caravan status.
- NPC settlements and player settlements can both supply liquidity.
- NPC settlements remain available even with low player population.
- Pricing uses specialties + supply/demand, not random rerolls.
- Market display should expose guide price, best buy, best sell and recent trend/history.
- Early trades use timed delivery; later caravans/carts/logistics reduce time and increase capacity.

---

# 10. Overworld Construction

- Normal RuneScape world gains predefined Construction Build Sites.
- Sites may contain broken/unfinished infrastructure such as banks, bridges, shortcuts, shelters, fires, crafting stations, storage, docks and gathering support.
- World sites use fixed valid locations; no arbitrary world-object placement.
- Prefab shelters may exist at designated outdoor/camp sites.
- World projects award Construction XP and achievement progress.
- Some structures may degrade/reset to remain repeatable while important upgrades may be permanent.
- Overworld Construction should improve gameplay without becoming mandatory.
- Settlement progression and overworld Construction can feed each other without letting the normal bank bypass settlement progression.

---

# 11. First-Settlement Bootstrap

## Accepted opening flow

1. Enter starter settlement area.
2. Gather initial resources personally.
3. Build starter shelter and minimum support infrastructure.
4. Completing the starter milestone attracts the first worker automatically.
5. Worker #1 introduces Allowed Jobs, needs, gathering, hauling and automation.

Starter resource access includes wood, food, basic stone and basic ore immediately so the player can choose priorities instead of following a single forced chain.

---

# 12. Combat — Deferred

Combat is intentionally not an active implementation priority for the first Construction phases.

Future direction may include combat as another Allowed Job, guard/patrol areas, worker combat progression, defensive structures, optional danger zones and raids. Routine settlement combat must not permanently erase trained workers.

---

# 13. Design Principles

1. Useful, not annoying.
2. Recoverable failure.
3. Workers are persistent characters.
4. No fixed professions.
5. Physical systems first.
6. Automation frees labor.
7. Settlement economy remains meaningful.
8. Slow long-term progression.
9. Overworld relevance.
10. Trade without bypassing progression.
11. Configurable where practical.
12. Matrix3-native ownership; 718 is reference-only.

---

# 14. Approved Implementation Roadmap

## Phase 1 — MVP Vertical Slice

**Status:** ACTIVE

### Required behavior

1. Enter a Construction settlement instance.
2. Place/remove/rotate basic wooden walls, floors and a doorway.
3. Gather wood, food, stone and basic ore inside the settlement.
4. Complete starter-shelter milestone.
5. First worker arrives.
6. Worker has Allowed Jobs checklist.
7. Worker can gather and haul resources.
8. Worker automatically satisfies hunger, thirst and energy.
9. Basic settlement storage exists.
10. Exact settlement layout/state persists through save/load.
11. Basic Construction XP follows the approved active/passive ownership rules.

### Explicitly deferred from Phase 1

- Minecarts/tracks.
- Settlement Exchange/trading.
- Offline production simulation.
- Worker combat/raids/guards.
- Advanced automation.

### Bundle 1.1 — Matrix3 ownership and foundation discovery

**Status:** ACTIVE

- [x] Confirm correct repository/branch and protected baseline.
- [x] Confirm no current `docs/construction_revamp/PROJECT.md` exists in Matrix3.
- [x] Targeted default-branch search found no indexed `Construction` implementation symbol.
- [x] Confirm Matrix3 player layer contains its own `ControlerManager`, `Player`, content and controllers packages; legacy 718 paths are not authoritative.
- [x] Inspect the narrow Matrix3 Construction/player/controller/placement path needed to classify current ownership: classic `House`/`HouseControler` exists, `Player` persists `House`, and the restored tree contains no freeform settlement-state foundation.
- [ ] Produce the exact persistent Phase 1 Bundle 1.2 settlement-state/instance file plan before modifying server-owned Construction runtime.

### Bundle 1.2 — Freeform placement foundation

**Status:** READY

- Persistent plot-relative placement state.
- Matrix3-native dynamic/instanced settlement projection.
- Definition-driven wall/floor/door pieces.
- Placement preview, rotation, confirmation and removal.
- Minimal entry/exit path for runtime testing.
- No 718 controller/code transplant.

### Construction Editor developer prototype

**Status:** RUNTIME VERIFIED

- Client Console has a lazy Construction Editor panel for owner/admin development use.
- Placement reuses verified Matrix3 Dev Mode tile resolution and the existing server-authoritative `itembrowser devspawn` path.
- Paint placement works through normal left-click world tiles.
- Continuous/right-click placement integration is present and uses the existing Dev placement menu path.
- Fixed rotation works from the editor; runtime test showed rotation state changing through the live panel.
- Existing Dev Mode object actions expose move, rotate left/right, duplicate, inspect/edit, copy id/tile and delete for placed Dev-owned runtime objects.
- Cancel Placement and Place Last are exposed in the live world menu while placement is armed.
- Normal RuneScape world/object options remain available alongside the Dev actions.
- Runtime-observed provisional mappings:
  - `13450`, type `0` -> `Wooden fence`; placement works, but this is not accepted as the final wooden-wall asset.
  - `13684`, type `22` -> `Floor decoration`; placement works as the current floor candidate.
  - `13344`, type `0` -> `Door`; placement and object interaction work as the current doorway candidate.
- The current editor remains a developer/debug harness; players should not need to enter raw object ids/types in the final Construction workflow.
- This tooling track does not satisfy Bundle 1.2 persistence, occupancy, settlement ownership, or final player-facing validity feedback.

### Next tooling slice — Custom Construction Palette + Preview Foundation

**Status:** PALETTE + WHITE/TRANSLUCENT GHOST + ALPHA ISOLATION RUNTIME VERIFIED — NATIVE FREE BUILD V1 IMPLEMENTED / RUNTIME ACCEPTANCE PENDING

Runtime-verified palette foundation:

- Custom-drawn in-game Construction object palette launched from the Construction Editor.
- Walls, Floors and Doors category tabs.
- Search/filter across piece name, category, object id and technical note.
- Shared selected-piece state containing object id, object type, rotation, category and placement mode.
- Selecting a palette entry immediately arms the already-proven Dev placement authority.
- Read-only hovered-world-tile tracking from Matrix3's verified action-23 menu-entry seam.
- Paint/Continuous controls, Rotate - / Rotate +, R / Shift+R and mouse-wheel rotation while the custom palette is active.
- Escape/Cancel/close clears placement instead of leaving a hidden armed state.
- Palette clicks are consumed only inside the palette; normal world clicks remain owned by Matrix3 outside it except when Free Build Paint deliberately consumes a valid placement click.
- The starter catalog remains explicit about current runtime evidence: Wooden fence is a temporary wall test, Floor decoration is the floor candidate, and Door is the doorway candidate.

3D ghost preview v1 state:

- `ConstructionGhostPreview` reads the selected piece, hovered world tile and rotation from `ConstructionPlacementController`.
- Active `Class523` scene and object definitions are resolved through the live Matrix3 scene stack.
- Preview model creation uses the same verified-static object-model path as Matrix3 scene objects (`ObjectDefinitions.method6057(...)`).
- Initial runtime attempt through pre-scene `Class110.method2071(...)` produced no visible ghost; palette/hover/confirmed placement remained functional.
- Post-scene rendering from `Class343.method4302(...)` plus Matrix3 per-tile `Class86` renderer-environment setup still produced no visible ghost.
- Matrix3's real draw contract was then mirrored with a reusable `Class90` bounds object for ordinary definitions and `Class106.method1738(...)` for `aClass326_5684` definitions; runtime retest with a valid displayed target tile still produced no visible ghost.
- On-screen diagnostics runtime-proved the active ghost hook and exposed an invalid footprint decode: `ObjectDefinitions.sizeX/sizeY` had been read using storage multipliers instead of their inverse decode multipliers.
- Correcting the footprint decode to `sizeX * -876498849` / `sizeY * 1922784011` allowed the Wooden fence preview to render visibly on the hovered tile; runtime diagnostic reached `DRAW_SUBMITTED object=13450 type=0 rot=0 specialBounds=false`.
- The base direct-render ghost pipeline is therefore runtime VERIFIED: hover state, bounds, definition lookup, model build and `Model.method1375(...)` submission all complete to a visible model.
- The first colour override appeared red because the weight 160 overshot `AbstractModel.method1396(...)`'s `>> 7` interpolation scale. Weight 128 now reaches the intended white/washed-out target and is runtime verified.
- Face alpha 96 through `Model.method1467(byte, byte[])` now produces the intended translucent ghost at runtime.
- A runtime regression proved preview alpha could leak into placed/cached models when the clone shared face-alpha backing data. Adding the `0x100` model capability forces private alpha storage; after a full client restart the ghost remained white/translucent while the placed Wooden fence remained normal brown/opaque. Preview-vs-placed alpha isolation is runtime VERIFIED.
- The preview remains unregistered with `Class523`; it has no collision, persistence, server ownership, or persistent world state.

Construction Build Camera v1 state:

- The first implementation reused `InterfaceManager.gazeOrbOfOculus()` through the owner-only `itembrowser constructioncamera` bridge; runtime proved that legacy root/component `475/57` path crashes this client and it remains permanently rejected for Construction.
- Bounded current-client tracing established the live generic renderer camera transform in `Class343.method4302(...)`: X `Class36.anInt387`, Y `Class572_Sub13_Sub2.anInt11451`, Z `Class49.anInt490`, pitch `Class455.anInt5187`, yaw `Class406.anInt4765`.
- Matrix3 modes 1/2/4/6 have dedicated update behavior; Free Build temporarily uses a Construction-only generic camera mode so rendering consumes those verified globals without installing another root interface.
- `ConstructionBuildCamera` snapshots the prior raw camera mode/transform, activates native Free Build when the palette opens, updates the renderer camera at ~60 Hz and restores the exact prior state on palette close.
- Implemented controls: WASD relative to current yaw, smooth acceleration/deceleration, Shift fast, Ctrl precision, Q/E vertical and right-mouse drag yaw/pitch with Matrix3's 1024-3072 pitch clamp.
- Left-click zeros camera velocity immediately.
- Free Build Paint uses Matrix3's already-resolved hovered tile and calls the existing `DevSpawnPlacement.placeActive(...)` path; the canvas event is consumed to avoid intentionally forwarding the same click into Walk Here.
- Real object creation remains server authoritative through the existing owner-only `itembrowser devspawn` bridge; Free Build performs no second scene pick and owns no world object.
- Server `itembrowser constructioncamera` remains fail-closed so the rejected Orb path cannot be re-entered accidentally.
- RTS, Top Down, Orbit/Focus and Player View remain accepted next views; deterministic preset values are deferred until Free Build runtime behavior is accepted.

Still pending in this slice:

- Runtime acceptance that opening the palette activates native Free Build with no root-interface change or crash.
- Runtime acceptance of WASD direction, Shift/Ctrl speeds, Q/E vertical sign, right-drag feel, acceleration/deceleration and exact normal-camera restore on close/reopen.
- Runtime acceptance that Paint click consumption keeps the player stationary and produces exactly one authoritative object; if the AWT consumed event still leaks to Matrix3 action 23, move that suppression to the already-verified menu-action seam.
- Runtime verification that hover/white ghost, rotation 0-3, model switching, terrain alignment, cancel/stale-hover cleanup and one-preview-per-cycle behavior remain stable while detached.
- Valid/invalid placement feedback tied to future settlement occupancy/rules.

Later interaction polish after single-piece preview is stable:

- Click/inspect an already placed Construction piece for move/rotate/duplicate/remove actions through Construction ownership.
- Click-drag wall runs.
- Corner-to-corner or drag area floor filling.
- Material/variant switching where supported by definitions.

### Bundle 1.3 — Starter resource loop

**Status:** PLANNED

- Wood, food, stone and ore gathering inside settlement.
- Settlement-only storage boundary.
- Starter shelter milestone.

### Bundle 1.4 — First worker vertical slice

**Status:** PLANNED

- First worker arrival.
- Allowed Jobs.
- Gathering/hauling.
- Hunger/thirst/energy.
- Persistence and basic XP.

## Phase 2 — Population + broader survival production

- Additional workers/recruitment.
- Housing/beds/population capacity.
- Cooking, farming and hunting.
- Worker management/status UI.

## Phase 3 — Processing chains + better materials

- Smithing/crafting.
- Processed construction materials.
- Better buildings/workstations.
- Advanced storage/input/output rules.

## Phase 4 — Logistics automation

- Minecart tracks.
- Loading/unloading points.
- Junctions/routes.
- Bottleneck/management tooling.

## Phase 5 — Settlement Wealth + offline production

- Settlement Wealth / Production Rating.
- Supply-limited offline calculations.
- Offline cap and worker XP.
- Login production/bottleneck summary.

## Phase 6 — Settlement Exchange + economy

- Settlement Gold.
- NPC/player market participation.
- Specialties + supply/demand.
- Timed deliveries and caravan/logistics improvements.
- Controlled one-way GP conversion.

## Phase 7 — Overworld Construction

- Fixed Build Site framework.
- Repair/build interactions.
- Prefab shelter sites.
- Repeatable/permanent world projects.
- Achievement/XP integration.

---

# Current execution state

- Phase: Phase 1 — MVP Vertical Slice
- Phase status: ACTIVE
- Persistent-runtime bundle: 1.1 — Matrix3 ownership and foundation discovery
- Persistent-runtime bundle status: ACTIVE
- Tooling track: Custom Construction Palette + Preview Foundation + Build Camera
- Tooling status: PROVEN CLASS411 FREE BUILD REUSE IMPLEMENTED — RUNTIME ACCEPTANCE PENDING
- Approval state: SAP AAA remains active for this Construction camera slice; runtime evidence identified the proven Class24/Class411 freecam owner and the approved implementation now reuses that exact path.
- Current checklist item: run one short Class411 Free Build acceptance session covering automatic activation, WASD/mouse-look, Shift/Ctrl, Q/E, player-stationary behavior, ghost compatibility and close/reopen lifecycle.
- Current objective: runtime-accept the proven Class24/Class411 Free Build reuse without legacy interfaces, player teleport, second scene picking or client world-object ownership; only then add smoothing and RTS/top-down presets.

## Verification classifications

### VERIFIED

- Construction Editor implementation commit is `09cd35fec87defd0f49ef8000f49eca3523f112e` on Matrix3 `main`.
- Construction Editor opens and functions in the live Matrix3 Client Console.
- Paint placement successfully creates multiple runtime objects on clicked world tiles.
- Live editor rotation state changes before placement.
- Placed runtime objects expose the existing Dev move, rotate, duplicate, edit/inspect and delete actions.
- Cancel Placement and Place Last are available while placement is armed.
- Normal RuneScape world/object options remain available alongside the development actions.
- Object id `13450` with the current wall preset resolves at runtime as `Wooden fence`; it is functional but is not the desired final wall asset.
- Object id `13684` with the current floor preset resolves at runtime as `Floor decoration` and places successfully.
- Object id `13344` with the current doorway preset resolves at runtime as `Door` and places/interacts successfully.
- Custom Construction palette is runtime verified and stable.
- Hovered world-tile tracking follows Matrix3's normal scene-tile resolution at runtime.
- Initial ghost v1 runtime attempt through `Class110.method2071(...)` produced no visible preview while palette/hover/confirmed placement remained functional.
- Post-scene + renderer-environment attempt also produced no visible preview.
- Draw-contract retest with a valid displayed target tile still produced no visible preview after adding Matrix3's `Class90`/special-bounds draw behavior.
- The on-screen `GHOST DEBUG:` bridge is runtime verified and exposed the incorrect object-footprint decode through `SKIP_BOUNDS`.
- Corrected footprint decoding produces a visible Wooden fence preview on the hovered world tile.
- Runtime diagnostic reaches `DRAW_SUBMITTED object=13450 type=0 rot=0 specialBounds=false` while the 3D preview is visible, proving the base direct-render pipeline completes successfully.
- Weight 128 plus face alpha 96 renders the ghost visibly white/washed-out and translucent at runtime.
- After the `0x100` alpha-copy capability fix and a full client restart, the preview remains translucent while the placed Wooden fence remains normal/opaque; ghost alpha isolation is runtime verified.
- The first Orb-backed Build Camera runtime attempt crashes the current client during `SET_INTERFACE` with `ArrayIndexOutOfBoundsException: 57`; that legacy camera path is rejected for Construction.
- Ghost debug messages before the exception are not the initiating crash source, and the later `ConnectException` is secondary to the client crash.
- Native generic-camera Free Build v1 failed runtime acceptance: Construction opened, but the attempted camera controls did not operate the live camera and the scene view became invalid/empty-looking. That implementation is rejected.
- Runtime CAM DEBUG captures proved the active detached camera remains mode 1 and renders through `source=CLASS411`; in the captured session generic camera XYZ remained `0,0,0` while effective Class411 render XYZ changed.

### verified-static

- `matrix-3` is the correct repository.
- Protected Matrix3 baseline is `e86851b95e1d2927d58463b67f600153b9166f6a`.
- Matrix3 has its own `Server/src/main/java/com/rs/game/player/...` ownership structure.
- Current Matrix3 Construction runtime contains the classic serializable `House` stack and `HouseControler`.
- `Player` owns/persists `House`; the restored tree does not contain the previously claimed freeform `SettlementState`/`PlacedBuildPiece`/`SettlementInstance` foundation.
- Dev Mode mirrors Matrix3's normal scene-tile action 23 instead of performing a second scene pick.
- Dev Spawn queues object placement through the existing `itembrowser devspawn` server bridge.
- `ClientConsoleInterfaceOverlay` proves Matrix3's live Canvas can host temporary custom AWT drawing without taking interface-definition ownership.
- `ObjectDefinitions.method6057(...)` / `method6061(...)` provide Matrix3's object-model creation path.
- `Class456_Sub1_Sub2_Sub1` / `Class456_Sub1_Sub4_Sub1` demonstrate the real object render path through `Model.method1375(...)`.
- Normal object draws pass `aClass90Array9007[0]` into `Model.method1375(...)`; definitions with `aClass326_5684` use null bounds followed by `Class106.method1738(...)`.
- `Class613.method7285()` exposes the active `Class523` scene manager and `Class613.method7288()` exposes object definitions.
- `Class523.method6232()` / `method6248()` are scene attach/remove ownership and are intentionally not used by the Construction ghost.
- `Class523.method6280(...)` establishes per-tile renderer environment state with `Class106.method1790(...)` before normal scene-object draw submission.
- `Class110.method2071(...)` runs before `Class523.method6257(...)` performs the main scene/object render pass, making it unsuitable as the final direct-model preview hook.
- `Class343.method4302(...)` owns the viewport scene call and invokes `ConstructionGhostPreview.render(...)` immediately after `Class523.method6240(...)` returns.
- `Class578.method6834(...)` provides the active-scene immediate post-object callback currently used by the preview; `ConstructionGhostPreview` guards rendering to once per `client.cycles`.
- `ConstructionGhostPreview` direct-renders the selected model without scene registration; static source shows no attach/remove call in the preview path.
- `ObjectDefinitions.sizeX/sizeY` require inverse read multipliers `-876498849` / `1922784011`; using their storage multipliers as reads creates invalid billion-scale footprints.
- `AbstractModel.method1396(...)` interpolates packed colour components using `weight >> 7`, so weight 128 reaches the target exactly while 160 overshoots it.
- `AbstractModel.alpha` is explicitly annotated as face alpha and `Model.method1467(byte, byte[])` sets face alpha; with null face data it applies the requested alpha to every face.
- `AbstractModel.method1351(...)` deep-copies face-alpha backing storage when `Class368.method4561(flags, ...)` is true, and `Class368.method4561(...)` is exactly `(flags & 0x100) != 0`; the preview therefore requests `0x100` before mutating alpha.
- `InterfaceManager.gazeOrbOfOculus()` installs the legacy Orb root/interface layout including component `57`; `Class83.method1256(...)` indexes the component array using the lower 16 bits of the UID, matching the runtime failing index.
- `ConstructionBuildCamera` and the owner-only server camera command no longer enter the rejected Orb path.
- Generic scene rendering in `Class343.method4302(...)` consumes camera X/Y/Z from `Class36.anInt387`, `Class572_Sub13_Sub2.anInt11451`, `Class49.anInt490` and pitch/yaw from `Class455.anInt5187`, `Class406.anInt4765`.
- Matrix3 camera mode 1 uses the newer `Class411` camera object directly; modes 2/4/6 also have dedicated update behavior, so Free Build uses a separate generic mode rather than overwriting those owners.
- Ctrl+backtick is verified-static as the existing developer detached-camera activator: Java backtick maps to internal key 28, Ctrl to 82, and the input branch calls `Class102_Sub5.method9948(...)` to create `Class24.aClass411_Sub1_158`; `IncomingPacket.method4113(...)` returns `Class24.aBool157`, `Class24.method711()` updates it, and `RSSocket.method7604(...)` closes it.
- `ConstructionBuildCamera` now activates/reuses that existing Class24/Class411 camera and tracks ownership so closing Construction does not kill a freecam that was already manually active. `Class24.method711()` retains arrow/mouse behavior and adds Construction-only W/S/A/D plus Shift/Ctrl step modifiers and Q/E vertical movement.

### HYPOTHESIS

- `13684` / `Floor decoration` may be usable as the first floor definition, but its final suitability/material appearance still needs acceptance against the intended Construction art direction.
- `13344` / `Door` may be usable as the first doorway definition, but its final suitability/material appearance still needs acceptance against the intended Construction art direction.

### UNKNOWN

- Exact persistent settlement-state owner to add alongside/around the classic POH `House` ownership model.
- Best Matrix3 instance/dynamic-region owner for freeform settlement projection.
- Final proper wooden-wall definition; `13450` is verified as a Wooden fence and should not be promoted as the final wall asset.
- Q/E vertical direction and final modern movement feel on the proven Class411 freecam path; runtime acceptance decides whether Q/E needs swapping before smoothing.
- Exact deterministic RTS/top-down/orbit preset values and transition feel.

## Testing

See `docs/construction_revamp/testlist.txt` and `docs/construction_revamp/BUILD_CAMERA.md`.

## Resume Here

**Last completed:**

- Runtime-verified the Client Console Construction Editor and its end-to-end placement path.
- Runtime-verified the custom Construction palette foundation and hovered world-tile tracking.
- Verified-static the Matrix3 object-model/direct-render path through `ObjectDefinitions.method6057(...)` / `method6061(...)`, `Class456_Sub1_Sub2_Sub1` / `Class456_Sub1_Sub4_Sub1`, and `Model.method1375(...)`.
- Runtime diagnostics exposed and corrected the obfuscated object-footprint decode bug.
- Runtime-verified the base direct-render ghost: Wooden fence is visibly rendered on the hovered tile and the diagnostic reaches `DRAW_SUBMITTED`.
- Runtime-verified the corrected weight-128 white tint and face-alpha 96 translucency.
- Runtime-verified the `0x100` alpha-copy isolation fix after a full client restart: preview alpha no longer contaminates the real placed object model.
- Runtime-proved the legacy Orb camera/interface path crashes the current client on component `57` and permanently rejected that path for Construction.
- Verified-static the current generic Matrix3 renderer camera transform through `Class343`, `Class457`, `Class67` and `Class411`.
- Runtime-rejected the first native generic-camera Free Build attempt after it failed to control the live camera correctly and produced invalid/empty-looking scene views.
- Runtime CAM DEBUG proved the real detached camera renders through mode 1 / Class411; source trace then identified the exact Class24 activation/update/close path.
- Reimplemented Construction Free Build by reusing the proven Class24/Class411 developer camera, added Construction-only WASD/Shift/Ctrl/Q/E inside `Class24.method711()`, removed the temporary CAM DEBUG camera overlay/probe, and updated `BUILD_CAMERA.md`, patchnotes and testlist for runtime acceptance.

**Current phase:** Phase 1 — MVP Vertical Slice.

**Active persistent-runtime bundle:** Bundle 1.1 — Matrix3 ownership and foundation discovery.

**Active tooling slice:** Custom Construction Palette + Preview Foundation + Build Camera.

**Next checklist item:** Pull and run one Class411 Free Build acceptance session: opening Construction should auto-enter detached freecam without a hotkey; test mouse-look, W/S/A/D, Shift/Ctrl and Q/E, confirm the player stays stationary and ghost/placement remain correct, then close/reopen and verify lifecycle ownership.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/rs3/SYSTEM_OWNERSHIP.md`
- `docs/construction_revamp/PROJECT.md`
- `docs/construction_revamp/BUILD_CAMERA.md`
- `docs/construction_revamp/patchnotes.txt`
- `docs/construction_revamp/testlist.txt`
- `Server/src/main/java/com/rs/game/player/content/construction/House.java`
- `Server/src/main/java/com/rs/game/player/controllers/HouseControler.java`
- `Server/src/main/java/com/rs/game/player/Player.java`
- `Server/src/main/java/com/rs/game/player/InterfaceManager.java`
- `Server/src/main/java/com/rs/net/encoders/WorldPacketsEncoder.java`
- `Server/src/main/java/com/rs/game/player/content/commands/Commands.java`
- `Server/src/main/java/com/rs/game/player/content/commands/ItemBrowserCommandBridge.java`
- `Client/src/main/java/game/DevModeBridge.java`
- `Client/src/main/java/game/DevSpawnPlacement.java`
- `Client/src/main/java/game/Class592.java`
- `Client/src/main/java/game/Class319.java`
- `Client/src/main/java/game/ObjectDefinitions.java`
- `Client/src/main/java/game/Model.java`
- `Client/src/main/java/game/AbstractModel.java`
- `Client/src/main/java/game/Class456_Sub1_Sub2_Sub1.java`
- `Client/src/main/java/game/Class456_Sub1_Sub4_Sub1.java`
- `Client/src/main/java/game/Class613.java`
- `Client/src/main/java/game/Class523.java`
- `Client/src/main/java/game/Class578.java`
- `Client/src/main/java/game/Class110.java`
- `Client/src/main/java/game/Class343.java`
- `Client/src/main/java/game/Class457.java`
- `Client/src/main/java/game/Class67.java`
- `Client/src/main/java/game/Class411.java`
- `Client/src/main/java/game/Class485.java`
- `Client/src/main/java/game/Class503.java`
- `Client/src/main/java/game/Class591_Sub2_Sub1_Sub1.java`
- `Client/src/main/java/game/Class174.java`
- `Client/src/main/java/game/Class261.java`
- `Client/src/main/java/game/Class90.java`
- `Client/src/main/java/game/Class83.java`
- `Client/src/main/java/game/Class512.java`
- `Client/src/main/java/game/Class104_Sub1.java`
- `Client/src/main/java/game/PacketsDecoder.java`
- `Client/src/main/java/game/ConstructionPlacementController.java`
- `Client/src/main/java/game/ConstructionBuildCamera.java`
- `Client/src/main/java/game/ConstructionGhostPreview.java`
- `Client/src/main/java/game/ConstructionPaletteOverlay.java`
- `Client/src/main/java/game/ClientConsoleInterfaceOverlay.java`
- `Client/src/main/java/game/console/ClientConsoleShell.java`
- `Client/src/main/java/game/console/ConstructionEditorPanel.java`
- `Client/src/main/java/game/console/ConsoleTheme.java`

**Do not re-scan without new evidence:**

- Do not inspect or port the old `Matrix-718_MAIN` Construction code as implementation authority.
- Do not re-audit unrelated historical Matrix3 workstreams.
- Do not re-trace the already-proven Dev Mode tile/menu dispatch and `itembrowser devspawn` placement path unless a runtime failure points back to it.
- Do not broaden or replace the ghost render seam without a new runtime regression; the base direct-render path is now runtime verified.
- Do not re-investigate the already-corrected object-footprint multipliers unless a different definition produces contradictory evidence.
- Do not claim the missing freeform settlement-state foundation exists until it is actually added to Matrix3.
- Do not make `13450` the final wooden-wall definition; runtime proved it is a Wooden fence.
- Do not add scene registration/collision/persistence to the client ghost; those belong to authoritative placement/runtime ownership, not preview rendering.
- Do not reuse the stale historical `Class291` camera-field mapping; it does not match the current Matrix3 source.
- Do not retry `InterfaceManager.gazeOrbOfOculus()` or legacy root/component `475/57` for Construction; runtime proved that path crashes the current client.
- Do not return to generic renderer-global camera ownership; runtime proved the active detached camera is the Class24/Class411 path.
- Do not patch action-23 suppression again before testing the current Free Build Paint event consumption; if it leaks, use the already-verified `DevModeBridge.handleMenuAction(...)` seam rather than broadening input discovery.
- Do not hardcode RTS/top-down/orbit values until Free Build behavior is runtime accepted.

**Pending runtime verification:**

- Opening Construction automatically activates the proven Class24/Class411 detached freecam without needing Ctrl+backtick.
- Free Build starts from a sensible view around the local player rather than an empty/invalid scene.
- Existing freecam mouse-look remains functional.
- W/S forward/back and A/D strafing match camera orientation.
- Shift fast and Ctrl precision speeds are useful.
- Q/E vertical direction is correct; swap only if runtime proves the intuitive direction is reversed.
- The player remains physically stationary while the detached camera moves.
- Existing white/translucent preview follows hovered tiles; piece switching, rotation, terrain alignment and cancel/stale-hover behavior remain stable.
- Confirmed placement still creates exactly one server-authoritative real object; if action-23 movement leaks through a build click, patch only the already-verified menu-action seam.
- Closing Construction returns to normal camera when Construction owns the freecam; reopening works cleanly.
- A manually active freecam that predates Construction survives palette close.
- The temporary CAM DEBUG camera overlay is gone; console receives transition-only ConstructionBuildCamera ENTER/EXIT lines.
- Final wall/floor/door art selections still need visual acceptance.

**Blockers:**

- No camera-owner discovery blocker remains: the proven detached owner is Class24/Class411. Current camera gate is runtime acceptance of Construction activation/control/lifecycle.
- Persistent settlement-state/instance ownership remains intentionally separate and unresolved until the exact Bundle 1.2 file plan is produced.

**Important remaining uncertainty:** Q/E vertical sign, player-stationary build-click behavior and final modern movement feel still require runtime confirmation on the proven Class24/Class411 path. Smooth acceleration/deceleration and RTS/top-down/orbit preset values remain intentionally deferred.

## Next recommended work

Run one short Class24/Class411 Free Build acceptance session. If automatic activation, WASD/mouse-look, Shift/Ctrl, Q/E, stationary-player behavior, ghost/placement stability and close/reopen lifecycle pass, mark the camera foundation runtime verified; then add smooth modern movement and view presets on this same owner without reopening camera architecture.