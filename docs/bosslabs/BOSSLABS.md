# Matrix3 BossLabs

## Purpose

BossLabs is the Matrix3 content-creation tool for building, testing, and iterating custom boss encounters.

Its success is measured by how quickly a competent RSPS developer can open it, understand it, and create useful content without needing the BossLabs source code, implementation vocabulary, or another developer beside them.

BossLabs must be powerful, but normal boss creation must feel simple.

The target is not "an editor for BossDefinition fields." The target is a professional encounter-authoring workflow.

A developer with limited time should be able to:

1. find/select an NPC,
2. create or inspect the boss,
3. define phases,
4. add attacks and mechanics,
5. visually author attack patterns,
6. configure drops,
7. spawn and test the encounter,
8. iterate live,
9. save the finished content,

without manually managing internal identifiers or understanding BossLabs transport/storage/runtime implementation.

## Authorities

BossLabs remains subordinate to:

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/client-console/CLIENT_CONSOLE.md`
- `docs/bosslabs/LIVE_EDITING.md`
- `docs/bosslabs/NPC_SEARCH.md`

Matrix3 remains authoritative for NPC lifecycle, combat, movement, pathing, world state, permissions, persistence, drops, and other gameplay behavior.

BossLabs is a content-authoring and testing layer over those authorities. It must never become a second combat engine, NPC engine, drop engine, scheduler, or world implementation.

Conceptually:

```text
BossLabs creator UI
        |
        v
Boss/Encounter Content API
        |
        v
BossLabs definitions/runtime adapters
        |
        v
Matrix3 NPC + Combat + World + Drop authorities
```

## Primary product rule

**Custom content is the product. BossLabs exists to make custom content faster and easier to create.**

Do not expand BossLabs merely because another control, mechanic, tab, or abstraction could be added.

The first complete custom boss remains the specification for the reusable encounter framework.

When real content work exposes repeated friction, improve the workflow. When real content needs a mechanic, add the smallest reusable mechanic that solves it.

## Creator-first UX laws

These are mandatory design rules.

### 1. Never require information BossLabs already knows

If BossLabs already owns or can derive a value, relationship, selection, or identifier, the creator must not be required to manually re-enter it.

Examples:

- Testing a selected attack must not require typing its Attack ID again.
- Entering a selected phase must not require typing its Phase ID again.
- Selecting an NPC must populate known identity/stats/combat information automatically.
- Selecting an attack inside a phase must preserve the phase relationship automatically.
- Selecting a minion from an NPC browser must fill the NPC id internally.
- Choosing a saved pattern must carry its geometry without retyping offsets.

Manual raw entry may remain as a power-user shortcut where useful, but it must not be the normal workflow when BossLabs can make the selection itself.

### 2. Normal creation must not require BossLabs implementation knowledge

A creator should need RuneScape/boss-design knowledge, not knowledge of:

- `BossDefinition`,
- registry ownership,
- wire versions,
- storage versions,
- internal phase IDs,
- internal attack IDs,
- command bridge protocol,
- Java class names,
- serialization details.

Technical information may be available under Advanced/Developer details, but it must not dominate normal authoring.

### 3. Prefer selection and direct manipulation over memorization

Use:

- searchable selectors,
- cards,
- lists,
- visual patterns,
- previews,
- context-aware buttons,
- sensible defaults,

instead of requiring creators to remember IDs, formats, or relationships.

### 4. Show complexity only when it is needed

A basic melee attack should look basic.

Do not present every possible attack/mechanic field at once.

Use progressive disclosure: common settings first; advanced or mechanic-specific settings appear only when enabled or expanded.

### 5. Every common action should be obvious

A first-time user should be able to identify how to:

- select a boss,
- add a phase,
- add an attack,
- test the selected attack,
- apply changes live,
- save changes,
- reset a test encounter,

without reading external documentation.

Tooltips and inline help should explain unusual concepts, not compensate for confusing layout.

## First-time-user acceptance standard

BossLabs should be designed so a competent RSPS developer unfamiliar with BossLabs can perform the normal creation loop with minimal onboarding.

A useful target workflow is:

```text
Open BossLabs
  -> Search NPC
  -> Select/Create Boss
  -> Add Phase
  -> Add Attack
  -> Configure attack
  -> Test Selected Attack
  -> Adjust
  -> Apply Live
  -> Save & Apply
