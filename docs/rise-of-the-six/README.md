# The Barrows: Rise of the Six

Target: reproduce the classic RuneScape 3 Rise of the Six encounter as completely as practical on Matrix3 while preserving Matrix3 combat, NPC, world, instance, drop, and persistence ownership.

A mechanic is not complete merely because the fight is killable.

## Classification

- VERIFIED: confirmed by runtime testing in this Matrix3 project.
- verified-static: directly established from Matrix3 source/cache data, donor assets, or strong RuneScape encounter evidence, but not yet runtime-confirmed here.
- HYPOTHESIS: plausible implementation detail still requiring evidence/runtime validation.

## Fidelity evidence ladder

RoTS must not be reconstructed from one RSPS donor and assumed accurate.

1. **Behavior authority - RuneScape encounter evidence**
   - Prefer Jagex-era mechanic descriptions, the Beasts transcript, strategy documentation, and period-correct gameplay footage for what an attack actually does.
   - Use old 2013-2015 RoTS footage when text/code cannot establish movement, timing, facing, throw trajectories, wall interaction, Deathcopter behavior, or escape sequencing.
2. **Asset evidence - Matrix-family donor implementations**
   - Use donor code for candidate NPC ids, animations, graphics, projectiles, objects, transformations, coordinates, and sequencing clues.
   - Donor gameplay logic is reference only. Known bugs/placeholders do not automatically become Matrix3 behavior.
3. **Data verification - revision-830 cache**
   - Candidate ids must resolve correctly in the active cache before being treated as project evidence.
4. **Final authority - Matrix3 runtime**
   - A mechanic becomes VERIFIED only after it behaves correctly in this project.

Normal workflow:

**Encounter behavior evidence -> donor asset candidate -> revision-830 data check -> smallest Matrix3-native implementation -> targeted runtime test -> VERIFIED or revise.**

## Current runtime checkpoint

VERIFIED from user runtime testing:

- The donor-backed RoTS arena renders from the copied revision-830 map rather than loading void/black terrain.
- All six empowered brothers spawn visibly on valid arena terrain and engage the player.
- Guthan resolves as `Guthan the Infested (level 650)`.
- At least one subdued brother has been observed returning through the current shadow-bond revival path.

Still requiring targeted runtime verification:

- exact 30-second mass revival timing / simultaneous-six victory
- dedicated incapacitated brother forms 18546-18551 and persistent kneeling presentation
- type-5 shared revival bar appearance, fill direction/colors, reset behavior, and removal on revival
- every current special and cleanup path
- Guthan Impale behavior including new same-side selection
- daily west/east formation ownership and physical side split
- empty-side empowerment warning / hop timing / landing presentation

## Brother toolkit map

| Brother | Classic toolkit to reproduce | Wall Slam? | Current state |
| --- | --- | --- | --- |
| Dharok | Hurricane -> Greatest Axe -> Wall Slam | Yes | All three mechanically present; exact shared visuals/timing still need runtime fidelity work. |
| Torag | Hurricane -> Whack -> Wall Slam + conditional Throw | Yes | Core three mechanically present; Throw pairing is side-gated but flight/landing execution is still pending exact assets. |
| Guthan | Hurricane -> Impale + conditional Throw | No | Hurricane + Impale mechanically present; Impale now uses same-side victim filtering. |
| Verac | Soulspot, Deathcopter, Wall Slam + conditional Throw | Yes | Normal Hurricane mismatch fixed; Wall Slam present; Soulspot/Deathcopter/Throw execution pending. |
| Ahrim | Turret of Fire, Shadow Pits, Flight | No | Base combat only. |
| Karil | Lightning Conductor, Bombard, Portal Dash | No | Base combat only. |

## Verified-static encounter fundamentals

