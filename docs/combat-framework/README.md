# Combat Framework

## Purpose

`Combat Framework` is the reusable combat-extension layer for Matrix3.

It is intentionally **not** a PoE-specific combat system, RTS-specific combat system, or replacement for Matrix3 combat.

Matrix3 remains the authoritative base combat engine. Combat Framework adds optional, reusable modules that game modes, content, bosses, items, abilities, and future systems can opt into only when needed.

## Core Rule

> Capability -> Mode permission -> Build/content modifier -> Final combat result

Example: extra projectiles.

1. The framework knows **how** multiple projectiles work.
2. A game mode decides whether extra projectiles are allowed.
3. The player's build, item, ability, boss, or content rule decides whether any bonus projectiles are granted.
4. Combat resolves the final projectile count.

The existence of a module must never force every game mode to use it.

## Architecture

```text
Matrix3 Base Combat
        |
        v
Combat Framework
        |
        +-- Combat Profile / Mode Rules
        |
        +-- Enabled Modules
        |     +-- Modifiers
        |     +-- Critical Hits
        |     +-- Status Effects
        |     +-- Extra Projectiles
        |     +-- Chain / Pierce
        |     +-- Area Effects
        |     +-- Procs
        |     +-- Leech / On-hit Recovery
        |     +-- Conditional Effects
        |     +-- Attack-speed / Timing Modifiers
        |
        +-- Player / NPC / Item / Ability / Boss Contributions
        |
        v
Final Combat Event
```

## Design Principles

### 1. Matrix3 remains the authority

Combat Framework must extend Matrix3 combat through clean hooks and stable APIs.

Do not replace working Matrix3 combat simply to support framework features.

### 2. Modules are independent

Each major feature should be an isolated capability where practical.

Examples:

- critical hits
- status effects
- extra projectiles
- projectile chain
- projectile pierce
- splash / area damage
- life steal / recovery
- on-hit effects
- on-kill effects
- conditional bonuses
- attack-speed modifiers
- weapon-family modifiers
- ability modifiers
- boss resistances
- proc effects

A module should not silently require unrelated modules unless the dependency is genuinely necessary and documented.

### 3. Modes select capabilities

Game modes should use a `CombatProfile` or equivalent configuration to define which framework modules and rules are active.

Conceptual examples:

```text
STANDARD
- Base Matrix3 combat
- Optional limited framework features

POE_STYLE
- Modifiers
- Critical hits
- Status effects
- Extra projectiles
- Chain / Pierce
- Area effects
- Procs
- Leech
- Conditional effects

RTS
- Unit modifiers
- Area effects
- Status effects
- Optional projectile extensions

CUSTOM
- Any explicitly selected module combination
```

Profiles configure combat behavior; they do not own the implementation of combat mechanics.

### 4. Do not build a giant mode-specific combat class

Avoid designs such as:

```text
PoECombat.java
RTSCombat.java
CustomModeCombat.java
```

when those classes would duplicate or absorb reusable mechanics.

The preferred design is reusable combat modules plus small mode/profile configuration.

### 5. Separate capability from granted value

A module being enabled does not mean every player, NPC, item, or ability receives its effect.

Example:

```text
Extra-projectile capability: enabled by mode
Base projectiles:              1
Passive bonus:                +2
Weapon bonus:                 +1
Mode cap:                      8
Final projectile count:        4
```

This same rule applies to critical chance, attack speed, chain count, pierce count, status stacks, leech, proc chance, and similar mechanics.

### 6. Prefer data/configuration over hardcoding

Where practical, content should describe its combat contributions through structured definitions rather than boss-specific or item-specific condition chains.

Examples:

```text
+2 projectiles
+15% attack speed
+8% critical chance
On hit: apply burn
Below 30% HP: +25% damage
Every fifth attack: trigger area effect
Projectile chains once
Projectile pierces two targets
```

The framework owns how these mechanics resolve. Content owns which mechanics it requests.

