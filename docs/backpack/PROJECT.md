# Rambler's Backpack

## Goal

Deliver Rambler's backpack as safe player-owned persistent carried storage using Matrix3's Bank interface as a familiar storage presentation surface, while keeping Backpack contents completely separate from the player's real Bank.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Player-owned backpack storage | 🟡 Foundation |
| Contextual access and actions | 🟡 Foundation |
| Bank integration | ⚠️ Needs runtime verification |
| Persistence and safety verification | ⚠️ Needs runtime verification |
| Final polish | ❌ Not started |

## Scope

### In scope

- Rambler's backpack item 21445.
- Existing 30-slot player-owned persistent Backpack container for the migration foundation.
- Matrix3 Bank interface `762` reused as Backpack presentation only.
- Backpack contents sent through Bank display container key `95`; normal inventory remains key `93`.
- Backpack-mode interception of interface `762` before Matrix3's normal Bank button handler.
- V1 Backpack-mode actions: Deposit-1/5/10/All, Withdraw-1/5/10/All, and Examine.
- Contextual Open access from inventory, equipment, main bank, and bank inventory.
- Transactional Empty to bank through the real Matrix3 Bank authority.
- Persistent storage across normal player save/load and death invariants already established by the storage foundation.
- Future progression for storage-slot capacity and per-item stack limits.

### Out of scope for the current migration bundle

- Using `Bank.bankTabs` as Backpack storage.
- Copying/forking the full Matrix3 `Bank` implementation.
- Bank search, tabs, insert/rearrange, withdraw-as-note, Bank PIN, equipment deposit, BoB deposit, money-pouch deposit, or native Bank drag/reorder while Backpack mode is open.
- Deposit-X / Withdraw-X / last-amount / all-but-one until the Backpack mode owns those workflows explicitly.
- Slot/stack upgrade economy and tier values in the Bank-interface foundation patch.
- Permanent cache/interface redesign or a custom new interface.
- A generic multi-backpack framework unless future content requires multiple backpack item types.

## Architecture / ownership

- Matrix3 `Inventory` owns the serialized `Backpack` instance.
- Matrix3 player/account persistence remains the save/load authority; Backpack does not create a second save system.
- `Backpack` owns player-carried storage and Backpack-mode interface routing.
- Matrix3 `Bank` remains authoritative only for the player's real bank and for explicit Backpack -> Bank transfers such as `emptyToBank()`.
- Backpack does **not** call `Bank.openBank()` and never uses `Bank.bankTabs` as its backend.
- Interface `762` is presentation only. Backpack supplies its own container to key `95` and intercepts `762` clicks through the existing `ControlerManager` pre-handler path before stock Bank handling.
- `CustomItemActions` continues to own configurable Open/Empty-to-bank context routing only; it does not own Backpack storage.
- Matrix3 client/cache remains authoritative for Bank-interface rendering.

## Verified foundation

### VERIFIED

- Configured inventory `Open` for item 21445 previously runtime-opened the Backpack successfully.
- Runtime tracing proved the abandoned 671/665 path had interface geometry/clipping problems; the user explicitly changed priority on 2026-09-08 to Bank-interface presentation instead of continuing that layout workaround.

### verified-static

- `Inventory` contains a non-transient `Backpack backpack`, restores it when absent, and reattaches the player in `Inventory.setPlayer(...)`.
- Backpack storage is an independent `ItemsContainer<Item>` and is not Familiar/BeastOfBurden storage.
- `Inventory.reset()` intentionally preserves Backpack storage.
- Matrix3 `Bank.openBank()` uses interface `762`, Bank item container key `95`, inventory component `762:7`, Bank grid `762:215`, and child interface `1463` at `762:112`.
- Matrix3 stock `ButtonHandler` routes `762:215` to Bank withdraw actions and `762:7` to Bank deposit actions.
- `ControlerManager.processButtonClick(...)` calls `Backpack.processButtonClick(...)` before stock `ButtonHandler` continues, giving Backpack mode a safe interception point.
- Current Backpack migration opens `762` directly without calling `Bank.openBank()`, sends Backpack items to key `95`, sends inventory to key `93`, and consumes the `762` surface while Backpack mode is active.
- Current Backpack migration unlocks only option slots for Deposit/Withdraw 1, 5, 10, All and Examine; native Bank drag/search/tab/note/etc. ownership is not enabled for Backpack mode.
- Closing Backpack mode restores real-Bank presentation vars/tabs through existing Bank refresh methods.
- `emptyToBank()` continues to delegate actual bank insertion to Matrix3 `Bank.addItem(...)` and removes only quantities successfully accepted.

## Unknown / research needed

### HYPOTHESIS

- Reusing Bank interface `762` with Backpack key `95` should provide the complete uncropped grid/layout the user wanted without requiring custom client geometry patches.

### UNKNOWN

