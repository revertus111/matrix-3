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

No identifiable old Owner/Client Console implementation is present on GitHub `main`. The replacement direction is now defined by `docs/client-console/CLIENT_CONSOLE.md`.

The Matrix3 Client Console will use a cleaner RuneLite-style sidebar workflow rather than recreating the old 718 console wholesale. Matrix3 remains authoritative for gameplay and server behavior; the console is a developer UI layer.

## Next - development control

1. **Client Console V1 scan/design verification**
   - Inspect the Matrix3 client frame/bootstrap and only the immediate layout dependencies needed to identify a safe docking hook.
   - Inspect the minimum old 718 Client Console implementation needed for UI/workflow reference.
   - Follow `docs/client-console/CLIENT_CONSOLE.md` as the design authority.

2. **Client Console shell**
   - Thin vertical icon rail plus one active docked panel.
   - Clean open/close/select behavior.
   - Do not bundle gameplay ownership into the shell.

3. **Client Console V1 panels**
   - Owner.
   - Commands.
   - Player.
   - Debug.
   - Add each panel as its own testable vertical slice.

4. **Command execution bridge**
   - Route command UI actions through the authoritative Matrix3 command/permission path where practical.
   - Do not duplicate command semantics in the client.

5. **Persistence verification pass**
   - Verify position, inventory, equipment, bank, and relevant account state across relog/restart.
   - Fix only failures proven by the test pass.

6. **Launcher/startup polish**
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
