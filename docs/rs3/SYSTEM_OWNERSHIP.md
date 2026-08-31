# Matrix3 System Ownership

This file answers one question: **which implementation is authoritative for this behavior?**

Do not add a competing implementation without explicitly changing this record.

| System | Current authority | Notes |
| --- | --- | --- |
| Networking / protocol | Matrix3 core | Preserve unless a requested feature requires a verified change. |
| Login/session lifecycle | Matrix3 core | Local bootstrap may start the login core, but does not replace login ownership. |
| Local owner/developer rights | Matrix3 rights flow + current bootstrap override | Current local development behavior is documented under `docs/matrix_bootstrap/`. |
| Player/account persistence | Matrix3 login/account store + current flush hardening | Do not create a second save system beside it without an explicit migration plan. |
| World lifecycle | Matrix3 core | Core authority. |
| Map/object handling | Matrix3 core | Historical fragility makes this a protected regression area. |
| NPC/game entity behavior | Matrix3 core/content layer | Engine remains Matrix3; custom mechanics belong in content extensions. |
| Combat engine | Matrix3 core | 718 combat is reference-only. Do not replace working Matrix3 combat by default. |
| Item/NPC/object/cache definitions | Revision-830 cache/data consumed by Matrix3 | Data authority does not imply engine ownership. |
| Animations/models/GFX | Revision-830 cache/data consumed by Matrix3 | Custom additions should use a documented content pipeline. |
| Interfaces | Matrix3 client/cache path | 718 interfaces are reference-only unless a specific port is approved. |
| Local launcher bootstrap | Matrix3 `GameLauncher` + current embedded-login bootstrap | Hosted behavior remains separate. |
| Owner Console | Custom Matrix3 tooling layer | Planned; must call documented Matrix3 APIs/commands. |
| Command browser UI | Custom Matrix3 tooling layer | Planned; existing command implementation remains server authority. |
| Custom bosses/content | Custom content layer on Matrix3 | Primary product lane; should extend, not replace, stable core systems. |
| 718 project implementations | Reference only | Ideas/UX/algorithms may be studied; never automatic authority. |

## Ownership-change rule

When ownership actually changes, record:

1. old authority,
2. new authority,
3. why the change is required,
4. compatibility boundary,
5. migration/rollback risk,
6. required smoke tests.
