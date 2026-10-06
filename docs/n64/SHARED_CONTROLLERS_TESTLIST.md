# Shared Alternate-Character Controller Runtime Test List

Date: 2026-10-05
Status: `verified-static`; Eclipse Java 8 build + gameplay acceptance pending.

Purpose: prove Mario and Link use the same Matrix-owned input/horizontal movement/combat architecture after removing per-driver movement paths and Link's duplicate combat controller. Focused movement automation passes; native gameplay acceptance remains pending.

## Build / startup

1. [ ] `git pull origin main`.
2. [ ] Eclipse Java 8 refresh + clean/build Client and Server.
3. [ ] Launch/login normally. No liboot rebuild is required. Mario already needs the existing protocol-v3 combat sidecar for its custom sword/contact proof; this Java patch does not change that protocol.

## Shared combat owner

1. [ ] Ctrl+M near an attackable NPC with a supported melee sword equipped and the existing Mario protocol-v3 custom weapon path active. Press F once. Mario's native slash must begin first; **no Matrix hit may be requested on the F key edge**.
2. [ ] During the same Mario swing, exactly one shared manual-melee request may fire when native `combatTime` reaches the V4 cross-body strike keyframe `0.45`. Console should report `[Alt Character Combat] MARIO contact ...` once. Actual blade/contact visual timing remains runtime acceptance.
3. [ ] Hold F in Mario mode. The native request latch plus shared attack lifecycle must produce at most one Matrix contact for the armed swing and must never start RuneScape repeating auto-combat.
4. [ ] Ctrl+L near the same NPC. Press F once while facing it. Link native sword animation must begin first; one shared manual-melee request may fire only after its configured native contact gate is reached. Console should report `[Alt Character Combat] LINK contact ...` once.
5. [ ] Hold F in Link mode. One armed native swing may produce at most one shared combat intent; no repeating RuneScape auto-combat.
6. [ ] For both characters, equip a ranged/magic weapon and attempt the melee action. The shared server authority must reject manual melee. Re-equip melee and confirm authority resumes.
7. [ ] Verify both characters use normal RuneScape accuracy/damage/weapon-delay/XP/NPC death/drop behavior after a valid one-shot contact.
8. [ ] Rapidly press F faster than the equipped RuneScape weapon's attack delay. Native animations may still be requested, but the server must not generate valid hits faster than existing RuneScape combat delay permits.
9. [ ] Exit imported-character mode and verify stock RuneScape click-to-attack remains unchanged.

## Shared targeting / Link lock adapter

1. [ ] Ctrl+L near two NPCs and hold Shift/Z while facing one. Console should report `[Alt Character Combat] target lock -> NPC index=...`.
2. [ ] Move/strafe while Shift remains held. Link should consume the shared locked-target direction as its liboot basis; target selection must not be implemented by a Link-only combat class.
3. [ ] Release Shift. Console should report `[Alt Character Combat] target lock released`; camera-relative steering resumes.
4. [ ] Without Shift, face an NPC inside the short melee cone and press F. Shared forward fallback may select it; an NPC behind the player must not be selected.
5. [ ] Despawn/kill/leave range of the locked NPC. Shared lock must clear/reacquire cleanly without stale target hits.

## Shared movement/input architecture regression

1. [ ] In both Mario and Link modes, W/A/S/D remain owned by the shared `AlternateCharacterController` and do not also pan the camera.
2. [ ] Rotate the camera while holding W in each character. Both must follow the current visible camera basis according to their native axis adapter.
3. [ ] With clipping OFF, both stop at arbitrary sub-tile positions and travel freely without tile walk requests/clamps. Toggle ON: both now use the same boundary-lead/pending-approval route, not separate Mario/Link tile policies. Toggle OFF without an accumulated-distance jump.
4. [ ] Switch Mario -> Link -> Mario and exit to RuneScape. No stale shared target/combat state or held-key state may carry between modes.
5. [ ] Run the consolidated movement session at the top of docs/n64/TESTLIST.md, including cardinal/diagonal/opposing input, Mario jump preservation and the shared target-relative Link clipping basis. Native action/acceleration differences are allowed; separate horizontal movement owners are not.

## Packet/server regression

1. [ ] Normal NPC Examine still works. Stock examine transformed flags 0/1 must remain normal examine behavior.
2. [ ] Imported-character manual melee uses the reserved transformed flag 2 route and must not display examine text.
3. [ ] Confirm the legacy server `LinkCombatPacketBridge` contains no combat logic; it only delegates to `AlternateCharacterCombatPacketBridge` until the large NPC handler can be renamed safely.

## Acceptance rule

Do not mark this cleanup `VERIFIED` until both Mario and Link have passed the same-session combat checks above. Both characters must demonstrate `native attack -> contact gate -> one shared server intent`; an immediate key-edge hit is a failure. Link contact-frame timing and Mario's exact visual blade-contact alignment may remain `HYPOTHESIS` until visually accepted.
