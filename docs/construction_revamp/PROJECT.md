# Matrix3 Construction Revamp

## Goal

Turn Construction into a persistent freeform settlement/factory-style system built natively on Matrix3, while preserving Matrix3 core ownership for maps, objects, persistence, NPCs, combat, interfaces, and cache data.

The player should be able to build a settlement wall-by-wall, recruit and train flexible workers, gather/haul/process resources, automate logistics, expand the settlement, and later connect the system to overworld Construction projects and a settlement economy.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Freeform settlement building foundation | ✅ Done |
| Starter resource loop and shelter milestone | ✅ Done |
| First worker, Allowed Jobs, gathering and hauling | ✅ Done |
| Worker needs, storage and settlement recovery | ✅ Done |
| Persistence and Construction XP ownership | ✅ Done |
| Population, processing and logistics expansion | 🔵 In Progress |
| Settlement Wealth, offline production and economy | ❌ Not started |
| Overworld Construction integration | ❌ Not started |

## Current status

- Repository authority: `revertus111/matrix-3`, branch `main`.
- Runtime foundation: protected Matrix3 baseline `e86851b95e1d2927d58463b67f600153b9166f6a` plus the restored pre-reset feature stack.
- State: Phase 1 MVP is DONE / runtime accepted. Phase 2 is ACTIVE: Bundle 2.1 population capacity + Worker #2 persistence is runtime VERIFIED and Bundle 2.2 multi-worker control + concurrent work is DONE / RUNTIME VERIFIED, including the two-worker rebuild and logout/relog persistence gate. The next persistent-runtime target is housing/beds/population-capacity expansion.
- Current user-prioritized side slice: Radial Worker Selection RWS-1 and RWS-2 are RUNTIME VERIFIED. The user confirmed the circular ring now keeps edge A fixed while edge B owns the expansion after ring-body calibration. RWS-3 is RUNTIME VERIFIED: live drag detection correctly identifies settlement workers inside the world-space circle and renders temporary small 4171 preview markers on included workers. Screenshot evidence verified both active settlers marked simultaneously inside the large selection ring. No persistent selection/server authority is added yet. RWS-4 has changed direction by user decision: GFX 4187 is dropped. The selected-worker visual layers two independent clones of proven GFX 4171. Runtime screenshot verified multi-worker layered rendering and independent outer/inner scaling; color controls initially had no visible effect because 4171 is texture-driven. The recolor path now gives each clone private texture IDs (0x8000), detaches textures only when a custom color is chosen, then applies the chosen HSL. A second runtime failure showed the first detach implementation only handled AbstractModel; Matrix3's Class89_Sub2 renderer keeps textures in a separate array. RWS-4 layered GFX 4171 styling is now RUNTIME VERIFIED: the user confirmed independent scaling and recolor both work across multiple workers after renderer-aware texture detachment covered AbstractModel, Class89_Sub2 and OpenGLModel. The first Worker Needs HUD prototype (three concentric 4171 rings) rendered successfully but was visually rejected by the user as the wrong design. The active prototype cuts GFX 4171 into three partial arches on one shared circumference: Hunger / Thirst / Energy occupy separate ~100-degree slots with gaps, color trends toward red at the real critical threshold, and arch length represents wellbeing remaining. Runtime video proved the first angular mask still included 4171's outer decorative diamond/spike geometry, producing chunky fragments instead of clean ring-body arches. The mask now first isolates the circular ring-body radial band, then applies the angular need slot. A follow-up strict pass now requires all three vertices of each candidate face to remain inside that annulus; centroid-only radial acceptance was still allowing decorative spike/diamond triangles to survive. Runtime screenshot also exposed an extra visual level: RWS-4 was still rendering both outer and inner full selection rings while the needs arches added a third circumference. Needs HUD now owns the inner/status level: outer full ring remains selection, the old inner full selection ring is suppressed while Needs HUD is enabled, and Hunger/Thirst/Energy arches render at the former inner-ring radius (70% default). Demo values remain explicitly client-only; real needs stay server-owned in SettlementWorkerState until a clean metadata sync seam is added.
- Construction Editor implementation: `09cd35fec87defd0f49ef8000f49eca3523f112e`.
- Custom Construction palette foundation implementation: `a2ce37439896d77d257d0966463104fcb962803f`.
- Visible 3D ghost base-render fix is runtime verified after `014a133f02c6e72c3bac08ea26f5c6bd98ebeb3d`; white/translucent styling and the `0x100` private-alpha isolation fix are also runtime verified after a full client restart.
- Construction no longer invokes Matrix3's legacy Orb-of-Oculus interface path because the current client/cache rejects legacy root/component `475/57` with `ArrayIndexOutOfBoundsException: 57` during `SET_INTERFACE` processing.
- Construction Free Build activates/reuses `Class24.aClass411_Sub1_158`, but runtime proved the first WASD/Q/E patch location did not execute. Construction controls now run through `ConstructionBuildCamera.tick()` from the live `Class343.method4302(...)` viewport seam immediately before the detached Class411 transform is submitted. Stock `Class24.java` is restored.
- The current Client Console Construction Editor remains a developer/debug harness; the custom in-game palette is the intended player-facing selection direction.
- The prior 718/legacy Construction implementation is reference material only and must not be transplanted as architecture.
- Phase 1 MVP vertical slice is runtime accepted; next playable target is Phase 2 population + broader survival production.

## Live Model Editor material-replacement bridge

- Developer-side foundation is now shared with the Construction revamp: Live Model Editor can consume the current `ConstructionPlacementController.BuildPiece` catalog as a non-destructive replacement library.
- This is an authoring/tooling bridge, not yet the final player-facing Construction Detail Mode. Replacements stay client-local + JSON-backed and never mutate settlement persistence or cache bytes.
- The intended reuse is now concrete: connected source part -> Construction catalog replacement -> direct in-world G/R/S adjustment -> JSON recipe. Repeated geometry can use Replace Matches for bars/supports/trim.
- Future player-facing Detail Mode should promote the same transform/asset recipe behind server-validated settlement placement rules rather than reimplementing selection/transforms.

## Supporting design specs

- `RADIAL_WORKER_SELECTION.md` — planned RuneScape-native world-space radial drag selection for settlement workers, including runtime-scaled ground reticule, live preview, server validation, and future work-area reuse.

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

**Status:** DONE / RUNTIME ACCEPTED

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

**Status:** DONE

- [x] Confirm correct repository/branch and protected baseline.
- [x] Confirm no current `docs/construction_revamp/PROJECT.md` exists in Matrix3.
- [x] Targeted default-branch search found no indexed `Construction` implementation symbol.
- [x] Confirm Matrix3 player layer contains its own `ControlerManager`, `Player`, content and controllers packages; legacy 718 paths are not authoritative.
- [x] Inspect the narrow Matrix3 Construction/player/controller/placement path needed to classify current ownership: classic `House`/`HouseControler` exists, `Player` persists `House`, and the restored tree contains no freeform settlement-state foundation.
- [x] Produce the exact persistent Phase 1 Bundle 1.2 settlement-state/instance file plan before modifying server-owned Construction runtime.

### Bundle 1.2 — Freeform placement foundation

**Status:** DONE

#### Exact ownership/file plan — LOCKED

Persistent data owner:

- `Player.settlementState` is the sole saved owner for the new freeform settlement. It is separate from classic POH `House`.
- `SettlementState` is serializable and stores plot-relative construction state only.
- `SettlementPlacedPiece` stores stable piece id, stable definition key, plot-relative X/Y/plane and rotation. Temporary dynamic-region/world coordinates are never serialized.
- `SettlementBuildPiece` is the server definition registry for allowed starter pieces; raw dev object ids are accepted only when they resolve to an approved definition while inside the settlement.

Runtime owner:

- `SettlementInstance` owns the transient dynamic-map allocation, world-coordinate projection, spawned runtime objects, placement/edit validation, rebuild and cleanup.
- The Phase 1 plot is one Matrix3 dynamic region: 8x8 chunks / 64x64 tiles. Runtime allocation uses `MapBuilder.findEmptyChunkBound(8, 8)`; cleanup uses `MapBuilder.destroyMap(...)`.
- The blank terrain projection reuses Matrix3's existing `HouseConstants.LAND` source chunk through `MapBuilder.copyChunk(...)`, but does not reuse POH rooms/build hotspots/House state.
- `SettlementControler` owns enter/exit/logout/teleport lifecycle only and keeps the runtime instance out of saved player state.
- `ControlerHandler` registers `SettlementControler`.

Development integration for Bundle 1.2 acceptance:

- `ItemBrowserCommandBridge` adds owner-only `itembrowser settlement enter|exit|status` for the first runtime harness.
- Existing palette `devspawn object` commands are intercepted only when the player is inside `SettlementControler`; approved starter pieces then persist into `SettlementState` and spawn through `SettlementInstance`.
- Existing Dev move/duplicate/rotate/delete object commands are likewise intercepted inside the active settlement so the persistent piece record and projected runtime object stay synchronized.
- Outside the settlement, Dev Mode behavior remains unchanged.

Files for the first persistent foundation:

- ADD `Server/src/main/java/com/rs/game/player/content/construction/SettlementState.java`
- ADD `Server/src/main/java/com/rs/game/player/content/construction/SettlementPlacedPiece.java`
- ADD `Server/src/main/java/com/rs/game/player/content/construction/SettlementBuildPiece.java`
- ADD `Server/src/main/java/com/rs/game/player/content/construction/SettlementInstance.java`
- ADD `Server/src/main/java/com/rs/game/player/controllers/SettlementControler.java`
- MODIFY `Server/src/main/java/com/rs/game/player/Player.java`
- MODIFY `Server/src/main/java/com/rs/game/player/controllers/ControlerHandler.java`
- MODIFY `Server/src/main/java/com/rs/game/player/content/commands/ItemBrowserCommandBridge.java`
- UPDATE `docs/construction_revamp/PROJECT.md`, `patchnotes.txt`, and `testlist.txt`

Acceptance target for this first server-owned slice:

`enter -> place/rotate/move/remove -> leave -> re-enter -> exact plot-relative layout rebuilds -> logout/relog -> layout still rebuilds`

#### Implementation checkpoint — PERSISTENCE CORE RUNTIME VERIFIED

Implemented under the approved Bundle 1.2 ownership plan:

- `SettlementBuildPiece` defines the approved starter-piece registry with stable keys.
- `SettlementPlacedPiece` persists stable piece id + definition key + plot-relative X/Y/plane + rotation only.
- `SettlementState` is serializable, player-owned, old-save safe and provides placement/move/duplicate/rotate/remove state mutation with same-type slot occupancy protection.
- `Player.settlementState` is initialized for new players and repaired/normalized for existing saves during `Player.init(...)`.
- `SettlementInstance` allocates an 8x8-chunk Matrix3 dynamic region, projects the existing blank Construction land chunk, rebuilds saved pieces, translates runtime world tiles back to plot coordinates and destroys the dynamic map on exit.
- `SettlementControler` owns runtime enter/exit/logout/teleport cleanup and never persists the runtime instance through controller arguments.
- `ControlerHandler` registers `SettlementControler`.
- `itembrowser settlement enter|exit|status` provides the first owner-only runtime harness.
- Inside an active settlement only, existing Dev object spawn/move/duplicate/rotate/delete operations are intercepted and synchronized with `SettlementState`; outside the settlement, Dev Mode remains unchanged.
- `docs/rs3/SYSTEM_OWNERSHIP.md` now records `Player.settlementState` as saved owner and `SettlementInstance` as transient projection owner.




- Persistent plot-relative placement state.
- Matrix3-native dynamic/instanced settlement projection.
- Definition-driven wall/floor/door pieces.
- Placement preview, rotation, confirmation and removal.
- Minimal entry/exit path for runtime testing.
- No 718 controller/code transplant.

### Bundle 1.3 — Starter resources and isolated storage

**Status:** DONE

#### Ownership/file plan — LOCKED

Persistent owner:

- `SettlementState` owns settlement-only resource totals and starter storage capacity alongside the already-verified placed-piece state.
- Stable resource identities are `SettlementResource.WOOD`, `FOOD`, `STONE` and `BASIC_ORE`.
- Storage is not represented by normal `Item` stacks and has no inventory/bank deposit API. Normal bank stock therefore cannot be dumped into settlement storage through this slice.
- Old player saves repair missing resource storage during `SettlementState.normalize()`. Resource storage was introduced in schema v2; the starter-milestone set advances the same owner to schema v3.

Runtime owner:

- `SettlementInstance` owns the transient projection/lifecycle of the four starter resource nodes.
- `SettlementControler` intercepts only those exact node object/NPC clicks while the settlement instance is active, before normal world Woodcutting/Mining/Fishing handling.
- `SettlementGatherAction` provides the short gathering action/animation and deposits one unit directly into `SettlementState` storage.
- Resource nodes occupy reserved plot tiles so freeform build place/move/duplicate cannot overlap them.
- Instance destroy explicitly removes starter node objects/NPCs; saved resource totals remain in `SettlementState`.

Starter v1 node layout:

- Wood: plot `8,8`, provisional object `1276`, type `10`.
- Food: plot `12,8`, fishing-spot NPC `327`.
- Stone: plot `16,8`, provisional rock object `11933`, type `10`.
- Basic ore: plot `20,8`, provisional rock object `11936`, type `10`.
- Current starter-node visuals/options are runtime accepted for the prototype and may still change for final art direction without changing resource/storage ownership.

Developer acceptance:

- `itembrowser settlement resources` reports real saved settlement-only totals/capacity.
- `itembrowser settlement resourceselftest` runs a disposable storage/node-definition self-test.
- Test Console -> Con Revamp exposes `Resource Status` and `Resource Self-Test`.

Acceptance target:

`enter -> four nodes visible -> gather each -> settlement-only totals increase -> inventory/bank unchanged -> exit/re-enter -> totals persist`

#### Starter shelter milestone — RUNTIME VERIFIED

Persistent progression owner:

- `SettlementBuildRole` classifies server definitions as WALL, FLOOR or DOOR so milestone semantics do not depend on provisional object ids.
- `SettlementMilestone.STARTER_SHELTER` is stored as a stable key in `SettlementState.completedMilestones`.
- The Phase-1 threshold is deliberately freeform/count-based rather than a prefab room: 4 wall pieces, 4 floor pieces, 1 doorway, and at least 1 Wood/Food/Stone/Basic ore in settlement storage.
- Completion is one-time and permanent. Later removing structures or spending stored resources does not revoke the milestone.
- `SettlementState` schema v3 repairs missing milestone storage on old saves and removes unknown milestone keys during normalization.

Runtime behavior:

- `SettlementInstance` checks the starter milestone after instance load, successful build placement/duplication and successful starter-resource gathering.
- Completion is automatic and server-owned; the client has no claim/complete authority.
- Completing the milestone emits a one-time message that the settlement is ready to attract its first worker.
- Bundle 1.4 can depend only on `SettlementMilestone.STARTER_SHELTER` instead of re-deriving structure/resource rules.

Developer acceptance:

- `itembrowser settlement shelter` reports the real saved requirement counts and milestone state.
- `itembrowser settlement shelterselftest` runs a disposable threshold/latch/serialization test.
- Test Console -> Con Revamp exposes `Shelter Status` and `Shelter Self-Test`.

Acceptance target:

`Shelter Self-Test PASS -> real Shelter Status reaches COMPLETE -> remove/spend after completion -> milestone remains COMPLETE -> save/load preserves completion`

Bundle 1.3 non-blocking carryover: explicit logout/relog resource/shelter recheck, reserved-node placement rejection and transient resource-node cleanup are retained for the next consolidated persistence/world-object smoke pass; they do not change Bundle 1.4 ownership.

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
- Test Console now consolidates the Client Console's developer/test tooling. Top-level rail ownership is intentionally reduced to Client Console/Home, Owner, Commands, Test Console and Settings.
- Player, Item Browser, Interface Editor, Construction Editor, Client Atlas and Boss Research now lazy-load as Test Console sub-tabs instead of separate rail panels.
- Test Console adds a `Con Revamp` sub-tab with one-click Enter Settlement, Settlement Status, Exit Settlement and Open Build Palette actions.
- `Con Revamp` is UI-only orchestration: settlement buttons queue the existing owner-only server command bridge through `ClientConsoleBridge`; no settlement logic or persistence ownership moved into the client.
- Legacy saved Client Console panel ids for moved tools normalize to Test Console so old layouts do not break.
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

- The legacy `InterfaceManager.gazeOrbOfOculus()` / root-component `475/57` path is permanently runtime-rejected for Construction.
- The generic renderer-global camera implementation is also runtime-rejected; it produced invalid/empty-looking views and did not control the live detached camera.
- Runtime diagnostics plus source tracing established `Class24.aClass411_Sub1_158` as the detached Class411 camera object used by the developer camera path.
- The first Class24 reuse patch detached correctly but its W/A/S/D/Q/E changes were placed in unreached `Class24.method711()`; that patch location is runtime-rejected and `Class24.java` is restored to stock.
- Current implementation activates/reuses the detached Class24/Class411 camera and drives Construction controls through `ConstructionBuildCamera.tick()`, called once per `client.cycles` from the live `Class343.method4302(...)` viewport seam immediately before camera submission.
- Runtime VERIFIED in the user's acceptance sweep: automatic activation, W/S/A/D movement, Shift fast, Ctrl precision, Q/E vertical movement, mouse-look, normal-camera restore on close, and clean reopen.
- Camera ownership remains client-side; confirmed object placement remains server-authoritative through the existing Dev placement / `itembrowser devspawn` path.
- Smooth acceleration/deceleration and click-to-stop are now implemented on the live controller: normalized input feeds time-based world-space velocity; action 23 clears velocity, latches movement off until key release, optionally confirms Paint placement and is consumed so the player remains planted. Runtime feel/integration acceptance is pending.
- RTS camera default + pivot-orbit revision is DONE / runtime VERIFIED on the same detached Class411 owner: Construction opens in RTS, W/A/S/D + arrows pan camera and pivot together, Q/E orbits the camera around the pivot, wheel changes orbit distance, Shift fast and Ctrl precision remain.
- Adjustable RTS pan speed is IMPLEMENTED / NEEDS RUNTIME TEST: palette header exposes -/+ presets from 0.5x through 3.0x, default 1.0x; the selected multiplier persists for the client session and scales normal/Shift/Ctrl RTS pan without altering Free Build speed.
- First runtime showed the detached RTS camera below the terrain looking through the underside; Matrix3's Class658_Sub2.method8927(...) negates its Y target internally, so the initial deterministic pitch sign was inverted.
- Corrective patch now backs the camera 3600 Matrix3 units away from the real rendered look vector on RTS entry and constrains zoom to a safe backoff band; pan/zoom direction is derived from Class658_Sub2.method7736(...) instead of guessed obfuscated axis signs.
- Input ownership remains mode-specific: RTS consumes world wheel for camera zoom; Free Build preserves the accepted wheel-to-piece-rotation behavior. R / Shift+R remains piece rotation in both modes.
- Orbit ownership is pivot-relative: the initial pivot is the pre-backoff detached-camera/player-area point; Q/E changes yaw then repositions the camera on the same radius around that pivot while keeping the fixed RTS pitch; pan translates the pivot and camera together so later rotation circles the new managed area rather than an old location.
- Top Down, Orbit/Focus and Player View remain accepted later views; deterministic presets beyond RTS remain deferred.
- Runtime VERIFIED: corrected RTS camera initializes above the terrain with a normal downward view; the later default-open + pivot-orbit revision is also user-confirmed working.

Still pending in this slice:

- Runtime VERIFIED: opening the palette activates the detached Class411 Free Build camera without a hotkey; W/S/A/D, Shift/Ctrl, Q/E, mouse-look, normal-camera restore and close/reopen all work in the user's acceptance sweep.
- Runtime acceptance that verified action-23 ownership now keeps the player stationary, stops camera momentum immediately and produces exactly one authoritative Paint object without Walk Here leakage.
- Runtime verification that hover/white ghost, rotation 0-3, model switching, terrain alignment, cancel/stale-hover cleanup and one-preview-per-cycle behavior remain stable while detached.
- Valid/invalid placement feedback tied to future settlement occupancy/rules.

Later interaction polish after single-piece preview is stable:

- Click/inspect an already placed Construction piece for move/rotate/duplicate/remove actions through Construction ownership.
- Click-drag wall runs.
- Corner-to-corner or drag area floor filling.
- Material/variant switching where supported by definitions.

### Bundle 1.3 — Starter resource loop

**Status:** DONE

- Wood, food, stone and ore gathering inside settlement.
- Settlement-only storage boundary.
- Starter shelter milestone.

### Bundle 1.4 — First worker vertical slice

**Status:** DONE

#### Worker #1 arrival foundation — RUNTIME VERIFIED

Persistent owner:

- `SettlementState` introduced worker records in schema v4; current schema v5 keeps those records and adds persistent per-worker Allowed Jobs inside normal Matrix3 player serialization.
- `SettlementWorkerState` stores stable worker id, stable worker-definition key, custom name and plot-relative home tile only; dynamic world coordinates/NPC instances are never serialized.
- `SettlementWorkerDefinition.STARTER_SETTLER` is the stable first-worker archetype. Its cache NPC id is presentation data and remains replaceable without changing saved worker identity.
- `SettlementState.ensureStarterWorker()` is hard-gated by `SettlementMilestone.STARTER_SHELTER` and is idempotent: repeated calls return the same Worker #1 instead of creating duplicates.
- Old schema-v3 saves repair missing worker storage and next-worker-id state during `SettlementState.normalize()`.

Runtime owner:

- `SettlementWorkerNpc` is a transient Matrix3 `NPC` projection of one persistent worker record.
- `SettlementInstance` creates/rebuilds Worker #1 only after the verified starter-shelter milestone, tracks exactly one runtime NPC per worker id, and finishes all worker NPCs when the transient settlement instance is destroyed.
- Worker #1 is intentionally stationary/non-combat in this foundation slice: random walk is disabled, generic interaction/combat is suppressed, and later Bundle 1.4 AI will drive explicit work movement.
- The starter-worker arrival tile is reserved against new settlement build placement/move/duplicate so the initial runtime projection has a deterministic safe anchor.

Developer acceptance:

- `SettlementWorkerSelfTest` disposably verifies milestone gating, stable identity, duplicate suppression and Java serialization.
- `SettlementWorkerArrivalCheck` verifies the real save contains exactly one starter worker and the active settlement contains exactly one matching runtime NPC.
- Test Console -> Con Revamp exposes `Worker Status`, `Worker Self-Test` and `Worker Arrival Check`.

Acceptance target:

`enter completed settlement -> Worker #1 auto-arrives -> Worker Arrival Check PASS -> exit/re-enter -> same worker id, one runtime NPC, no duplicate`

#### Allowed Jobs foundation — RUNTIME VERIFIED

Persistent policy owner:

- `SettlementWorkerJob` defines stable permission keys for Gather Wood, Gather Food, Gather Stone, Gather Basic Ore and Haul.
- `SettlementWorkerState.allowedJobs` stores only stable job keys. Runtime pathing/action state is intentionally not serialized here.
- New and migrated workers default to an empty allowlist: every job is OFF until explicitly enabled.
- Worker-state normalization repairs a missing allowlist on old saves and removes unknown job keys.
- `SettlementState` advances to schema v5 for this persistent policy slice.

Control/test boundary:

- Owner-only `workerjobs` reports authoritative saved permissions.
- `workerjob <key> <on|off>` changes one permission explicitly; `workerjobsall <on|off>` provides bounded developer convenience.
- `SettlementWorkerJobsSelfTest` disposably verifies stable keys, default-OFF policy, independent toggles, disable behavior and Java serialization.
- Test Console -> Con Revamp exposes five Allowed Jobs checkboxes plus Jobs Status / Jobs Self-Test / Enable All / Disable All.
- The checkboxes are command controls, not an independent client owner. Jobs Status is authoritative readback from the server save.

Acceptance target:

`Jobs Self-Test PASS -> enable mixed allowlist -> Jobs Status exact ON/OFF -> exit/re-enter unchanged -> logout/relog unchanged`

Runtime acceptance on 2026-09-19 confirmed the Allowed Jobs controls/self-test/readback flow is working and the saved allowlist is ready to be consumed by worker AI.

#### Gather / haul vertical slice — IMPLEMENTED / NEEDS TEST

Runtime owner:

- `SettlementWorkerNpc` now owns only transient work state: current node target, route, gathering timer, one-unit carried cargo and readable work status.
- Persistent policy remains `SettlementWorkerState.allowedJobs`; the AI reads the live saved allowlist every cycle and never rewrites it.
- The first runtime movement attempt exposed a pathing bug: `Entity.findBasicRoute(...)` is Matrix3's greedy/basic stepper and stalled at the Wood route with `No Path to Wood node`. A second targeted trace showed the remaining tree-only issue: the worker was still routing to a synthetic adjacent `WorldTile`, forcing `FixedTileStrategy` even for object-backed nodes. Worker routing now passes the actual live `WorldObject` / resource NPC into Matrix3 `calcFollow(..., true)`, so object nodes use `ObjectStrategy` and NPC nodes use `EntityStrategy`; no parallel movement owner or force-teleport fallback is added.
- A gathering permission maps to its existing `SettlementResourceNode`. The worker walks to the node, performs the node's existing gathering animation, and carries one settlement resource.
- Carried output is not credited to settlement storage until `HAUL` is allowed and the worker physically returns to its stable home/storage access tile.
- If Haul is disabled or storage is full, the worker keeps the carried unit and reports why it is waiting. Re-enabling Haul or freeing storage resumes delivery automatically.
- With multiple gathering permissions enabled, the worker rotates across available starter nodes instead of permanently preferring the first enum entry.
- Runtime cargo/action/path state is intentionally discarded with `SettlementInstance`; no new save schema is introduced by this slice.

Developer acceptance:

- `itembrowser settlement workerai` reports live Worker #1 work/carry state plus authoritative settlement storage totals.
- Test Console -> Con Revamp exposes `Worker AI Status` and a compact gather/haul acceptance checklist.

Acceptance target:

`Gather Wood + Haul -> walk/gather/return/deposit -> Wood rises only -> Haul OFF holds cargo -> Haul ON resumes deposit -> gathering OFF stops new cycles`

#### Worker needs vertical slice — RUNTIME VERIFIED

Persistent owner:

- `SettlementWorkerState` now persists Hunger, Thirst and Energy under settlement schema v6. Hunger/Thirst are pressure values (0 satisfied -> 100 critical); Energy is a reserve (100 rested -> 0 exhausted).
- Older worker saves normalize missing need fields to Hunger 0 / Thirst 0 / Energy 100 without changing worker identity or Allowed Jobs.
- Each completed gather cycle applies a bounded work cost: Hunger +4, Thirst +5, Energy -6.
- Critical thresholds are Hunger >= 80, Thirst >= 80 and Energy <= 20.

Runtime recovery:

- A worker finishes a deliverable carried-resource action first when Haul/storage are valid, then checks critical needs before starting another gather cycle.
- Critical Hunger returns Worker #1 home and consumes exactly 1 settlement Food. If no Food exists, work stops with readable `No Food`; adding Food lets the worker recover/resume automatically.
- Critical Thirst returns Worker #1 home and uses the Phase-1 basic water supply provided by the completed starter shelter. This is an explicit provisional seam for later physical wells/barrels/tanks; it does not create hidden bank/inventory water.
- Critical Energy returns Worker #1 home and rests for a bounded recovery delay.
- Need recovery does not erase carried cargo, Allowed Jobs, worker identity or progression state.

Developer acceptance:

- `workerneeds`, `workerneed <need> <0-100>`, `workerneedsreset` and disposable `SettlementWorkerNeedsSelfTest` provide bounded inspection/forcing without duplicating gameplay ownership.
- Test Console -> Con Revamp exposes Needs Status/Self-Test plus explicit critical-threshold buttons so gather/haul carryover and needs can be tested in one launch.

