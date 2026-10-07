# Imported Character Combat Dummy Runtime Test

Status: verified-static; Eclipse Java 8 + runtime acceptance pending.

1. Pull/build Client + Server and open Client Console -> N64 -> Combat Test.
2. Click `Spawn Combat Dummy`. A `Combat Dummy` should appear one tile east with 10,000,000 HP and stay stationary/passive.
3. Attack it once with normal RuneScape click-to-attack. Stock combat must still work against the dummy and retain normal RuneScape weapon delay.
4. Enter Mario mode. Hold/press Shift near the dummy: Mario must only use native crouch/ground-pound behavior; there must be no `[Alt Character Combat] target lock` message.
5. Face the dummy as Mario and press F. The shared forward fallback may target it. Each accepted native contact should log `EXECUTED_NATIVE_CONTACT`; it must not start repeating auto-combat/following.
6. Enter Link mode. Hold Shift/Z facing the dummy: Link should acquire the shared target lock. Press F once; exactly one native contact request may reach server combat.
7. Press F repeatedly at Link's natural native sword cadence. Valid in-range contacts must no longer be discarded merely because RuneScape `mainHandDelay` is still positive. Each accepted contact should log `EXECUTED_NATIVE_CONTACT` even when `mainDelayBefore` is positive.
8. Confirm each native sword contact still performs the existing RuneScape accuracy/damage roll rather than guaranteeing non-zero damage; legitimate misses remain valid combat results.
9. Step outside legal melee range and attack. Server should log `REJECT range`; Mario/Link must not scoot, follow or route toward the dummy.
10. Leave the dummy alive for several cycles. It must not move, retaliate or become aggressive.
11. Exit imported-character combat and use normal RuneScape combat again. Stock weapon delay must still behave normally.
12. Click `Remove Combat Dummies`. All dummies spawned by this developer tool should disappear.

Acceptance: Link has Z-lock; Mario does not. Imported-character melee cadence is driven by native attack/contact timing, while RuneScape remains authoritative for legality, accuracy, damage, XP, HP, death and drops. Stock RuneScape combat retains its normal weapon-delay cadence.
