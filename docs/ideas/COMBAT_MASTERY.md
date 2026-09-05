# Combat Mastery

## Status

**IDEA / DESIGN IN PROGRESS**

This document is intentionally living documentation. The core direction is established, but individual numbers, class names, perk values, unlock levels, and endgame scaling rules are not final.

The goal is to build a long-term RuneScape progression system that adds deep class/build customization without replacing the familiar Matrix3/RuneScape foundation.

---

## 1. Core Vision

Combat Mastery adds a persistent class progression layer on top of normal RuneScape combat.

The important distinction is:

- RuneScape skills remain the character's familiar base progression.
- Classes provide new mechanics, abilities, passives, and build identity.
- Players build their own version of a class rather than following one fixed skill tree.
- Class progression is persistent and never lost when swapping classes.
- A later secondary-class system allows hybrid builds while keeping the main class more powerful and important.
- Higher-end content can be designed around these stronger builds without making ordinary low-level NPCs scale endlessly with the player.

The system should create build freedom similar in spirit to Path of Exile while remaining understandable, RuneScape-friendly, and practical to build in Matrix3.

---

## 2. Design Principles

### 2.1 Preserve RuneScape

Combat Mastery should extend RuneScape rather than erase it.

Existing combat skills, weapons, armour, bosses, abilities, and combat styles should remain useful foundations. Classes modify how players use those systems instead of requiring an entirely separate game.

### 2.2 Player-built classes

A class is not a mandatory preset build.

Two players using the same main class should be able to produce substantially different builds through their perk choices, equipment, abilities, secondary class, and future modifiers.

Example Berserker builds could include:

- fast dual-wield / rage generation,
- slow two-handed critical hits,
- bleed / sustain,
- low-life risk/reward,
- defensive bruiser,
- melee/spell hybrid through a secondary class.

### 2.3 No punishment for experimentation

Players should be encouraged to experiment.

Class swapping must not delete or reset progression. Perk respeccing should also avoid punitive systems whose primary purpose is making players afraid to change their build.

### 2.4 Server authority

The client is presentation and input only.

The server owns:

- class XP,
- class levels,
- class ownership/unlocks,
- perk points,
- perk ownership,
- perk requirements,
- main/secondary class state,
- saved build state,
- combat bonuses and effects,
- achievement progress.

A modified client must never be able to grant itself class levels, perks, points, bonuses, or combat effects.

### 2.5 Data-driven expansion

Adding a class or perk should eventually be primarily definition/data work rather than rewriting the panel or creating one-off combat code everywhere.

The UI should render the definitions supplied by the system instead of containing hardcoded layouts for every perk.

---

## 3. RuneScape Levels vs. Class Levels

The current preferred direction is to keep class progression separate from normal RuneScape skill levels.

Example character state:

- Attack: 99
- Strength: 99
- Defence: 99
- Berserker: 73
- Arcanist: 41
- Ranger: 22

This avoids requiring Attack/Strength/etc. to become level 255 purely to create longer progression.

### Why separate class levels are preferred

- Prevents extreme base-stat inflation.
- Keeps normal RuneScape progression recognizable.
- Gives us a large new progression space without rewriting every existing combat calculation.
- Allows each class to level independently.
- Makes "max every class" a meaningful long-term account goal.
- Lets future classes be added without increasing the RuneScape skill cap again.

### Level 255 concept

The original idea included raising combat skills to level 255 or higher.

This is **not rejected**, but it is currently considered optional rather than the foundation of Combat Mastery.

Possible future uses for 255 include:

- an overall Combat Mastery level,
- post-max mastery progression,
- prestige/ascension progression,
- a separate extended-progression game mode.

**OPEN DECISION:** whether 255 has a place in the final system and what it represents.

---

## 4. Main Classes

A player selects one active **Main Class**.

The main class receives the full version of its class mechanics and establishes the primary identity of the build.

