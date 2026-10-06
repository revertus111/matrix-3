# Imported Character Combat Dummy Runtime Test

Status: verified-static; Eclipse Java 8 + runtime acceptance pending.

1. Pull/build Client + Server and open Client Console -> N64 -> Combat Test.
2. Click `Spawn Combat Dummy`. A `Combat Dummy` should appear one tile east with 10,000,000 HP and stay stationary/passive.
3. Attack it once with normal RuneScape click-to-attack. Stock combat must still work against the dummy.
4. Enter Mario mode. Hold/press Shift near the dummy: Mario must only use native crouch/ground-pound behavior; there must be no `[Alt Character Combat] target lock` message.
5. Face the dummy as Mario and press F. The shared forward fallback may target it. Server output must identify the contact result (`EXECUTED`, `BLOCKED_OR_COOLDOWN`, or a specific rejection) and must not start repeating auto-combat/following.
6. Enter Link mode. Hold Shift/Z facing the dummy: Link should acquire the shared target lock. Press F once; exactly one native contact request may reach server combat.
7. Press F faster than the equipped RuneScape weapon delay. Extra contacts may animate, but server diagnostics should show blocked/cooldown results instead of producing valid hits faster than RuneScape permits.
8. Step outside legal melee range and attack. Server should log `REJECT range`; Mario/Link must not scoot, follow or route toward the dummy.
9. Leave the dummy alive for several cycles. It must not move, retaliate or become aggressive.
10. Click `Remove Combat Dummies`. All dummies spawned by this developer tool should disappear.

Acceptance: Link has Z-lock; Mario does not. Both still use the same manual-melee server path and RuneScape weapon-delay/damage authority.