Acceptance target:

`Needs Self-Test PASS -> Hunger critical consumes Food / No Food blocks -> Food supplied resumes -> Thirst critical drinks/resumes -> Energy critical rests/resumes -> exit/re-entry + logout/relog preserve need values`

#### Worker progression + Construction XP — RUNTIME VERIFIED

Persistent worker progression:

- `SettlementWorkerSkill` defines stable personal skills for the current vertical slice: Woodcutting, Food Gathering, Mining and Hauling.
- `SettlementWorkerState.skillXp` persists stable skill-key -> XP totals under settlement schema v7; older saves normalize a missing map to zero XP without changing identity, jobs or needs.
- Worker levels use the normal RuneScape-style 1-99 XP curve. This slice records progression only; skill-based speed/yield bonuses wait for a later tuning pass so the already-verified worker loop is not silently changed.
- Current starter-job mapping is explicit: Wood -> Woodcutting, Food -> provisional Food Gathering, Stone/Ore -> Mining, Haul -> Hauling. Proper Fishing/Hunting/Farming jobs can gain their own stable skills later without rewriting starter progression.
- Completed gather actions award 12 personal XP to the mapped gathering skill. A successful stored haul awards 6 Hauling XP per deposited resource.

Construction XP ownership:

- Productive worker output awards exactly 1 base Construction XP per resource only after that resource successfully enters authoritative settlement storage.
- Passive worker Construction XP calls Matrix3 `Skills.addXp(CONSTRUCTION, ..., true)` so this modest base rate is not multiplied by normal server skilling rates.
- Haul disabled, storage full, idle/waiting workers and failed deposits award no passive Construction XP.
- Developer placement/build-palette actions still award no Construction XP. Active-building XP remains intentionally deferred until the real material-consuming player build path owns the action.
- Multi-worker diminishing-return tuning and offline XP remain later-phase work; this one-worker slice establishes the authoritative award seam only.

Developer acceptance:

- `workerprogress` reports Worker #1 skill levels/XP and the player's current Construction XP.
- `SettlementWorkerProgressionSelfTest` verifies stable skills, job mapping, XP gain, RuneScape-style leveling and serialization without mutating the player save.
- Test Console -> Con Revamp exposes Progress Status and Progress Self-Test.

Acceptance target:

`Progress Self-Test PASS -> one gather raises mapped worker skill -> successful deposit raises Hauling + Construction XP -> blocked/idle time raises no Construction XP -> exit/re-entry + logout/relog preserve worker skill XP and needs`

Bundle 1.4 closure:

- Productive progression, negative idle/no-XP behavior, runtime-instance rebuild persistence and normal logout/relog persistence are runtime VERIFIED.
- Explicit zero-Food Hunger blocking/resupply remains non-blocking carryover; it does not invalidate the first-worker Phase-1 gate.

### Bundle 1.5 — Player build materials + active Construction XP

**Status:** DONE / RUNTIME VERIFIED

Ownership:

- `SettlementBuildPiece` now owns definition-driven Phase-1 material cost and active Construction XP values.
- Starter tuning is deliberately simple and configurable: Wooden fence = 1 Wood / 4 base XP, Floor decoration = 1 Wood / 4 base XP, Door = 2 Wood / 8 base XP.
- `SettlementPlayerBuildTransaction` now owns the atomic persistent placement + settlement-material mutation used by the real player-build path. `SettlementInstance.placePlayerPiece(...)` owns runtime projection and the one successful active Construction XP award after that transaction succeeds.
- Failed/invalid/occupied/insufficient-material placement consumes no material and awards no active Construction XP. Short-consume rollback restores both the new piece and any partially removed material before returning failure.
- The existing owner-only `itembrowser devspawn` path remains available as a development harness and stays no-cost/no-XP.

Client/server seam:

- Construction palette requests now carry the stable build-piece key instead of treating the raw object id as gameplay authority.
- The client continues to reuse the already-verified placement input/menu plumbing, but Construction requests now send the normal-player `settlementbuild` command.
- `Commands.processNormalCommand(...)` accepts `settlementbuild <piece-key> <x> <y> <plane> <rotation>` for any player, but the server accepts it only while an active `SettlementInstance` owns the target and all normal settlement validation passes.

Final-gate harness:

- `SettlementBundle15FinalCheck.runSelfTest()` disposably exercises the exact `SettlementPlayerBuildTransaction` for configured costs plus insufficient/reserved/invalid/occupied no-mutation behavior; it never touches the player's save.
- `capture/check` stores only a process-local snapshot of exact saved piece signatures, settlement resource totals and Construction XP. With Worker #1 jobs OFF/non-critical, the same snapshot verifies exit/re-entry persistence without modifying the save.
- Con Revamp exposes Self-Test, Capture Build Baseline, Check Build Baseline and an ordered Exit + Outside Rejection action.

Runtime acceptance target:

`Self-Test PASS -> Capture Build Baseline -> exit/re-enter -> Check PASS -> Exit + Outside Rejection returns the outside-settlement denial`

## Phase 2 — Population + broader survival production

**Status:** ACTIVE

### Bundle 2.1 — Population capacity + additional-worker recruitment

**Status:** DONE / RUNTIME VERIFIED

Persistent population owner:

- `SettlementState` remains the single persistent worker owner and now derives starter population capacity from the verified shelter milestone: incomplete shelter = 0 capacity; completed starter shelter = 2 capacity.
- The first Phase-2 capacity is intentionally derived rather than stored in a second counter. Later beds/housing extend this same owner.
- Worker #1 remains `STARTER_SETTLER` and still auto-arrives through `ensureStarterWorker()`; no Worker #1 identity/jobs/needs/progression behavior is replaced.
- `SettlementWorkerDefinition.RECRUITED_SETTLER` provides Worker #2's stable definition key and a separate reserved plot-relative home slot.
- `SettlementState.recruitAdditionalWorker()` is shelter/capacity gated, allocates the next stable worker id, defaults the new worker's Allowed Jobs OFF through normal `SettlementWorkerState` construction, and refuses a third worker at starter capacity.
- No save schema bump is required: existing `workers` + `nextWorkerId` already persist the new worker record.

Runtime owner:

- `SettlementInstance.ensureSettlementWorkersRuntime()` preserves automatic Worker #1 creation, then projects every saved worker exactly once by stable worker id.
- Recruited Worker #2 is projected immediately in the live instance and all saved workers rebuild through the same path after instance destruction/re-entry and player save/load.
- Existing `SettlementWorkerNpc` AI/needs/progression behavior is reused unchanged; Worker #2 begins idle because its Allowed Jobs default OFF.
- `SettlementWorkerArrivalCheck` now validates the starter worker without assuming the entire settlement must contain exactly one worker.

Developer acceptance:

- Con Revamp adds Population Status, Recruit Worker #2, Population Self-Test and Population Check.
- `SettlementPopulationCheck.runSelfTest()` disposably verifies capacity lock/unlock, Worker #2 recruitment, unique ids, default jobs OFF, capacity-full rejection and serialization.
- `SettlementPopulationCheck.run(player)` verifies the real settlement has starter + recruited workers with unique ids and exactly two live NPC projections.

Acceptance direction:

`Population Self-Test PASS -> status workers=1/2 READY -> recruit Worker #2 -> Population Check PASS saved=2/runtime=2 -> exit/re-entry PASS -> logout/relog + re-entry PASS`

### Bundle 2.2 — Multi-worker control + concurrent work

**Status:** DONE / RUNTIME VERIFIED

Patch 2.2.1 — worker-id targeting:

- Existing owner-only worker commands now accept an optional stable worker id while preserving the old no-id Worker #1 syntax for compatibility.
- Jobs status/toggle/all, Needs status/set/reset, AI status and Progress status resolve the requested persistent `SettlementWorkerState` through `SettlementState.findWorker(...)`.
- Missing worker ids are rejected explicitly instead of falling back to another worker.

Patch 2.2.2 — Con Revamp worker selector:

- Con Revamp now has a numeric persistent Worker ID selector used by Allowed Jobs, Needs, AI and Progress controls.
- Switching selected workers clears local Allowed Jobs checkbox visuals so stale Worker #1 UI state cannot be mistaken for Worker #2's authoritative saved state.
- Server readback remains authoritative; the client selector owns no worker policy.

Patch 2.2.3 — Worker #2 real work control:

- No second AI implementation was added. Targeted Allowed Jobs simply expose the already-verified `SettlementWorkerNpc` gather/haul/needs/progression loop for Worker #2's own persistent state.
- Worker #2 can therefore be independently assigned gathering + Haul while Worker #1 retains a different allowlist.

Patch 2.2.4 — concurrent workers:

- Both runtime NPCs already execute independently against their own `SettlementWorkerState`; shared settlement resource mutation remains in the synchronized `SettlementState` resource owner.
- Bundle acceptance deliberately uses different resource jobs (Worker #1 Food + Haul, Worker #2 Wood + Haul) to prove simultaneous independent work without relying on same-node contention.
- Disabling one worker's jobs must not stop the other worker.

Patch 2.2.5 — all-worker status:

- `workerallstatus` reports saved/runtime counts, shared storage, and each worker's authoritative jobs, needs, skills and live AI summary in one bounded command.
- Con Revamp exposes this readout alongside selected-worker controls.

Patch 2.2.6 — final gate:

- `SettlementBundle22FinalGate.runSelfTest()` disposably verifies two-worker id targeting, independent Allowed Jobs, Needs, progression and serialization through the real persistent worker-state APIs.
- Live baseline capture is read-only/process-local and requires exactly two runtime workers with all jobs OFF and no critical needs.
- Baseline check compares both worker ids/definitions, Allowed Jobs, Needs, all personal skill XP and player Construction XP across instance rebuild/relog.
- Con Revamp exposes Bundle 2.2 Self-Test, All Worker Status, Capture 2-Worker Baseline and Check 2-Worker Baseline.
- Con Revamp was pruned to the active Phase-2 workflow: completed Phase-1/Bundle-2.1 test cards and redundant self-test buttons are removed from the tab while their server commands remain available for regression use.
- Con Revamp action buttons are non-focusable and `setStatus(...)` preserves/restores the current viewport position, preventing status-output updates from jumping the scroll position to the bottom.

Patch 2.2.7 — per-resource storage ownership:

- Replaced the active shared `200/200` storage bucket with definition-owned starter capacities per `SettlementResource`: Wood 100, Food 100, Stone 100, Basic ore 100.
- Existing saved resource totals are preserved exactly; the legacy serialized shared-cap field remains only for old-save compatibility and no longer controls deposits.
- `SettlementState.getStorageCapacity(resource)` / `getStorageRemaining(resource)` are now the single capacity seam. Future storage buildings should extend this owner rather than adding another counter.

Patch 2.2.8 — resource-isolated hauling:

- `SettlementWorkerNpc` now checks remaining capacity for its carried resource, not aggregate storage.
- Full Wood storage blocks only Wood deposits; Food/Stone/Ore workers continue independently while their own storage has room.
- `SettlementState.addResource(...)` clamps against the target resource's capacity.

Patch 2.2.9 — storage readback + gate:

- Resource summaries now render `amount/capacity` for each resource plus aggregate `total/totalCapacity`.
- Existing `All Worker Status` therefore exposes the new separated storage model without adding another temporary UI owner.
- `SettlementResourceSelfTest` now proves a full Wood store cannot block Food; Bundle 2.2 Self-Test consumes that real resource-storage self-test before worker targeting checks.

Patch 2.2.10 — storage-aware worker job selection:

- Workers no longer select or continue a gather target when that resource's own storage has no remaining capacity.
- If storage becomes full while a worker is moving/gathering, the gather action is canceled before new cargo is created.
- A worker with multiple allowed gather jobs can skip full-resource jobs and select another allowed resource that still has storage space.
- When every allowed gather resource is full, AI reports `Allowed resource storage is full.` instead of gathering excess cargo.

Patch 2.2.11 — in-flight storage reservation:

- Runtime evidence exposed a concurrency race at the final per-resource slot: two workers could both see `99/100`, one could deposit first, and the other could stop mid-haul holding cargo after the resource reached `100/100`.
- `SettlementInstance` now owns transient per-worker/per-resource storage reservations. Reservations are runtime-only and never enter `SettlementState` or player-save serialization.
- A worker atomically reserves its one carried-resource slot before gathering begins; another worker cannot reserve the same final slot, but can still reserve capacity for a different resource.
- The reservation follows gathered cargo through hauling and is consumed/released by the authoritative deposit path.
- Job disable, gather cancellation, need interruption, worker runtime recreation, and settlement-instance destruction release stale reservations.
- The Bundle 2.2 disposable self-test now reproduces the exact one-slot race: Worker #1 reserves final Wood; Worker #2 must fail Wood reservation but still succeed Food reservation.

Patch 2.2.12 — persistent worker Pause/Resume:

- Added a persistent per-worker `paused` policy flag to `SettlementWorkerState`; existing saves deserialize it as false, so no save-schema migration is required.
- Pause is independent of Allowed Jobs. It stops new productive gather/haul work without erasing the worker's saved job policy.
- Pausing cancels any in-progress gather target and releases its transient storage reservation. Already-carried cargo remains with the worker, but its reservation is released until work resumes.
- Needs/recovery remain active while paused so Pause cannot freeze Hunger/Thirst/Energy recovery behavior.
- Resume continues from the same persistent Allowed Jobs policy.
- Owner-only `workerpause` command and restored Con Revamp selected-worker controls expose Pause/Resume; all-worker/live-AI status report pause state. Pause/Resume remains independent of Allowed Jobs.
- Bundle 2.2 disposable serialization and live persistence snapshots now include independent pause state for both workers.

Patch 2.2.13 — selected-worker role presets:

- Added server-authoritative `SettlementWorkerRolePreset` definitions for Lumberjack, Forager, Stone Miner, Ore Miner, Hauler Only and Idle.
- Presets are convenience writes into the existing persistent Allowed Jobs set only; there is no second persistent role field or AI owner.
- Applying a preset never changes Pause, Needs, identity or worker progression.
- `workerpreset [workerId] <preset-key>` resolves the same stable worker-id owner used by the existing selected-worker commands and rewrites the complete allowlist atomically.
- Jobs Status / All Worker Status infer and display the matching preset when the exact allowlist matches; otherwise they report `Custom`.
- Con Revamp adds a compact selected-worker Role Preset dropdown + Apply button inside the existing Allowed Jobs card and locally mirrors the selected preset checkboxes after the authoritative command is queued.
- Bundle 2.2 Self-Test cycles through all six preset definitions, verifies exact round-trip matching, then uses Worker #1 Forager / Worker #2 Lumberjack for the existing independent-state serialization gate.

Patch 2.2.14 — storage developer/test controls:

- Added owner-only `storagereset` to empty Wood/Food/Stone/Basic ore through the existing authoritative `SettlementState.removeResource(...)` path.
- Added generic `storageset <resource> <amount>` with resource validation and exact per-resource capacity bounds; no second storage owner or direct map mutation was added.
- Con Revamp now exposes Storage Status, Reset All Storage, Prime Wood 99/100 and Storage Self-Test.
- Prime Wood 99/100 makes the final-slot concurrency regression cheap to reproduce; workers should be paused/idle before priming for deterministic acceptance.
- Storage Self-Test reuses the existing disposable `SettlementResourceSelfTest`, so one-click validation does not mutate the player's persistent settlement.

Patch 2.2.15 — zero-Food hunger bootstrap recovery:

- Runtime acceptance exposed the historical zero-Food Hunger deadlock as a real Bundle 2.2 blocker: at critical Hunger, Needs processing stopped a Forager before it could gather the Food required to recover.
- A worker may now enter emergency Food foraging only when it is unpaused, Hunger is critical, Gather Food + Haul are both allowed, Food storage has room, and the starter Food node is available.
- Emergency mode forces the Food node instead of rotating through other allowed gather jobs, then ends after the gathered Food is deposited so normal Hunger recovery consumes stored Food on the next cycle.
- Workers without the Food+Haul policy still stop normally at critical Hunger with no stored Food; Pause, Allowed Jobs ownership, storage reservations, progression and persistence are unchanged.

Patch 2.2.16 — prepared baseline capture:

- Added `SettlementBundle22FinalGate.prepareAndCapture(...)` for the remaining persistence gate.
- It requires exactly saved=2/runtime=2, then explicitly disables every Allowed Job and resets Needs for both persistent workers before calling the existing authoritative baseline capture.
- Pause state, identity, progression and Construction XP are not rewritten; they remain part of the captured persistence snapshot.
- Con Revamp removes already-completed Bundle 2.2 runtime instructions/buttons from the Final Gate card and now exposes only Prepare + Capture Baseline and Check 2-Worker Baseline.

Runtime acceptance target:

`ACCEPTED: Storage Self-Test PASS -> Reset Storage -> Prime Wood 99/100 -> W1 Forager / W2 Lumberjack concurrent production -> final-slot reservation race-safe -> prepared two-worker baseline -> exit/re-enter PASS -> logout/relog/re-enter PASS`

### Bundle 2.3 — Housing, beds + population capacity

**Status:** DONE / RUNTIME VERIFIED

Patch 2.3.1 — persistent bed capacity + Worker #3 foundation:

- `SettlementState` schema v9 owns persistent `housingBedCount`; the completed starter shelter remains base capacity 2 and each saved housing bed adds +1 capacity through the same population owner.
- Bed capacity cannot be added before the starter shelter, and occupied capacity cannot be removed below the current worker count.
- Recruitment is no longer hardcoded to a single `RECRUITED_SETTLER`. Additional recruited settlers keep the same stable definition key but receive unique free plot-relative home slots from the recruited-worker arrival row.
- Recruitment skips occupied saved-piece/home slots and refuses when no valid home slot or population capacity remains.
- Runtime placement reserves actual saved worker home tiles in addition to the original starter/recruited arrival anchors.
- `SettlementHousingCheck.runSelfTest()` disposably proves capacity 2 -> 3, unique Worker #3 id/home, default jobs OFF, default unpaused state, removal guard and Java serialization.
- Live housing check requires at least one saved bed and at least three persistent workers, verifies capacity exactly equals starter 2 + saved beds, and requires runtime projection count to match the full saved population with unique ids/home slots.
- The process-local Bundle 2.3 housing baseline captures the full current worker set, persistent bed count, capacity and exact worker id/definition/home signatures across instance rebuild and logout/relog; populations beyond Worker #3 are valid and must round-trip exactly.
- Con Revamp replaces the completed Bundle 2.2 gate card with Bundle 2.3 Housing controls: status, Add/Remove Bed Capacity, Recruit Next Worker, Self-Test, live Check and Worker #3 baseline/check.
- **Art boundary:** Add/Remove Bed Capacity are developer harness controls only. No bed object id was guessed. Normal Construction placement hookup remains Patch 2.3.2 after a bed asset is verified.

Runtime acceptance target:

`Self-Test PASS -> Housing Status workers>=3 with beds>=1 -> live Check PASS with saved/runtime counts equal -> Capture housing baseline -> exit/re-enter Check PASS -> logout/relog/re-enter Check PASS`

Runtime acceptance: VERIFIED. The live settlement persisted beds=3, capacity=5 and workers=5/5 through settlement exit/re-entry and full logout/relog. The scalable gate passed with saved=5/runtime=5 and exact captured worker identities/home slots preserved.

Patch 2.3.2 — physical bed placement + housing-capacity hookup:

- Runtime asset evidence: Matrix3 Dev Inspector identifies object definition `14872` as `Bed`. The initial palette mapping uses standard game-object type `10`; that type mapping remains a runtime acceptance point rather than being treated as independently verified.
- Added stable `BASIC_BED` / `basic-bed` as a `BED` build role with 3 Wood cost and 12 base Construction XP.
- Client Construction palette now exposes a `Furniture` category with Bed and routes it through the existing ghost/rotation/`settlementbuild` player-build path.
- `SettlementState.place(...)` is now the central physical-bed capacity seam: successful persistent Bed placement adds one existing housing-capacity unit. `duplicate(...)` follows the same rule.
- `SettlementState.remove(...)` removes the corresponding housing-capacity unit only when doing so would not put saved worker population over capacity; otherwise the physical Bed remains.
- `SettlementPlayerBuildTransaction` rejects Bed placement before the starter shelter / when no housing slot can be added and rolls persistent piece + capacity back together if material consumption cannot complete.
- Existing developer Add/Remove Bed Capacity controls remain test harnesses only; normal gameplay capacity is now reachable through physical Bed placement.
- Bundle 2.3 Self-Test now exercises the real Bed transaction, pre-shelter rejection, unoccupied removal, occupied-removal guard and serialization.
- Runtime acceptance: VERIFIED. Bed 14872 renders correctly through the palette/ghost path with object type 10; placement consumed 3 Wood and awarded 12 base Construction XP per Bed; housing capacity increased/decreased with physical placement/removal; exact placement/capacity survived exit/re-entry and logout/relog; occupied-capacity removal was correctly rejected.
- Bunk bed `24339` is recorded as a future space-efficient housing upgrade candidate; capacity >1 per furniture piece is intentionally deferred until the basic single-bed path is runtime accepted.
- Keep cooking, farming/hunting and broader worker-management UI as later Phase-2 work after this physical housing gate is accepted.

### Bundle 2.4 — RTS radial multi-worker control

**Status:** IMPLEMENTED / NEEDS RUNTIME TEST

Ownership:

- RWS-5 promotes the already-proven radial drag/detection tool into a real worker-management command seam without creating a second worker identity system.
- On mouse release, the client snapshots only the active runtime NPC indexes inside the accepted world-space drag circle.
- The client immediately sends that one-time runtime-index snapshot to the active settlement.
- `SettlementInstance` resolves those transient NPC indexes only against its own live `SettlementWorkerNpc` projections, converts them to authoritative persistent Worker IDs, and stores the committed selection only for the lifetime of that settlement instance.
- Batch actions never trust client NPC indexes after the commit step. Re-entry creates a new `SettlementInstance` with no stale server selection, so the player must drag-select again.
- The committed radial selection is transient management state only; no selection list is added to `SettlementState` or player-save serialization.

Batch worker actions:

- `workerselectionstatus` reports the server-owned selected Worker IDs plus current preset, Pause state and Allowed Jobs.
- `workerselectionpreset` applies one existing `SettlementWorkerRolePreset` to every selected persistent worker. It changes only the existing Allowed Jobs policy and leaves Pause, Needs, identity and progression untouched.
- `workerselectionjob <job> <on|off>` applies one Allowed Job toggle to every worker in the committed drag selection.
- `workerselectionjobsall <on|off>` enables/disables every existing Allowed Job for every worker in the committed drag selection.
- `workerselectionpause on|off` applies the existing persistent Pause flag to every selected worker without rewriting Allowed Jobs.
- `workerselectionclear` clears only the transient active-instance selection.
- **Command ownership:** RWS-5 committed drag selection is now the sole Con Revamp mutation target for Role Preset, Pause/Resume and Allowed Jobs. The numeric Worker Inspector is inspection-only for per-worker Jobs/AI/Needs/Progress readback; it cannot redirect worker commands.
- Both RWS-5 quick controls and the Allowed Jobs card route to the same server-owned committed selection, eliminating the prior split ownership.
- **Selection visibility:** after release, the large drag circle disappears but the selected workers retain their existing layered GFX 4171 rings. The rings follow the committed runtime NPCs until Clear Selection, replacement by a new committed drag, Worker Control is disabled, or the runtime NPC set disappears during settlement instance rebuild/exit.

Runtime acceptance target:

`Enable Worker Control -> drag-select workers and optionally self -> release -> selected-unit rings persist -> single ground click preserves selection and issues no move -> second ground click within the bounded double-click window moves selected workers and also self only when self is selected -> vanilla resource actions command selected workers -> manual order completes -> worker policy resumes -> Clear/reselect updates rings -> settlement exit/re-entry requires a fresh selection`

Runtime defect/fix note:
- First RWS-5 runtime attempt: drag preview worked, but mouse release produced no useful selection action.
- Root seam isolated verified-static: the initial implementation re-traversed Matrix3 live NPC collections from the AWT mouse-release handler even though the proven worker detection runs in the Matrix3 render/game path.
- Fix implemented: the render-thread preview now caches the exact runtime NPC indexes it already detected/ringed. Mouse release only copies that cached array and queues the existing server-owned selection commit; it no longer traverses live NPC collections from AWT.
- This preserves exact visual-selection parity: the workers ringed by the last rendered drag frame are the workers committed on release.
- Runtime VERIFIED: after the fix, a two-worker drag released successfully and game chat reported `Radial worker selection committed: #1,#2.`
- Follow-up runtime exposed a UI ownership mismatch: after selecting #1,#2, the legacy lower Allowed Jobs card still sent `workerpreset <spinnerWorkerId>`, so only Worker #1 changed.
- Ownership fix implemented: the lower Role Preset, Pause/Resume, individual Allowed Job and Enable/Disable All controls now target the committed RWS-5 selection. The numeric spinner is explicitly inspection-only.
- Runtime confirmation: user reports all multi-worker command behavior now works; the only remaining Bundle 2.4 issue was that selected-worker rings disappeared on release.
- Persistent-ring patch implemented: the scene pass now renders the committed selected-worker ring layers after release without re-rendering the large drag circle.
- Follow-up runtime clarified that ordinary clicks were replacing the selection because every mouse press/release counted as a drag. RWS now requires a 6px drag threshold; a normal click preserves the committed group and continues into Matrix3's normal context action.
- RWS self-selection is now part of the transient active selection: the local player can be inside the drag circle, receives the same layered 4171 ring, and the active SettlementInstance records transient `self` membership alongside persistent Worker IDs without adding save-schema state.
- Matrix3's existing context actions are the RTS command surface rather than a second custom command UI. Ground movement is deliberately RTS-gated: the first action-23 ground click is consumed as a pending move click, and a second click within 375 ms on the same/adjacent tile issues the transient Move order. If self is selected, only that accepted second click falls through to vanilla Walk Here for the player.
- Starter-resource skilling now follows the same vanilla interaction seam. First object option routes Wood tree 1276, Stone 11933 and Basic ore 11936 to the selected workers; first NPC option routes Food spot 327. Choosing those same options from the normal right-click menu produces the identical action dispatch, so workers respond to normal vanilla left-click/right-click skilling without a parallel worker menu.
- A committed selection adds one RuneScape-native `Clear Selection` option to world right-click menus (ground/object/NPC). It clears the client rings/self flag and the active SettlementInstance selection together; ordinary clicks and other vanilla menu actions do not clear the group.
- Worker manual Move/Gather orders are runtime-only overrides owned by `SettlementWorkerNpc`; after the order completes, normal Allowed Jobs AI resumes. Pause remains authoritative.
- The worker arrival seam is hardened: resource gathering now requires explicit physical interaction range (adjacent for resource nodes). A `calcFollow(...)` success with zero queued steps no longer means the worker has arrived, preventing the remote-chop behavior seen in runtime video.
- Persistent rings no longer clear merely because one render pass cannot resolve a selected NPC. Local selection clears when explicitly replaced/cleared or when the committed selection center leaves the active scene, covering settlement exit/rebuild without transient-frame flicker.
- Runtime video exposed action arbitration after a successful selection: releasing a second radial drag could immediately fall through as Matrix3 Walk Here and move the newly selected self/workers. The client consumes that release-side action 23 and explicitly clears pending move-click state.
- Follow-up UX decision: selected-unit ground movement requires a bounded double-click (375 ms, same/adjacent tile). A single ground click never moves the selected group, and a completed radial drag can never count as the first move click. Fresh Construction camera sessions still force RTS mode on entry.

## Phase 3 — Processing chains + better materials

- Smithing/crafting.
- Processed construction materials.
- Better buildings/workstations.
- Advanced storage/input/output rules.

## Phase 4 — Logistics automation

Early asset-discovery tooling is intentionally pulled forward without advancing the Phase-4 gameplay gate:
- Object / Automation Asset Probe is IMPLEMENTED / NEEDS RUNTIME TEST. It reads effective live object slots from the player's current tile or nearby 3x3 area and can append candidate IDs/types/rotations/definition metadata to `Server/data/construction/object_catalog.txt`. It is read-only and exists to identify track/cart/loading assets before persistent rail gameplay is defined.
- Matrix3 Asset Studio v1 (superseding the debug-form Object Lab workflow) is IMPLEMENTED / NEEDS RUNTIME TEST. It captures live client-scene objects around the player without requiring object interaction, reads exact ID/type/rotation from Matrix3 scene objects, deduplicates multi-tile scene references, browses the capture as a session table, auto-populates the direct-render viewer, filters rail candidates and exports the entire session to `Client/data/construction/asset_studio/captures/`. The server Object Probe remains a diagnostic cross-check rather than the primary workflow.
- Asset Studio paired evidence capture is IMPLEMENTED / NEEDS RUNTIME TEST. One click performs a fresh 9x9 scene capture, deterministically orders/number-labels the captured objects, saves matching `capture_YYYYMMDD_NNN.png` + `.tsv` files, captures the real Matrix3 game canvas through `Class584.aCanvas7745`, and appends a numbered tile-grid/row legend to the PNG. The workflow is read-only and exists so rail primitives can be identified from uploaded evidence without manual ID/type/rotation transcription.
- Rail Kit Classifier is IMPLEMENTED / NEEDS RUNTIME TEST as a native lazy-loaded `Client Console -> Test -> Rail Classifier` sub-tab; the temporary standalone classifier window path has been removed. It seeds the 27 type-22 rail candidates verified from the 2026-09-21 evidence captures, can refresh/merge live 9x9 rail candidates without opening Asset Studio, previews the selected object client-only beside the live player, supports double-click spawn plus arrow-key previous/next spawning, and persists independent Straight/Curve/Merge/Split/End-Buffer/Crossing/Not-Rail/Unsure checkboxes to `Client/data/construction/asset_studio/rail_kit.tsv`.
- A->B Rail Route Preview V0 is IMPLEMENTED / NEEDS RUNTIME TEST inside the docked Rail Classifier. It reuses Matrix3's resolved action-23 ground hover plus the proven Class578 direct-render seam: Left-drag captures A/B, generates a bounded X-then-Y or Y-then-X Manhattan path, and direct-renders the configured straight rail per tile with automatic horizontal/vertical rotation. V0 is deliberately client-only and straight-only; the corner uses a placeholder straight orientation so drag feel/path generation can be judged before final curve/switch auto-tiling.
- A->B Rail Route Preview V1 is IMPLEMENTED / NEEDS RUNTIME TEST. The docked tool now auto-configures the first saved `Curve` classification in addition to `Straight`; orthogonal routes replace the placeholder bend tile with that curve object and select one of four quadrant rotations from the route's neighboring directions. A non-destructive R0-R3 curve-map offset is exposed for runtime calibration because the classified curve's canonical base orientation is not yet verified.
- Rail build UX decision: individual 1x1 rail objects are internal authoring/auto-tiling primitives, not the intended player-facing placement unit. The future Construction rail tool will drag Point A -> Point B and choose/place the required straight/curve/switch primitives automatically from the verified Asset Studio rail kit.
- Generic Object Explorer + Same-Tile Overlay Proof are IMPLEMENTED / NEEDS RUNTIME TEST under `Client Console -> Test -> Object Explorer`. The tool searches named object definitions, browses the full object-id range including unnamed definitions, direct-renders arbitrary ID/type/rotation combinations, saves research rows to `Client/data/tools/object_explorer.tsv`, and can draw two stock object models on exactly the same tile. The overlay mode proves visual composability only: Matrix3 still exposes one ordinary floor-decoration scene slot per tile, so two type-22 floor decorations cannot both be registered there normally. If stock rail fragments visually compose into a compact curve, the settlement rail layer can later persist one logical tile and render a composite rather than requiring a new cache model.

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

- Phase: Phase 2 — Population + broader survival production
- Phase status: ACTIVE
- Last completed phase: Phase 1 — MVP Vertical Slice (DONE / runtime accepted)
- Persistent-runtime bundle: 2.4 — RTS radial multi-worker control
- Persistent-runtime bundle status: IMPLEMENTED / NEEDS RUNTIME TEST
- Tooling track: Phase-1 Construction palette + ghost + Free Build camera
- Tooling status: Phase-1 Free Build DONE / runtime accepted; RTS default + pivot-orbit camera DONE / runtime VERIFIED; adjustable RTS pan speed IMPLEMENTED / NEEDS RUNTIME TEST; Matrix3 Asset Studio v1 + paired evidence capture + Rail Kit Classifier + Object Probe fallback IMPLEMENTED / NEEDS RUNTIME TEST; later Top Down/Orbit/Player presets remain non-blocking
- Side tooling verification: stand on/near a known track or cart, run Current Tile then Nearby 3x3 if needed, confirm ID/name/type/rotation/options readback, then Log and verify `Server/data/construction/object_catalog.txt` receives the full scan.
- Approval state: Bundle 2.3 is fully RUNTIME VERIFIED. Bundle 2.4 RWS-5 radial multi-worker control is SAP AAA approved and implemented.
- Current checklist item: focused RWS-5 persistent-selection-ring retest: drag-select one or more workers, release, verify the large drag circle disappears while the selected workers' small rings remain attached/following them; Clear Selection and a replacement drag must update/remove the rings cleanly.
- Current objective: runtime-accept persistent selected-worker ring visibility, then close Bundle 2.4 and continue Phase 2 into cooking and farming/hunting production chains.

## Verification classifications

### VERIFIED

- Bundle 2.2 final persistence gate is runtime VERIFIED: `Prepare + Capture Baseline` saved a two-worker baseline, the baseline passed after settlement exit/re-entry with saved=2/runtime=2, and it passed again after logout/relog/re-entry with worker identities, pause state, Allowed Jobs, Needs, progression and player Construction XP preserved.
- Bundle 2.2 role-preset + concurrent-production acceptance is runtime VERIFIED: Worker #1 Forager and Worker #2 Lumberjack operate independently, Food and Wood production proceed concurrently, and Wood reaching 100/100 does not leave a worker stranded carrying excess Wood.
- Bundle 2.2 zero-Food Hunger bootstrap recovery is runtime VERIFIED: with Food storage empty and Hunger critical, a Forager with Gather Food + Haul enabled successfully escapes the starvation deadlock and resumes productive work after emergency Food recovery.
- Bundle 2.2 Self-Test is runtime VERIFIED: runtime PASS covered per-resource storage isolation, in-flight reservation race protection, all worker role presets, stable worker-id targeting, independent pause/jobs/needs/progression, and two-worker serialization.
- Bundle 2.2 Prime Wood 99/100 is runtime VERIFIED: the Con Revamp developer control sets Wood to 99/100 correctly without requiring manual gathering, completing runtime acceptance of the storage test-control slice.
- Bundle 2.2 Reset All Storage is runtime VERIFIED: the Con Revamp control successfully clears persistent Wood/Food/Stone/Basic ore storage through the existing authoritative SettlementState resource APIs.
- Bundle 2.2 Storage Self-Test is runtime VERIFIED: Con Revamp `Storage Self-Test` returned PASS for per-resource storage add/remove/capacity isolation plus the four starter-node definitions. The disposable test does not mutate the player's persistent settlement storage.
- Bundle 2.2 selected-worker Pause/Resume is runtime VERIFIED: Pause Worker stops the selected worker's productive work, Resume Worker restarts it, and the worker retains its existing Allowed Jobs policy; the user confirmed the controls work correctly in the live settlement.

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
- Bundle 1.5 positive player-build path is runtime VERIFIED: Wooden fence consumed 1 Wood and awarded 4 base Construction XP, Floor decoration consumed 1 Wood and awarded 4 base Construction XP, and Door consumed 2 Wood and awarded 8 base Construction XP through the new palette -> `settlementbuild` path.
- Occupied player-build placement is runtime VERIFIED to return `That settlement slot is already occupied.`; source ownership verifies this rejection occurs before material consumption and active XP award.
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
- First Class24 reuse runtime acceptance was partial: opening Construction successfully detached the camera, but W/A/S/D/Q/E did not move it. The movement code had been placed in `Class24.method711()` and was not reached by the current Construction runtime path.
- Live-tick Free Build controls are runtime VERIFIED: the user's follow-up sweep confirmed automatic activation, W/S/A/D, Q/E, Shift/Ctrl speed modifiers, mouse-look, normal-camera restore and clean close/reopen behavior.
- Smoothed Free Build integration is runtime VERIFIED: acceleration/deceleration, normalized diagonals, Shift/Ctrl under velocity smoothing, click-to-stop with held-key latch, planted-player action-23 ownership, exactly one authoritative Paint object/no Walk Here, detached ghost stability and combined camera/render stability all passed the user's acceptance run.
- Runtime CAM DEBUG captures proved the observed moving view remained mode 1 / `source=CLASS411` while generic camera XYZ stayed `0,0,0`; source tracing separately established `Class24.aClass411_Sub1_158` as the actual developer detached-camera owner.
- Bundle 1.3 starter resource/storage slice is runtime VERIFIED by the user: Resource Self-Test passes and the current wood/food/stone/ore node + settlement-only storage flow works in the live settlement.
- Bundle 1.3 starter shelter milestone is runtime VERIFIED: the user's real settlement automatically completed after reaching 9 walls, 5 floors, 1 doorway and 1 Wood/Food/Stone/Basic ore, and `Bundle 1.3 Final Check` returned PASS on the loaded settlement.
- Worker #1 first-arrival path is runtime VERIFIED: entering the completed settlement automatically produced the visible settler NPC, Worker Self-Test passed, and Worker Arrival Check passed with exactly one persistent worker and one live NPC projection.
- Worker #1 exit/re-entry persistence is runtime VERIFIED: re-entering rebuilt the same Worker #1 (`id=1`, `starter-settler`, home `24,12,0`) with `saved=1/runtime=1` and Worker Arrival Check PASS.

### verified-static

- RWS context-command ownership is verified-static pending runtime: Class319 dispatch now gives `ConstructionRadialSelection` first refusal on Matrix3 action 23 and first-object action 3; worker Move/Gather orders are sent through the existing owner bridge to the active SettlementInstance, while selected-self actions fall through to vanilla Matrix3 player movement/interaction.
- Worker gather-arrival hardening is verified-static pending runtime: `SettlementWorkerNpc.walkToward(..., interactionRange)` requires explicit tile range after `calcFollow`; zero queued steps outside range no longer starts gathering.
- Bundle 2.4 RWS-5 is verified-static pending runtime: the client commits a world-space detected runtime-NPC set once on drag release; the active `SettlementInstance` validates only its own live `SettlementWorkerNpc` indexes, converts them to persistent Worker IDs and owns the transient selection; batch preset/pause/status commands then operate only on those server-owned Worker IDs. No new persistent selection state or worker identity owner was added.
- Bundle 2.3 Patch 2.3.2 physical-bed path is VERIFIED at runtime: object definition 14872 renders correctly as Bed through the Furniture palette and existing player `settlementbuild` path using object type 10; each physical Bed consumes 3 Wood, awards 12 base Construction XP and adds +1 housing capacity; spare-capacity removal subtracts that capacity; required-bed removal is rejected; physical placement and capacity survive exit/re-entry and logout/relog.
- Bundle 2.3 Patch 2.3.1 housing-capacity foundation is VERIFIED at runtime: SettlementState schema v9 persists housingBedCount; base shelter capacity 2 gains +1 per bed; recruitment supports multiple recruited-settler records with unique free saved home slots; occupied capacity cannot be removed; the live five-worker settlement persisted beds=3, capacity=5 and workers=5/5 across exit/re-entry and logout/relog; saved/runtime projection counts remained 5/5 and exact captured worker identities/home slots survived. No bed art id is assumed; normal physical bed placement remains Patch 2.3.2.


- Bundle 2.1 population ownership is VERIFIED at runtime: Population Self-Test passed; Worker #2 recruited successfully from the completed shelter capacity; live Population Check reported `workers=2/2`, `saved=2/runtime=2`, unique Worker #1/#2 ids/projections; Worker #2 remained idle under the default-OFF Allowed Jobs policy; logout/relog + settlement re-entry preserved the two-worker population without duplicate runtime projections.

- `matrix-3` is the correct repository.
- Protected Matrix3 baseline is `e86851b95e1d2927d58463b67f600153b9166f6a`.
- Matrix3 has its own `Server/src/main/java/com/rs/game/player/...` ownership structure.
- Current Matrix3 Construction runtime contains the classic serializable `House` stack and `HouseControler`.
- Before Bundle 1.2, `Player` owned/persisted classic `House` and no freeform settlement foundation existed. Bundle 1.2 now adds a separate `Player.settlementState` without replacing or migrating `House`.
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
- Ctrl+backtick is verified-static as the existing developer detached-camera activator: Java backtick maps to internal key 28, Ctrl to 82, and the input branch calls `Class102_Sub5.method9948(...)` to create `Class24.aClass411_Sub1_158`; `IncomingPacket.method4113(...)` exposes detached-camera state and `RSSocket.method7604(...)` closes it.
- `ConstructionBuildCamera` activates/reuses the existing Class24/Class411 camera and tracks ownership so closing Construction does not kill a camera that was already manually active. Its `tick()` now runs from the live `Class343.method4302(...)` viewport seam, is guarded to once per `client.cycles`, and directly updates the detached Class411 position/orientation for W/S/A/D, arrow aliases, Shift/Ctrl, Q/E and mouse look. `Class24.java` no longer contains Construction movement changes.
- `ConstructionBuildCamera` now applies normalized, time-based world-space velocity on that same live tick with bounded delta time, exponential acceleration/deceleration and exact zero settling; `stopMovement()` clears velocity and latches movement off until key release.
- `DevModeBridge.handleMenuAction(...)` now consumes action 23 only while Construction Free Build is active: it stops camera movement, optionally confirms the existing Paint placement and prevents the same click from becoming Walk Here. Outside Free Build, existing Dev Paint action-23 behavior is unchanged.
- `SettlementState` is a normal serializable Player field; new players construct it and existing saves repair a null field during `Player.init(...)`, so no parallel save system is introduced.
- `SettlementState` now owns plot-bound validity itself: only plane 0 and plot-relative X/Y in 0-63 are accepted by place/move/duplicate, so persistent correctness no longer depends on callers filtering world coordinates first.
- `SettlementInstance` derives its plot size/plane constants from `SettlementState` and refuses to project any invalid saved record, keeping runtime projection aligned with the saved owner.
- `SettlementStateAudit` is a read-only real-save invariant check for unique positive piece ids, approved definitions, valid plot location/plane, 0-3 rotation and no same-object-type occupancy collision.
- `SettlementBundle12FinalCheck` aggregates the disposable state self-test, real-save audit, saved-piece count consistency and definition-registry uniqueness/lookup checks into one non-mutating owner-only PASS/FAIL path.
- `SettlementResource` defines the four stable settlement-only resource identities; `SettlementState` schema v3 owns their persistent totals, 200-unit starter storage capacity and stable completed-milestone keys.
- `SettlementResourceNode` defines four fixed reserved starter node positions. `SettlementInstance` spawns/cleans their transient objects/NPC and rejects building onto reserved node tiles.
- `SettlementControler` consumes only exact settlement starter-node clicks; ordinary ObjectHandler/Woodcutting/Mining/Fishing ownership remains unchanged outside those nodes.
- `SettlementGatherAction` adds resources directly to settlement storage and contains no inventory/bank mutation path.
- `SettlementResourceSelfTest` disposably verifies storage add/remove/capacity clamping plus unique/valid four-node definitions.
- `SettlementBuildRole` decouples starter shelter semantics from provisional art ids; current definitions map to WALL/FLOOR/DOOR.
- `SettlementMilestone.STARTER_SHELTER` is the stable persisted progression key. `SettlementState` requires 4 walls, 4 floors, 1 doorway and 1 of each starter resource, then latches completion permanently.
- `SettlementShelterSelfTest` disposably verifies threshold gating, one-time completion, permanent latch behavior and Java serialization persistence.
- `SettlementWorkerState` is the serializable worker identity owner under `SettlementState` schema v4; it stores stable id/definition/name and plot-relative home coordinates only.
- `SettlementState.ensureStarterWorker()` is milestone-gated and idempotent, and normalization repairs old saves plus duplicate/invalid worker ids.
- `SettlementWorkerNpc` is the transient Matrix3 NPC projection. `SettlementInstance` rebuilds/cleans it and guarantees one live projection per persistent worker id.
- `SettlementWorkerSelfTest` and `SettlementWorkerArrivalCheck` provide disposable and real-state confidence paths for the first-worker arrival slice.
- `SettlementWorkerJob` defines stable per-worker permission keys for four gathering jobs plus Haul; gathering jobs map to stable `SettlementResource` identities.
- `SettlementWorkerState.allowedJobs` is a schema-v5 persistent allowlist that defaults empty, prunes unknown keys during normalization and exposes explicit server-owned ON/OFF mutation/readback.
- `SettlementWorkerJobsSelfTest` verifies default-OFF behavior, per-job independence and serialization without touching the player's real worker.
- Allowed Jobs controls/self-test/readback are runtime VERIFIED from the user's 2026-09-19 acceptance run.
- First gather/haul runtime acceptance reached the AI state machine but stalled at `MOVING_TO_RESOURCE | No Path to Wood node`; this runtime-rejects the original greedy `Entity.findBasicRoute(...)` movement choice for worker routing.
- Source inspection verified `Entity.findBasicRoute(...)` greedily steps toward the destination and returns false when a direct step is blocked, while intelligent `calcFollow(...)` selects `ObjectStrategy`, `EntityStrategy` or `FixedTileStrategy` from the runtime target type.
- The remaining tree-only failure was traced to `SettlementInstance.getWorkerNodeApproachTile(...)`, which converted every resource node into a synthetic fixed tile. That bypassed Matrix3's object-footprint/access strategy for tree id 1276.
- Worker resource routing now returns the actual live `WorldObject` or starter resource NPC. Runtime showed Worker #1 reaching a tile from which the player can cut the tree but remaining stuck there. The follow-up one-tile adjacency check was runtime-rejected: tree/object interaction is footprint/access-strategy based, not anchor-distance based. `walkToward(...)` now trusts Matrix3's intelligent `calcFollow(...)` contract directly: success with zero queued steps means `ObjectStrategy` / `EntityStrategy` already considers the current tile interaction-ready, so gathering begins immediately.
- Runtime retest on 2026-09-19 confirmed the strategy-owned handoff fixes tree id 1276: Worker #1 now reaches the tree and successfully proceeds into the gather/haul loop instead of stalling.
- Worker needs is runtime VERIFIED: the 2026-09-19 acceptance run showed natural work-cycle need changes, `SettlementWorkerNeedsSelfTest PASS`, critical Hunger recovery, critical Thirst recovery, critical Energy recovery and reset controls all functioning in the live settlement.
- Gather/haul acceptance is runtime VERIFIED from the same consolidated pass: Worker #1 gathers/hauls successfully and the user confirmed the remaining job-toggle behavior works.
- The explicit zero-Food blocked-state branch and restart persistence checks remain recorded as carryover into the progression/basic-XP runtime pass so they can be covered without another dedicated test launch.
- Worker progression is runtime VERIFIED for the productive path: `SettlementWorkerProgressionSelfTest` returned PASS and live work increased the expected personal skills. Runtime evidence showed Food Gathering 0 -> 12 XP, Mining 0 -> 24 XP, and Hauling 6 -> 24 XP while work completed.
- Productive-worker Construction XP is runtime VERIFIED: live Progress Status showed Construction XP 187522810 -> 187522811 after the next successful deposit, then -> 187522814 after additional productive deposits, matching the 1-base-XP-per-stored-resource seam.
- Source ownership remains verified-static for the negative path: only a successful worker deposit calls `SettlementInstance.recordWorkerDepositProgress(...)`; blocked/idle paths have no award call.
- `SettlementBundle14FinalGate` is runtime VERIFIED. The user captured a stable all-jobs-OFF baseline and received exact-match PASS results while idle, after settlement exit/re-entry, and after normal logout/relog without restarting the server.
- The final gate proved Worker #1 identity, Allowed Jobs, Hunger/Thirst/Energy, all personal skill XP totals and player Construction XP remain unchanged through idle time, runtime-instance rebuild and player save/load.
- Developer Construction placement remains outside XP ownership; Bundle 1.5 adds a separate material-consuming player-build path so the dev harness stays no-cost/no-XP.
- Bundle 1.5 player-build ownership is verified-static: stable piece key -> normal-player `settlementbuild` command -> active `SettlementInstance.placePlayerPiece(...)` -> settlement-material consume -> persistent placement -> one active Construction XP award.
- Bundle 1.5 final-gate harness is verified-static: gameplay and disposable tests share `SettlementPlayerBuildTransaction`; the self-test uses only a new disposable `SettlementState`, while persistence capture/check stores read-only process-local signatures/totals/XP.
- Bundle 1.5 final gate is runtime VERIFIED: disposable Self-Test PASS; baseline captured with 19 saved pieces and Wood=81/Food=12/Stone=18/Basic ore=28 at Construction XP 187522868; after exit/re-entry the exact build layout/resource totals/Construction XP baseline passed; the ordered outside-settlement build was rejected with `You must be inside your settlement to build.`.
- Phase-1 closure audit is complete: previously unchecked ghost hover/rotation/model-switch/terrain/cancel behavior and consolidated resource/shelter/world-object persistence carryovers were already runtime-confirmed by the user and are no longer blockers. Historical prototype/debug-only diagnostics are explicitly non-blocking.
- Phase 1 final build/startup gate is accepted from the current runtime evidence: the user confirmed there are no compile errors, and the current Client + Server successfully launched and executed the newly added Bundle 1.5 classes/commands/UI. A failing current build could not have reached that runtime acceptance path.
- `SettlementWorkerNpc` consumes the persistent allowlist as a transient gather/haul state machine, holds one carried resource until Haul/storage are valid, and deposits only through `SettlementState.addResource(...)` via `SettlementInstance`; this tree-specific route correction is verified-static pending runtime acceptance.
- `SettlementPlacedPiece` contains only stable piece identity and plot-relative coordinates/rotation; dynamic chunk/world coordinates are absent from persistent records.
- `SettlementInstance` is the transient projection owner and uses Matrix3 `MapBuilder.findEmptyChunkBound(8, 8)`, `copyChunk(...)`, `destroyMap(...)` and `World.spawnObject/removeObject`.
- The Phase 1 runtime plot is one 8x8-chunk / 64x64-tile dynamic region based on `HouseConstants.LAND` terrain only; classic POH room/hotspot state is not reused.
- `SettlementControler` clears its runtime instance argument immediately in `start()`; unexpected login recovery removes the stale controller instead of trying to persist dynamic-map ownership.
- `ItemBrowserCommandBridge` intercepts object devspawn/edit operations only while `SettlementControler` is active, keeping ordinary Dev Mode behavior unchanged elsewhere.
- `TestConsolePanel` is the consolidated lazy-loaded Client Console workspace for developer/test tools; Owner, Commands and Settings remain top-level shell authorities.
- `ConstructionRevampTestPanel` calls `ClientConsoleBridge.queueConsoleCommand(...)` for settlement enter/status/exit and opens the existing `ConstructionPaletteOverlay`; it owns no server/gameplay state.
- `SettlementStateSelfTest` is a disposable deterministic test owner that creates a fresh in-memory `SettlementState`, exercises place/overlap/coexistence/rotate/move/duplicate/delete, serializes/deserializes it with Java serialization, and reports PASS/FAIL without reading or mutating the player's real settlement.
- `SettlementStateSelfTest` is runtime VERIFIED: the user received PASS for place/occupancy/compatible-layer coexistence/rotate/move/duplicate/delete/serialization.
- The self-test has since been extended to cover negative/out-of-range X/Y, wrong plane, unknown raw definition, invalid move destination and invalid duplicate destination; that expanded validity pass is `NEEDS TEST`.
- Con Revamp now exposes `State Self-Test` and `Saved Pieces`; both queue the existing owner-only server bridge rather than duplicating settlement logic client-side.
- Test Console / Con Revamp is runtime VERIFIED for navigation and settlement enter/exit harness use: the user confirmed the consolidated rail/sub-tabs are visible, Enter Settlement queues successfully, the private settlement loads, and exit/re-entry works.
- Settlement persistence core is runtime VERIFIED: the user confirmed a placed settlement piece/layout survives settlement exit/re-entry and normal logout/relog, proving `SettlementState` persists through Matrix3 player save/load and `SettlementInstance` rebuilds from plot-relative saved state.
- Live settlement edit synchronization is runtime VERIFIED: move, rotate, duplicate and delete operations update the persistent settlement record and rebuild correctly after settlement exit/re-entry.
- Bundle 1.2 final auto check is runtime VERIFIED: expanded state self-test, real saved-state audit, count consistency and definition-registry integrity all pass on the loaded settlement.
- Outside-settlement ordinary Dev Mode is runtime VERIFIED as unaffected by the settlement interception path.
- Classic POH `House` enter/build/leave behavior is runtime VERIFIED as unaffected by the separate freeform settlement owner.

### HYPOTHESIS

- `13684` / `Floor decoration` may be usable as the first floor definition, but its final suitability/material appearance still needs acceptance against the intended Construction art direction.
- `13344` / `Door` may be usable as the first doorway definition, but its final suitability/material appearance still needs acceptance against the intended Construction art direction.
- Starter resource-node cache visuals are provisional: object `1276` for wood, objects `11933`/`11936` for stone/ore and NPC `327` for food must be runtime accepted for visibility/options/appearance. Resource/storage ownership does not depend on retaining those art ids.
- Worker #1 currently uses provisional NPC id `1` as its runtime presentation. Its visibility/appearance must be runtime accepted; persistent identity uses `starter-settler`, not the cache id.

### UNKNOWN

- Final proper wooden-wall definition; `13450` is verified as a Wooden fence and should not be promoted as the final wall asset.
- Final RTS/Top-Down/Orbit preset values and transition feel.
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
- Runtime CAM DEBUG proved the observed moving view uses the Class411 render family rather than the generic globals; source trace then identified the exact Class24 detached-camera activation/update/close path.
- Runtime-tested the first Class24 reuse: camera detachment worked but W/A/S/D/Q/E did not. Restored stock `Class24.java`, moved Construction controls into `ConstructionBuildCamera.tick()` at the live `Class343.method4302(...)` viewport seam, and added one-shot server-console ENTER/TICK/INPUT/FAIL/EXIT diagnostics.
- Runtime-verified the live-tick Free Build control/lifecycle sweep: automatic activation, W/S/A/D, Q/E, Shift/Ctrl, mouse-look, camera restore and reopen all work.
- Implemented time-based smoothing and action-23 click-to-stop on the verified live camera seam; Free Build ground clicks now stop camera velocity and consume Walk Here while preserving server-authoritative Paint placement.
- Runtime-verified the smoothed Free Build integration pass: acceleration/deceleration, normalized diagonals, Shift/Ctrl speed modes, click-stop latch, planted-player/no-Walk-Here action-23 ownership, exactly one authoritative Paint placement, detached ghost stability and combined camera/render stability all work.
- Locked the exact Bundle 1.2 ownership/file plan and marked Bundle 1.1 discovery DONE.
- Implemented the first server-owned Bundle 1.2 persistent settlement foundation: player-owned `SettlementState`, plot-relative `SettlementPlacedPiece`, definition registry, dynamic `SettlementInstance`, `SettlementControler`, owner-only enter/exit/status harness and settlement-only Dev placement/edit persistence bridge.
- Updated system ownership so classic POH `House` remains separate and Matrix3 persistence/map/world authority remain underneath the new content layer.
- Runtime-verified the Bundle 1.2 persistence core: placed settlement state survives runtime-instance destruction/re-entry and logout/relog, then rebuilds correctly from player-owned plot-relative state.
- Added a disposable Bundle 1.2 state self-test plus Saved Pieces inspection to Con Revamp so remaining edit/occupancy verification is cheap and does not require raw commands.
- Runtime-verified live move/rotate/duplicate/delete synchronization and confirmed those edits rebuild correctly after settlement exit/re-entry.
- Hardened `SettlementState` plot validity, aligned `SettlementInstance` with the saved-owner rules, extended the disposable self-test, and added a non-mutating Saved State Audit + final-gate UI in Con Revamp.

**Current phase:** Phase 2 — Population + broader survival production.

**Active persistent-runtime bundle:** Bundle 2.4 — RTS radial multi-worker control (IMPLEMENTED / NEEDS RUNTIME TEST).

**Current side-task:** Settlement RTS loaded-scene camera clamp is IMPLEMENTED / NEEDS RUNTIME TEST. Stale saved pivots from rebuilt dynamic scenes are rejected; pan look-target is clamped to the live scene height-grid bounds while retaining the 700..10000 orbit range. Fog distance and minimap RTS heading remain unresolved follow-ups after this camera runtime gate.

**Active tooling slice:** Custom Construction Palette + Preview Foundation + Build Camera + Radial Worker Selection RWS-4 layered GFX 4171 worker-ring styling (IMPLEMENTED / NEEDS RUNTIME TEST). RWS-2 drag geometry and RWS-3 worker detection are RUNTIME VERIFIED. The user-prioritized rail slice is now wired into the player-facing Construction palette: Rails -> Rail route reuses RailRoutePreview V2 A-to-B drag/auto-tiling and release queues stable server-authoritative settlementbuild pieces for straight 46353 plus accepted curve 46377/46379/46381 (IMPLEMENTED / NEEDS RUNTIME TEST). Side tooling remains Matrix3 Asset Studio v1 + paired evidence capture + docked Rail Classifier + Object Explorer/Rail Layout Lab. Object Explorer broadens research beyond rails and can test two stock models visually composited on one logical tile; normal scene-slot constraints remain unchanged.

**Next checklist item:** Worker Needs HUD arch acceptance: Con Revamp -> Worker Needs -> Enable Needs HUD Preview. Confirm each active Settler shows three separated partial arches on one shared circumference rather than three full rings. Verify Hunger/Thirst/Energy demo values independently shorten their own arch as wellbeing falls and still trend toward red at the real critical threshold. Adjust the one shared arch scale for readability with normal selection rings. If PASS, keep the arc renderer and move to a clean per-worker server->client needs metadata seam; then resume the rail runtime backlog.

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

- Existing Dev move/duplicate/rotate/delete object actions update both the live projected object and persistent settlement record, and each edit survives exit/re-entry.
- Same-type occupancy/invalid placement is rejected cleanly while compatible different object types can coexist where Matrix3 permits.
- Settlement Status reports the expected saved-piece count after placements, duplicates and deletes.
- Destroying/re-entering repeatedly does not leak or duplicate transient runtime objects/regions.
- Inside the settlement, an unapproved raw object id/type is rejected instead of becoming persistent state.
- Outside the settlement, ordinary Dev Mode behavior remains unchanged.
- Classic POH `House` behavior remains unchanged.
- Run the full relevant build/start/login/persistence/world-object smoke coverage after the targeted Bundle 1.2 edit/validity pass.
- Camera edge diagnostics and full ghost completeness remain recorded carryover, not blockers for this persistence slice.

**Blockers:**

- No settlement ownership/design blocker remains; the first Bundle 1.2 server foundation is implemented and the active gate is runtime persistence/dynamic-instance acceptance.
- Camera edge diagnostics and the separate ghost completeness checklist remain carryover and do not block the current persistent-runtime test.

**Important remaining uncertainty:** runtime PASS for the newly-added Final Auto Check only. Outside-settlement Dev Mode and classic POH regressions are already runtime verified, as are core persistence and live edit synchronization.

## Next recommended work

Runtime-test the Bundle 1.4 gather/haul vertical slice in one consolidated session. If the worker obeys Gather Wood + Haul, holds cargo while Haul is OFF, resumes delivery when Haul returns, and stops starting new work when gathering is disabled, mark gather/haul runtime VERIFIED and continue directly to hunger/thirst/energy. Keep camera/ghost edge checks and older non-blocking persistence/world-object checks as carryover unless a regression appears.

## Rail composite authoring follow-up — 2026-09-22
- Runtime VERIFIED: Object Explorer same-tile overlay can direct-render multiple stock object models on one logical tile; the user's A/B proof visibly rendered both type-22 rail models simultaneously.
- Latest rail evidence confirms the stock mine curve is a multi-tile assembly, not a single curve primitive. Verified captured family for the shown curve: 46360/R2, 46361/R0, 46353/R2, 46377/R0, 46379/R0, 46382/R0, 46381/R0, with 46353/R3 continuing north/south.
- The single-object V1 curve assumption is superseded for final rail design.
- Rail Layout Lab is IMPLEMENTED / NEEDS RUNTIME TEST in Test -> Object Explorer. It can assemble up to 32 arbitrary stock object/type/rotation components around a stable preview origin, move each component by relative X/Y tiles, rotate/duplicate/delete it, and save the exact layout evidence to Client/data/construction/asset_studio/rail_composites.tsv.
- Rail Layout Lab hotkeys are implemented while the Object Explorer tab is visible and hotkeys are enabled: [ / ] select previous/next piece, arrows move the selected piece by one tile, R rotates, Delete removes, Ctrl+D duplicates. Text/spinner/combo editing is excluded.
- Screenshot identification is explicit: the Rail Layout Lab shows a large ACTIVE OBJECT ID banner and every list row starts with OBJECT ID, rotation and relative dX/dY.
- The prior uploaded curve evidence can be restored directly from the tool: Load Last Curve Scan seeds 46360/R2@(-4,0), 46361/R0@(-3,0), 46377/R0@(-1,0), 46379/R0@(0,0), 46382/R0@(-1,+1), 46381/R0@(0,+1). Load 2x2 Bend Core loads only 46377/46379/46382/46381. 46353 remains documented as the separate straight connector (R2 E/W, R3 N/S).
- RailCompositeLibrary TSV is backward compatible: old six-column same-tile rows load as dX=0/dY=0; new rows persist offset_x/offset_y.
- Accepted curve evidence from the uploaded rail_composites.tsv is `CURVE_RAIL_LAYOUT_01`: 46377/T22/R0@(2,-1), 46379/T22/R0@(3,-1), 46381/T22/R0@(3,0). Normalized around the detected elbow 46379 this is 46377@(-1,0), 46379@(0,0), 46381@(0,+1).
- Same-tile overlay is no longer required for the accepted curve: its three type-22 origins occupy three ordinary neighboring tiles. Overlay remains a research tool for future pieces that genuinely need the same origin.
- A->B Rail Route Preview V2 now prefers the exact-name accepted `CURVE_RAIL_LAYOUT_01` even if the local authoring TSV still says CUSTOM; otherwise it uses a saved CURVE role, then the same three-piece built-in fallback.
- Multi-tile curve routing is elbow-anchored: the router detects the component with one cardinal horizontal and one cardinal vertical neighbor, rotates the full pattern from its authored quadrant to the requested bend quadrant, and suppresses straight rails on every tile occupied by the curve pattern so there is no overlap at either approach leg.


## Construction palette rail build handoff — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST: the player-facing palette now owns the entry point for A-to-B rail building.
- Rails -> Rail route configures straight 46353/T22 with R3 north/south and derives east/west by quarter-turn through the existing RailRoutePreview V2 owner.
- Route release snapshots the same physical pieces used by the preview and queues stable settlementbuild keys for 46353, 46377, 46379 and 46381; no client-side persistent object owner was introduced.
- Server SettlementBuildPiece now owns those four rail definitions under SettlementBuildRole.RAIL. Rails are intentionally material-free for the current build workflow and retain 4 base Construction XP per physical component.
- Runtime gate is intentionally consolidated into one pull/start/login session: palette visibility -> straight route -> L curve -> no-material placement/XP feedback -> exit/re-entry persistence -> ordinary non-rail placement regression.


## Rail Logistics V1 handoff — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under the approved SAP/AAA rail-logistics slice.
- Server topology owner: SettlementRailNetwork derives cardinal connectivity directly from persistent plot-relative SettlementState RAIL pieces and BFS-routes Loader -> Unloader.
- Player-facing endpoints: Rails palette now includes material-free rail-loader and rail-unloader stable definitions. Their current fence/door visuals are deliberate V1 placeholders, not accepted final machine art.
- Runtime cart: SettlementRailCartNpc is transient and follows the derived rail tiles. It is never serialized and is destroyed with the SettlementInstance.
- V1 payload proof: requires at least 1 stored Wood; arrival round-trips that Wood through SettlementState and reports success without changing the stored total. This proves physical logistics before machine-local inventories/processing outputs are introduced.
- Intentionally deferred: final cart model, endpoint art, local machine inventories, switches/intersection policy, multiple carts, signals, offline rail simulation, acceleration/curve animation polish.


## Rail Auto-Connect V1 handoff — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under SAP AAA.
- Existing identical rail tiles are valid A/B connection anchors. Placement is idempotent: the persistent existing piece remains authoritative and no duplicate XP/material transaction occurs.
- Same-axis extension and joining authored routes through the accepted A->B straight/three-piece-curve router can now share an existing endpoint without the prior occupied-slot failure.
- Conflicting same-tile rail visuals are intentionally rejected rather than silently replacing/overlaying them. Proper T junction, crossing and switch visuals require accepted cache art before branch auto-tiling can be enabled.
- Rail topology remains derived from persistent SettlementState pieces; no second topology owner was added.


## Continued endpoint visual auto-tiling — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under the approved AAA continuation.
- Correct target: preserve the existing A->B then B->C workflow. If the new B->C leaves B on a perpendicular axis, the previous route's final direction plus the new route's first direction select/rotate CURVE_RAIL_LAYOUT_01 around B.
- Curve footprint pieces replace old persistent RAIL visuals at those exact tiles, so the connection is represented by the already-accepted three-piece bend rather than two visually disconnected authored routes.
- Same-axis continuation remains straight/idempotent. T/cross/switch cases are still separate because they require accepted junction art rather than the ordinary two-direction curve.
- Runtime gate: straight continuation, four turn quadrants, no gap/double straight at B, then exit/re-entry persistence.


## Continued endpoint curve seam correction — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST.
- Runtime evidence proved the continuation-turn trigger worked but the accepted three-piece curve was anchored one seam too far forward, creating the crossed visual.
- The continuation path now retains the previous route's incoming travel vector, shifts the composite one tile back along that vector, and suppresses the new B straight so the accepted curve owns the A->B / B->C seam.
- Next focused check: fresh A->B then exact-B perpendicular B->C in both turn directions; if clean, expand to all four quadrants.


## Rail continuation correction after runtime regression — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under active AAA.
- The one-tile-back curve seam offset was reverted after runtime video showed it worsened the visual result.
- B is restored as the accepted elbow-normalized curve anchor. The key ownership correction is server-side: authored rail components/rotations may replace existing persistent RAIL visuals inside the continuation footprint rather than being rejected by the prior conflict guard.
- No further guessed geometry offsets were added. Focused gate is one fresh straight A->B followed by exact-B perpendicular B->C; only expand after that case is visually clean.


## Rail route debug instrumentation — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under SAP AAA.
- Rail geometry is frozen until evidence identifies the failing ownership/math seam.
- Construction -> Rails now has Rail Debug + Copy Debug. Every committed route records current/previous A/B, continuation detection, in/out direction, H/V continuation values, accepted curve corner/anchor/layout turns, exact footprint tiles and final physical object/type/rotation/world-tile plan.
- Next focused gate: enable debug -> fresh straight A->B -> exact-B perpendicular B->C reproducer -> Copy Debug -> compare the pasted intent report with one runtime screenshot before any further geometry change.
- If client intent is correct but runtime differs, instrument persistent server before/replace/after state next. If client intent itself is wrong, fix the route calculation from the captured coordinates rather than guessing offsets.

## RTS build-control follow-up — 2026-09-24
- Ctrl+Z persistent build undo is RUNTIME VERIFIED by user report.
- Construction palette Erase mode and guarded Clear Builds control are IMPLEMENTED / NEEDS RUNTIME TEST.
- Erase owns exact clicked settlement tile origins through server-authoritative SettlementState removal; Clear requires a second click within 3.5 seconds before invoking clearPlayerBuilds().
- RTS scene-visibility focus correction is IMPLEMENTED / NEEDS RUNTIME TEST. It re-anchors Class523.method6240 visibility focus to the detached RTS camera rather than the stationary player.
- RTS minimap heading remains UNRESOLVED. A targeted source search did not establish the current Matrix3 minimap-angle owner, so no speculative obfuscated-field patch was made.
- Consolidated next runtime gate: erase one normal build, erase one rail component, verify Clear requires confirmation then clears persistent builds, exit/re-enter to confirm removals persist, and pan RTS far enough to check the prior fog/scene wall.


## Rail diagnostic safety freeze — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under SAP AAA.
- Runtime video established that broad rail-on-rail replacement could mutate previously correct track while continuation intent was wrong. That replacement path is frozen: non-identical occupied rail tiles are preserved and reported as debug conflicts.
- Rail Debug now visibly renders the latest authored-intent evidence inside the Construction palette; Copy Debug retains the complete report.
- Geometry remains frozen. Next gate is one fresh A->B then exact-B perpendicular B->C screenshot with the visible evidence panel. Use that evidence to decide whether the client route plan or persistent server result is wrong before changing geometry again.


## Rail Endpoint Editing V1 — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under SAP AAA.
- Correct interaction model is now one route with two endpoints only: A is fixed and B is movable. There is no player-facing C. Starting a drag exactly on the currently committed B edits that endpoint and recalculates the complete A-to-new-B route through the accepted router.
- The failed independent-route continuation/seam geometry is no longer injected into committed route pieces.
- Endpoint replacement removes the exact old persistent RAIL object IDs/tiles and then rebuilds the complete recalculated route. The server refuses the edit-removal command for non-RAIL targets, and the prior broad rail replacement remains frozen.
- Physical rail layout remains persistent through SettlementState. V1 endpoint identity itself is client-session state; persistent route metadata/re-edit-after-relog is CARRYOVER until the new A/B interaction passes runtime. Do not add route-schema complexity before that acceptance gate.
- Focused runtime gate: fresh straight A-to-B -> grab exact B and extend straight -> grab B again and drag 90 degrees -> verify one clean recalculated route with one accepted curve and no obsolete old pieces.


## Atomic Rail Endpoint Replacement V1 — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under active AAA.
- Runtime video proved Rail Endpoint Editing V1 had the correct A/B interaction but the wrong application boundary: many independent erase/build commands exposed partial old/new routes and produced parallel/disconnected remnants.
- Endpoint edits now cross the client/server boundary as one complete old-route -> replacement-route transaction. SettlementInstance validates both sides before mutation, removes the exact old RAIL route, installs the complete recalculated route, and restores the old route if an unexpected replacement placement fails.
- Unrelated persistent builds are never overwritten; they reject the edit before mutation.
- Geometry remains unchanged. Focused gate is straight A-to-B -> move B farther -> move B 90 degrees -> move B again, with exactly one final route visible after each release.


## Rail Endpoint Old/New Ownership Diagnostic — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME EVIDENCE under active AAA.
- Runtime retest rejected the atomic-command hypothesis: the malformed result is unchanged even when endpoint replacement is server-atomic.
- The next evidence boundary is now explicit in the visible Rail Debug panel: MODE endpointEdit/fixedA, complete OLD_PIECE route before B movement, and complete replacement PIECE route after B movement.
- No geometry guess was made. One screenshot after fresh A-to-B -> exact-B 90-degree edit decides the owner: endpointEdit=false means endpoint-hit ownership is wrong; malformed replacement PIECE rows mean the client router is wrong; correct replacement PIECE rows with malformed world objects mean the server application/state path is wrong.
- Resume here: obtain that single visible screenshot, classify the failing owner, then patch only that owner.

## RTS vanilla-render convergence — 2026-09-24
- SAP AAA trace established verified-static ownership: Matrix3's stock detached camera path already consumes Class24.aClass411_Sub1_158 through Class411_Sub1.method5027(...) before the normal Class343 scene render.
- Settlement RTS renderer overrides for scene focus, Class523 radius, and Class174_Sub1 fog were removed after failed runtime evidence.
- ConstructionBuildCamera now remains responsible for detached RTS camera controls/state while vanilla Matrix3 remains responsible for scene/environment/terrain rendering.
- Status: IMPLEMENTED / NEEDS RUNTIME TEST. Do not reintroduce renderer-specific RTS hacks unless vanilla-convergence runtime evidence identifies one exact remaining divergence.


## Packet-safe atomic Rail Endpoint Editing — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under active AAA.
- The visible old/new screenshot proved endpoint ownership itself: endpointEdit=true, fixedA=true, A remained fixed. Review of the actual transport then established the concrete application fault: the prior one-command atomic replacement raised ClientConsoleBridge's guard to 8192 even though Matrix3's COMMANDS_PACKET uses a bounded command-packet framing path. The apparent new route could therefore be the committed client preview while the old persistent route remained.
- Endpoint edits now use bounded staging commands (<=220 characters) for old/new route pieces and one final server commit. SettlementInstance still performs the actual mutation atomically after complete staging.
- Client command guard is restored to 252. Curve geometry remains unchanged pending this transport correction's runtime result.
- Resume gate: fresh A-to-B -> exact-B 90-degree move -> chat must report 'Rail route replaced atomically' and only one route may remain; move B once more and exit/re-enter to verify persistence.


## Continuous Rail Path V1 — 2026-09-24
- IMPLEMENTED / NEEDS RUNTIME TEST under active AAA.
- User-confirmed target replaces the rejected endpoint-recalculation abstraction: rails are an authored continuous path with one START and one current END. Each drag beginning exactly on END appends another segment; previous segments do not move or get recalculated.
- Same-axis extension appends straight pieces. A 90-degree direction change inserts the accepted three-piece curve at END, then appends the outgoing straight. Repeated extensions support paths such as START -> straight -> curve -> straight -> curve -> straight -> END.
- Client keeps the complete authored physical path for the current session and submits old complete path -> new complete path through the existing packet-safe atomic server replacement. Exact overlapping seam/curve tiles are replaced inside the authored path; unrelated builds remain protected server-side.
- CARRYOVER after runtime acceptance: persist authored path identity/end/incoming direction across relog so an existing saved path can be extended after client-session state is lost; branches/T/switches remain separate.
- Resume gate: build at least four continuous segments with multiple 90-degree turns. Each END extension must leave all earlier segments untouched and produce exactly one connected persistent path.


## Rail Network V1 — logical topology foundation — 2026-09-25
- IMPLEMENTED / NEEDS RUNTIME TEST under SAP AAA.
- Authoritative gameplay target is Factorio-style rail construction: freely draw continuous track, make repeated 90-degree turns, extend existing track, begin a new drag from the middle of authored track to create a branch, connect networks intentionally, and later resolve degree-3/4 nodes to proper switch/splitter/junction art.
- The rejected route abstractions (A->B continuation, movable-B whole-route recalculation, endpoint-only authoredPath extension) remain SUPERSEDED for new commits.
- Runtime debug evidence VERIFIED that cardinal tile adjacency was corrupting topology: nearby/parallel rails were being treated as connected merely because their tiles touched, causing previously correct corners to be reclassified on later commits. Rail Network V1 now stores explicit authored cardinal edges from the sampled drag gesture; tile proximity alone does not create a connection.
- Physical RS3 objects remain derived output. Two opposite authored edges resolve straight, two perpendicular authored edges resolve the accepted three-piece curve, and degree-3/4 authored nodes remain logical junctions with temporary straight-through art until final junction assets are classified.
- The prior 64-piece resolver ceiling was also exposed by runtime evidence: a 69-logical-tile network resolved only 64 physical pieces. Active gestures now allow up to 256 sampled tiles, complete-network resolution allows up to 4096 physical pieces, and packet-safe server rail deltas allow up to 256 old/new pieces per edit.
- Physical rails remain server-persistent today. Logical graph metadata is current-session V1 and must become server-persistent only after draw/turn/middle-branch/close-parallel behavior passes runtime.
- Runtime acceptance bundle: fresh loop with intentional close, close parallel tracks that remain independent, repeated 90-degree turns, branch from the middle, extend branch, disconnected second segment, and growth beyond 64 total physical pieces without truncation.
- Resume Here: snapshot-preview race is fixed and runtime evidence is stable. Crossing an existing authored rail mid-gesture is now intentionally clamped at the first contact tile: the drag may branch FROM existing track or connect INTO existing track, but cannot pass through and create multiple unsupported degree-3/4 junctions until dedicated crossing/switch art is classified. Runtime-test branch-from-middle, connect-into-existing, and attempted cross-through; previous rails must remain visually stable.

## Construction Build Hotbar H1 — 2026-09-25
- IMPLEMENTED / NEEDS RUNTIME TEST under SAP AAA.
- Added a dedicated nine-slot Construction build hotbar as a separate client overlay; Matrix3's real combat/action bar remains untouched.
- 1-9 are owned only while the Construction palette is open.
- Implemented slots: 1 Rail, 5 last selected non-rail Object, 6 Eraser, 7 Rotate +90, 8 existing settlement Undo.
- Reserved visible slots: 2 Junction, 3 Crossing, 4 Splitter, 9 Favorites. They deliberately do not fake placement before accepted assets/semantics exist.
- Current rail safety rule remains: ordinary Rail may branch from or connect into authored rail, but cross-through is clamped at first existing-rail contact. Explicit Crossing/Junction/Splitter tools will own those semantics in H2.
- Detailed design and phased plan: docs/construction_revamp/BUILD_HOTBAR.md.
- Runtime gate: open palette -> hotbar appears -> 1 Rail -> select an object -> 1/5 swap -> 6 Eraser -> 7 Rotate -> 8 Undo -> reserved slots do nothing destructive -> close palette and verify hotbar hides/number keys return to Matrix3.
- Resume Here: runtime-test H1 once. If it passes, continue Rail Network special-node asset classification for Junction/Crossing/Splitter rather than weakening ordinary Rail overlap safety.

## Minecart object-animation discovery — 2026-09-25
- Scope is cart asset/animation only; player sitting/riding animation is intentionally out of scope.
- verified-static: Matrix3 ObjectDefinitions opcode 24/106 decodes object sequence IDs into anIntArray5645. method6051()/method6052() select from that set, while method6053() returns the decoded sequence IDs directly.
- Object Explorer now surfaces those object animation IDs for the selected cache definition through DevDefinitionBridge; no 28k-animation manual scan is required.
- Historical object ID 8831 is a HYPOTHESIS candidate for the Keldagrim mine cart and is not treated as revision-830 verified until runtime lookup confirms its name/asset in the user's cache.
- Resume gate: Object Explorer -> ID 8831 -> record name + animation IDs. If it is not the minecart, use the existing name search for "Mine cart" and inspect the matching definition's animation IDs.



## Bundle 2.4 vanilla-context interaction closeout — 2026-09-25
- RUNTIME VERIFIED by user.
- Vanilla Matrix3 world interactions remain authoritative. Selected workers mirror Walk Here plus starter Wood/Stone/Ore object first-actions and the starter Food NPC first-action; the player continues the same vanilla action only when self is part of the committed selection.
- World right-click menus expose one `Clear Selection` action whenever a committed RTS selection exists. It does not alter Allowed Jobs, Pause, Needs, progression or persistence.
- Server gather validation accepts either OBJECT or NPC starter-resource sources and still resolves the exact plot-relative SettlementResourceNode before assigning any worker order.
- Resume gate superseded for ground movement by the double-click arbitration slice below; retain this slice for vanilla Wood/Stone/Ore/Food actions, Clear Selection, and exit/re-entry selection cleanup.


## Bundle 2.4 double-click movement arbitration — 2026-09-25
- RUNTIME VERIFIED by user.
- Selection drag and movement are separate gestures: ordinary press/release clicks preserve pending double-click state, while crossing the >6px LMB drag threshold clears it; the completed drag release is consumed as selection-only input.
- With a committed selection, one ground click is consumed and only arms the short double-click window. A second ground click within 375 ms on the same or adjacent tile issues the existing workerselectionmove order.
- If self is selected, only the accepted second click is allowed through to Matrix3 vanilla Walk Here, keeping player and workers on the same destination.
- Object/NPC skilling actions and Clear Selection behavior are unchanged.
- Runtime VERIFIED: user confirmed the complete Bundle 2.4 interaction gate works, including drag selection without release movement, single-click no-move behavior, double-click movement, self+worker movement, vanilla resource commands, Clear Selection, and transient selection lifecycle.
- Resume Here: Bundle 2.4 is DONE. Do not retest or reopen it without new regression evidence. Return to the canonical active main-goal row: Population, processing and logistics expansion; next implementation focus is Phase 3 processing chains + better materials.


## Construction Development tab cleanup — 2026-09-25
- IMPLEMENTED / NEEDS UI SMOKE TEST under approved AAA.
- ConstructionRevampTestPanel is now a focused live-development panel instead of a completed-bundle regression archive.
- Kept: settlement enter/exit/status/palette, Worker Control status/enable/disable, selected-worker role/pause/jobs commands, Worker diagnostics, Storage status/reset, Needs HUD demo controls, Asset Studio and output.
- Removed from the tab only: storage prime/self-test, Object Probe fallback buttons, drag-button chooser, duplicate Clear Selection button, RWS-4 ring-style controls, needs mutation test buttons, standalone progression card, Bundle 2.3 housing test card and completed-phase explanatory clutter.
- No server commands or gameplay owners were deleted. Completed regression commands remain callable through the existing command bridge if future evidence requires them.
- Resume Here: UI smoke-test the cleaned Con Revamp tab once; then continue Phase 3 processing chains + better materials.


## Phase 3 / Bundle 3.1 — Processing core: Wood -> Planks — 2026-09-25
- Status: IMPLEMENTED / NEEDS RUNTIME TEST under approved AAA.
- SettlementResource now distinguishes raw starter resources from processed resources. PLANKS is persistent settlement storage but is not a starter resource node or starter-shelter requirement.
- SettlementProcessingRecipe is the stable recipe authority. First recipe: saw-planks, 2 Wood -> 1 Plank.
- SettlementProcessingTransaction owns exact-cycle atomic storage conversion. Insufficient input or insufficient output capacity rejects the whole requested transaction without partial mutation.
- Existing starter-shelter gates/self-tests now explicitly operate only on isStarterResource() resources, so new processed materials cannot retroactively invalidate Phase 1/2 saves.
- Developer bridge exposes processing, process <recipeKey> [cycles], and disposable processingselftest operations.
- Cleaned Con Revamp tab gains one compact active Processing card: status, Prime 10 Wood, Saw 1 Plank, Processing Self-Test.
- This slice intentionally does NOT guess workstation art, worker processing AI, animations, or input/output object placement. The recipe/storage owner must pass first.
- Runtime gate: Processing Self-Test PASS -> Prime 10 Wood -> Saw 1 Plank -> storage must read Wood=8 and Planks=1 -> repeat until Planks output is blocked by capacity without consuming Wood.
- Resume Here: after Bundle 3.1 passes, select/verify a physical saw/crafting workstation asset and bind saw-planks to normal Construction placement + worker path/work cycle.
