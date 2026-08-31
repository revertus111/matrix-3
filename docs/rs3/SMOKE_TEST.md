# Matrix3 Permanent Smoke Test

Use this checklist after meaningful core changes. Run the targeted section for isolated changes; run the full list after cache/loading, object handling, networking, persistence, broad engine, or uncertain changes.

## Build / startup

- [ ] Eclipse clean/build completes with the expected Java 8 project settings.
- [ ] Server starts without new compile/verifier/runtime bootstrap errors.
- [ ] Client starts.
- [ ] Local launcher/login startup path starts successfully.
- [ ] Game world connects to the expected login path.

## Login / player lifecycle

- [ ] Login succeeds with a normal local development account.
- [ ] Player enters the expected world/location.
- [ ] Owner/developer rights behave as expected for the configured local account path.
- [ ] Logout completes cleanly.
- [ ] Relog succeeds without duplicate/stale session behavior.

## Persistence

- [ ] Player position survives logout/relog.
- [ ] Inventory survives logout/relog.
- [ ] Equipment survives logout/relog.
- [ ] Bank changes survive logout/relog.
- [ ] Relevant account/player-file changes are actually written, not only held in memory.

## World / objects

- [ ] A bank object opens and functions.
- [ ] A non-bank object interaction succeeds.
- [ ] A door/gate or other state-changing object interaction succeeds.
- [ ] No object interaction produces the historical ObjectHandler/NPE/verifier-style regression.

## NPC / combat

- [ ] NPC interaction succeeds.
- [ ] Combat starts normally.
- [ ] Player can damage an NPC.
- [ ] NPC death completes.
- [ ] Drops appear when expected.
- [ ] Dropped item can be picked up.

## Movement / interfaces / utility

- [ ] Normal movement works.
- [ ] Teleport works.
- [ ] A representative interface opens and responds.
- [ ] Bank/interface closing returns control normally.

## Shutdown

- [ ] Logout/shutdown path does not corrupt player state.
- [ ] Embedded local login core shuts down with `GameLauncher` when using the local bootstrap path.

## Result recording

For a baseline promotion or major core patch, record:

- commit SHA tested,
- date,
- Java/Eclipse environment,
- failed checks,
- whether failures existed before the patch,
- and whether the commit is accepted as known-good.
