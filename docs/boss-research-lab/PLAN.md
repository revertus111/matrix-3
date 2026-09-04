# Boss Research Lab

## Purpose

Build a fast, reusable boss/NPC reverse-engineering tool for Matrix3.

The first preset is **Rise of the Six**, but the tool must remain generic enough to support other bosses later without becoming another combat editor or BossLabs replacement.

Primary design goal:

**Open tool -> choose unresolved mechanic -> test several candidates -> save evidence -> close tool.**

A short 10-minute session should be enough to classify or verify multiple useful assets/mechanics.

## Scope rule

Boss Research Lab is a **research and verification tool only**.

It may help identify and test:

- NPC forms/variants,
- animations,
- GFX,
- projectiles,
- animation/GFX relationships,
- timing metadata,
- candidate asset families,
- mechanic sequences,
- evidence/confidence.

It is **not** responsible for:

- boss combat scripting,
- map editing,
- drop editing,
- spawn editing,
- BossLabs encounter authoring,
- FX Labs authoring,
- generic cache editing.

Research findings may later inform those systems, but Boss Research Lab does not become their owner.

## Evidence model

Every saved finding must use the project evidence labels:

- `VERIFIED` - confirmed at runtime.
- `verified-static` - directly established from cache/source/data but not runtime-confirmed as authentic behavior.
- `HYPOTHESIS` - plausible candidate still requiring proof.

Do not save a donor mapping, visual resemblance, nearby ID, or cache correlation as VERIFIED without runtime evidence.

## Main workflow

1. Open Boss Research Lab.
2. Select a boss preset or search an NPC by name/ID.
3. Select an unresolved mechanic or asset question.
4. Load a candidate list from manual input or related-asset scan.
5. Test candidates with one click.
6. Mark each candidate `Right`, `Wrong`, or `Maybe`.
7. Save useful findings to the boss/NPC/mechanic evidence record.
8. Resume later from the remaining unresolved queue.

The tool should persist research state so no IDs or notes must be remembered between sessions.

## v1 interface

### Boss / NPC selection

Support:

- search by NPC ID,
- search by NPC name,
- boss preset selector,
- one-click NPC preset buttons.

RoTS preset buttons:

- Ahrim
- Dharok
- Guthan
- Karil
- Torag
- Verac

Actions:

- `Become NPC`
- `Spawn NPC`
- `Reset Appearance`

This replaces repeated manual transform/spawn commands during research.

### Animation testing

Controls:

- animation ID field,
- Play,
- Stop,
- Loop,
- Previous,
- Next,
- optional frame/sequence timing display when available from cache data.

The selected candidate queue should support double-click/one-click playback without retyping IDs.

### GFX testing

Controls:

- GFX ID field,
- Play on self,
- Play on target,
- Play on selected tile,
- Previous,
- Next.

Where cache data allows it, show:

- linked model,
- linked animation/sequence,
- recolors/retextures,
- scale,
- rotation,
- duration/timing metadata.

### Projectile testing

Controls:

- projectile ID,
- fire at target,
- source/target preview where practical,
- Previous,
- Next.

Use existing Matrix3 projectile APIs rather than inventing a separate rendering system.

## Research assignment system

A discovered asset must be linkable to:

**Boss -> NPC/Form -> Mechanic -> Asset type -> Asset ID**

Example:

- Boss: Rise of the Six
- NPC: Guthan
- Mechanic: Impale
- Type: Animation
- ID: 21944
- Confidence: VERIFIED
- Note: Spear throw animation

Supported asset types should begin with:

- NPC/form
- animation
- GFX
- projectile
- model/reference
- sound
- map/object coordinate reference
- timing observation

## Persistent findings

Each boss preset should retain:

- verified findings,
- verified-static findings,
- hypotheses,
- rejected candidates,
- unresolved questions,
- notes,
- candidate queues,
- saved test sequences.

The tool must make it obvious what is already known and what still needs work.

## Candidate queue

The queue is central to the fast-session workflow.

Example:

`Greatest Axe research - candidate 3 / 12`

Actions:

- `Right`
- `Wrong`
- `Maybe`
- `Next`
- `Previous`
- `Save Finding`

Rejected candidates remain recorded so later sessions do not waste time retesting them.

## Find Related Assets

Reuse and extend the existing RoTS/cache-research logic rather than creating a second independent scanner.

The related-asset search should be able to rank candidates using evidence such as:

### Animation relationships

- shared frame/skeleton family,
- render-set/BAS relationships,
- nearby sequence groups only as a weak signal,
- compatible NPC/render families,
- sequence timing/duration,
- loop/replay metadata,
- sequence sound references,
- known related RoTS/boss animations.

### GFX relationships

- GFX -> model,
- GFX -> animation/sequence,
- model relationships,
- recolor/retexture relationships,
- animation-family relationships,
- known boss model/animation references.

### Other relationships

Where cache support exists:

- projectile metadata,
- sound associations,
- object/map references,
- alternate NPC forms.

The result should be a ranked short candidate list, not a giant raw cache dump.

## Candidate scoring

Candidate scoring is a prioritization aid only.

A high score means "test this first", not "this is authentic".

Display why a candidate ranked highly, for example:

- shares skeleton family with known RoTS animation,
- linked through same render set,
- same sequence sound family,
- GFX sequence references known boss animation,
- related NPC/model family.

Never convert score into evidence confidence automatically.

## Sequence Builder / Test Sequence

Allow a lightweight research sequence to be saved and replayed without modifying real combat scripts.

Example Guthan Impale research sequence:

1. Guthan animation 21944
2. projectile 4411 to target
3. target animation 21945
4. optional GFX
5. wait configured test delay
6. Guthan animation 21947

The sequence system is for visual/research reconstruction only.

It must not become a second boss scripting engine.

Useful controls:

- add animation step,
- add GFX step,
- add projectile step,
- add wait/tick step,
- target self/NPC/player/tile where applicable,
- run,
- stop,
- save sequence.

## Unresolved-work dashboard

When a boss preset loads, show useful unresolved work immediately.

Example RoTS / Dharok:

- Greatest Axe GFX: unresolved
- Greatest Axe authentic duration: unresolved
- Wall Slam animation: hypothesis
- Hurricane animation 21941: VERIFIED runtime visual

The purpose is to prevent spending a short session deciding what to test.

## RoTS v1 preset

RoTS is the first proof of Boss Research Lab.

Initial preset data should include the six known empowered brothers and currently documented forms/findings, while preserving confidence labels.

Examples already worth exposing in the UI:

- Ahrim active/flying forms
- Guthan armed/spearless forms
- known RoTS animation candidates/findings
- known Guthan projectile/GFX candidates
- Hurricane 21941 finding
- Dharok Greatest Axe unresolved visual/timing
- Torag current special animation findings
- unresolved Ahrim/Karil special sets

Do not hardcode research conclusions into the generic tool architecture. Boss-specific preset data owns those mappings.

## RoTS research workflow

Typical 10-minute session:

1. Open Boss Research Lab -> Rise of the Six.
2. Select Dharok -> Greatest Axe.
3. Tool shows unresolved animation/GFX/timing questions.
4. Run `Find Related Assets`.
5. Test candidate animation/GFX entries one by one.
6. Mark wrong candidates immediately.
7. Save promising or verified findings.
8. Exit.

Next session resumes at the remaining candidate queue instead of restarting research.

## Relationship to existing tools

### Existing cache / RoTS scanner

Boss Research Lab should consume or reuse its focused cache-analysis logic.

Do not maintain two unrelated implementations for:

- BAS/render-set decoding,
- animation-family analysis,
- GFX-definition decoding,
- model relationships.

### AnimLab

Reuse proven animation playback/testing behavior where practical.

Boss Research Lab should provide the boss-oriented workflow around it, not replace all animation tooling.

### Fight Recorder

Fight Recorder remains the runtime timing/behavior evidence tool for the actual encounter.

Boss Research Lab discovers/tests candidates; Fight Recorder measures implemented combat behavior.

### BossLabs

BossLabs remains encounter/content authoring.

Boss Research Lab remains reverse-engineering/research.

Keep the boundary explicit.

## Persistence

Research data should survive client/server restart.

Prefer simple human-readable boss-specific data files where practical so discoveries can also be reviewed in Git.

Persist at minimum:

- boss preset,
- NPC/form,
- mechanic,
- asset type/ID,
- confidence,
- notes,
- rejected status,
- candidate queue state,
- saved sequences.

Exact file format should be chosen during implementation after checking existing Matrix3 tool persistence patterns.

## Implementation principles

- Java 8 / Eclipse compatible.
- Keep UI fast and dark-theme compatible with the existing Client Console/tooling direction.
- Lazy-load expensive cache relationships where practical.
- Never block the game/world thread for cache research UI work.
- Reuse existing stable client/server bridges instead of creating command hacks when a proper API exists.
- Keep server-authoritative actions server-owned.
- Keep candidate research read-only with respect to cache files.
- Do not mutate real boss combat scripts from the research UI.
- Keep v1 small enough that RoTS research resumes quickly.

## v1 acceptance target

Boss Research Lab v1 is successful when the user can:

1. open RoTS preset,
2. choose any brother,
3. become/spawn that NPC without typing a command,
4. test animations/GFX/projectiles quickly,
5. run a related-asset candidate search,
6. classify candidates Right/Wrong/Maybe,
7. assign a useful candidate to a brother/mechanic with an evidence label,
8. save notes/findings,
9. close/reopen the tool and continue from the same unresolved research state.

If those nine actions work cleanly, stop v1 and use it on RoTS before adding more features.

## Future expansion

Only after RoTS proves the workflow useful, add boss presets such as:

- Nex
- Vorago
- Araxxor
- other Matrix3/custom encounter research.

Future boss support should mostly be new preset/research data, not new tool architecture.

## Next implementation step

When implementation is approved, first inspect the existing Client Console/BossLabs/AnimLab/RoTS scanner ownership paths and choose the smallest integration point.

Build the first vertical slice around:

**RoTS preset -> brother selection -> Become NPC -> animation/GFX playback -> persistent finding.**

Do not begin with the full related-asset scoring engine. Prove the fast research workflow first, then connect the existing scanner logic in the next slice.