A class can provide some combination of:

- a unique resource mechanic,
- class abilities,
- class-specific perk access,
- passive effects,
- interactions with existing RuneScape abilities,
- equipment/style synergies,
- special combat rules,
- visual/audio feedback,
- class challenges and achievements.

Classes should provide mechanics, not force one weapon or one exact build.

### Example class concepts

These names are placeholders, not a final launch roster.

#### Berserker

Possible identity:

- Rage resource,
- aggressive momentum,
- bleed,
- execute mechanics,
- low-life risk/reward,
- attack-speed or heavy-hit specialization.

#### Arcanist

Possible identity:

- spell interactions,
- elemental effects,
- resource manipulation,
- spell chaining,
- melee/spell hybrid potential.

#### Ranger

Possible identity:

- target marking,
- projectile interactions,
- critical/weak-point mechanics,
- mobility,
- rapid-fire or charged-shot builds.

#### Guardian

Possible identity:

- mitigation,
- block/fortify,
- retaliation,
- protection,
- heavy defensive bruiser builds.

More classes can be added later after the class contract is proven.

---

## 5. Class Progression

Each class maintains independent persistent progression.

Example:

```text
Berserker   Level 100
Arcanist    Level 74
Ranger      Level 53
Guardian    Level 31
```

Switching from Berserker to Ranger does not alter the stored Berserker level or XP.

Returning to Berserker restores its saved progression.

### Class XP

Class XP should come from meaningful gameplay while the class is active.

Potential sources:

- combat kills,
- bosses,
- class challenges,
- higher-tier encounters,
- achievements,
- future mastery content.

The exact XP formula is **OPEN**.

### Class points

Class levels can award points used to unlock perks.

Not every level needs to grant a major effect. Progression can alternate between:

- perk points,
- ability unlocks,
- new perk tiers,
- class mechanic upgrades,
- secondary-class milestones,
- build/loadout features,
- pinnacle unlocks.

The important requirement is that players regularly have something meaningful to work toward.

---

## 6. Perk System

Combat Mastery will not require one enormous fixed visual passive tree.

Instead, players choose from a pool of perks and construct their own build.

A perk can define:

- stable server-side ID,
- display name,
- description,
- class affinity,
- point cost,
- class-level requirement,
- prerequisite perks,
- incompatible perks,
- tags/categories,
- primary-class behavior,
- secondary-class behavior where applicable,
- combat effect handler/rules,
- future UI metadata.

### Example perk categories

- Offense
- Defence
- Utility
- Sustain
- Critical
- Bleed / damage-over-time
- Rage/resource
- Two-handed
- Dual-wield
- Magic
- Projectile
- Summon
- Hybrid

These are filtering/build concepts, not mandatory separate trees.

---

## 7. Minor, Major, and Build-Changing Perks

The system should avoid becoming hundreds of boring percentage increases.

### Minor perks

Useful supporting bonuses such as:

- resource generation,
- accuracy,
- sustain,
- duration,
- cooldown interaction,
- small conditional bonuses.

### Major perks

More meaningful effects that influence the direction of a build.

Example:

- consecutive melee attacks build increasing Rage faster,
- marked enemies receive stronger critical effects,
- blocking an attack empowers the next hit.

### Build-changing perks / keystones

These should significantly alter how the character plays.

Concept examples:

#### Blood Price

Abilities consume health instead of their normal resource under defined conditions.

#### Iron Fortress

Critical-hit potential is traded for a defensive/offensive conversion mechanic.

#### Arcane Warrior

Melee attacks can interact with equipped spell effects.

#### Executioner

Damage profile changes significantly based on the target's remaining health.

#### Berserk Momentum

Continuous aggression builds a powerful combat bonus that is lost or reduced when momentum breaks.

Exact balance values are intentionally not defined yet.

---

## 8. Perk Prerequisites Without a Fixed Tree

