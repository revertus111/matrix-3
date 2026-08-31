# Matrix3 BossLabs

## Purpose

BossLabs is the Matrix3 developer tool for building, configuring, testing, and iterating custom boss encounters.

Its first job is not to become a general-purpose NPC editor or a giant visual scripting system. Its first job is to help produce one complete custom Matrix3 boss end-to-end, then turn the proven needs of that boss into a reusable encounter framework.

BossLabs is a **developer tool**. Matrix3 remains authoritative for NPC lifecycle, combat, movement, pathing, world state, permissions, persistence, drops, and all other gameplay behavior.

## Authorities

BossLabs must follow:

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/client-console/CLIENT_CONSOLE.md`

The Client Console visual/lifecycle contract applies to BossLabs where relevant, including:

- permanent intentional dark presentation,
- no default unfinished Swing appearance,
- responsive layouts with no cut-off controls,
- usable non-maximized window sizes,
- DPI/display-scaling tolerance,
- safe minimum sizes,
- validated/restorable window geometry,
- quiet/coalesced preference writes,
- correct Swing/game-thread ownership,
- clean keyboard/mouse focus handoff,
- lazy or deferred expensive work,
- failure isolation where practical.

## Ownership rule

BossLabs must never become a second boss/combat engine.

Conceptually:

```text
BossLabs UI
   |
   v
Boss/Encounter Content API
   |
   v
Matrix3 NPC + Combat + World Authorities
```

BossLabs may create/edit boss content definitions and invoke documented testing/development bridges. It must not duplicate authoritative combat calculations, NPC pathing, drops, command permissions, persistence, or world lifecycle inside the client tool.

No system ownership changes are introduced by the initial BossLabs project.

## Tool placement

Boss/encounter tooling is a specialist tool and should be implemented as an **external developer window**, not forced into the narrow Client Console sidebar.

The Client Console may later provide a small `Open BossLabs` launcher action once BossLabs has a runnable shell.

BossLabs should reuse/shared-call Client Console presentation primitives where practical instead of inventing an unrelated visual system.

## First-boss rule

The first complete custom boss is the BossLabs specification.

Do not attempt to predict every mechanic every future boss may need.

The first boss should intentionally require enough variety to prove the reusable framework:

- multiple combat styles,
- projectile attack,
- area attack,
- phase transitions,
- environmental hazard,
- alternate/random targeting,
- minion spawn,
- timed mechanic,
- enrage/final phase,
- death/cleanup,
- drops,
- targeted developer testing controls.

A working boss is more important than a feature-complete editor.

## Proposed first boss

Working concept: **Volcanic Warden**.

This name and exact content are not permanent authority; the encounter exists to define the first vertical slice.

### Phase 1

- Basic melee attack.
- Fire projectile attack.

### Phase 2

- Ground-slam area attack.
- Burning-floor hazard.
- Random-target fireball.

### Phase 3

- Enrage behavior.
- Minion wave.
- Meteor attack.
- Faster attack cadence.

The first boss should force BossLabs/runtime work to solve phases, projectiles, AoE, hazards, minions, targeting, timers, transitions, cleanup, and drops without building unrelated systems.

## BossLabs V1 layout

BossLabs is expected to use tabs/sections rather than one wall of controls.

Initial sections:

1. Identity
2. Stats
3. Attacks
4. Phases
5. Mechanics
6. Arena
7. Drops
8. Testing

The exact Swing class/layout structure is not pre-approved here. It must be chosen from the smallest structure that follows the Client Console UI contract.

## Identity

Candidate fields:

- boss name,
- NPC ID,
- combat level where applicable,
- size,
- maximum hitpoints,
- spawn position,
- plane,
- respawn time,
- aggression range,
- leash range,
- examine text where supported,
- idle/walk/death animation references where supported.

Only expose fields that are verified to have a stable Matrix3 owner/path.

NPC selection/search behavior is refined by `docs/bosslabs/NPC_SEARCH.md`:

- one search field,
- numeric input means NPC id,
- text input means NPC name search,
- no ID/name mode dropdown,
- existing NPC/boss data and current combat ownership auto-populate from Matrix3 authorities.

## Stats

Candidate editable/viewable values include:

- hitpoints,
- attack,
- strength,
- defence,
- magic,
- ranged,
- style-specific defence values where Matrix3 supports them,
- attack speed,
- attack distance,
- maximum hit,
- damage cap,
- poison/stun/bind/freeze or similar immunities where supported.

Do not invent stat semantics that Matrix3 does not actually use.

## Attack definitions

Boss attacks should become reusable content definitions rather than one giant boss-specific combat class where practical.

Candidate attack data:

- stable attack identifier/name,
- combat style,
- animation,
- graphic,
- projectile,
- attack range,
- cooldown/tick cadence,
- target selection,
- minimum/maximum damage or references to authoritative damage behavior,
- accuracy behavior where supported,
- phase/condition requirements,
- attached mechanic/action list.

Complex attacks may still require custom Java behavior. BossLabs must never prevent a boss from extending into custom Java when a definition is not expressive enough.

## Phases

A boss may have ordered phase definitions such as:

```text
Phase 1: 100% -> 70%
Phase 2:  70% -> 30%
Phase 3:  30% -> 0%
```

Candidate phase data:

- entry condition,
- exit/transition condition,
- attacks enabled during phase,
- on-enter actions,
- on-exit actions,
- attack-speed/cadence modifiers where supported,
- damage/defence modifiers where supported,
- mechanic activation/deactivation,
- phase message/animation/graphic hooks.

Phase transition ownership belongs to the boss/encounter runtime, not the Swing UI.

## Mechanics and actions

Reusable mechanics should be added because the first boss needs them.

Candidate actions:

- spawn NPC,
- spawn/remove object where safe,
- launch projectile,
- play graphic,
- play animation,
- apply damage through the authoritative combat/damage path,
- apply verified status effect,
- teleport player,
- teleport boss,
- knockback,
- pull player,
- heal boss,
- enable/disable shield state,
- create/remove hazard,
- send encounter message,
- change boss form where supported.

Do not implement this entire list up front.

## Trigger model

A later reusable trigger model should remain intentionally small:

```text
WHEN / EVERY
  -> conditions
  -> actions
