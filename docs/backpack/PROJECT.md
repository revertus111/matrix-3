# Rambler's Backpack

## Goal

Deliver Rambler's backpack as safe player-owned persistent carried storage that integrates cleanly with Matrix3 inventory, equipment, bank, and interface behavior without creating a second persistence or bank implementation.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Player-owned backpack storage | 🟡 Foundation |
| Contextual access and actions | 🟡 Foundation |
| Bank integration | 🟡 Foundation |
| Persistence and safety verification | ⚠️ Needs runtime verification |
| Final polish | ❌ Not started |

## Scope

### In scope

- Rambler's backpack item 21445.
- 30-slot player-owned carried storage.
- Store/withdraw behavior through the existing 671/665 interface layout.
- Contextual Open access from inventory, equipment, main bank, and bank inventory.
- Bank-inventory actions: Deposit, Wear, Open, Empty to bank, Examine.
- Persistent storage across normal player save/load.
- Safe partial/full-bank behavior when emptying backpack contents.
- Regression protection for normal Matrix3 inventory, equipment, bank, familiar/BoB, and death behavior.

### Out of scope

- A generic multi-backpack framework unless a future content requirement needs multiple backpack item types.
- Replacing Matrix3 inventory, bank, equipment, persistence, or familiar systems.
- Redesigning the 671/665 interfaces.
- Unrelated custom-item-action features.

## Architecture / ownership

- Matrix3 `Inventory` owns the serialized `Backpack` instance.
- Matrix3 player/account persistence remains the save/load authority; Backpack does not create a second save system.
- `Backpack` owns player-carried storage behavior and validates physical access-item presence.
- Matrix3 `Bank` remains the authority for adding items to the bank.
- `CustomItemActions` owns configurable routing of item context actions; it does not own backpack storage, inventory, equipment, or bank behavior.
- The Matrix3 client/interface path owns menu presentation and the existing 671/665 interface rendering.
- Rambler's item ID may remain specific to `Backpack` while the shared custom-action routing remains config-driven. Do not generalize backpack item identity without a real content requirement.

## Verified foundation

### VERIFIED

- Runtime evidence previously confirmed that a configured `Open` label could be presented by the client. That test also exposed a server config-path/routing failure, which was subsequently corrected in the custom-item-action implementation.

### verified-static

- `Inventory` contains a non-transient `Backpack backpack` field, creates it for new inventories, restores it when absent, and reattaches the player in `Inventory.setPlayer(...)`.
- `Inventory.reset()` clears the normal 28-slot inventory while intentionally preserving Backpack storage.
- `Backpack` uses a 30-slot `ItemsContainer<Item>` independent of Familiar/BeastOfBurden state.
- Backpack storage uses interfaces 671 and 665 for its storage/inventory interaction surface.
- `Backpack` implements validated Open access from inventory, equipment, and bank locations.
- Store/withdraw supports 1, 5, 10, and all amounts with inventory/storage-space checks.
- The physical backpack cannot be stored inside its own Backpack storage.
- `emptyToBank()` delegates item addition to Matrix3 `Bank.addItem(...)`, measures the successfully banked quantity, and removes only that quantity from Backpack storage.
- Full/partial bank handling preserves quantities that were not successfully banked.
- `CustomItemActions` routes configured Backpack actions before normal controller/preset handling while `STOCK` entries deliberately fall through to Matrix3's normal handlers.
- The canonical config currently maps Rambler's backpack 21445 to inventory/equipment/bank Open actions and an explicit bank-inventory allowlist.
- The configured bank-inventory allowlist contains only Deposit, Open, Wear, Examine, and Empty to bank slots for Rambler's backpack.

## Unknown / research needed

### HYPOTHESIS

- The exact visible ordering of the five bank-inventory menu entries should be `Deposit / Wear / Open / Empty to bank / Examine`, but Matrix3's internal menu sorting requires runtime confirmation.

### UNKNOWN

- Whether all four Open entry points currently execute correctly at runtime after the latest routing/config fixes.
- Whether Empty to bank behaves correctly at runtime with both sufficient and insufficient bank space.
- Whether backpack contents persist correctly across logout/login in the current runtime build.
- Whether death handling and familiar/BoB behavior remain unaffected in the current runtime build.
- Whether repeated bank open/close cycles ever duplicate or restore suppressed menu entries.

## Dependencies