- Rise of the Six supports 1-4 players; classic intended group size is four.
- Empowered brothers are level 650 with 50,000 life points.
- Empowered NPC ids: Ahrim 18538/18539, Dharok 18540, Guthan 18541/18542, Karil 18543, Torag 18544, Verac 18545.
- Donor-backed dedicated incapacitated forms are Ahrim 18546, Dharok 18547, Guthan 18548, Karil 18549, Torag 18550, Verac 18551.
- The donor uses client hitbar type 5 specifically as the RoTS incapacitation/revival bar while those downed NPC forms are active.
- The donor uses animation 21914 when a downed brother returns to its active NPC form at 25,000 life points.
- Defeating a brother heals each active brother by 5,000 life points.
- Shared shadow-bond revive timer is 30 seconds and resets whenever another brother is defeated.
- If that timer expires, currently defeated brothers return at 25,000 life points.
- The fight ends only when all six are simultaneously subdued.
- Fight music is The Price is Wight, track id 1208.
- Nocturne's RoTS donor copies source chunk 290,753 as an 8x8-chunk map; Matrix3's 1x1 MapInstance ratio matches that shape.
- RuneScape uses a repeating twenty-day west/east brother formation cycle.
- A dated RuneScape formation record for 2025-06-11 is West Ahrim/Torag/Guthan and East Karil/Dharok/Verac; this is rotation 11 in the documented twenty-rotation sequence and is the Matrix3 UTC cycle anchor.
- If one arena side has no players while the opposite side is occupied, the empty-side brothers eventually cross over to assist the occupied side.
- The empowerment warning occurs at about 24 seconds of a continuously empty side, and the incoming brothers become available on the occupied side at about 27 seconds.
- The empty-side hop cannot occur while any brother is incapacitated.
- Karil Shadow Dash and players hugging the second barrier are additional live blockers; those systems are not implemented in the current checkpoint yet.

## Implemented now

### Native encounter foundation

- Matrix3 `BossInstance` registration with four-player cap.
- Admin/developer launch through `bosslabs rots` / `bosslabs riseofthesix` without modifying Commands.java.
- Dedicated RoTS brother NPC class; normal `BarrowsBrother` remains untouched.
- Dedicated CombatScript keys for all empowered/alternate ids.
- Admin/owner Start creates a fresh private boss instance instead of reusing the old hour-long session.

### Daily formation / side ownership

The encounter has an explicit west/east formation authority instead of treating all six brothers as one undifferentiated group.

- All twenty documented brother rotations are encoded in `RiseOfTheSixInstance`.
- Rotation advances by UTC calendar day using 2025-06-11 rotation 11 as the dated anchor.
- Each loaded encounter records one active rotation and assigns exactly three brothers WEST and three EAST.
- The six already runtime-proven spawn tiles are deliberately reused as two groups of three: source X 2326/2328/2330 for WEST and 2332/2334/2336 for EAST, all on source Y 6034 plane 1.
- Source X 2331 is the temporary Matrix3 side-classification midpoint for player-position queries.
- `getBrotherSide`, `getPlayerSide`, `isPlayerOnBrotherSide`, and `areBrothersOnSameSide` provide one encounter-owned side API.
- Runtime brother-side ownership can change after an empowerment hop; it is no longer permanently inferred only from the day's starting rotation.
- The verified Throw pairing matrix rejects otherwise-valid targets on the opposite current side.
- `findVerifiedThrowTarget` therefore returns only active same-side candidates.

**Important fidelity boundary:** the daily lineup ownership is evidence-backed, but the exact final north/middle/south physical brother tiles and exact Jagex arena side bounds are not yet runtime-verified. The current implementation intentionally preserves known-good terrain instead of moving the fight onto guessed coordinates.

Static date anchors for testing the cycle:

- 2025-06-11 UTC -> rotation 11: WEST Ahrim/Torag/Guthan, EAST Karil/Dharok/Verac
- 2026-09-02 UTC -> rotation 19: WEST Karil/Torag/Dharok, EAST Ahrim/Guthan/Verac
- 2026-09-03 UTC -> rotation 20: WEST Karil/Dharok/Verac, EAST Ahrim/Torag/Guthan

These are test anchors, not permanently hardcoded lineups.

### Empty-side empowerment / side hop

The encounter now owns the first Matrix3-native side-collapse behavior.

Behavior implemented:

1. A repeating encounter task counts only active instance players on WEST and EAST using the current side classifier.
2. If both sides have players, or neither side has an active player, the empty-side counter is reset.
3. If exactly one side remains empty continuously, the counter begins for that empty side.
4. Any incapacitated brother resets/suppresses the empty-side counter, matching the live requirement that all six must be standing before the hop can occur.
5. At roughly 24 seconds (40 Matrix3 ticks), the instance broadcasts: `As there is no one on the other side of the portal, it empowers the Barrows Brothers to destroy everyone!`
6. Roughly 3 seconds later (5 ticks), the hop is allowed only if the same side is still empty, the opposite side is occupied, and no brother is incapacitated.
7. The three active brothers owned by the empty side have their RoTS special state safely cleared, use donor-backed transition GFX 4413, relocate onto the occupied half, and have their current side ownership changed to the occupied side.
8. The hop is one-time for the encounter. After it succeeds, all six brothers are owned by the occupied side for same-side coordination such as future Throw behavior.
9. Fight completion / instance destruction invalidates the side-hop generation so a stale delayed relocation cannot fire afterward.

