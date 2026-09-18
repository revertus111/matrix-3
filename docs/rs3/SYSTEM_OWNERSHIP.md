# Matrix3 System Ownership

This file answers one question: **which implementation is authoritative for this behavior?**

Do not add a competing implementation without explicitly changing this record.

| System | Current authority | Notes |
| --- | --- | --- |
| Networking / protocol | Matrix3 core | Preserve unless a requested feature requires a verified change. |
| Login/session lifecycle | Matrix3 core | Local bootstrap may start the login core, but does not replace login ownership. |
| Local owner/developer rights | Matrix3 rights flow + protected baseline bootstrap | Preserve baseline behavior unless a verified requirement changes it. |
| Player/account persistence | Matrix3 login/account store | Construction settlement state must integrate with this authority, not create a parallel save system. |
| World lifecycle | Matrix3 core | Core authority. |
| Map/object handling | Matrix3 core | Protected regression area; Construction may extend it only through the smallest verified integration boundary. |
| NPC/game entity behavior | Matrix3 core/content layer | Worker AI belongs in a Construction content layer using Matrix3 NPC/entity authority. |
| Combat engine | Matrix3 core | Construction combat is deferred and must not replace combat ownership. |
| Item/NPC/object/cache definitions | Revision-830 cache/data consumed by Matrix3 | Data authority does not imply engine ownership. |
| Animations/models/GFX | Revision-830 cache/data consumed by Matrix3 | Construction visuals should use existing data/definitions where practical. |
| Interfaces | Matrix3 client/cache path | Construction UI must use Matrix3 interface ownership; 718 interfaces are reference-only. |
| Construction / settlement gameplay | `Player.settlementState` + `SettlementInstance` in the custom Matrix3 Construction content layer | `SettlementState` is the sole saved freeform-settlement owner inside normal Matrix3 player persistence. `SettlementInstance` is transient runtime projection only and uses Matrix3 `MapBuilder`/`World` authority. Classic POH `House` remains separate. |
| Historical custom tools/features | Reference documentation only until revalidated | The runtime tree was reset to the protected Matrix3 baseline; preserved docs do not prove current code ownership. |
| 718 project implementations | Reference only | Ideas/UX/algorithms may be studied; never automatic authority. |

## Ownership-change rule

When ownership actually changes, record:

1. old authority,
2. new authority,
3. why the change is required,
4. compatibility boundary,
5. migration/rollback risk,
6. required smoke tests.


### 2026-09-17 — Freeform Construction settlement foundation

1. **Old authority:** no freeform settlement-state/runtime owner existed; classic POH `House` owned only legacy room-based POH data.
2. **New authority:** `Player.settlementState` owns saved plot-relative settlement data; `SettlementInstance` owns transient dynamic-map projection/lifecycle.
3. **Why:** Phase 1 requires exact freeform layout persistence independent of temporary dynamic-region coordinates.
4. **Compatibility boundary:** Matrix3 player serialization remains the save authority; `MapBuilder`, `World`, object definitions and controller lifecycle remain core authority. Classic `House` is not replaced.
5. **Migration/rollback risk:** old saves initialize a new empty `SettlementState`; no existing POH data is migrated or rewritten. Runtime dynamic bounds are never serialized.
6. **Required smoke tests:** build/start/login, enter/exit settlement, object place/edit/remove, leave/re-enter rebuild, logout/relog persistence, dynamic-region cleanup, classic POH unaffected.
