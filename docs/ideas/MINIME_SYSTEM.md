# MiniMe Multi-Character Gameplay System

## Status

IDEA / DESIGN PLAN ONLY — not an approved implementation task yet.

This document captures the intended gameplay identity, progression rules, automation model, control model, balancing direction, retention loop, anti-abuse philosophy, and implementation acceptance checklist for a MiniMe system in Matrix3.

The central idea is **not** to build a separate traditional RTS game mode.

The stronger direction is to make multi-character control part of normal RuneScape gameplay and potentially one of the server's defining features.

## Core Pitch

A player has one normal Main character and can eventually unlock up to five additional independently progressing characters called **MiniMes**.

Each MiniMe is a real playable character under the same owner profile:

- its own combat stats
- its own skilling stats
- its own equipment
- its own inventory
- its own XP and progression
- its own combat role/build
- its own name/identity
- direct player control when desired
- autonomous orders while the player controls someone else

The Main remains the primary character. MiniMes expand what the player can do without replacing normal RuneScape progression.

The result should feel like:

**RuneScape progression + squad management + intentional automation + optional RTS-style control.**

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

A core player story should eventually be possible:

> "I finally maxed my first MiniMe. Time to finish the rest."

That is an intentional long-term retention loop, not an accidental side effect.

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
- Visual scale should not automatically imply smaller collision/pathing rules.
- Gameplay size should remain Matrix3-safe unless a later verified design requires otherwise.

The visual difference should make a Main followed by several equipped MiniMes immediately recognizable as a signature server feature.

A fully geared Main walking around with five smaller, separately trained characters should be a visual flex in itself.

### Independent Progression

MiniMes do **not** inherit the Main's levels.

If the Main has 99 Mining, a newly unlocked MiniMe does not receive 99 Mining. The MiniMe begins from its own starting progression and must train Mining itself.

That applies to:

- combat skills
- gathering skills
- production skills
- Slayer or equivalent character-specific progression where appropriate
- equipment requirements
- content requirements when character-specific progression makes sense

The point is to raise and develop each MiniMe, not buy an instant maxed worker.

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

### Direct Control

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

### Command / RTS Control

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

### Quick Character Switching

The player should have a fast way to switch direct control between owned characters without navigating a cumbersome menu.

Possible future UX:

```text
[Main] [M1] [M2] [M3] [M4] [M5]
```

Each slot could eventually show:

- health
- current task
- combat state
- inventory warning/full state
- current skill/action
- distance/status
- total level or progression marker

Exact UI belongs to later implementation design.

## Intentional Automation

MiniMe automation is not a loophole to reluctantly tolerate. It is part of the feature.

The server is intentionally giving players a controlled form of automation that people commonly try to create through multilogging, macros, or bot clients.

The design goal is:

> **Build the useful automation into the game, make it fun, and keep it inside server-owned rules.**

Examples of intended behavior:

- send a MiniMe mining while directly playing the Main
- leave another MiniMe chopping trees
- have another fishing supplies
- assign a combat-capable MiniMe to a supported PvM activity
- switch direct control to any of them at any time
- command several characters together when desired

A MiniMe performing an officially supported autonomous action is **not abuse**. It is using the system as designed.

Do not add captchas, random interruptions, artificial misclicks, or other anti-bot friction to punish players for using official MiniMe automation.

The fun should come from deciding how to use the roster efficiently, not fighting the interface.

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

Autonomy must still remain bounded by server-owned game rules. MiniMes should not become unrestricted programmable macro clients.

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

A shared owner bank is currently attractive because it keeps the roster feeling like one progression profile and avoids needless item-transfer friction. This must be checked against economy, persistence, and content assumptions before becoming a final rule.

Direct item transfer between the Main and MiniMes should be designed intentionally rather than accidentally relying on player trading.

## Unlocking MiniMes

MiniMes should not be free command spawns, but they also should not require an absurd grind before the player experiences the server's signature feature.

The first MiniMe should be obtainable relatively early.

Later slots should represent increasing progression milestones.

| Slot | Intended Pace |
| --- | --- |
| MiniMe #1 | Early / accessible introduction |
| MiniMe #2 | Moderate progression |
| MiniMe #3 | Mid progression |
| MiniMe #4 | Significant progression |
| MiniMe #5 | Long-term account goal |

Exact costs must be balanced from actual game rates rather than guessed in advance.

The player should get enough access early to understand why MiniMes are special, then have meaningful reasons to keep playing to expand the roster.

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

## Long-Term Progression / Retention Loop

MiniMes intentionally multiply meaningful progression without simply multiplying forced grind.

A player can progress several characters in parallel, but each still has its own goals.

Possible long-term progression loop:

