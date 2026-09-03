# Rise of the Six - 1:1 Reconstruction

## Goal

Bring the existing Matrix3-native Rise of the Six implementation as close as possible to the original RuneScape encounter using revision-830 cache data plus runtime/video/documentary evidence.

This is no longer a from-scratch NPC-discovery task. A substantial RoTS encounter foundation already exists in the repository and is the implementation we are refining toward 100% / 1:1 parity.

The cache is authoritative for available revision-830 assets. It does not by itself prove the gameplay meaning of an animation, GFX, model, projectile, object, sound, timing, or mechanic.

## Main-goal status plan

The permanent status anchor for this workstream is:

**100% / 1:1 2014-era Rise of the Six.**

Status updates in chat must keep the following top-level rows stable so they act as a roadmap back to the original goal after any side task. Small animation, GFX, tooling, cache-research, bug-fix, or one-mechanic patches are reported separately as `Just completed:` and do not replace this table.

| Main RoTS milestone | Current status | Meaning |
| --- | --- | --- |
| Encounter / arena foundation | 🟡 Foundation | Six-brother instance, daily rotation, side ownership, spawn/fight foundation exist; exact arena/side fidelity remains incomplete. |
| Shadow bond / subdue / revive / all-six completion | 🔵 In Progress | Core logical system works in runtime, but exact original presentation/timing still needs parity work. |
| Normal combat and attack cadence | 🟡 Foundation | Normal attacks exist for all brothers; exact 2014 cadence/timing still needs verification. |
| Melee brother specials | 🔵 In Progress | Dharok, Guthan, Torag, and Verac have substantial special foundations; Hurricane now uses 21941, while remaining specials still need 1:1 verification/completion. |
| Ahrim special rotation | ❌ Not started | Normal magic and flying state exist, but authentic special rotation is not implemented. |
| Karil special rotation | ❌ Not started | Normal ranged combat exists, but authentic special rotation/Shadow Dash behavior is not implemented. |
| Shared side/team mechanics | 🟡 Foundation | Empty-side empowerment foundation and Brother Throw pairing logic exist; exact movement/assets and Throw execution remain incomplete. |
| Portal / barriers / shadow-realm / arena transitions | 🟡 Foundation | Some side ownership/boundary behavior exists, but exact encounter transition and barrier parity is incomplete. |
| Completion / escape / rewards / cleanup | ⚠️ Needs focused audit | Do not assume absent or complete until the existing flow is audited directly. |
| Final 1:1 parity and full-fight regression pass | ❌ Not started | Full encounter timing, visuals, mechanics, edge cases, and completion flow must be verified together after the major systems are finished. |
| **100% / 1:1 Rise of the Six** | 🔵 **In Progress** | This is the permanent overall goal. |

### Roadmap order

Use this order to prevent side work from becoming the project:

1. Keep the existing encounter/bond foundation stable while fixing only evidence-backed parity gaps.
2. Lock down normal combat cadence and finish the melee-brother special set already furthest along.
3. Implement and verify Ahrim's authentic special rotation.
4. Implement and verify Karil's authentic special rotation.
5. Finish shared side/team mechanics, especially empowerment behavior and Brother Throw execution.
6. Finish portal/barrier/shadow-realm/arena-transition fidelity.
7. Audit and finish completion, escape, rewards, and cleanup.
8. Run the final full-fight 1:1 regression/parity pass.

At any point, a necessary side task may temporarily interrupt this order, but the status table and `Next main step:` must continue to point back to this roadmap.

### Current next main step

Run the live encounter with the RoTS Fight Recorder, verify the integrated 21941 Hurricane and Dharok Greatest Axe behavior from the generated runtime log, then continue finishing the **melee brother specials** milestone rather than returning to broad animation/tool research.

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

- Dharok: Hurricane now uses animation 21941, while Greatest Axe/charge and Wall Slam shells exist. Greatest Axe visual/timing and Wall Slam authenticity remain incomplete.
- Torag: Hurricane now uses animation 21941; Whack/Pummel flow and Wall Slam shell exist. Current Torag animation mapping is documented in `assets/animations.txt`; exact 1:1 timing still needs runtime comparison.
- Guthan: Hurricane now uses animation 21941 plus a substantial Impale implementation exists, including armed/spearless NPC transformation, projectile, bleed, victim state, retrieval, and Torag interaction. Exact damage/timing/visual parity still needs verification.
- Verac: animation 21941 is runtime-confirmed to produce his shared melee spin presentation. Current ordinary special path is still Wall Slam, with forced post-revive Hurricane behavior available through the shared melee system; authentic full special rotation remains incomplete.
- Ahrim: normal magic combat exists; flying NPC state 18539 is VERIFIED, but the authentic Ahrim special rotation is not yet implemented.
- Karil: normal ranged combat exists; authentic Karil special rotation, including Shadow Dash-related behavior, is not yet implemented.

## Important runtime findings

