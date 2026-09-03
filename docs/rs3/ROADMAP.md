# Matrix3 RS3 Roadmap

The roadmap is deliberately stability-first. Completed foundation work is recorded as completed even when it happened earlier than the originally planned order.

## Foundation - completed/current baseline

- [x] Establish a working Matrix3 server/client baseline.
- [x] Stabilize the current Eclipse/Java 8 server compile target and legacy source encoding handling.
- [x] Establish local development owner-rights bootstrap behavior.
- [x] Harden local player/account persistence by flushing completed writes.
- [x] Start/stop the embedded local login core from the local `GameLauncher` path.
- [x] Establish project constitution, system ownership, baseline, and permanent smoke-test documentation.
- [x] Establish convenient Gradle-owned local startup through the Eclipse green-button bootstrap.

## Client Console direction

The replacement direction is defined by `docs/client-console/CLIENT_CONSOLE.md`.

The Matrix3 Client Console uses a cleaner RuneLite-style sidebar workflow rather than recreating the old 718 console wholesale. Matrix3 remains authoritative for gameplay and server behavior; the console is a developer UI layer.

## Current - development control

1. **Client Console V1 scan/design verification** - [x] Complete
   - Matrix3 client frame/bootstrap docking ownership was established without replacing renderer/canvas ownership.
   - `docs/client-console/CLIENT_CONSOLE.md` remains the design authority.

2. **Client Console shell** - [x] Complete
   - Thin vertical icon rail plus one active docked panel.
   - Open/close/select behavior, resize protection, dark theme, workspace persistence, and lazy panel hosting are implemented.

3. **Client Console V1 panels**
   - [x] Owner.
   - [x] Commands.
   - [x] Player.
   - [ ] Debug.
   - Additional specialist panels/tools may exist when driven by active content needs; they do not replace the unfinished V1 Debug slice.

4. **Command execution bridge** - [x] Complete
   - Client Console command actions route through the existing Matrix3 command packet/permission authority.
   - Specialist tools may use isolated narrow bridges when explicitly required, without moving gameplay ownership into the console.

5. **Persistence verification pass** - [ ] Pending
   - Verify position, inventory, equipment, bank, and relevant account state across relog/restart.
   - Fix only failures proven by the test pass.

6. **Launcher/startup polish** - [ ] As needed
   - Preserve the now-working Gradle-owned Eclipse/Windows startup path.
   - Improve user-facing startup only where needed; do not redesign the login architecture.

## Content pipeline

Build only capabilities required by real content work. Likely needs include NPC configuration, drops, animations, models, GFX/projectiles, and encounter mechanics, but do not build all editors in advance.

The 718 tools are references for lessons and workflow, not code that must be ported.

## First major content milestone - one complete custom boss

The first boss should prove the full path:

- custom NPC/content definition,
- stats,
- spawn/location,
- combat behavior,
- animations,
- at least one special mechanic,
- GFX/projectile support if required,
- drops/rewards,
- death/respawn lifecycle,
- configuration/persistence needed by the encounter,
- repeatable runtime test.

Only after this pipeline works cleanly should it be generalized into a reusable boss/content framework.

## Later

- Reusable boss/content framework.
- Additional bosses and areas.
- Client Console/tool improvements driven by repeated content needs.
- Optional graphics/engine experiments after core/content priorities are healthy.

## Priority rule

If choosing between a tool feature and playable content, prefer the content unless the missing tool capability is directly blocking that content.
