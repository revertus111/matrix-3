# MiniMe Multi-Character Gameplay System

## Status

IDEA / DESIGN PLAN ONLY — not an approved implementation task yet.

This document captures the intended gameplay identity, progression rules, control model, balancing direction, and possible implementation phases for a MiniMe system in Matrix3.

The central idea is not to build a separate traditional RTS game mode. The stronger direction is to make multi-character control part of normal RuneScape gameplay.

## Core Pitch

A player has one normal Main character and can eventually unlock up to five additional independently progressing characters called **MiniMes**.

Each MiniMe is a real playable character under the same owner profile:

- its own combat stats
- its own skilling stats
- its own equipment
- its own inventory
- its own XP and progression
- its own combat role/build
- direct player control when desired
- autonomous orders when the player is controlling someone else

The Main remains the primary character. MiniMes expand what the player can do without replacing normal RuneScape progression.

The result should feel like:

**RuneScape progression + squad management + optional RTS-style control.**

It should not feel like a conventional RTS with base construction, unit factories, resource buildings, or disposable troops.

## Player Fantasy

A player might eventually have:

```text
Main       -> bossing
MiniMe #1  -> mining
MiniMe #2  -> woodcutting
MiniMe #3  -> fishing
MiniMe #4  -> Slayer / combat
MiniMe #5  -> smithing or another support task
```

At any moment the player can stop managing the group, take direct control of one character, and play normal RuneScape with that character.

The appeal is that the player is not merely commanding bots. They are building their own RuneScape squad over time.

## Main Character

Every owner profile has one Main.

The Main:

- uses the normal full-size player presentation
- progresses normally
- receives normal XP rules
- receives normal drop-rate rules
- remains the preferred character for high-value PvM reward hunting
- can be controlled exactly like a normal Matrix3 player
- acts as the primary identity of the account/profile

The Main should never require MiniMes to remain viable. A player who wants to focus almost entirely on the Main should still be able to enjoy the game.

## MiniMes

### Identity

"MiniMe" is the current preferred player-facing term.

A MiniMe is not intended to be a second login, a disposable summoned NPC, or a clone with copied stats.

It is an additional owned character profile with independent progression.

### Visual Identity

Current direction:

- MiniMes use normal player equipment/models where possible.
- They are visually scaled smaller than the Main so they are instantly recognizable.
- A starting target could be approximately 70-80% of normal player visual scale, subject to runtime testing.
- Visual scale should not automatically imply smaller collision/pathing rules. Gameplay size should remain Matrix3-safe unless a later verified design requires otherwise.

The visual difference should make a Main followed by several equipped MiniMes immediately recognizable as a signature server feature.

### Independent Progression

MiniMes do **not** inherit the Main's levels.

Example:

If the Main has 99 Mining, a newly unlocked MiniMe does not receive 99 Mining. The MiniMe begins from its own starting progression and must train Mining itself.

That applies to:

- combat skills
- gathering skills
- production skills
- Slayer or equivalent character-specific progression where appropriate
- equipment requirements
- content requirements when character-specific progression makes sense

The point is to raise and develop the MiniMe, not buy an instant maxed worker.

## Ownership Model

Current design target:

```text
Owner Profile
|
|-- Main
|-- MiniMe #1
|-- MiniMe #2
|-- MiniMe #3
|-- MiniMe #4
`-- MiniMe #5
```

Initial cap target: **five MiniMes per owner profile**.

This is a design target, not yet a technical constant.

MiniMes should ideally be persisted as owned character data rather than implemented as five full external game clients or five normal simultaneously logged-in accounts.

The exact Matrix3-native representation must be determined by a targeted source scan before implementation.

## Control Modes

The player should be able to move naturally between normal RuneScape control and group command control.

### 1. Direct Control

The player takes direct control of one character.

While directly controlled, that character should behave as closely as practical to normal RuneScape gameplay:

- click-to-move
- interact with NPCs
- interact with objects
- skill
- fight
- loot
- equip items
- use inventory
- use interfaces

The player should be able to directly control the Main or any owned MiniMe.

Possible action name:

**Take Control**

### 2. Command / RTS Control

The existing free-camera direction, currently accessed by `Ctrl + \``, could later provide the presentation layer for multi-character command control.