```

Examples:

```text
WHEN boss HP <= 50%
DO spawn minions
```

```text
EVERY 20 ticks
DO use Meteor
```

```text
WHEN player enters active hazard
DO apply hazard effect
```

A general-purpose scripting language or large node editor is explicitly not required for the first boss.

## Arena

V1 arena support should describe encounter boundaries and named positions, not become a map editor.

Candidate values:

- southwest boundary,
- northeast boundary,
- plane,
- boss spawn,
- player entry/exit,
- named minion spawns,
- named hazard/mechanic positions.

A future in-game coordinate capture bridge may be added when repeated manual coordinate entry becomes a real development pain point.

## Drops

BossLabs should edit/reference boss-specific drop configuration through Matrix3's existing drop authority when a stable integration path is verified.

Do not create a second independent drop engine.

Candidate presentation:

- guaranteed,
- common,
- uncommon,
- rare,
- very rare,
- pet/special entries,
- amount ranges,
- chance/weight representation matching Matrix3's actual drop system.

## Testing controls

Testing is a first-class BossLabs responsibility because fast iteration is the reason for the tool.

Candidate development actions:

- spawn boss,
- teleport to boss,
- reset encounter,
- set boss HP to common thresholds,
- force phase,
- force attack,
- kill boss,
- clear minions,
- clear hazards,
- inspect current phase/target/HP,
- inspect last attack,
- inspect active mechanics/timers.

All state-changing actions must route through safe Matrix3 server/client development paths. BossLabs must not directly mutate authoritative gameplay state merely because the UI can see it.

## Definition/runtime separation

Preferred responsibility model:

```text
BossLabs
   -> edits definitions