A visual tree is not required for perks to have progression relationships.

Example:

```text
Bloodlust
   +
Frenzy
   -> unlocks Blood Frenzy
```

Internally, `Blood Frenzy` can simply require the two prerequisite perk IDs.

This gives us tree-like build depth while allowing the UI to begin as a searchable/filterable perk manager.

A future custom graph/tree UI could render the exact same underlying definitions without changing the progression rules.

---

## 9. Secondary Classes

At a later main-class milestone, the player can unlock a **Secondary Class**.

Example:

```text
Main:      Berserker
Secondary: Arcanist
```

The main class remains more important.

### Current direction

- Main class: full class power/mechanics.
- Secondary class: reduced or limited version of its normal power.
- Initial design target: approximately half-strength where that makes mechanical sense.

However, the system should **not** blindly multiply every secondary-class number by 0.5.

Some mechanics need explicitly designed secondary behavior.

Example:

```text
Berserker as Main
- 100 max Rage
- full Rage generation
- full class abilities

Berserker as Secondary
- reduced max Rage
- reduced Rage generation
- limited/modified ability access
```

Each class definition should eventually specify what it means to be:

- Primary
- Secondary

This keeps hybrid builds controllable and makes the main-class choice meaningful.

### Secondary-class unlock level

**OPEN DECISION.**

Possible milestone examples discussed include class level 50 or another clear mid/late progression checkpoint.

---

## 10. Cross-Class Synergy

Hybrid combinations should eventually create interactions that are more interesting than simply adding two passive lists together.

Example concepts:

- Berserker + Arcanist: Rage can empower spell effects.
- Guardian + Arcanist: blocking can generate magical charges.
- Ranger + Berserker: ranged critical chains can build an aggression resource.

These can be implemented as dedicated cross-class perks or synergy rules once the base system is stable.

This is a future expansion, not required for the first vertical slice.

---

## 11. Class Swapping

Players can change active classes without losing progression.

Required behavior:

- Stored class XP remains.
- Stored class level remains.
- Saved builds remain.
- Returning to a class restores its previous progression/build state.
- Swapping should not require restarting the account or retraining from level 1 unless the class itself has never been trained.

### Respec philosophy

Perk experimentation should also be player-friendly.

Preferred direction:

- Respec outside combat or under another simple safety restriction.
- No intentionally painful gold sink.
- No rare respec token required merely to fix or test a build.

A small restriction may exist to prevent combat abuse, but it should not punish normal experimentation.

---

## 12. Saved Builds / Loadouts

Each class or class combination should eventually support saved builds.

Example:

```text
Berserker / Arcanist
- Bleed Spellblade
- Two-Hand Crit
- Sustain

Ranger / Guardian
- Bossing
- Defensive Ranged
```

A saved build may include:

- selected perks,
- main class,
- secondary class,
- future class ability configuration,
- possibly equipment/ability-bar integration later if intentionally added.

Build-load validation must occur on the server.

---

## 13. RuneLite-Style Class Panel

The preferred player-facing UI is a RuneLite-style sidebar panel rather than repurposing a RuneScape shop interface.

### Why

- No dependency on finding suitable interface/component IDs.
- No CS2/interface-layout fight for every new feature.
- Easy search, filtering, scrolling, tooltips, categories, and build management.
- The panel can evolve quickly while the underlying game system remains stable.
- Better suited to a large data-driven perk system.

### Proposed layout concept

#### Class header

- Main class
- Secondary class
- Active class level
- XP bar
- Available perk points

#### Available perks

- Search box
- Category filters
- Requirement filters
- Perk cards/rows
- Cost
- Level requirement
- Prerequisites
- Unlock button

#### Current build

- Selected perks
- Remove/respec controls
- Total points spent
- Warnings for invalid combinations

#### Builds

- Saved build slots
- Save
- Load
- Rename
- Duplicate later if useful

The exact visual design is **OPEN**.

