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
- State: ACTIVE — Construction Editor developer placement prototype is runtime verified; the next approved direction is the custom Construction object-selection palette + ghost-preview foundation.
- Construction Editor implementation: `09cd35fec87defd0f49ef8000f49eca3523f112e`.
- The current Client Console Construction Editor remains a developer/debug harness, not the intended final player-facing Construction interface.
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
- Normal world/object actions remain available alongside the Dev actions.
- Runtime-observed provisional mappings:
  - `13450`, type `0` -> `Wooden fence`; placement works, but this is not accepted as the final wooden-wall asset.
  - `13684`, type `22` -> `Floor decoration`; placement works as the current floor candidate.
  - `13344`, type `0` -> `Door`; placement and object interaction work as the current doorway candidate.
- The current editor remains a developer/debug harness; players should not need to enter raw object ids/types in the final Construction workflow.
- This tooling track does not satisfy Bundle 1.2 persistence, occupancy, settlement ownership, or final 3D ghost-preview requirements.

### Next tooling slice — Custom Construction Palette + Preview Foundation

**Status:** READY

The intended player-facing Construction workflow is a custom client-side object-selection interface layered on top of the now-proven placement path.

Required first slice:

- Custom Construction object palette instead of raw developer id/type controls.
- Category navigation beginning with Walls, Floors and Doors, designed to expand later into Windows, Fences, Furniture, Workstations and Decoration.
- Search/filter support for buildable definitions.
- Selected-piece state containing object/definition, object type, rotation and category metadata.
- Selecting a palette entry immediately arms placement/preview mode.
- Client-only hover-tile tracking for the selected build piece.
- Rotate before confirmation via editor controls and keyboard/mouse-wheel controls where safe.
- Escape/cancel clears preview state without spawning a real object.
- Confirm click routes through the already-proven Matrix3 placement authority rather than creating a second world-placement owner.
- Valid/invalid placement feedback belongs to the preview layer once occupancy/settlement rules exist.
- True 3D ghost/blueprint rendering remains blocked on a narrow render-path trace; do not invent an unverified render hook.
- Until the true ghost hook is verified, palette/state/hover/confirm architecture may be built independently if it does not fake preview ownership.

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
- Tooling track: Custom Construction Palette + Preview Foundation
- Tooling status: READY
- Approval state: Construction Editor prototype was AAA-approved and is runtime verified; this documentation update is AAA-approved.
- Current checklist item: narrowly identify the Matrix3 render/scene seam required for a true client-only Construction ghost preview, then implement the custom palette/selected-piece foundation around the already-proven placement authority.
- Current objective: replace developer-facing raw object selection with the intended custom Construction build interface while preserving Matrix3 placement authority and keeping persistence/settlement ownership separate.

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

### verified-static

- `matrix-3` is the correct repository.
- Protected Matrix3 baseline is `e86851b95e1d2927d58463b67f600153b9166f6a`.
- Matrix3 has its own `Server/src/main/java/com/rs/game/player/...` ownership structure.
- Current Matrix3 Construction runtime contains the classic serializable `House` stack and `HouseControler`.
- `Player` owns/persists `House`; the restored tree does not contain the previously claimed freeform `SettlementState`/`PlacedBuildPiece`/`SettlementInstance` foundation.
- Dev Mode mirrors Matrix3's normal scene-tile action 23 instead of performing a second scene pick.
- Dev Spawn queues object placement through the existing `itembrowser devspawn` server bridge.

### HYPOTHESIS

- `13684` / `Floor decoration` may be usable as the first floor definition, but its final suitability/material appearance still needs acceptance against the intended Construction art direction.
- `13344` / `Door` may be usable as the first doorway definition, but its final suitability/material appearance still needs acceptance against the intended Construction art direction.

### UNKNOWN

- Exact persistent settlement-state owner to add alongside/around the classic POH `House` ownership model.
- Best Matrix3 instance/dynamic-region owner for freeform settlement projection.
- Final 3D ghost-preview render hook; no unverified render hook is used by the current editor prototype.
- Final proper wooden-wall definition; `13450` is verified as a Wooden fence and should not be promoted as the final wall asset.

## Testing

See `docs/construction_revamp/testlist.txt`.

## Resume Here

**Last completed:**

- Runtime-verified the Client Console Construction Editor and its end-to-end placement path.
- Confirmed Paint placement, live rotation state, existing Dev object manipulation actions, cancellation and Place Last integration.
- Runtime-identified the current provisional object mappings: `13450` Wooden fence, `13684` Floor decoration, `13344` Door.
- Confirmed the current panel is the developer/debug harness; the intended final workflow is a custom Construction object-selection palette feeding placement/preview state.

**Current phase:** Phase 1 — MVP Vertical Slice.

**Active persistent-runtime bundle:** Bundle 1.1 — Matrix3 ownership and foundation discovery.

**Active tooling slice:** Custom Construction Palette + Preview Foundation.

**Next checklist item:** Trace only the smallest Matrix3 render/scene path needed to determine how a client-only 3D ghost object can follow the hovered world tile without becoming real world state. Once that seam is classified, implement the custom category/search palette and selected-piece state around the proven placement controller.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/construction_revamp/PROJECT.md`
- `docs/construction_revamp/patchnotes.txt`
- `docs/construction_revamp/testlist.txt`
- `Server/src/main/java/com/rs/game/player/content/construction/House.java`
- `Server/src/main/java/com/rs/game/player/controllers/HouseControler.java`
- `Server/src/main/java/com/rs/game/player/Player.java`
- `Server/src/main/java/com/rs/game/player/content/commands/Commands.java`
- `Server/src/main/java/com/rs/game/player/content/commands/ItemBrowserCommandBridge.java`
- `Client/src/main/java/game/DevModeBridge.java`
- `Client/src/main/java/game/DevSpawnPlacement.java`
- `Client/src/main/java/game/Class592.java`
- `Client/src/main/java/game/Class319.java`
- `Client/src/main/java/game/console/ClientConsoleShell.java`
- `Client/src/main/java/game/console/ConstructionEditorPanel.java`
- `Client/src/main/java/game/console/ConsoleTheme.java`

**Do not re-scan without new evidence:**

- Do not inspect or port the old `Matrix-718_MAIN` Construction code as implementation authority.
- Do not re-audit unrelated historical Matrix3 workstreams.
- Do not re-trace the already-proven Dev Mode tile/menu dispatch and `itembrowser devspawn` placement path unless a runtime failure points back to it.
- Do not claim the missing freeform settlement-state foundation exists until it is actually added to Matrix3.
- Do not make `13450` the final wooden-wall definition; runtime proved it is a Wooden fence.

**Pending runtime verification:**

- Continuous/right-click placement should receive a focused explicit runtime check if it becomes important to the final player workflow; Paint is already runtime verified.
- R / Shift+R / mouse-wheel shortcuts should receive a focused explicit runtime check if retained in the final interface; editor rotation itself is runtime verified.
- Final wall/floor/door art selections still need visual acceptance.
- Future custom palette/preview implementation will require its own targeted acceptance pass.

**Blockers:**

- No blocker to building the palette/state layer.
- True 3D ghost rendering is blocked until the narrow Matrix3 render seam is verified.

**Important remaining uncertainty:** The persistent settlement-state/instance owner and final ghost-preview render hook are still unverified; keep both separate from the already-proven developer placement authority.

## Next recommended work

Start the Custom Construction Palette + Preview Foundation slice: first classify the minimal client render seam for a non-authoritative ghost object, then build the custom category/search object selector and selected-piece state on top of the working placement controller. Persistence follows after the preview/interface foundation is stable.