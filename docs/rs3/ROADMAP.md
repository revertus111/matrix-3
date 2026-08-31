# Matrix3 RS3 Roadmap

The roadmap is deliberately stability-first. Completed foundation work is recorded as completed even when it happened earlier than the originally planned order.

## Foundation - completed/current baseline

- [x] Establish a working Matrix3 server/client baseline.
- [x] Stabilize the current Eclipse/Java 8 server compile target and legacy source encoding handling.
- [x] Establish local development owner-rights bootstrap behavior.
- [x] Harden local player/account persistence by flushing completed writes.
- [x] Start/stop the embedded local login core from the local `GameLauncher` path.
- [x] Establish project constitution, system ownership, baseline, and permanent smoke-test documentation.

## Next - development control

1. **Owner Console shell**
   - One top-level `Owner` tab.
   - Keep the shell thin; no gameplay ownership inside the UI.

2. **Owner Commands sub-tab**
   - Discover the existing Matrix3 command authority.
   - Present commands as clickable actions without duplicating command logic.

3. **Command execution bridge**
   - Route UI actions through the same authoritative server command/permission path where practical.
   - Add targeted tests for permission and execution behavior.

4. **Persistence verification pass**
   - Verify position, inventory, equipment, bank, and relevant account state across relog/restart.
   - Fix only failures proven by the test pass.

5. **Launcher/startup polish**
   - Preserve the already-working embedded-login bootstrap.
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
- Tool improvements driven by repeated content needs.
- Optional graphics/engine experiments after core/content priorities are healthy.

## Priority rule

If choosing between a tool feature and playable content, prefer the content unless the missing tool capability is directly blocking that content.