BossDefinition
BossAttackDefinition
BossPhaseDefinition
BossTrigger/Condition/Action definitions
   -> consumed by

Boss/Encounter runtime
   -> calls

Matrix3 combat/NPC/world/drop authorities
```

This is a conceptual responsibility model. The current V1 implementation uses `BossDefinition`, `BossPhaseDefinition`, `BossAttackDefinition`, `BossDefinitionRegistry`, and `BossCombatScript` for the proven portion of that model.

## Storage direction

The storage scan verified Matrix3's existing convention of dedicated `data/...` content locations with explicit Java loader/writer ownership, including NPC combat/drop/examine data.

BossLabs V1 therefore uses its own versioned binary definition store:

```text
Server/data/bosslabs/definitions.bld
```

`BossDefinitionStore` owns only BossLabs definition serialization. It must not replace or rewrite Matrix3's existing NPC combat-definition, spawn, examine, or drop files.

Saved BossLabs definitions load into `BossDefinitionRegistry` during combat-script initialization before live NPC combat uses them.

Do not add JSON or another parser/dependency merely for convenience unless a later content need justifies a deliberate storage migration.

## Live editing and publishing

The detailed contract is `docs/bosslabs/LIVE_EDITING.md`.

BossLabs must distinguish three states:

```text
DRAFT
LIVE
SAVED
```

- Draft edits remain local to the future tool until explicitly applied.
- `Apply Live` publishes one complete immutable definition through `BossDefinitionPublisher` without writing to disk.
- `Save & Apply` persists first; only a successful save publishes that exact definition live.
- `Undo Last Apply` restores the immediately previous live registration state, including restoring normal Matrix3 Java/default combat when the NPC was not previously BossLabs-owned.
- `Apply Saved` may republish the last persisted definition without rewriting it.
- Do not publish every keystroke.
- Definition-backed combat changes may take effect on subsequent attack/phase resolution without recreating the NPC.
- Identity/cache/world-instance changes may require an explicit controlled respawn rather than unsafe live mutation.

## Custom Java escape hatch

BossLabs must support the principle:

```text
Common/medium boss behavior
   -> reusable definitions/framework

Encounter-specific mechanic that does not fit cleanly
   -> small custom Java extension