Potential controls:

- click to select one MiniMe
- Shift-click or similar to add/remove selections
- drag-box to select several MiniMes
- select all owned MiniMes in range
- right-click ground -> move here
- right-click NPC -> attack
- right-click resource -> gather
- follow Main
- follow selected character
- hold position
- stop current task
- return/fallback
- Take Control

The command camera should enhance RuneScape rather than replace its normal interface.

### 3. Quick Character Switching

The player should have a fast way to switch direct control between owned characters without navigating a cumbersome menu.

Possible future UX:

```text
[Main] [M1] [M2] [M3] [M4] [M5]
```

Each slot could eventually show useful information such as:

- health
- current task
- combat state
- inventory warning/full state
- current skill/action
- distance/status

Exact UI belongs to a later implementation design.

## Autonomous Tasks

MiniMes should be capable of continuing reasonable tasks while the player directly controls another character.

Examples:

- mine a rock/resource cycle
- chop trees
- fish
- smith
- perform another supported production loop
- fight an assigned NPC or supported encounter
- follow another owned character
- continue a valid Slayer/combat task

This is intentionally stronger than simply having followers.

The gameplay fantasy is:

> "Send this MiniMe mining while I continue progressing my Main."

However, autonomy must remain bounded by game rules. MiniMes should not become unrestricted scriptable macro clients.

## Resource Gathering

Skilling is one of the strongest uses for MiniMes.

A MiniMe assigned to Mining should:

1. require the proper Mining level itself,
2. require valid equipment/tool rules,
3. perform the normal Matrix3 skilling action or a Matrix3-native controlled equivalent,
4. gain its own XP,
5. receive resources through normal character inventory rules,
6. stop or change behavior when normal requirements prevent continuation.

The same principle should apply to Woodcutting, Fishing, and other supported skills.

The system should reuse authoritative Matrix3 skill/content logic rather than create a second simplified MiniMe-only skilling engine.

## Inventory, Equipment, and Banking

Each MiniMe should have its own active inventory and worn equipment.

Open design question:

- shared owner bank,
- character-specific banks,
- or a hybrid model.

A shared owner bank is currently attractive because it keeps the player's roster feeling like one progression profile and avoids needless item-transfer friction. This must be checked against economy, persistence, and content assumptions before becoming a final rule.

Direct item transfer between the Main and MiniMes should be designed intentionally rather than accidentally relying on player trading.

## Unlocking MiniMes

MiniMes should not be free command spawns, but they also should not require an absurd grind before the player experiences the server's signature feature.

The first MiniMe should be obtainable relatively early.

Later slots should represent increasing progression milestones.

Conceptual pacing:

| Slot | Intended Pace |
| --- | --- |
| MiniMe #1 | Early / accessible introduction |
| MiniMe #2 | Moderate progression |
| MiniMe #3 | Mid progression |
| MiniMe #4 | Significant progression |
| MiniMe #5 | Long-term account goal |

Exact costs must be balanced from actual game rates rather than guessed in advance.

## MiniMe Unlock Currency

Current preferred direction is a dedicated account-wide currency or progression token used to unlock MiniMe slots.

Possible names:

- Command Points
- Companion Marks
- Echoes
- Squad Marks
- Bond Shards
- Legacy Marks

Name is not finalized.

The currency should be earned by **playing the game**, not by standing in one repetitive grind for dozens of hours.

Potential sources:

- bossing
- Slayer
- skilling milestones
- achievements
- quests/content completion
- account milestones
- world events
- selected daily/weekly objectives if those systems fit the final server

The system should reward broad progression while keeping MiniMe access understandable.

## No Traditional RTS Base Building Requirement

This feature does **not** require:

- building a base
- constructing barracks
- purchasing mining buildings
- RTS-style technology trees
- unit factories
- disposable armies
- replacing RuneScape towns/economy with RTS infrastructure