## Shared Combat Context

Framework modules should eventually evaluate against one shared combat context rather than independently rebuilding combat state.

Conceptually, a combat context may expose only the information required by active modules, such as:

- attacker
- target
- attack style
- weapon / item
- ability
- base damage
- final damage
- projectile information
- active combat profile
- active modifiers
- status state
- hit index / attack sequence
- source content or boss mechanic

The exact implementation must follow Matrix3's existing combat architecture after source inspection. This document does not pre-approve replacing current Matrix3 ownership.

## Player Builds

A PoE-style build system should consume Combat Framework rather than become part of the framework itself.

The build system may contribute values such as:

- attack speed
- critical chance
- critical damage
- projectile count
- chain count
- pierce count
- area size
- bleed / poison / burn behavior
- life steal
- conditional damage
- weapon-family bonuses
- ability modifiers
- major passive / keystone-style rule changes

This allows the same RuneScape weapon or ability to support radically different builds without duplicating the underlying combat implementation.

## BossLabs Integration

BossLabs should eventually configure supported Combat Framework mechanics for NPCs and bosses instead of generating one-off combat spaghetti where a reusable mechanic already exists.

Example boss configuration:

```text
Attack:
- +3 projectiles
- chain once
- on hit: apply burn

Phase rule:
- below 25% HP: +30% attack speed

Sequence rule:
- every fifth attack: area explosion
```

Boss-specific mechanics that truly require custom logic can still exist, but reusable effects should use the shared framework.

## Tooling Integration

Future developer tools should discover and edit Combat Framework definitions through stable APIs/configuration rather than directly owning combat behavior.

Potential integrations:

- BossLabs
- item/action tooling
- ability tooling
- live developer mode
- combat debug inspector
- game-mode editor
- passive/build editor

A useful long-term debug view should be able to explain **why** a final combat result occurred, for example:

```text
Projectile count = 4
- Base: 1
- Passive: +2
- Weapon: +1
- Mode cap: 8
```

That traceability is important once modifier stacking becomes complex.

## Performance Rules

The framework will run on hot combat paths, so implementation must stay disciplined.

- Disabled modules should add negligible overhead.
- Avoid unnecessary allocations per hit/tick.
- Do not repeatedly scan every possible modifier when only a small active set is relevant.
- Cache stable build/profile state where safe.
- Preserve Matrix3 tick timing and server authority.
- Keep deterministic resolution where game behavior requires it.

## Safety / Maintainability Rules

- No unrelated rewrite of Matrix3 combat.
- No giant all-purpose combat manager.
- No duplicated implementation per game mode.
- No mode-name checks scattered throughout core combat.
- No content-specific hardcoding when a reusable rule already exists.
- No guessed architecture changes without first tracing the relevant Matrix3 combat path.
- Keep Java 8 / Eclipse compatibility.
- Build the framework in small vertical slices and runtime-test each slice before expanding it.

## Recommended First Vertical Slice

Do not build every module at once.

A strong first proof would be one small reusable modifier path, for example:

```text
Base attack
-> active Combat Profile
-> one numeric modifier
-> final combat value
-> debug explanation
```

After that path is proven cleanly inside Matrix3, add modules individually.

Suggested progression:

1. shared combat profile / feature gating
2. generic numeric modifier resolution
3. debug/explanation output
4. critical-hit module
5. status-effect foundation
6. projectile-extension foundation
7. proc/conditional system
8. broader PoE-style build integration
9. BossLabs exposure
10. additional mode-specific profiles

The exact implementation order may change once the existing Matrix3 combat path is inspected.

## End Goal

Combat Framework should make new combat styles feel radically different without creating separate combat engines.

The same base Matrix3 combat should be capable of supporting:

- standard RuneScape-style gameplay
- PoE-style buildcraft
- RTS gameplay
- custom bosses
- special events
- future experimental modes

by enabling only the modules each experience actually requires.
