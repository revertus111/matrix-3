# Ocarina of Time Integration Test List

## Phase 0 — Native compatibility

- [ ] Confirm local ROM identity and byte order without modifying or uploading the ROM.
- [ ] Create an OoT engine instance with the exact user ROM; record the native result and supported gameplay systems.
- [ ] Create adult and child Link on a test floor and advance the native fixed-step update.
- [ ] Confirm movement, current action/animation, and animated geometry advance across frames.
- [ ] Confirm native bridge failure returns cleanly to normal RuneScape control.
- [ ] Record the exact native build target, toolchain, dependency revision, and license/provenance decision.

## Phase 1 — Matrix3 Link presentation

- [ ] Link mode captures movement input without driving camera WASD.
- [ ] Camera-relative movement works while rotating the view.
- [ ] Idle, movement, turn, jump, and attack geometry render through Matrix3.
- [ ] Exit restores the RuneScape player model, input, and transform; re-entry starts cleanly.

## Later combat and menu acceptance

- [ ] Sword contact requires an active swing window and valid reach/target contact.
- [ ] Weapon speed changes attack cadence while RuneScape stats determine hit and damage outcomes.
- [ ] Valid damage grants the configured per-hit skill XP; misses and blocked hits follow explicit XP rules.
- [ ] Inventory opens and closes while the Matrix world continues.
- [ ] Ranged, magic, and item actions remain usable through the same Link control owner.