Characters progress through RuneScape activities.

If construction/territory systems are ever added later, they should be optional content built on top of the MiniMe system, not a requirement for the core idea.

## PvM and Economy Balance

This is one of the most important design areas.

Five independently fighting MiniMes using normal boss unique drop rates could multiply rare-item supply far too aggressively.

The Main should retain normal PvM reward rules.

MiniMes should use intentionally reduced access to high-value rare rewards.

### Current Rare-Drop Direction

For selected valuable/unique rewards, a MiniMe could receive approximately a **5x-10x rarity penalty** compared with the Main.

Example only:

```text
Main unique:   1/500
MiniMe unique: 1/2,500 to 1/5,000
```

Exact rates must be configured per reward class/content and balanced from actual economy data.

### Do Not Punish Every Drop

The rare penalty should not blindly multiply the rarity of every item a MiniMe receives.

MiniMes still need useful ordinary PvM output.

Possible normal or lightly adjusted categories:

- food
- bones
- herbs
- seeds
- ores
- common supplies
- low-value materials
- ordinary monster drops

Possible strongly reduced categories:

- boss weapons
- boss armor
- high-value uniques
- pets
- chase cosmetics when rarity matters
- other economy-sensitive rare drops

This lets MiniMes remain useful for PvM progression and supplies without turning five characters into an uncontrolled rare-item printer.

## Main-vs-MiniMe Reward Identity

The reward model should intentionally give the Main a reason to matter.

### Main strengths

- normal rare-drop rates
- primary chase-reward character
- main account identity
- no MiniMe rare penalty

### MiniMe strengths

- parallel progression
- skilling/resource work
- ordinary supply generation
- combat support
- squad tactics
- independent development
- optional rare jackpot at much lower odds

The goal is not "MiniMes are worse players." The goal is that parallelism itself has a cost when interacting with economy-sensitive rewards.

## Bossing With MiniMes

Long term, MiniMes could make unique PvM content possible.

Examples:

- assign a MiniMe to tank while directly controlling another character
- place ranged characters at separate positions
- split a squad across mechanics
- use one character to interact with encounter objects while others fight
- defend multiple locations
- group-healing/support roles
- encounters designed around positioning several owned characters

Existing RuneScape bosses should not automatically be redesigned around MiniMes. MiniMe-specific or MiniMe-enhanced encounters can be added when content proves the need.

## Death and Risk

MiniMes should not be disposable no-risk workers.

Their death rules need to remain meaningful.

Open questions for later design:

- Do they use normal death/reclaim behavior?
- Can a dead MiniMe be revived by the Main in certain content?
- What happens if the directly controlled character dies while other MiniMes remain alive?
- Can control immediately transfer to another owned character?
- What happens to autonomous tasks when the Main/logged owner disconnects?

Default safe direction: no unattended online progression after the owner leaves the game unless a future feature explicitly permits it.

## Logout / Disconnect Rule

MiniMes are intended to perform parallel tasks **while their owner is actively playing**.

They should not automatically become 24/7 offline workers.

Initial design direction:

- owner logs in -> available MiniMes may be activated
- owner logs out/disconnects -> active MiniMes stop and are safely persisted/despawned

Offline progression would be a separate feature requiring separate balance approval.

## Anti-Abuse Direction

The system should prevent MiniMes from becoming an in-game automation API that can be exploited as a general bot scripting platform.

Likely principles:

- only supported high-level actions
- server-owned validation
- normal skill/content requirements
- normal movement/clipping constraints
- bounded targeting/action ranges
- no arbitrary user scripting
- no packet/client duplication requirement
- no hidden action path that bypasses Matrix3 content authority

Every MiniMe action should ultimately be validated by the same authoritative gameplay rules a normal character would face wherever practical.

## Performance Direction

The old 718 project demonstrated useful NPC/player-bot ideas, but it is reference material only.

The Matrix3 implementation should not require literally running six full clients and six normal network sessions for one owner.

Preferred architectural goal:

- one connected owner/client session
- multiple persisted owned character states
- lightweight server-side active MiniMe entities/controllers
- reuse normal Matrix3 gameplay systems where safe
- send only the required update state to the owning client and nearby players