```

If this path feels like editing a configuration file through Swing controls, the UI needs improvement.

## Visual direction

BossLabs is an external specialist developer window using the Client Console visual system.

It must use:

- permanent intentional dark theme,
- clean modern hierarchy,
- clear selected/hover/focus/disabled states,
- consistent spacing and typography,
- cards/groups rather than undifferentiated control walls,
- responsive layouts,
- scroll/reflow/collapse instead of clipping,
- practical non-maximized sizes,
- 100%/125%/150% DPI tolerance,
- clean focus handoff to/from the game.

Default/unfinished Swing presentation is not acceptable as final BossLabs UI.

## Target BossLabs workspace

The preferred high-level navigation is creator-oriented:

```text
BOSSLABS

Boss: [ Search NPC by name or ID... ]

Overview | Combat | Phases | Attacks | Arena | Drops | Testing
```

Exact labels may evolve from runtime use, but navigation should describe creator tasks rather than implementation classes.

A permanent header/status area should make the current boss and authoring state obvious.

Example:

```text
Kerapac
NPC 19464

DRAFT: Modified   LIVE: Applied   SAVED: Older

[Apply Live] [Save & Apply] [Undo] [Apply Saved]
```

The creator should never wonder whether the screen represents draft-only, currently-live, or persisted content.

## Boss selection and overview

Use one search field governed by `NPC_SEARCH.md`:

- all digits -> NPC id,
- text -> NPC name,
- no ID/name mode dropdown.

Selecting an NPC must automatically populate all verified information BossLabs can safely inspect.

The normal Overview should emphasize useful content information:

- name,
- NPC id,
- combat level,
- size,
- hitpoints,
- current combat ownership,
- whether a BossLabs definition exists,
- phase count,
- attack count,
- drop configuration status,
- test readiness.

Implementation details such as resolved Java script class may remain available under an Advanced/Developer Details section.

## Internal IDs

BossLabs may continue using stable internal definition/phase/attack identifiers for storage/runtime references.

Normal creators should not have to manage them manually.

Preferred behavior:

- creator names attack `Meteor Strike`,
- BossLabs generates/maintains a stable internal identifier such as `meteor_strike`,
- creator renaming rules preserve references safely,
- internal ID can be inspected/overridden only in Advanced mode when genuinely needed.

The same principle applies to phases and other reusable content objects.

Internal identifiers are implementation necessities, not primary UX.

## Safe defaults

New content should begin valid whenever possible.

Example new attack defaults:

```text
New Attack
Style: Melee
Target: Current target
Damage: NPC default
Animation: NPC default
Weight: 1
Cooldown: none
Immediate repeat: allowed
Special mechanics: none
```

The creator should be able to create and test a simple attack without filling a long list of mandatory fields.

Defaults must be visible and predictable; never silently invent dangerous gameplay behavior.

## Combat / stats

BossLabs should expose editable combat/stat values only when a stable Matrix3 owner/path has been verified.

Useful values may include:

- hitpoints,
- attack/strength/defence,
- magic/ranged,
- style-specific defence where applicable,
- attack speed,
- attack distance,
- default max hit,
- aggression range,
- supported immunities,
- damage caps where Matrix3 supports them.

Read-only Matrix3 values should be visually distinguishable from BossLabs-owned editable overrides.

Do not present technical cache/combat fields as editable merely because they can be read.

## Phases

Phases should be presented visually and by boss-design meaning, not as raw ID/range rows.

Preferred presentation:

```text
PHASE 1
100% ---------------- 70%
2 attacks