---

## 14. Client/Server Security Model

The RuneLite-style panel is never authoritative.

A player may modify their client, edit displayed values, use Cheat Engine, or create custom requests. The server must remain safe even if the entire client is hostile.

### Example unlock flow

Client:

```text
REQUEST_UNLOCK_PERK 1002
```

Server validates:

1. Perk ID exists.
2. Player has access to the required class.
3. Required class level is met.
4. Player has enough server-owned perk points.
5. Prerequisite perks are actually unlocked server-side.
6. Incompatibility rules pass.
7. Perk is not already owned when duplicates are forbidden.
8. Player is in a valid state to change the build.
9. The resulting build is valid.

Only then does the server change persistent state.

### Never trust client-supplied combat data

The client must never be allowed to tell the server:

- "I am class level 100."
- "I have 50 perk points."
- "I own this perk."
- "This perk gives +500% damage."
- "My secondary class is full power."

The client can request an action. The server decides the result.

### Stable perk IDs

Perks should use stable server-side identifiers.

Example:

```text
1001 = Bloodlust
1002 = Frenzy
1003 = Executioner
```

The client sends the ID only when requesting an action. Effect values and rules are looked up and applied by the server.

Changing a local display from `+5%` to `+5000%` must only change what the cheater sees, never actual combat behavior.

---

## 15. Server State and Persistence

Persistent player data will eventually need to represent at least:

- known/unlocked classes,
- XP per class,
- level per class or enough XP to derive it,
- active main class,
- active secondary class,
- perk selections,
- available/spent perk points,
- saved builds,
- class/mastery achievements.

The exact Matrix3 persistence integration must be designed only when implementation begins and after tracing the existing authoritative persistence path.

No separate shadow-save system should be created if Matrix3 already has an appropriate player persistence owner.

---

## 16. Higher-Level NPC and Boss Progression

Combat Mastery will eventually make players much stronger, so the world needs content designed for that progression.

The preferred direction is **not** to automatically scale every NPC directly to the player's power.

A high-level player should still be able to return to weak content and feel powerful.

### Preferred hybrid model

- Existing low-level NPCs remain low-level.
- New higher-tier NPCs/areas exist for stronger players.
- Existing bosses may gain intentionally designed ascended/higher-tier variants.
- Certain special content may scale within controlled ranges when useful.

### Example boss progression

```text
Nex
-> Ascended Nex
-> Mythic/Pinnacle Nex
```

A higher-tier boss should ideally gain more than inflated HP.

Possible changes:

- additional mechanics,
- faster mechanic cycles,
- new phases,
- altered attacks,
- class/build checks,
- new visual effects,
- improved rewards,
- optional challenge modifiers.

This preserves the feeling of character growth while still providing difficult endgame content.

---

## 17. Endgame and Class Completion

Because every class retains independent progression, maxing classes becomes long-term account content.

Possible achievements:

### Jack of All Trades

Reach a meaningful intermediate level on every class.

### Master of All

Reach maximum level on every class.

### Duality

Max a main/secondary class combination or reach another major hybrid milestone.

### Pathwalker

Unlock or use a defined number of major/build-changing perks.

### Class-family achievements

Examples:

- master all martial classes,
- master all magical classes,
- master all defensive classes.

### Completion rewards

The strongest completion rewards should not make unfinished players irrelevant.

Good candidates:

- title,
- cape,
- aura/cosmetic,
- special animations,
- UI cosmetics,
- prestige indicator,
- access to pinnacle challenges,
- small carefully balanced account-wide benefits.

---

## 18. Future Mastery / Ascension

After maximum class progression, we can add a separate prestige/mastery layer instead of endlessly increasing base combat stats.

Possible directions:

- overall Combat Mastery level,
- mastery level up to 255,
- ascension ranks,
- class prestige,
- pinnacle perk points with strict caps,
- cosmetic progression,
- challenge-based progression rather than pure XP.