- Matrix3 player/account persistence.
- Matrix3 Inventory, Equipment, and Bank implementations.
- Existing 671/665 interface behavior.
- `Server/data/items/custom-item-actions.properties`.
- Client custom-item-action presentation hook.
- Server `CustomItemActions` routing and targeted debug trace.

## Development plan

### Phase 1 - Stabilize and verify Backpack

**Purpose:** Convert the existing Backpack implementation into a runtime-verified Matrix3 feature before expanding or polishing it.

**Status:** NEEDS TEST

**Entry conditions:**

- Existing Backpack storage and contextual-action implementation present.

**Exit conditions:**

- Storage persistence, contextual actions, bank integration, familiar/BoB isolation, and death behavior pass the required runtime checks.
- Any defects exposed by the gate are fixed with evidence-backed minimal patches and retested.

#### Bundle 1.1 - Existing implementation foundation

**Purpose:** Preserve the implemented storage/action architecture and its ownership boundaries while awaiting runtime proof.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Serialized Backpack ownership inside Inventory. `NEEDS TEST`
- [x] 30-slot player-owned storage independent of Familiar/BeastOfBurden. `NEEDS TEST`
- [x] Store/withdraw/take-all behavior. `NEEDS TEST`
- [x] Contextual Open from inventory/equipment/main bank/bank inventory. `NEEDS TEST`
- [x] Explicit Rambler bank-inventory action allowlist. `NEEDS TEST`
- [x] Matrix3 stock Deposit/Wear/Examine fallthrough. `NEEDS TEST`
- [x] Transactional Empty to bank behavior. `NEEDS TEST`
- [x] Preserve Backpack storage across normal Inventory reset. `NEEDS TEST`

#### Bundle 1.2 - Consolidated runtime verification

**Purpose:** Verify the complete Backpack path in one short PC session and establish evidence for any remaining patch.

**Status:** READY

**Dependencies:**

- Bundle 1.1 implementation present.

**Checklist / patches:**

- [ ] Confirm Client and Server load the canonical custom-item-action config.
- [ ] Verify inventory Open.
- [ ] Verify equipment Open.
- [ ] Verify main-bank Open.
- [ ] Verify bank-inventory five-action menu and visible ordering.
- [ ] Verify Deposit/Wear/Examine stock fallthrough.
- [ ] Verify Empty to bank with normal and insufficient bank space.
- [ ] Verify logout/login persistence.
- [ ] Verify familiar/BoB isolation and death persistence.
- [ ] Record runtime results; patch only evidence-backed failures.

**Runtime tests:**

- Use the quick/high-value checks first.
- Preserve `Server/data/logs/custom-item-actions-debug.txt` only if a contextual action/menu test fails.

### Phase 2 - Evidence-backed polish

**Purpose:** Finish only the polish or cleanup justified by Phase 1 runtime evidence.

**Status:** PLANNED

**Entry conditions:**

- Phase 1 exit conditions satisfied.

**Exit conditions:**

- Evidence-backed defects/polish are complete and retested.
- Backpack reaches the defined finished goal without unnecessary generalization.

#### Bundle 2.1 - Final polish

**Purpose:** Resolve confirmed menu/order/usability issues and close the workstream.

**Status:** PLANNED

**Checklist / patches:**

- [ ] Review Phase 1 runtime evidence.
- [ ] Apply only required minimal fixes/polish.
- [ ] Retest affected paths.
- [ ] Update canonical status and close the workstream when all exit conditions pass.

## Current execution state

- Phase: Phase 1 - Stabilize and verify Backpack
- Phase status: NEEDS TEST
- Bundle: Bundle 1.2 - Consolidated runtime verification
- Bundle status: READY
- Approval state: Documentation normalization approved by AAA on 2026-09-05. No additional code change is approved by this normalization patch.
- Current checklist item: Confirm Client and Server load the canonical custom-item-action config.
- Current objective: Run one consolidated Backpack verification session before changing working code.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Serialized player-owned storage | 1 | 1.1 | NEEDS TEST | Static ownership established; runtime persistence gate remains. |
| Contextual Open routing | 1 | 1.1 | NEEDS TEST | Config and server routing are present. |
| Explicit bank-inventory action set | 1 | 1.1 | NEEDS TEST | Exact visible menu/order requires runtime verification. |
| Empty to bank | 1 | 1.1 | NEEDS TEST | Partial/full-bank behavior is statically defensive; runtime proof remains. |
| Consolidated Backpack runtime gate | 1 | 1.2 | READY | Next execution target. |
| Evidence-backed final polish | 2 | 2.1 | BLOCKED | Dependency only: do not enter until Phase 1 exit conditions pass. |

