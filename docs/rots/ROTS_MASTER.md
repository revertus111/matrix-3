# Rise of the Six - 1:1 Reconstruction

## Goal

Bring the existing Matrix3-native Rise of the Six implementation as close as possible to the original RuneScape encounter using revision-830 cache data plus runtime/video/documentary evidence.

This is no longer a from-scratch NPC-discovery task. A substantial RoTS encounter foundation already exists in the repository and is the implementation we are refining toward 100% / 1:1 parity.

The cache is authoritative for available revision-830 assets. It does not by itself prove the gameplay meaning of an animation, GFX, model, projectile, object, sound, timing, or mechanic.

## Evidence labels

- `VERIFIED` - confirmed in runtime.
- `verified-static` - directly established from source/cache data but not runtime-confirmed as authentic RuneScape behavior.
- `HYPOTHESIS` - plausible interpretation that still needs proof.

Never promote a cache correlation or current implementation choice to authentic `VERIFIED` behavior without runtime/video confirmation.

## Current implementation authority

RoTS is currently owned by the existing Matrix3 gameplay/content classes:

- `Server/src/main/java/com/rs/game/map/bossInstance/impl/RiseOfTheSixInstance.java`
  - encounter-wide state, daily west/east rotation, subdue/revive bond, survivor healing, fight completion, side ownership, and empty-side empowerment.
- `Server/src/main/java/com/rs/game/npc/rots/RiseOfTheSixBrother.java`
  - brother logical state plus current melee-special implementations.
- `Server/src/main/java/com/rs/game/npc/combat/impl/RiseOfTheSixCombat.java`
  - normal empowered-brother attacks and special dispatch.
- `Server/src/main/java/com/rs/game/npc/rots/RiseOfTheSixReviveBar.java`
  - encounter revive-progress hit bar.

Do not replace these with a second RoTS controller. Improve the existing implementation in small verified slices.

## Current parity status

### Encounter foundation

- Six empowered brothers: implemented.
- 20-day west/east brother rotation: implemented.
- Side ownership/player-side classification: implemented, but exact arena split/bounds still need runtime verification.
- Logical subdue instead of ordinary NPC death: working in runtime.
- Survivor heal on brother subdue: implemented at 5,000.
- Revive countdown: implemented at 50 server ticks.
- Revive hitpoints: implemented at 25,000.
- Custom revive bar: implemented.
- All-six-subdued fight-complete state: working in runtime.
- Empty-side warning/hop: implemented foundation; exact classic landing positions/assets and blockers are incomplete.
- Brother Throw eligibility/pairing rules: implemented; actual Throw flight/landing mechanic remains dormant.

### Brother mechanics

- Dharok: Hurricane shell, Greatest Axe/charge shell, and Wall Slam shell exist. Greatest Axe visual/timing and Hurricane/Wall Slam authenticity remain incomplete.
- Torag: Hurricane shell, Whack/Pummel flow, and Wall Slam shell exist. Current Torag animation mapping is documented in `assets/animations.txt`; exact 1:1 timing still needs runtime comparison.
- Guthan: Hurricane shell plus a substantial Impale implementation exists, including armed/spearless NPC transformation, projectile, bleed, victim state, retrieval, and Torag interaction. Exact damage/timing/visual parity still needs verification.
- Verac: current melee special path is Wall Slam, with forced post-revive Hurricane behavior available through the shared melee system. Authentic special parity remains incomplete.
- Ahrim: normal magic combat exists; flying NPC state 18539 is VERIFIED, but the authentic Ahrim special rotation is not yet implemented.
- Karil: normal ranged combat exists; authentic Karil special rotation, including Shadow Dash-related behavior, is not yet implemented.

## Important runtime findings