- Runtime appearance of `762` when populated by Backpack rather than `Bank.bankTabs`.
- Whether the restricted option masks expose exactly the intended five menu actions on this cache revision.
- Whether all unsupported Bank controls remain inert/locked as expected.
- Whether closing Backpack mode restores normal Bank visual/tab state perfectly before the next real-bank open.
- Whether all contextual Open entry points still behave correctly after the presentation migration.
- Current logout/login and death persistence proof after the migration.
- Final storage-capacity tiers, stack-limit tiers, costs, and whether special item categories should modify stack limits.

## Dependencies

- Matrix3 player/account persistence.
- Matrix3 Inventory and Bank implementations.
- Matrix3 Bank interface `762` / child `1463` presentation.
- `Server/data/items/custom-item-actions.properties`.
- Client custom-item-action presentation hook.
- Server `CustomItemActions` routing.

## Development plan

### Phase 1 - Bank-interface Backpack foundation

**Purpose:** Replace the abandoned BoB presentation with a safe Bank-interface mode while preserving existing player-owned storage.

**Status:** NEEDS TEST

**Exit conditions:**

- Backpack opens interface `762` without displaying or mutating real Bank contents.
- Deposit/withdraw/examine actions operate only on Backpack + Inventory.
- Real Bank remains unchanged before, during, and after Backpack use.
- Contextual Open and persistence/safety regressions pass the consolidated runtime gate.

#### Bundle 1.1 - Persistent storage/action foundation

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Serialized Backpack ownership inside Inventory. `NEEDS TEST`
- [x] Player-owned storage independent of Familiar/BeastOfBurden. `NEEDS TEST`
- [x] Contextual Open from inventory/equipment/main bank/bank inventory. `NEEDS TEST`
- [x] Transactional Empty to bank. `NEEDS TEST`
- [x] Preserve Backpack storage across normal Inventory reset. `NEEDS TEST`

#### Bundle 1.2 - Bank-interface presentation migration

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Replace 671/665 Backpack presentation with Bank interface `762`. `NEEDS TEST`
- [x] Feed Backpack storage to Bank display key `95` without using `Bank.bankTabs`. `NEEDS TEST`
- [x] Route `762:7` inventory deposits to Backpack before stock Bank handling. `NEEDS TEST`
- [x] Route `762:215` withdrawals to Backpack before stock Bank handling. `NEEDS TEST`
- [x] Restrict V1 menu mask to 1/5/10/All/Examine actions. `NEEDS TEST`
- [x] Consume unsupported Bank-interface controls while Backpack mode owns `762`. `NEEDS TEST`
- [x] Restore normal Bank presentation state when Backpack closes. `NEEDS TEST`
- [x] Update ownership/docs/tests. `NEEDS TEST`

#### Bundle 1.3 - Consolidated runtime acceptance

**Status:** READY

**Checklist / patches:**

- [ ] Open Backpack from inventory and confirm interface `762` shows Backpack contents, not real Bank contents.
- [ ] Verify Deposit-1/5/10/All and inventory Examine.
- [ ] Verify Withdraw-1/5/10/All and Backpack Examine.
- [ ] Confirm item 21445 cannot be stored inside itself.
- [ ] Confirm unsupported Bank controls do not mutate real Bank state.
- [ ] Close Backpack, open the real Bank, and confirm tabs/items/counts/actions are normal.
- [ ] Verify equipment/main-bank/bank-inventory Open contexts.
- [ ] Verify Empty to bank normal/full-bank behavior.
- [ ] Verify logout/login persistence and death/familiar isolation.

### Phase 2 - Backpack progression

**Purpose:** Turn Backpack storage into an upgradeable player progression system after the Bank-interface foundation is proven.

**Status:** PLANNED

#### Bundle 2.1 - Capacity progression

**Status:** PLANNED

**Planned direction:**

- Separate player-owned storage-slot progression from the physical item.
- Support increasing usable Backpack slots without changing real Bank capacity.
- Preserve existing contents safely when capacity increases.
- Exact starting capacity, tiers, costs, and unlock sources to be designed before implementation.

#### Bundle 2.2 - Stack-limit progression

**Status:** PLANNED

**Planned direction:**

- Add a Backpack-specific maximum amount per stack.
- User direction: early tiers may begin around 10 of the same stackable item, then increase through upgrades.
- Keep normal item stackability semantics unless a later explicit compression mechanic is approved.
- Exact tiers, costs, category exceptions, and endgame cap remain design work.

### Phase 3 - Final polish

**Status:** PLANNED

**Purpose:** Add only the Bank-mode presentation polish and quality-of-life features justified after runtime proof and progression design.

## Current execution state