PHASE 2
70% ----------------- 30%
3 attacks | 1 transition action

PHASE 3
30% ------------------ 0%
2 attacks | Enraged
```

Normal phase editing should focus on:

- display name,
- HP range/threshold,
- attacks available in the phase,
- On Enter actions,
- On Exit actions,
- proven phase modifiers/mechanics.

BossLabs should prevent/clearly flag gaps or overlaps that the runtime does not support.

A selected phase should provide context-aware actions such as:

- `+ Add Attack`,
- `+ Add On Enter Action`,
- `+ Add On Exit Action`,
- `Test / Enter This Phase`.

Testing the selected phase must never require retyping its internal ID.

## Attacks

Attack authoring is a central BossLabs workflow and must not become a giant form.

Each attack should be represented as a readable card/list entry.

Example:

```text
Meteor Strike
Magic | AoE | Weight 2 | Cooldown 3
```

Selecting the attack opens grouped/collapsible editing sections.

### Basic

Always-visible/common fields:

- display name,
- combat style,
- target mode,
- damage/max-hit behavior,
- range when relevant,
- combat delay/attack cadence when overridden.

### Rotation

Show when expanded or when non-default:

- weight,
- cooldown turns,
- immediate-repeat rule.

### Animation & FX

- animation,
- NPC graphic,
- projectile,
- warning graphic,
- impact graphic.

### Area / Pattern

Only show when an area mechanic is enabled:

- origin mode,
- telegraph duration,
- tile-pattern summary,
- `Edit Pattern` action.

### Lingering effect

Only show when enabled:

- effect type,
- GFX,
- duration,
- interval,
- amount/max-hit.

### Additional mechanics

Future mechanic-specific sections appear only when those mechanics are enabled.

Do not solve future extensibility by adding another permanent row to the attack form for every mechanic.

## Mechanics model

Prefer a bounded mechanic catalog over raw implementation fields.

Conceptual workflow:

```text
+ Add Mechanic

Damage Area
Ground Hazard
Spawn Minions
Heal Boss
Heal Players
Random Target
Projectile
...only mechanics actually supported/proven...
```

Choosing a mechanic reveals only settings required by that mechanic.

Reusable mechanics should compose cleanly when the runtime supports the combination.

BossLabs must not become a general-purpose scripting language or giant node editor merely to appear flexible.

Complex encounter behavior that does not fit the proven definition model must retain a custom-Java escape hatch.

## Asset selection and preview

Raw RuneScape IDs remain important and must remain usable.

However, raw ID entry should be the power-user shortcut, not the only practical workflow.

Where the existing cache/client architecture safely permits it, asset controls should evolve toward:

```text
Animation
[ Search / ID... ] [Preview]

Graphic
[ Search / ID... ] [Preview]