```

Do not make the editor/framework so rigid that custom encounters become harder to implement.

## Threading and focus

BossLabs must follow the Client Console lifecycle contract:

- Swing mutations on the Swing event-dispatch thread.
- Slow file/content loading off the EDT.
- Game/server state changes through established Matrix3 owner threads/bridges.
- No heavy cache or world scans on UI/game threads.
- BossLabs text fields must not leak W/A/S/D or other developer typing into game movement/hotkeys.
- Returning focus to the game must restore normal game input cleanly.
- Boss definition persistence must not block the Swing EDT.

The exact client/server development bridge must be verified before the external window invokes runtime actions.

## Window behavior

BossLabs should be a resizable external window with:

- intentional dark styling consistent with the Client Console,
- scroll/reflow rather than clipped controls,
- practical minimum size,
- safe geometry restore/clamping,
- DPI tolerance,
- no requirement for maximized/fullscreen mode,
- clean close/reopen behavior,
- no continuous disk writes while dragging/resizing.

A standalone BossLabs preference file should not be added casually. Prefer the Client Console's shared/versioned settings authority if it can safely own external-tool geometry, or extend that authority deliberately when the implementation scan reaches persistence.

## V1 exclusions

Do not build these before the first boss proves a need:

- giant node editor,
- full NPC editor,
- full map editor,
- model editor,
- animation editor,
- particle/FX editor duplication,
- general-purpose scripting language,
- universal content editor,
- hundreds of speculative mechanics,
- alternate combat engine,
- alternate drop engine,
- multi-level definition version-control/history system.

BossLabs may later link/open specialist tools rather than duplicating them.

## Implementation sequence

### Phase 0 - authority and architecture

- Create `docs/bosslabs/` authority.
- Lock BossLabs to Matrix3 ownership and the Client Console UI/lifecycle contract.
- Define first-boss-driven scope.

Exit: the project has one written BossLabs direction before code is added.

### Phase 1 - Matrix3 boss/runtime scan

Inspect only the Matrix3 files directly owning:

- NPC combat behavior,
- NPC definitions/stats needed by the first boss,
- NPC spawning/lifecycle,
- drops,
- existing boss/custom-NPC examples if directly relevant,
- safe developer command/bridge paths needed for testing.

Classify findings as `VERIFIED`, `verified-static`, or `HYPOTHESIS`.

Do not inspect the old 718 project unless a specific BossLabs workflow question needs reference material.

Exit: exact runtime owners and minimum extension points are identified.

### Phase 2 - first boss runtime foundation

Implement the smallest reusable runtime needed for the first boss.

Start with manually-defined content if necessary before introducing editor UI.

The proven Phase 2 support may include definition registration, live replacement/rollback, persistent BossLabs definition storage, and discovery/inspection APIs because those directly support first-boss iteration without taking gameplay ownership.

Exit: one boss can spawn and execute a minimal verified phase/attack loop without BossLabs UI ownership of combat.

### Phase 3 - BossLabs external shell

Add the external BossLabs window using the Client Console visual/lifecycle rules.

Initial UI should remain small:

- one-field NPC search/selector,
- Identity,
- Stats,
- Attacks,
- Phases,
- Testing,
- Apply Live / Save & Apply / Undo Last Apply controls.

Mechanics/Arena/Drops may begin as narrow sections/placeholders only when the first boss actually reaches them.

Exit: BossLabs opens safely, resizes without clipping, respects focus/threading, can edit/view the first proven definition fields, and can publish through the approved server-side APIs.

### Phase 4 - first boss complete vertical slice

Drive BossLabs/runtime development from the Volcanic Warden encounter requirements.

Add only the reusable mechanics needed to finish that encounter.

Exit: the full boss can be configured, spawned, fought, reset, phase-tested, killed, cleaned up, and rewarded through Matrix3-owned systems.

### Phase 5 - framework generalization

After boss #1 is runtime-tested, identify which behavior is truly reusable and extract only those parts into stable definitions/components.

Exit: boss #2 can reuse the framework without copying boss #1's encounter class.

### Phase 6 - polish driven by actual use

Possible later additions:

- model/NPC preview,
- animation/GFX/projectile lookup/preview,
- in-game coordinate capture,
- visual attack timeline,
- stronger validation/error reporting,
- drag/drop mechanic composition,
- node graph only if linear trigger/action editing becomes a verified limitation.

## Current classification

### verified-static

- Matrix3 project rules require tooling to remain subordinate to Matrix3 gameplay/core ownership.
- The Client Console authority explicitly allows boss/encounter tooling as an external specialist window.
- The Client Console requires dark theme, responsive/no-cutoff layout, safe focus/threading behavior, lazy/heavy-work discipline, and validated workspace behavior.
- The project constitution identifies one complete custom boss as the first major content milestone.
- Matrix3 combat dispatch and BossLabs runtime extension points are identified and implemented without replacing `NPCCombat` ownership.
- Matrix3 cache/NPC definition boundaries support automatic NPC ID/name discovery and read-only existing-boss inspection.
- BossLabs V1 storage uses a dedicated versioned binary store under `data/bosslabs` and does not modify existing NPC packed data.
- BossLabs live publishing supports explicit Apply Live, one-level rollback, Save & Apply ordering, and startup reload of saved definitions.

### HYPOTHESIS / not yet runtime-verified

- Exact first custom NPC id/model/animations/GFX/projectiles for Volcanic Warden.
- Exact client-to-server development bridge used by the external BossLabs window for live apply/save/spawn/reset/force-phase requests.
- Whether the first encounter should use the existing BossInstance path; inspect it only if the first boss actually needs instancing.

These items remain unverified until the next narrow runtime/content slices establish them.