```text
Main -> established/maxed
MiniMe #1 -> maxed
MiniMe #2 -> progressing
MiniMe #3 -> specialist build
MiniMe #4 -> newly unlocked
MiniMe #5 -> locked
```

Potential roster completion tracking:

```text
MiniMe #1 - Maxed
MiniMe #2 - Maxed
MiniMe #3 - 2471 total
MiniMe #4 - 1822 total
MiniMe #5 - Locked
```

Possible future achievements/titles can celebrate the roster without making them mandatory:

- first MiniMe unlocked
- first MiniMe maxed
- multiple maxed MiniMes
- full five-MiniMe roster
- all MiniMes maxed
- specialist skill milestones
- squad PvM milestones

The player should feel attachment to specific MiniMes because they personally trained, geared, named, and developed them.

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

## Balance the Output, Not the Fun

A major design rule is:

> **Do not make MiniMes miserable to use just because parallel progression is powerful.**

If a player has earned five MiniMes, seeing all five working should feel powerful and rewarding.

Control economy-sensitive outputs where necessary instead of constantly weakening the core fantasy.

Examples:

- rare PvM multipliers
- content-specific squad caps
- reward classifications
- encounter-specific eligibility
- account-level reward limits only where proven necessary

Avoid arbitrary restrictions that make the signature feature feel fake.

## Main-vs-MiniMe Reward Identity

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
- What happens to autonomous tasks when the owner disconnects?

Default safe direction: no unattended online progression after the owner leaves the game unless a future feature explicitly permits it.

## Logout / Disconnect Rule

MiniMes are intended to perform parallel tasks **while their owner is actively playing**.

They should not automatically become 24/7 offline workers.

Initial design direction:

- owner logs in -> available MiniMes may be activated
- owner logs out/disconnects -> active MiniMes stop and are safely persisted/despawned

Offline progression would be a separate feature requiring separate balance approval.

## Mischief / "Getting Away With It" Player Feel

A useful part of the intended server identity is allowing players to feel clever, cheeky, or slightly outside the normal RuneScape rules.

Historically, part of the attraction of private servers has been discovering unusual efficiencies, hidden interactions, loophole-feeling mechanics, and things that feel more permissive than the official game.

MiniMes can intentionally preserve that feeling.

The design can encourage:

- discovering efficient MiniMe task combinations
- finding surprisingly strong but legitimate squad strategies
- sharing hidden-feeling optimizations
- unusual progression routes
- playful achievements/messages around "unauthorized" workers or multilogging-style behavior
- systems that feel permissive without actually bypassing server authority

The important boundary is that published enforcement should still be understandable and consistent. The game can make players feel mischievous without requiring arbitrary punishment or misleading players about actions that are intentionally supported.

## Anti-Abuse Philosophy

MiniMes create an intentional, server-owned form of bot-like automation.

That changes what anti-bot enforcement should care about.

### Allowed by design

- official MiniMe autonomous skilling
- official MiniMe combat orders
- official follow/move/gather commands
- managing several MiniMes simultaneously
- optimizing the roster aggressively
- leaving supported tasks running while directly controlling another owned character

### Still outside the MiniMe system

Potentially disallowed external behavior can remain separate from the feature:

- external bot clients
- packet automation
- arbitrary macro/script engines that bypass supported commands
- attempts to exceed active MiniMe limits
- automation that bypasses game requirements
- client modifications used to create actions the server did not authorize

### Highest enforcement priority

The server should pick its battles.

The highest priority is behavior that harms the shared game or other players:

- dupes
- server nulling/crashing
- save corruption
- destructive packet abuse
- economy-breaking item generation
- bypassing reward restrictions
- interference with other players
- exploits that threaten world stability

A harmless player squeezing efficiency out of intended MiniMe mechanics is a different category from a player capable of ruining the world for everyone.

The system should be designed so these distinctions are technically visible where practical.

## Server Validation Rules

Likely principles:

- only supported high-level actions
- server-owned validation
- normal skill/content requirements
- normal movement/clipping constraints
- bounded targeting/action ranges
- no arbitrary MiniMe scripting API
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

Avoid excessive overhead text if five MiniMes are following many players in a crowded area.

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
- roster portraits/status

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

These are tactical improvements, not requirements for proving the core system.

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

Another simple pitch is:

> **Build your own RuneScape squad. Train them, gear them, command them, or take control of any one yourself.**

That is immediately understandable and visually marketable.

A full-sized Main walking through the world with several separately geared small MiniMes could become a recognizable visual identity for the project.

## Development Workflow — One Feature, Not Ten Approval Phases

Do not treat MiniMes as a chain of separate "Phase 1 / Phase 2 / Phase 3" projects requiring repeated approval every time one checkpoint is reached.

When implementation is actually approved, define the MiniMe implementation scope clearly and treat it as **one system task**.

A future `SAP AAA` for that defined task may cover the approved MiniMe build checklist as a whole.