- NPC 18539 = VERIFIED flying Ahrim state.
- NPC 18542 = VERIFIED spearless Guthan state used by current Impale.
- NPCs 18546-18551 are body-only/null-name cache definitions, but current revision-830 runtime testing found these inactive definitions render invisibly. The current encounter therefore deliberately keeps the active brother model visible at 1 HP while logically subdued. Their authentic intended cache role remains unproven.
- The current RoTS combat-family models render with Santa hats in this revision-830 cache/runtime.
- Animation 21941 = VERIFIED runtime shared spin visual across Dharok, Guthan, Torag, and Verac. The live Hurricane implementation now uses 21941 instead of the old normal-attack-emote/manual-facing approximation.

## Documentary evidence guiding the shared spin

A period RoTS guide documents a shared `Spinning Attack` for all melee brothers and lists it for Torag, Guthan, Verac, and Dharok. The same guide separately describes Verac's `Helicopter` move, so those two visuals must not be conflated during animation identification.

Reference:
- https://forum.tip.it/topic/326100-rise-of-the-six-guide-rots/

This evidence supports using 21941 for the shared Hurricane/Spinning Attack presentation. Exact original sequence duration, pulse alignment, movement cadence, and damage timing still require direct encounter/video comparison before Hurricane is considered fully 1:1.

## Current research strategy

1. Audit the existing RoTS implementation before adding anything new.
2. Separate mechanics that already exist from mechanics that are approximated or missing.
3. Use source-known RoTS animation/GFX/projectile IDs to narrow cache research rather than scanning broad numeric neighborhoods blindly.
4. Use Owner Console -> RoTS Deep Scan to correlate BAS/render-set and GFX data for targeted candidates.
5. Runtime/video-test the short candidate list before changing gameplay.
6. Use the RoTS Fight Recorder for live encounter tests so event ordering, target changes, damage, special state, and timing can be read from one uploaded log instead of reconstructed from memory.
7. Patch one mechanic at a time and keep Matrix3 combat/instance ownership intact.
8. Record exact timing only after measurement or authoritative evidence.

## Research tools

Primary cache workflow:

- Client Console -> Owner -> RoTS cache research
- `Scan RoTS` for focused NPC/animation definition evidence.
- `Deep Scan` for render-set/BAS and GFX correlation.
- `Copy All` to move the complete research dump into analysis without rerunning standalone tools.

Runtime Fight Recorder:

- `Server/src/main/java/com/rs/game/npc/rots/RiseOfTheSixDebugLog.java`
- Starts automatically when a RoTS instance loads.
- Creates one unique file per instance under `data/logs/rots/`.
- Uses a bounded in-memory queue and daemon writer so combat/world tasks never wait on disk writes.
- Records elapsed milliseconds plus one recorder tick per scheduled world tick.
- Records full instance/player/brother snapshots every tick plus explicit encounter, normal-attack, and brother-special events.
- Includes RoTS-owned special state needed for debugging: Dharok stored damage, Torag rescue damage/victim, Hurricane/Wall Slam state, Guthan spear/victim/retrieval state, special rotation gates/index, side ownership, HP, targets, and tiles.
- Players are shown the generated relative log path once per fight. Upload that `.log` when runtime behavior is difficult to describe.
- If the bounded queue ever overflows, combat remains non-blocking and the writer records `DROPPED_LINES` at shutdown; treat a nonzero count as a recorder-capacity issue, not gameplay evidence.

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

1. Runtime-verify the integrated 21941 Hurricane inside the live encounter: spin duration, walking/tracking, pulse alignment, and clean return to combat.
2. Authentic subdued/kneeling presentation while preserving the working logical subdue/revive system.
3. Greatest Axe visual/timing verification.
4. Normal attack cadence verification for all six brothers.
5. Ahrim special rotation.
6. Karil special rotation.
7. Brother Throw execution/assets/timing.
8. Exact portal/barrier/shadow-realm and arena transition behavior.
9. Completion escape/reward flow if still absent after focused audit.

## Next checkpoint

Run one live RoTS encounter with the Fight Recorder active before changing more Hurricane/Greatest Axe mechanics.

Confirm from runtime plus the generated log:

1. 21941 begins when Hurricane starts and is not visibly restarted/frozen by the 10 damage pulses.
2. Dharok, Guthan, and Torag continue tracking the target at walking speed during the spin.
3. Forced post-revive Hurricane still uses the same 21941 presentation and returns cleanly to normal combat.
4. Greatest Axe records its exact charge start, animation 21940, every absorbed hit/stored total, charge end, and later stored-damage release.
5. Normal attack event spacing can be measured directly from log ticks instead of inferred from CombatScript return values alone.

Also run the repaired Owner -> Deep Scan and preserve its GFX correlation output for Greatest Axe visual research.

If Hurricane's animation ends before the 10-pulse mechanic finishes, use the recorder ticks plus video/runtime observation to measure the mismatch first. Do not blindly restart 21941 every pulse.

After this evidence is collected, continue the melee-brother-specials milestone rather than returning to broad animation or tool expansion.