Projectile
[ Search / ID... ] [Preview]
```

Desired behavior:

- accept direct numeric ID immediately,
- search/browse when the creator does not know the ID,
- show enough identity/preview information to avoid repeated trial-and-error,
- link/open existing specialist tools rather than duplicating full AnimLab/FX/model editors inside BossLabs.

Do not add expensive cache scans to the Swing EDT.

## Attack Pattern Editor

The existing relative tile-pattern system remains valuable and should be preserved.

Attack Pattern authoring is distinct from Arena Layout authoring.

Attack Pattern describes **which tiles a mechanic affects relative to an origin**.

Supported/proven concepts include:

- target-centered origin,
- direct paint/erase,
- mouse-wheel zoom,
- middle-mouse pan,
- hover coordinates,
- cross,
- horizontal line,
- vertical line,
- filled square,
- ring,
- copy/paste pattern,
- warning/impact/hazard stages.

Future transforms such as rotate/mirror/facing orientation should be added only when real content needs them.

The pattern editor should eventually support concise staged/timeline preview:

```text
Ticks 0-2  Warning
Tick 3     Impact
Ticks 3-15 Fire floor
```

Preview is descriptive/testing UI only; Matrix3/BossLabs runtime remains timing authority.

## Arena Layout

Arena Layout describes **where encounter things exist in the world/encounter space**.

It must not be confused with the attack-pattern editor.

Potential proven arena content includes:

- arena bounds,
- boss spawn,
- player entry/exit,
- named mechanic anchors,
- minion spawn locations,
- safe zones,
- environmental hazard regions,
- object/mechanic spawn anchors.

Arena support remains encounter-focused. It is not a general RuneScape map editor.

The Arena and Attack Pattern tools may share grid/canvas technology, zoom/pan behavior, selection styling, and pattern helpers without merging their data semantics.

## Drops

Drops should become a creator-friendly editor over Matrix3's existing drop authority after the exact integration path is verified.

Preferred presentation should match Matrix3's real semantics while remaining understandable:

- guaranteed,
- common,
- uncommon,
- rare,
- very rare,
- pet/special where supported,
- quantity ranges,
- chance/weight.

Item selection should use name/ID search rather than requiring item IDs from memory when a shared item browser can provide the selection.

BossLabs must not create a second drop engine.

## Testing is a first-class workflow

Fast testing is one of the main reasons BossLabs exists.

Testing must be context-aware.

### Global encounter controls

Useful controls include:

- Spawn Boss Here,
- Reset Encounter,
- Set Boss HP %,
- Clear Hazards,
- Clear Minions,
- later Kill Boss/Teleport when an authoritative safe path is justified.

### Context actions

When a phase is selected:

```text
[Test / Enter Selected Phase]
```

When an attack is selected:

```text
[Test Selected Attack]
```

The selected phase/attack is passed internally. The creator does not type exact internal IDs that BossLabs already has.

### Encounter debugger/status

Testing should evolve toward useful runtime visibility such as:

```text
Boss HP: 43%
Current Phase: Phase 2
Current Target: PlayerName
Last Attack: Meteor Strike
Active Hazards: 8 tiles
Owned Minions: 3
```

Only expose values that can be read safely from authoritative runtime state.

Testing mutations remain admin-only and must continue targeting the exact per-admin controlled test NPC instance rather than arbitrary world NPCs.

## DRAFT / LIVE / SAVED

The existing live-editing architecture is retained.

```text
DRAFT
LIVE
SAVED
```

- Editing modifies DRAFT only.
- `Apply Live` publishes a complete validated immutable definition without disk persistence.
- `Save & Apply` persists successfully first, then publishes that exact definition.
- `Undo Last Apply` restores the prior live registration state.
- `Apply Saved` restores the persisted definition without rewriting it.
- Do not publish every mouse movement or keystroke.

The UI must explain these states visually in plain language.

The underlying implementation details remain governed by `LIVE_EDITING.md`.

## Validation and error design

Validation should prevent mistakes without making normal creation annoying.

Requirements:

- validate as locally/early as practical,
- preserve server-side constructors/runtime as final validation authority,
- identify the exact bad phase/attack/mechanic,
- explain the fix in plain language,
- never fail with only a generic "invalid definition" message when a specific cause is known,
- avoid modal-dialog spam for common validation,
- keep invalid edits in DRAFT rather than partially publishing them.

Example:

```text
Phase 2 overlaps Phase 1 at 70% HP.
Change one boundary before applying.
```

is preferable to:

```text
Invalid phase range.
```

## Advanced mode

BossLabs should support power users without forcing power-user complexity on everyone.

An `Advanced` or `Developer Details` area may expose verified technical information such as:

- internal definition ID,
- internal phase/attack IDs,
- Matrix3 combat source,
- resolved script class,
- raw animation/GFX/projectile values,
- unusual override controls,
- debugging/ownership information.

Advanced mode must not become a dumping ground for fields that should have a proper normal UX.

## Search, shortcuts, and repeated work

Common repeated work should become faster over time.

High-value creator conveniences include:

- duplicate attack,
- duplicate phase,
- copy/paste attack pattern,
- reusable named patterns,
- recently-used assets,
- searchable NPC/item/asset selectors,
- context-aware test buttons,
- keyboard shortcuts only where discoverable and safe.

Add these when real use shows they remove repeated effort.

## Custom Java escape hatch

BossLabs definitions should handle common and medium-complexity encounter behavior cleanly.

For behavior that does not fit without twisting the framework:

```text
Reusable/common behavior
    -> BossLabs definitions/runtime