The checklist still exists for stability, testing, and rollback discipline. It is **not** intended to create repetitive approval overhead.

Implementation should still use narrow commits/checkpoints where needed so regressions remain traceable, but development should continue through the approved checklist until the defined task is complete or new evidence requires stopping.

## Implementation Checklist

Before implementation, perform the smallest Matrix3 architecture scan necessary to establish ownership and classify findings as VERIFIED / verified-static / HYPOTHESIS.

Then work through the system checklist as one approved feature:

- [ ] identify player persistence authority
- [ ] identify active world entity ownership
- [ ] identify player update/render requirements for a player-equipped MiniMe
- [ ] identify free-camera ownership
- [ ] identify safest direct-control switching boundary
- [ ] inspect old 718 bot/player-agent code only after Matrix3 ownership is established
- [ ] persist one MiniMe under an owner profile
- [ ] spawn/despawn one MiniMe safely
- [ ] render MiniMe with intended smaller visual scale if safe
- [ ] preserve independent identity/stats/inventory/equipment
- [ ] switch direct control Main -> MiniMe -> Main cleanly
- [ ] verify save/relog stability
- [ ] support one autonomous skill as the first vertical proof
- [ ] verify Main remains directly playable during MiniMe work
- [ ] add basic move/follow/stop/task commands
- [ ] connect command/free-camera presentation if verified appropriate
- [ ] add fast roster/direct-control switching UX
- [ ] add MiniMe unlock progression/currency
- [ ] add naming/basic customization
- [ ] support one normal combat path
- [ ] verify damage/loot attribution
- [ ] add configurable economy-sensitive MiniMe drop penalties
- [ ] expand supported skilling actions
- [ ] expand supported combat/content actions
- [ ] increase from one to multiple active MiniMes
- [ ] performance-test multi-MiniMe update load
- [ ] validate target cap of five active MiniMes
- [ ] add roster progression/completion tracking
- [ ] add anti-abuse visibility around bypass attempts
- [ ] verify disconnect/despawn/persistence behavior
- [ ] run relevant Matrix3 smoke tests before considering the core system stable

Additional tactical features such as formations, squad bosses, specialized PvP, and advanced command UX can be added when the working MiniMe system gives them a real content need.

## Non-Goals for the Core Build

The MiniMe system does not need to become all of these things at once:

- custom RTS base building
- offline 24/7 resource farming
- arbitrary user automation scripting
- complete StarCraft/Halo Wars control parity
- unrestricted squad PvP
- complex formations before basic control works
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
14. Which progression is owner-wide versus character-specific?
15. What data should be logged to distinguish official MiniMe automation from bypass/exploit behavior?

## Design Rules to Preserve

1. **RuneScape progression remains the foundation.**
2. **Each MiniMe progresses independently.**
3. **The Main keeps normal rare-drop rules.**
4. **Parallel MiniMe PvM must not flood rare rewards.**
5. **The first MiniMe should be obtainable early enough for players to experience the feature.**
6. **MiniMes are characters, not disposable troops.**
7. **Direct control must remain available.**
8. **Official MiniMe automation is intentional gameplay.**
9. **Autonomy uses server-validated game actions, not unrestricted scripting.**
10. **Balance economy-sensitive outputs instead of nerfing the fun out of MiniMes.**
11. **No offline farming by default.**
12. **Let players discover strong efficiencies and feel clever without allowing destructive server abuse.**
13. **Prioritize preventing dupes, nulls/crashes, corruption, and economy-breaking bypasses over policing harmless intended efficiency.**
14. **Matrix3 remains the gameplay authority.**
15. **The 718 bot system is reference material only.**
16. **Treat implementation as one defined MiniMe feature with an acceptance checklist, not repetitive approval phases.**
17. **Use narrow implementation checkpoints/commits for stability without turning them into separate projects.**
18. **Do not let the feature force broad rewrites of working Matrix3 systems without evidence.**

## Current Working Summary

The intended server experience is:

> Start with one normal Main. Progress through RuneScape normally. Unlock MiniMes through gameplay. Each MiniMe begins and progresses as its own character. Train them, gear them, name them, assign them work, directly control any one of them, or use a command/free-camera view to manage several at once. Official MiniMe automation is a supported part of normal gameplay: one MiniMe can mine, another can chop, another can fish or fight, while the player directly controls another owned character. The Main retains normal valuable PvM drop rates, while MiniMes receive strong rarity penalties on economy-sensitive uniques so parallel progression does not destroy the item economy. Players are encouraged to optimize and feel clever with the system, while destructive exploits such as dupes, crashes, save corruption, and reward bypasses remain hard boundaries.

If implemented cleanly, the MiniMe system should be treated as a potential core identity feature of the server rather than a disposable side minigame.