- Phase: Phase 1 - Bank-interface Backpack foundation
- Phase status: NEEDS TEST
- Bundle: Bundle 1.3 - Consolidated runtime acceptance
- Bundle status: READY
- Approval state: Bank-interface Backpack foundation approved by `SAP AAA` on 2026-09-08.
- Current checklist item: Open Backpack from inventory and confirm `762` displays Backpack contents rather than real Bank contents.
- Current objective: Prove backend isolation and V1 transfers in one short runtime session before adding progression.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Serialized player-owned storage | 1 | 1.1 | NEEDS TEST | Existing persistent foundation retained. |
| Contextual Open routing | 1 | 1.1 | NEEDS TEST | Existing config/router retained; presentation target changed to `762`. |
| Bank-interface presentation | 1 | 1.2 | NEEDS TEST | `762` + key `95`, Backpack backend only. |
| Backpack-mode transfer routing | 1 | 1.2 | NEEDS TEST | `762:7` deposit and `762:215` withdraw intercepted before real Bank handler. |
| Backend isolation | 1 | 1.2 | NEEDS TEST | No `Bank.openBank()` / `Bank.bankTabs` ownership in Backpack mode. |
| Consolidated runtime gate | 1 | 1.3 | READY | Next execution target. |
| Slot-capacity progression | 2 | 2.1 | PLANNED | Design after foundation passes. |
| Stack-limit progression | 2 | 2.2 | PLANNED | Design after foundation passes. |

## Decisions / new ideas

### Decision log

- 2026-09-08: User explicitly superseded the BoB 671/665 presentation direction after persistent clipping/layout problems.
- 2026-09-08: Reuse Matrix3 Bank interface `762` as presentation while keeping Backpack storage completely separate from real Bank storage.
- 2026-09-08: Do not fork/copy `Bank.java`; Backpack mode intercepts the existing interface before stock Bank handling.
- 2026-09-08: Foundation first: prove safe independent storage on Bank UI before adding upgrade mechanics.
- 2026-09-08: Future Backpack progression will have separate storage-slot and per-stack-limit upgrade tracks; exact progression/economy remains to be designed.
- `CustomItemActions` remains a supporting router, not the Backpack authority.
- Backpack contents remain attached to the player rather than the physical item.

## Testing

The authoritative migration checks are in `docs/backpack/testlist.txt`.

### Quick/high-value checks

1. Open Backpack from inventory.
2. Confirm interface `762` shows Backpack items only.
3. Deposit one item and withdraw it again.
4. Close Backpack and open real Bank; verify real Bank contents/tabs remain unchanged.
5. Logout/login and confirm Backpack item contents persist.

### Smoke/regression checks

- Normal Inventory add/remove/equip behavior remains normal.
- Normal real Bank open/deposit/withdraw/tab behavior remains normal after Backpack use.
- Player save/load remains on Matrix3 persistence.
- Familiar/BeastOfBurden behavior remains independent.
- No item loss/duplication during Backpack transfer or Empty to bank.

## Carryover / blockers

### CARRYOVER

- Backpack progression (slot capacity + stack limits) is intentionally deferred until Phase 1 Bank-interface isolation passes runtime.
- Bank-interface title/reskin/search/preset polish is intentionally deferred; first prove the storage owner and transfer path.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Bank-interface presentation migration implemented statically: interface `762`, Backpack display key `95`, restricted V1 actions, and pre-Bank-handler routing.

**Current phase:**

- Phase 1 - Bank-interface Backpack foundation (`NEEDS TEST`).

**Active bundle:**

- Bundle 1.3 - Consolidated runtime acceptance (`READY`).

**Next checklist item:**

- Open Backpack from inventory and confirm the Bank interface displays Backpack contents rather than the real Bank.

**Current state / next action:**

- Pull/build Server and run the short Bank-interface Backpack test. Do not start slot/stack progression until backend isolation is runtime-proven.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/backpack/PROJECT.md`
- `docs/backpack/patchnotes.txt`
- `docs/backpack/testlist.txt`
- `docs/rs3/SYSTEM_OWNERSHIP.md`
- `Server/src/main/java/com/rs/game/player/Backpack.java`
- `Server/src/main/java/com/rs/game/player/Bank.java`
- `Server/src/main/java/com/rs/game/player/InterfaceManager.java`
- `Server/src/main/java/com/rs/game/player/ControlerManager.java`
- `Server/src/main/java/com/rs/net/decoders/handlers/ButtonHandler.java`
- `Client/src/main/java/game/CustomItemActionConfig.java`

**Do not re-scan without new evidence:**

- Existing Backpack persistence ownership.
- Custom-item-action routing architecture.
- Matrix3 Bank key/component ownership (`95`, `762:7`, `762:215`).
- BoB 671/665 layout debugging for Backpack; that presentation direction is superseded.

**Pending runtime verification:**

- Bank-interface visual population.
- Restricted option labels/actions.
- Real-Bank backend isolation.
- Close/reopen Bank state restoration.
- All contextual Open entry points.
- Empty to bank normal/full-bank behavior.
- Logout/login persistence.
- Familiar/BoB and death isolation.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Runtime behavior of Bank interface `762` when driven by the independent Backpack container rather than `Bank.bankTabs`.

## Next recommended work

Run the consolidated Bank-interface Backpack acceptance. If isolation passes, design Phase 2 capacity/stack progression next.