Current landing implementation deliberately reuses the three already runtime-proven occupied-side tiles. This can stack an incoming brother with a resident brother. Live RuneScape places the incoming trio slightly away from the resident starting positions, so **exact empowered landing tiles are still HYPOTHESIS** and must be replaced only when map/video/runtime evidence establishes them.

Known blockers still pending:

- Karil Shadow Dash must prevent/delay the side hop once Shadow Dash exists.
- The second-barrier player-position blocker cannot be reproduced accurately until the real barrier/portal sub-areas are mapped.
- Exact transition animation and exact landing spacing still need cache/video/runtime verification. GFX 4413 is donor-backed, not yet runtime-VERIFIED here.

### Shadow bond / incapacitation presentation

Encounter state:

- brothers become logically `subdued` instead of using normal NPC drop/respawn death
- Matrix3 keeps the subdued NPC shell at 1 HP so the client does not remove it as a true zero-HP NPC
- delayed hits already in flight are reduced to zero after subdual so they cannot hide the 1-HP shell
- surviving brothers heal 5,000 HP after each subdual
- new subdual resets the pending revival generation
- subdued brothers revive at 25,000 HP when the revival task wins
- all-six-subdued state cancels further revival and any pending side-hop task

Current visual implementation:

- each newly subdued brother transforms into its donor-backed dedicated incapacitated form: Ahrim 18546, Dharok 18547, Guthan 18548, Karil 18549, Torag 18550, Verac 18551
- those dedicated NPC forms are intended to own the persistent downed/kneeling presentation rather than leaving an active brother model standing after its death animation ends
- every subdued brother receives a `RiseOfTheSixReviveBar`, using donor-backed client hitbar type 5
- the shared bar progresses from 0 to 255 over the same 50 Matrix3 ticks used by the ~30-second revival generation
- when another brother is subdued, the old bar generation becomes stale and **all currently subdued brothers immediately reset to an empty bar together**
- the revive bar is inserted before Matrix3 can fall back to its ordinary entity HP bar, preventing the temporary logical 1/50,000 HP shell from becoming the intended downed UI
- on revival, the special bar is cleared, the brother returns to its active primary NPC id, animation 21914 is played, and the existing revival path restores 25,000 HP
- all-six completion and instance cleanup clear the pending revival bars/tasks rather than allowing stale visual updates

**Fidelity boundary:** NPC ids 18546-18551, hitbar type 5 and revival animation 21914 are verified-static donor evidence. Their exact revision-830 visual result in this Matrix3 client—including the persistent kneeling pose and exact bar colors/style—must be runtime-confirmed before being promoted to VERIFIED.

### Base combat

- Ahrim 3,000 max, donor-backed projectile 559 / impact GFX 377 / 5-tick baseline
- Karil 3,000 max, donor-backed projectile 955 / animation 18232 / 7-tick baseline
- Guthan/Torag/Verac 3,500 melee baseline
- Dharok 2,000 -> 7,000 max-hit bands by remaining HP
- Guthan life-steal baseline at 1-in-8
- Ahrim Strength drain baseline at 1-in-8

### Dharok - Greatest Axe

Donor-backed first faithful slice:

- `Give me everything!`
- GFX 4406
- animation 21940
- absorbs/stores incoming damage during the charge
- normal autos pause during the charge
- stored damage is added once to Dharok's next normal outgoing hit

Exact timing/visual behavior still requires runtime verification.

### Torag - Whack

Donor-backed first faithful slice:

- Torag start animation 21933
- victim pin animation 21934
- hammering animation 21935
- release animation 21938
- victim is locked/pinned
- incoming teammate damage is redirected into the 2,500 release threshold instead of Torag HP
- temporary solo safety release remains until authentic termination behavior is fully verified

### Hurricane

Current Matrix3 mechanical implementation:

- normal Hurricane belongs to Dharok, Guthan and Torag
- brother pursues at walking speed
- ten escalating hard-typeless pulses
- nearby players can also be hit
- normal autos are suppressed while Hurricane owns the brother
- exact spin animation/GFX and final damage/radius distribution remain HYPOTHESIS

**Verac correction:** ordinary Hurricane has been removed from Verac's normal implemented rotation. Verac can still be forced into Hurricane through the shared post-revival melee rule. This matches the documented rare post-revival Verac spin behavior rather than treating Hurricane as one of his normal specials.

### Wall Slam

Current Matrix3 mechanical implementation for Dharok, Torag and Verac:

- captures the target's tile when the special begins
- runs toward a reachable arena collision edge
- rushes back toward the captured location
- resolves a 5x5 hard-typeless impact around the captured tile
- current damage range 500-3,000

Exact wall anchors, run-up/fling animations, impact animation/GFX and final damage distribution remain HYPOTHESIS.

## Guthan Impale

### Behavior authority

Classic behavior:

1. Guthan prefers a player he is not currently attacking on his side.
2. If no secondary target is available, he can throw the spear at his current target.
3. The spear impales the victim and Guthan loses access to spear specials while it is away.
4. Guthan continues normal melee attacks by punching with the same combat effectiveness.
5. The victim takes repeated 400-500 hard-typeless bleed damage.
6. Returning adjacent to Guthan causes him to rip the spear back out.
7. Retrieval deals about 1,000 hard-typeless damage.
8. Victim death, Guthan death/subdual, or Torag pummelling the impaled player returns the spear automatically.

### verified-static asset/data evidence

- normal Guthan id 18541; alternate spear-absent Guthan id 18542
- cache attack animation: 18541 -> 18223, 18542 -> 18224
- donor spear projectile: 4411
- donor throw animation candidate: 21944
- donor victim/impaled animation: 21945
- donor spear-retrieval animation: 21947
- donor bleed graphics: 4411 and 4407
- donor bleed values: 400-500
- donor retrieval hit: 1,000

### Current Matrix3 implementation

- Impale is the second normal Guthan special after Hurricane in the implemented rotation.
- The target selector now prefers another eligible **same-side** instance player instead of Guthan's primary target.
- Opposite-side players are rejected from the secondary Impale candidate pool.
- If there is no secondary same-side victim, Guthan can fall back to his primary target only when that primary target is also on Guthan's current side; a cross-side primary target causes Impale to wait rather than fire through the portal.
- After empty-side empowerment moves Guthan to the occupied side, the selector automatically consumes Guthan's updated current side ownership.
- Guthan sends projectile 4411 and transforms to alternate NPC 18542 while the spear is away.
- The victim receives animation 21945 and repeated 400-500 hard-typeless bleed ticks with GFX 4411/4407.
- Guthan resumes ordinary melee combat while spearless; the alternate NPC's cache attack animation 18224 is therefore used naturally by Matrix3 combat.
- Guthan cannot start another special while the spear is away.
- When the victim becomes adjacent, Guthan briefly owns the retrieval sequence, plays animation 21947, restores NPC 18541, and deals a 1,000 hard-typeless retrieval hit.
- Generation tokens stop old projectile/bleed/retrieval tasks after reset/death/new-instance cleanup.
- Torag beginning Whack on the impaled victim automatically returns Guthan's spear through the encounter's cross-brother coordinator.
- Guthan subdual/finish and invalid/dead/leaving victims also restore the spear safely.

Still HYPOTHESIS / pending runtime:

- runtime confirmation that the temporary player side midpoint matches the visible portal well enough for Impale selection
- exact throw/retrieval tick timing
- whether donor animation 21944 is visually the exact launch animation in this cache
- exact defensive-ability clearing/cooldown behavior applied by each bleed tick
- exact Shadow Realm Impale multipliers/interaction

## Shared Throw interaction model

Throw is a cross-brother encounter interaction, not a generic NPC attack.

### verified-static thrower/target matrix

- Guthan -> Ahrim, Karil, Verac
- Torag -> Ahrim, Karil, Verac
- Verac -> Ahrim, Karil
- Dharok is not listed as a Throw brother in the Beasts descriptions used for this fidelity target.

The encounter instance owns this matrix and applies **current** side ownership, including side ownership changed by an empty-side empowerment hop, before returning an eligible Throw target.

### Important current boundary

**Actual Throw launches are intentionally not active yet.**

Side ownership is authoritative enough to reject cross-side pairing and to merge all six onto one side after empowerment, but the exact brother Throw launch/flight/landing animation, projectile/trajectory, timing and impact damage are not established strongly enough to activate the attack without guessing.

The shared coordinator should continue to own:

- same-side eligibility
- thrower/target reservation
- Surge/run-to-target sequence
- thrown brother flight/landing state
- landing/arc hard-typeless damage
- cleanup on subdual/death/realm transition
- Verac Deathcopter interception when Verac has signaled and another brother attempts to Throw him

Do not implement Throw separately in each brother's CombatScript.

## HYPOTHESIS items still pending

