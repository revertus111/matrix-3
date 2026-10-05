# Shared Alternate-Character Controller Runtime Test List

Date: 2026-10-05
Status: `verified-static`; Eclipse Java 8 build + gameplay acceptance pending.

Purpose: prove Mario and Link use the same Matrix-owned input/combat architecture after removing Link's duplicate combat controller.

## Build / startup

1. [ ] `git pull origin main`.
2. [ ] Eclipse Java 8 refresh + clean/build Client and Server.
3. [ ] Launch/login normally. No libsm64/liboot rebuild is required for this Java-only controller cleanup.

## Shared combat owner

1. [ ] Ctrl+M near an attackable NPC with a melee weapon equipped. Press F once. Mario must send one imported-character manual-melee request; it must not start RuneScape repeating auto-combat.
2. [ ] Hold F in Mario mode. Existing Mario native slash/request behavior must not create a repeating Matrix auto-attack loop.
3. [ ] Ctrl+L near the same NPC. Press F once while facing it. Link native sword animation must begin first; one shared manual-melee request may fire when the configured liboot contact gate is reached.
4. [ ] Hold F in Link mode. One armed native swing may produce at most one shared combat intent; no repeating RuneScape auto-combat.
5. [ ] For both characters, equip a ranged/magic weapon and attempt the melee action. The shared server authority must reject manual melee. Re-equip melee/unarmed and confirm authority resumes.
6. [ ] Verify both characters use normal RuneScape accuracy/damage/XP/NPC death/drop behavior after a valid one-shot contact.
7. [ ] Exit imported-character mode and verify stock RuneScape click-to-attack remains unchanged.

## Shared targeting / Link lock adapter

1. [ ] Ctrl+L near two NPCs and hold Shift/Z while facing one. Console should report `[Alt Character Combat] target lock -> NPC index=...`.
2. [ ] Move/strafe while Shift remains held. Link should consume the shared locked-target direction as its liboot basis; target selection must not be implemented by a Link-only combat class.
3. [ ] Release Shift. Console should report `[Alt Character Combat] target lock released`; camera-relative steering resumes.
4. [ ] Without Shift, face an NPC inside the short melee cone and press F. Shared forward fallback may select it; an NPC behind the player must not be selected.
5. [ ] Despawn/kill/leave range of the locked NPC. Shared lock must clear/reacquire cleanly without stale target hits.

## Shared movement/input architecture regression

1. [ ] In both Mario and Link modes, W/A/S/D remain owned by the shared `AlternateCharacterController` and do not also pan the camera.
2. [ ] Rotate the camera while holding W in each character. Both must follow the current visible camera basis according to their native axis adapter.
3. [ ] Toggle the shared RuneScape clipping option and verify both characters continue using the same shared policy toggle; native presentation differences are allowed, duplicate keyboard/camera ownership is not.
4. [ ] Switch Mario -> Link -> Mario and exit to RuneScape. No stale shared target/combat state or held-key state may carry between modes.

## Packet/server regression

1. [ ] Normal NPC Examine still works. Stock examine transformed flags 0/1 must remain normal examine behavior.
2. [ ] Imported-character manual melee uses the reserved transformed flag 2 route and must not display examine text.
3. [ ] Confirm the legacy server `LinkCombatPacketBridge` contains no combat logic; it only delegates to `AlternateCharacterCombatPacketBridge` until the large NPC handler can be renamed safely.

## Acceptance rule

Do not mark this cleanup `VERIFIED` until both Mario and Link have passed the same-session combat checks above. Link contact-frame timing remains a separate visual/runtime calibration question and may stay `HYPOTHESIS` even when shared ownership is accepted.