This system should only be designed once normal class progression has proven fun and balanced.

---

## 19. Relationship to Combat Framework

Combat Mastery should eventually plug into the reusable Combat Framework rather than replacing Matrix3 combat.

Conceptually:

```text
Matrix3 Combat
      |
Combat Framework
      |
Combat Mastery Rules
      |
Class + Perk Effects
```

The exact ownership boundary must be verified when implementation begins.

The desired outcome is that:

- Matrix3 remains the authoritative combat foundation.
- Combat Framework exposes clean extension points where needed.
- Combat Mastery supplies class/perk rules through those extension points.
- Individual perks do not scatter unrelated special cases across core combat code.

---

## 20. Recommended Internal Shape

This is conceptual only; no implementation names are approved yet.

The final system will likely need clear responsibilities equivalent to:

- class definitions,
- class progression,
- perk definitions,
- perk validation,
- active build state,
- class/secondary-class rules,
- persistence integration,
- combat effect hooks,
- panel state/snapshots,
- client action requests.

The important architectural rule is separation of authority, not the exact Java class names.

---

## 21. First Vertical Slice

When implementation eventually begins, do **not** build the entire system at once.

Recommended first proof:

1. One test class.
2. Persistent class XP/level.
3. A very small perk pool.
4. Server-owned perk points.
5. RuneLite-style panel displaying server state.
6. Unlock/remove one perk through validated requests.
7. One real server-side combat effect proving the perk pipeline.
8. Save/relog verification.
9. Class swap away/back with progression preserved.

Only after that works should we expand into:

- more classes,
- secondary classes,
- saved builds,
- cross-class perks,
- higher-tier bosses,
- prestige/mastery.

---

## 22. Open Decisions

These are intentionally unresolved and should be updated as design work continues.

### Progression

- Maximum class level.
- Class XP formula.
- Whether class XP starts immediately or after a RuneScape progression milestone.
- How frequently class points are awarded.
- Whether overall Combat Mastery uses level 255.

### Class selection

- When the first main class is selected.
- Initial class roster.
- Whether every class is immediately available or unlocked through gameplay.

### Secondary class

- Unlock level/milestone.
- Exact reduced-power rules.
- Whether all abilities are available to a secondary class.
- Whether a player can change secondary class freely outside combat.

### Perks

- Total point budget.
- Number of available perks per class.
- Universal perk pool vs. entirely class-affinity-based perks.
- Respec restrictions needed purely to prevent combat abuse.
- Whether perk prerequisites form loose graphs, tiers, or both.

### UI

- Final sidebar placement.
- Exact visual layout.
- Whether build management stays entirely in the panel or later gains an in-game interface too.

### Endgame

- Higher-tier world structure.
- Ascended boss rules.
- Overall mastery/prestige system.
- Whether level 255 belongs here or in a separate extended mode.

---

## 23. Non-Goals for the First Version

Do not require the first implementation to include:

- dozens of classes,
- a giant visual PoE-style tree,
- every perk category,
- secondary classes,
- cross-class synergies,
- level-255 combat skills,
- ascension,
- new high-tier worlds,
- reworked versions of every RuneScape boss,
- a custom cache interface.

The system should prove one complete, secure, persistent class/perk loop first.

---

## 24. Current Direction Summary

The current preferred design is:

```text
Normal RuneScape progression
          |
Choose/train a Main Class
          |
Earn Class XP + Class Levels
          |
Earn Class Points
          |
Build freely from available perks
          |
Save persistent class/build progression
          |
Unlock reduced-power Secondary Class
          |
Create hybrid builds
          |
Train/max multiple classes
          |
Higher-tier / pinnacle content
          |
Mastery / completion / possible level 255 system
```

### Core rule

**The class gives the player a mechanic and identity. The perks let the player decide what that class becomes.**

That is the foundation this design should preserve as it evolves.