## Decisions / new ideas

### Decision log

- `CustomItemActions` is a supporting routing system, not the Backpack workstream authority.
- `Backpack.java` may remain specifically tied to Rambler's backpack 21445. A generic multi-backpack framework is deferred until content actually requires multiple backpack item types.
- Preserve existing Matrix3 Bank, Inventory, Equipment, persistence, familiar, and interface ownership.
- Do not redesign working code before the runtime gate produces evidence that a change is required.

## Testing

The detailed existing action-level test list remains at `docs/custom-item-actions/testlist.txt`. This workstream keeps only the high-value Backpack gate so testing stays short.

### Quick/high-value checks

1. Start Client and Server; confirm both load `Server/data/items/custom-item-actions.properties`.
2. Inventory: right-click 21445 and confirm Open works.
3. Bank inventory: confirm only Deposit, Wear, Open, Empty to bank, Examine are present; exercise Open and Empty to bank.
4. Logout/login and confirm stored contents remain.

### Deeper checks

1. Equipment and main-bank Open.
2. Full/partial-bank Empty to bank behavior.
3. Repeated bank open/close menu stability.
4. Ordinary unconfigured item regression check.
5. Familiar/BoB isolation and death persistence.

### Smoke/regression checks

- Inventory add/remove/equip behavior remains normal.
- Bank deposit/equip/examine behavior remains normal.
- Player save/load remains on Matrix3's existing persistence path.
- No change to real familiar/BoB storage behavior.

## Carryover / blockers

### CARRYOVER

- Task: Backpack runtime verification.
- Phase/bundle: Phase 1 / Bundle 1.2.
- Current state: Implementation foundation is present; runtime proof is incomplete.
- Remaining work: Execute the consolidated verification list and preserve the targeted debug log only if a failure occurs.
- Likely files/systems: `Backpack.java`, `Inventory.java`, `CustomItemActions.java`, client custom-item-action presentation hook, canonical properties file.
- Next action: Run the quick/high-value checks before any additional implementation patch.

### BLOCKED

- None. Further code changes are intentionally gated on runtime evidence, not blocked by an implementation dependency.

## Resume Here

**Last completed:**

- Backpack workstream normalized under the current Matrix3 project rules without changing working code.

**Current phase:**

- Phase 1 - Stabilize and verify Backpack.

**Active bundle:**

- Bundle 1.2 - Consolidated runtime verification.

**Next checklist item:**

- Confirm both Client and Server load the canonical custom-item-action config, then verify inventory Open and the bank-inventory five-action menu.

**Current state / next action:**

- Do not redesign or genericize Backpack yet. Run the short runtime gate first. If a failure occurs, use `Server/data/logs/custom-item-actions-debug.txt` to establish the smallest evidence-backed patch boundary.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/rs3/WORKSTREAMS.md`
- `docs/rs3/SYSTEM_OWNERSHIP.md`
- `docs/rs3/WORKSTREAM_TEMPLATE.md`
- `docs/custom-item-actions/patchnotes.txt`
- `docs/custom-item-actions/testlist.txt`
- `Server/data/items/custom-item-actions.properties`
- `Server/src/main/java/com/rs/game/player/Backpack.java`
- `Server/src/main/java/com/rs/game/player/Inventory.java`
- `Server/src/main/java/com/rs/game/player/ControlerManager.java`
- `Server/src/main/java/com/rs/game/player/content/CustomItemActions.java`

**Do not re-scan without new evidence:**

- Backpack storage ownership/persistence structure.
- Current custom-item-action routing architecture.
- Bank-inventory configured slot mapping already documented in the existing custom-item-action patchnotes/testlist.

**Pending runtime verification:**

- Config loading on both sides.
- All intended Open contexts.
- Five-action bank-inventory menu and visible ordering.
- Stock action fallthrough.
- Empty to bank normal/full-bank behavior.
- Logout/login persistence.
- Familiar/BoB isolation and death behavior.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Runtime behavior after the latest config/routing fixes, especially exact bank-menu ordering and persistence/regression checks.

## Next recommended work

Run Phase 1 Bundle 1.2 as one consolidated Backpack runtime session. Patch only failures supported by that runtime evidence.