Encounter-specific unusual behavior
    -> small custom Java extension
```

Do not make BossLabs so rigid that a unique boss becomes harder to implement.

A custom Java extension should integrate with the same encounter/runtime ownership rules rather than creating hidden alternate combat systems.

## Threading and responsiveness

BossLabs follows the Client Console lifecycle contract.

- Swing mutation on the EDT.
- Slow cache/index/file work off the EDT.
- Server/world mutations through established Matrix3 owner threads/bridges.
- No file polling or alternate sockets.
- No heavy scans on render/game/packet threads.
- Text editing must not leak W/A/S/D/hotkeys into game controls.
- Returning focus to the game must restore normal input.
- Window/panel failures should remain isolated where practical.

## Window behavior and persistence

BossLabs must be usable without maximizing the window.

Required behavior:

- practical minimum size,
- scroll/reflow/collapse rather than clipped controls,
- resizable work areas where useful,
- safe geometry restore/clamping when persistence is added,
- no continuous preference writes while dragging/resizing,
- clean close/reopen lifecycle,
- no stale listeners retained by disposed windows.

Prefer the Client Console's versioned preference authority rather than ad-hoc BossLabs settings files when that authority is extended for external tools.

## UI implementation quality

Swing is an implementation toolkit, not the design.

Avoid:

- giant vertical forms,
- technical IDs as the primary labels,
- duplicate entry of already-selected values,
- tabs full of placeholder prose in the finished workflow,
- default-looking Swing controls,
- title-string/component-tree hacks as permanent panel composition,
- requiring exact internal identifiers for normal test actions,
- hundreds of always-visible fields,
- modal confirmation for ordinary editing.

Prefer:

- direct panel composition,
- reusable presentation components,
- attack/phase cards,
- collapsible sections,
- context actions,
- shared search selectors,
- responsive split panes/canvases,
- concise inline status/error feedback.

## Existing runtime foundation to preserve

The current BossLabs runtime work provides valuable foundations that the UX redesign should reuse rather than rewrite without evidence:

- Matrix3 CombatScript delegation for registered BossLabs NPC IDs,
- immutable BossDefinition/BossPhaseDefinition/BossAttackDefinition content,
- BossDefinitionRegistry live replacement/rollback,
- versioned BossDefinitionStore,
- BossDefinitionPublisher,
- DRAFT/LIVE/SAVED publishing,
- complete phase/attack wire round-tripping,
- weighted rotation/cooldowns/repeat behavior,
- current/random-nearby targeting,
- projectile/direct attacks,
- telegraphed relative tile attacks,
- lingering hazards,
- damage/heal player/boss tile effects,
- phase On Enter/On Exit actions,
- encounter runtime context and stale-task invalidation,
- encounter-owned minions,
- exact per-admin controlled test NPCs,
- basic encounter testing operations,
- reusable tile-pattern presets and large pattern workspace.

The redesign should simplify how creators use these capabilities, not duplicate or replace their runtime ownership.

## Current known UX debt

The existing implementation proves the runtime/tool bridge, but it is not the target finished creator experience.

Known redesign targets include:

- attack editing is currently too form-heavy,
- phase/attack internal IDs are too visible/important to normal workflows,
- Testing currently requires manual Phase ID / Attack ID entry,
- the Arena / Tiles workspace currently represents attack-relative pattern editing more than true arena layout,
- placeholder/temporary tab composition should be replaced by direct final panel ownership,
- raw asset IDs lack a friendly browse/search/preview workflow,
- Drops and richer combat/stat authoring are incomplete,
- runtime testing visibility is limited compared with the desired encounter debugger.

These are UX/product gaps, not justification to rewrite working Matrix3/BossLabs runtime architecture.

## First complete boss rule

The next major BossLabs proof remains one complete custom boss.

Working first-boss concept: **Volcanic Warden**.

It should intentionally exercise enough proven functionality to validate the workflow:

### Phase 1

- basic melee,
- projectile attack.

### Phase 2

- ground-slam area attack,
- burning-floor hazard,
- alternate/random-target attack.

### Phase 3

- enrage/faster cadence,
- minion wave,
- meteor/area attack.

The encounter must also prove:

- phase transitions,
- cleanup,
- live iteration,
- persistence,
- targeted testing,
- drops through Matrix3 authority.

BossLabs UX improvements should be judged by whether they make building and tuning this boss faster and clearer.

## V2 implementation sequence

### V2.0 - creator UX authority

- Rewrite BossLabs around creator-first workflow.
- Lock the no-retyping-known-values rule.
- Lock progressive disclosure instead of giant forms.
- Separate Attack Pattern from Arena Layout concepts.
- Make context-aware testing a requirement.

Exit: this document is the authority before major UI restructuring.

### V2.1 - shell and composition cleanup

- Make the window/panel hierarchy directly compose real panels.
- Remove temporary placeholder-replacement patterns when touched by the redesign.
- Establish creator-oriented navigation/header/state presentation.
- Preserve existing bridge/runtime behavior.

Exit: clean modern BossLabs shell with no functionality regression.

### V2.2 - phases and attacks workflow

- Replace manual/internal-ID-first phase/attack UX with creator names and internally managed stable IDs.
- Introduce phase/attack cards/lists.
- Group attack settings with progressive disclosure.
- Provide safe valid defaults.
- Add context-aware `Test Selected Attack` and `Enter Selected Phase` workflow through existing authoritative testing paths.

Exit: a new developer can create, tune, and test a multi-phase boss without memorizing BossLabs internals.

### V2.3 - asset workflow

- Add shared direct-ID + search/browse selection where practical.
- Add previews/links to specialist tools where architecture safely supports them.
- Do not duplicate AnimLab/FX/model tooling.

Exit: common asset iteration no longer requires constantly leaving BossLabs to look up forgotten IDs.

### V2.4 - arena and drops

- Keep relative Attack Pattern authoring as its own workspace.
- Add true encounter Arena Layout only when the first boss requires world anchors/bounds.
- Integrate creator-friendly drops through Matrix3's verified drop authority.

Exit: the first boss can be authored end-to-end from encounter geometry through rewards.

### V2.5 - first boss completion

Build, test, kill, reset, save/reload, and tune the complete first boss.

Exit: BossLabs has proven the complete content pipeline rather than merely accumulating editor features.

### V2.6 - second-boss proof

Build a second smaller boss with the same framework.

Exit: reusable parts are proven reusable; encounter-specific behavior remains isolated.

## Explicit exclusions until proven necessary

Do not build these merely for completeness:

- giant node editor,
- general-purpose scripting language,
- full NPC editor,
- general world/map editor,
- duplicate model editor,
- duplicate animation editor,
- duplicate FX/particle editor,
- hundreds of speculative mechanics,
- alternate combat engine,
- alternate drop engine,
- alternate scheduler,
- complicated multi-level content version-control system.

BossLabs may link/open specialist tools instead of copying them.

## Professional usability question

Before accepting a BossLabs UI feature, ask:

> Could another competent RSPS developer understand and use this correctly on an off day without me explaining the implementation?

If the answer is no, either the workflow needs to be simplified or the complexity belongs behind Advanced mode.

That is the BossLabs quality bar.