- NPC 18539 = VERIFIED flying Ahrim state.
- NPC 18542 = VERIFIED spearless Guthan state used by current Impale.
- NPCs 18546-18551 are body-only/null-name cache definitions, but current revision-830 runtime testing found these inactive definitions render invisibly. The current encounter therefore deliberately keeps the active brother model visible at 1 HP while logically subdued. Their authentic intended cache role remains unproven.
- The current RoTS combat-family models render with Santa hats in this revision-830 cache/runtime.
- Animation 21941 = VERIFIED runtime visual on Dharok: sustained spinning motion. Authentic Hurricane/Spinning Attack assignment remains a strong HYPOTHESIS until cross-brother/footage confirmation.

## Documentary evidence now guiding the spin search

A period RoTS guide documents a shared `Spinning Attack` for all melee brothers and lists it for Torag, Guthan, Verac, and Dharok. The same guide separately describes Verac's `Helicopter` move, so those two visuals must not be conflated during animation identification.

Reference:
- https://forum.tip.it/topic/326100-rise-of-the-six-guide-rots/

This documentary evidence does not by itself prove animation 21941, but it gives a concrete visual/mechanic target for runtime comparison.

## Current research strategy

1. Audit the existing RoTS implementation before adding anything new.
2. Separate mechanics that already exist from mechanics that are approximated or missing.
3. Use source-known RoTS animation/GFX/projectile IDs to narrow cache research rather than scanning broad numeric neighborhoods blindly.
4. Use Owner Console -> RoTS Deep Scan to correlate BAS/render-set and GFX data for targeted candidates.
5. Runtime/video-test the short candidate list before changing gameplay.
6. Patch one mechanic at a time and keep Matrix3 combat/instance ownership intact.
7. Record exact timing only after measurement or authoritative evidence.

## Research tools

Primary workflow:

- Client Console -> Owner -> RoTS cache research
- `Scan RoTS` for focused NPC/animation definition evidence.
- `Deep Scan` for render-set/BAS and GFX correlation.
- `Copy All` to move the complete research dump into analysis without rerunning standalone tools.

Optional fallback:

- `Server/src/main/java/com/rs/tools/RotsCacheScanner.java`
- Keep it read-only and use it only when a standalone server-cache dump is specifically useful.

## Documentation areas

### Encounter

Track arena setup, side ownership, rotation, subdue/revive bond, side transitions, completion, escape, and cleanup in `mechanics/encounter.txt`.

### Brothers

Create brother-specific mechanic files when a mechanic has enough verified/static evidence to justify its own focused record. Do not create empty files just to mirror the six brothers.

### Assets

Use `assets/` for cache/source/runtime-supported IDs and evidence. Clearly distinguish a current implementation mapping from an authentic RuneScape mapping that has been runtime-verified.

### Timings

Use `timings/` for measured attack cadence, animation duration, special windups, hit timing, cooldowns, revive timing, and other encounter timers.

## Current highest-value gaps

1. Confirm whether animation 21941 is the authentic shared Hurricane / Spinning Attack animation.
2. Authentic subdued/kneeling presentation while preserving the working logical subdue/revive system.
3. Greatest Axe visual/timing verification.
4. Normal attack cadence verification for all six brothers.
5. Ahrim special rotation.
6. Karil special rotation.
7. Brother Throw execution/assets/timing.
8. Exact portal/barrier/shadow-realm and arena transition behavior.
9. Completion escape/reward flow if still absent after focused audit.

## Next checkpoint

Do not continue broad random animation testing yet.

First test animation 21941 on:

1. Dharok - already VERIFIED as a sustained spin visual.
2. Guthan.
3. Torag.
4. Verac.

If 21941 produces the appropriate weapon-spin presentation across the four melee brothers, compare that runtime result directly against authentic RoTS footage/documentary behavior. Only then promote 21941 from strong HYPOTHESIS to VERIFIED Hurricane/Spinning Attack and patch the existing Hurricane implementation to replace the current normal-attack-emote approximation.

After the animation is proven, separately verify Hurricane timing/damage/movement before calling the mechanic 1:1.