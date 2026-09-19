# Matrix3 Construction Revamp

## Goal

Turn Construction into a persistent freeform settlement/factory-style system built natively on Matrix3, while preserving Matrix3 core ownership for maps, objects, persistence, NPCs, combat, interfaces, and cache data.

The player should be able to build a settlement wall-by-wall, recruit and train flexible workers, gather/haul/process resources, automate logistics, expand the settlement, and later connect the system to overworld Construction projects and a settlement economy.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Freeform settlement building foundation | 🔵 In Progress |
| Starter resource loop and shelter milestone | ✅ Done |
| First worker, Allowed Jobs, gathering and hauling | 🔵 In Progress |
| Worker needs, storage and settlement recovery | 🔵 In Progress |
| Persistence and Construction XP ownership | 🔵 In Progress |
| Population, processing and logistics expansion | ❌ Not started |
| Settlement Wealth, offline production and economy | ❌ Not started |
| Overworld Construction integration | ❌ Not started |

## Current status

- Repository authority: `revertus111/matrix-3`, branch `main`.
- Runtime foundation: protected Matrix3 baseline `e86851b95e1d2927d58463b67f600153b9166f6a` plus the restored pre-reset feature stack.
- State: ACTIVE — Construction Editor and custom Construction palette/selected-piece/hover foundation are runtime verified; the direct-render 3D ghost, white/translucent styling and preview-vs-placed alpha isolation are runtime verified. Legacy Orb and generic-renderer camera attempts are runtime-rejected. Construction Free Build uses the live Class24/Class411 path, and automatic activation, W/S/A/D, Q/E, Shift/Ctrl, mouse-look, normal-camera restore, close/reopen, time-based smoothing, normalized diagonals, click-to-stop latch, planted-player action-23 ownership, one authoritative Paint placement/no Walk Here and combined camera/render stability are runtime VERIFIED. Only edge diagnostics/pre-existing-freecam preservation and the separate full ghost checklist remain.
- Construction Editor implementation: `09cd35fec87defd0f49ef8000f49eca3523f112e`.
- Custom Construction palette foundation implementation: `a2ce37439896d77d257d0966463104fcb962803f`.
- Visible 3D ghost base-render fix is runtime verified after `014a133f02c6e72c3bac08ea26f5c6bd98ebeb3d`; white/translucent styling and the `0x100` private-alpha isolation fix are also runtime verified after a full client restart.
- Construction no longer invokes Matrix3's legacy Orb-of-Oculus interface path because the current client/cache rejects legacy root/component `475/57` with `ArrayIndexOutOfBoundsException: 57` during `SET_INTERFACE` processing.
- Construction Free Build activates/reuses `Class24.aClass411_Sub1_158`, but runtime proved the first WASD/Q/E patch location did not execute. Construction controls now run through `ConstructionBuildCamera.tick()` from the live `Class343.method4302(...)` viewport seam immediately before the detached Class411 transform is submitted. Stock `Class24.java` is restored.
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
- RTS, Top Down, Orbit/Focus and Player View remain accepted next views; deterministic preset values are deferred until the current Free Build integration gate closes.

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

**Status:** ACTIVE

#### Worker #1 arrival foundation — IMPLEMENTED / NEEDS TEST

Persistent owner:

- `SettlementState` schema v4 owns settlement worker records and the next stable worker id inside normal Matrix3 player serialization.
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

Remaining Bundle 1.4 sequence after arrival acceptance:

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
- Persistent-runtime bundle: 1.4 — first worker vertical slice
- Persistent-runtime bundle status: ACTIVE
- Tooling track: Custom Construction Palette + Preview Foundation + Build Camera
- Tooling status: CLASS411 FREE BUILD V1 RUNTIME VERIFIED — EDGE CHECKS + FULL GHOST CHECKLIST REMAIN
- Approval state: SAP AAA approved the Bundle 1.4 Worker #1 ownership/arrival foundation. Camera/ghost edge checks remain carryover and do not block this slice.
- Current checklist item: Worker #1 ownership/arrival foundation is implemented verified-static; runtime-test Worker Self-Test and Worker Arrival Check, then re-enter once to confirm the same worker id rebuilds without duplication.
- Current objective: runtime-verify milestone-gated Worker #1 persistence/projection, then continue Bundle 1.4 with Allowed Jobs before gathering/hauling.

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
- First Class24 reuse runtime acceptance was partial: opening Construction successfully detached the camera, but W/A/S/D/Q/E did not move it. The movement code had been placed in `Class24.method711()` and was not reached by the current Construction runtime path.
- Live-tick Free Build controls are runtime VERIFIED: the user's follow-up sweep confirmed automatic activation, W/S/A/D, Q/E, Shift/Ctrl speed modifiers, mouse-look, normal-camera restore and clean close/reopen behavior.
- Smoothed Free Build integration is runtime VERIFIED: acceleration/deceleration, normalized diagonals, Shift/Ctrl under velocity smoothing, click-to-stop with held-key latch, planted-player action-23 ownership, exactly one authoritative Paint object/no Walk Here, detached ghost stability and combined camera/render stability all passed the user's acceptance run.
- Runtime CAM DEBUG captures proved the observed moving view remained mode 1 / `source=CLASS411` while generic camera XYZ stayed `0,0,0`; source tracing separately established `Class24.aClass411_Sub1_158` as the actual developer detached-camera owner.
- Bundle 1.3 starter resource/storage slice is runtime VERIFIED by the user: Resource Self-Test passes and the current wood/food/stone/ore node + settlement-only storage flow works in the live settlement.
- Bundle 1.3 starter shelter milestone is runtime VERIFIED: the user's real settlement automatically completed after reaching 9 walls, 5 floors, 1 doorway and 1 Wood/Food/Stone/Basic ore, and `Bundle 1.3 Final Check` returned PASS on the loaded settlement.

### verified-static

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

**Current phase:** Phase 1 — MVP Vertical Slice.

**Active persistent-runtime bundle:** Bundle 1.4 — first worker vertical slice.

**Active tooling slice:** Custom Construction Palette + Preview Foundation + Build Camera.

**Next checklist item:** Pull/build, enter the completed settlement and run Test Console -> Con Revamp -> `Worker Arrival Check`. Expect PASS with exactly one saved worker and one live NPC. Exit/re-enter and rerun; the worker id must remain identical and counts must stay 1/1. Then proceed to Allowed Jobs.

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

The Bundle 1.2 persistence core is runtime verified. Continue with persistent edit operations, occupancy/validity rules, status accounting and removal/regression coverage; then close Bundle 1.2 and proceed to Bundle 1.3 starter resources/storage. Keep camera/ghost edge checks as carryover unless a regression appears.