This is a hypothesis-level architecture until Matrix3's player/NPC/update/persistence ownership is scanned specifically for this feature.

Do not port the 718 bot subsystem wholesale.

## Multiplayer Visibility

Other players should be able to see MiniMes as actual world characters/entities.

They should have clear ownership identity so players understand who they belong to.

Possible presentation:

```text
MiniMe Name
Owner: PlayerName
```

or another cleaner visual indicator.

We should avoid excessive overhead text if five MiniMes are following many players in a crowded area.

## Naming / Customization

Future customization possibilities:

- player-chosen MiniMe names
- role labels
- appearance customization
- gender/body appearance
- gear exactly like normal characters
- saved combat/skilling presets
- formation position
- behavior preference

A MiniMe should feel like a character the player raised, not a generic worker slot.

## Roles Are Player-Created, Not Hard Classes

The system should not require fixed classes such as "Miner MiniMe" or "Tank MiniMe."

A player can naturally create roles through progression and gear.

Example:

- one MiniMe becomes the best miner because the owner trained it heavily in Mining
- one becomes a tank through Defence and equipment
- one becomes ranged-focused
- one becomes a general skiller

RuneScape's open progression should remain intact.

## Formations and Group Commands — Future

Possible later additions:

- line formation
- wedge formation
- loose spread
- surround target
- stay near Main
- maintain distance
- protect selected character
- focus target
- disengage

These are later tactical improvements, not requirements for the first playable slice.

## Potential PvP Direction

MiniMe PvP could be extremely interesting but also extremely difficult to balance.

Do not enable unrestricted multi-character PvP by default without a dedicated design.

Potential future content:

- MiniMe squad arenas
- owner-vs-owner squad fights
- territory events
- controlled team battles
- special PvP modes with roster limits

Normal wilderness/player combat would need explicit rules about whether MiniMes can participate and how many can attack one player.

## Why This Could Be a Server Identity Feature

Most RSPS projects compete through familiar content, XP rates, bosses, custom items, or revision differences.

This system changes the moment-to-moment relationship with RuneScape itself while keeping RuneScape progression recognizable.

A player could truthfully describe the server as:

> "RuneScape, except you build and control your own squad of characters while every character still trains normally."

That is immediately understandable and visually marketable.

A full-sized Main walking through the world with several separately geared small MiniMes could become a recognizable visual identity for the project.

## Suggested First Playable Slice

Do **not** start by implementing all five MiniMes, RTS selection boxes, boss logic, every skill, formations, and custom currency at once.

A safe first proof should be extremely small.

### Phase 0 — Architecture Scan

Before code:

- identify Matrix3 player persistence authority
- identify active player/world entity ownership
- identify player update/render path relevant to another player-like entity
- identify existing free-camera ownership
- identify safest character switching boundary
- inspect the old 718 bot system only for lessons after Matrix3 ownership is established

Output should classify findings as VERIFIED / verified-static / HYPOTHESIS.

### Phase 1 — One MiniMe Exists

Goal:

One owner can activate one persisted MiniMe.

Minimum proof:

- one MiniMe record under owner profile
- MiniMe appears in world
- smaller visual model if safe
- persisted position/basic identity
- no autonomous skilling yet
- no RTS controls yet

### Phase 2 — Direct Control Switching

Goal:

Player can switch between Main and one MiniMe.

Prove:

- direct movement
- inventory ownership
- equipment ownership
- stat ownership
- clean switch back to Main
- save/relog stability

This phase is crucial. If direct control cannot be made cleanly Matrix3-native, stop and redesign before adding automation.

### Phase 3 — One Autonomous Skill

Use one skill as the vertical proof, likely Mining or Woodcutting.

Goal:

- command MiniMe to perform one valid skilling action
- MiniMe uses its own level/tool/inventory
- MiniMe gains its own XP
- Main remains directly playable at the same time

Do not add every skill until this loop is proven.

### Phase 4 — Basic Command View

Add:

- select MiniMe
- move command
- gather command for the proven skill
- follow
- stop
- Take Control

Reuse the existing free-camera direction only if it is confirmed to be a clean foundation.

### Phase 5 — Roster and Unlock Progression

Add:

- MiniMe roster UI
- unlock currency
- first additional slot progression
- naming/customization basics

Only after the character/control loop is proven should slot economy be finalized.

### Phase 6 — Combat Proof

Add one simple supported combat path.

Prove:

- autonomous attack order
- own stats/equipment
- normal clipping/movement
- normal damage ownership
- loot attribution
- Main + MiniMe simultaneous combat without corrupting normal combat state

### Phase 7 — Rare-Drop Protection

Add a configurable reward classification / multiplier mechanism for economy-sensitive MiniMe drops.

Do not hardcode blanket x10 logic into every drop path if a cleaner Matrix3-owned reward boundary exists.

### Phase 8 — Expand Skills / Commands

Add additional skills and higher-level command UX only after the first complete vertical loops are stable.

### Phase 9 — Multi-MiniMe Squad

Increase from one MiniMe to multiple active MiniMes.

Test performance and update load before committing to the target cap of five.

### Phase 10 — Tactical / Custom Content

Only after normal gameplay works:

- formations
- multi-position encounters
- squad bosses
- MiniMe-specific activities
- optional squad PvP
- advanced automation rules

## Non-Goals for the Initial Implementation

The first implementation should **not** attempt to provide:

- five active MiniMes immediately
- every RuneScape skill
- every boss
- custom base building
- offline 24/7 resource farming
- arbitrary automation scripting
- complete StarCraft/Halo Wars controls
- squad PvP
- complex formations
- replacement of Matrix3 player/combat/skilling engines

## Important Open Questions

These should be answered only when implementation planning begins:

1. What Matrix3 entity type can safely represent a player-equipped MiniMe without duplicating full network sessions?
2. Should MiniMes share the owner's bank or have separate banks?
3. How should quest/content unlocks be divided between account-wide and character-specific progression?
4. How should Slayer tasks work across the roster?
5. How should XP rates compare with the Main?
6. Which drops receive MiniMe rarity penalties, and where is that classification owned?
7. What happens to loot ownership when several owned characters kill the same NPC?
8. How is direct control transferred without confusing interfaces, action state, camera, or client-side player identity?
9. How many MiniMes can be active simultaneously before update/load costs become meaningful?
10. How should crowded areas render ownership indicators cleanly?
11. Which skills are safe for autonomous repeat actions and which require direct interaction?
12. What is the final MiniMe unlock currency and acquisition curve?
13. Should all five unlocked MiniMes always be activatable, or can content enforce smaller squad caps?

## Design Rules to Preserve

1. **RuneScape progression remains the foundation.**
2. **Each MiniMe progresses independently.**
3. **The Main keeps normal rare-drop rules.**
4. **Parallel MiniMe PvM must not flood rare rewards.**
5. **The first MiniMe should be obtainable early enough for players to experience the feature.**
6. **MiniMes are characters, not disposable troops.**
7. **Direct control must remain available.**
8. **Autonomy must use server-validated game actions, not unrestricted scripting.**
9. **No offline farming by default.**
10. **Matrix3 remains the gameplay authority.**
11. **The 718 bot system is reference material only.**
12. **Build one vertical slice at a time.**
13. **Do not let the feature force broad rewrites of working Matrix3 systems without evidence.**

## Current Working Summary

The intended server experience is:

> Start with one normal Main. Progress through RuneScape normally. Unlock MiniMes through gameplay. Each MiniMe begins and progresses as its own character. Train them, gear them, name them, assign them work, directly control any one of them, or use a command/free-camera view to manage several at once. MiniMes can skill and fight in parallel while the player actively plays another owned character. The Main retains normal valuable PvM drop rates, while MiniMes receive strong rarity penalties on economy-sensitive uniques so parallel progression does not destroy the item economy.

If implemented cleanly, the MiniMe system should be treated as a potential core identity feature of the server rather than a disposable side minigame.