- exact revision-830 rendering/idle pose of downed NPC forms 18546-18551 until runtime-tested
- exact client colors/style of type-5 RoTS revival bar until runtime-tested
- exact physical north/middle/south brother spawn coordinates/facing inside each daily side
- exact player west/east sub-area bounds beyond the current source-X midpoint classification
- exact empowered-side landing coordinates/spacing and transition animation
- Karil Shadow Dash blocker for empowerment hop
- exact second-barrier sub-area blocker for empowerment hop
- exact special cadence / number of autos between specials; current gate is 3-5 autos
- exact Hurricane visuals/radius/damage distribution
- exact Wall Slam anchors/animations/path timing
- Torag authentic Whack natural termination behavior
- exact Throw launch/flight/landing assets/timing/damage
- exact Shadow Realm interaction for every special
- real entrance/graveyard/tunnel object routing

## Required for 100% encounter fidelity

### Entry / staging

- wire real Rise of the Six well object 87997
- consume one Barrows totem (item 30004) where appropriate
- reproduce join/rejoin rules
- reproduce staging tunnel / random tunnel layouts / bridge counts
- reproduce four pressure pads and fight lock
- replace temporary exit/grave tile with verified RoTS route

### Formation / arena

- runtime-verify the twenty-day UTC formation cycle against the expected rotation
- runtime-verify the current three-west/three-east known-good tile split
- replace temporary within-side slot coordinates with exact classic north/middle/south positions only when evidence establishes them
- verify exact brother facing
- map the real central barrier / second-barrier sub-areas
- runtime-verify the new ~24s warning / ~27s empty-side empowerment hop
- replace stacked occupied-side arrival slots with exact live landing positions once established
- add Karil Shadow Dash and second-barrier blockers to the empowerment gate when those systems exist
- prevent late joins after combat begins

### Shared shadow mechanics

- runtime-verify dedicated downed forms 18546-18551 remain visible/kneeling through the whole incapacitation window
- runtime-verify type-5 revive bars fill/reset together and disappear on revival/completion
- runtime-verify revival animation 21914 and 25,000 HP return
- Shadow Drag selection/thresholds
- full-team Shadow Realm move in/out
- Shadow Realm accuracy/damage changes

### Shared melee fidelity

- exact Hurricane spin animation/GFX, pulse timing/range/damage
- exact Wall Slam wall anchors/run-up/fling/impact assets
- replace temporary 3-5 auto cadence with classic cadence when verified
- activate shared Throw flight/landing only after exact assets/timing are established

### Ahrim

- blood-spell self healing
- Flight transformation using alternate NPC id 18539
- airborne melee immunity / magic reduction as appropriate to target era
- purple/red Shadow Pits
- Turret of Fire
- exact rotation/assets/messages

### Dharok

- runtime-verify Greatest Axe charge and returned damage
- runtime-verify Hurricane/Wall Slam fidelity

### Guthan

- runtime-verify full Impale lifecycle from this checkpoint
- runtime-verify same-side target preference against the visible arena split
- implement defensive-ability clearing/cooldown behavior if target-era evidence confirms it
- activate verified Throw interactions after exact Throw assets are established
- exact healing/resistance/set-effect behavior

### Karil

- Lightning Conductor
- Bombard pillar/line lightning
- Portal/Shadow Dash with portal explosions/closure behavior
- ranged stat-drain effect
- exact rotation/assets/messages

### Torag

- adrenaline-drain set effect
- activate verified Throw interactions after exact Throw assets are established
- replace temporary Whack solo safety timeout with authentic termination rules
- runtime-verify Whack/Hurricane/Wall Slam

### Verac

- armour/prayer-ignore set effect
- Soulspot/prayer drain
- Deathcopter and its Throw interception relationship
- activate verified Throw to Ahrim/Karil after exact Throw assets are established
- runtime-verify Wall Slam

### Victory / rewards / escape

- authentic victory/collapse sequence
- reward chest eligibility
- real RoTS reward rolls / level-90 shields / Malevolent energy
- unstable Malevolent energy item 30026
- 30-second collapsing escape
- collapsing walls/pads/bridges/alternate crossings
- destroy unstable energy on invalid escape/death/logout/teleport
- convert to stable Malevolent energy item 30027 only after valid escape
- no real loot in practice mode

## Non-goals

- Do not modify normal Barrows or `BarrowsBrother` to make RoTS work.
- Do not create a second combat engine/timer/world/NPC lifecycle inside BossLabs.
- Do not invent exact side-hop landing coordinates before map/runtime evidence establishes them.
- Do not activate guessed Throw flight/landing behavior merely because side ownership now exists.
- Do not mark HYPOTHESIS behavior VERIFIED without runtime evidence.
