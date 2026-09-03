# Rise of the Six - 1:1 Reconstruction

## Goal

Reconstruct Rise of the Six as closely as possible to the original RuneScape encounter using revision-830 cache data plus runtime/video/documentary evidence.

The cache is authoritative for available revision-830 assets. It does not by itself prove the gameplay meaning of an animation, GFX, model, projectile, object, sound, timing, or mechanic.

## Evidence labels

- `VERIFIED` - confirmed in runtime.
- `verified-static` - directly established from source/cache data but not runtime-confirmed.
- `HYPOTHESIS` - plausible interpretation that still needs proof.

Never promote a cache correlation to `VERIFIED` without runtime confirmation.

## Current discovery strategy

1. Find every NPC definition whose name matches Dharok, Ahrim, Karil, Torag, Guthan, or Verac.
2. Record each candidate's models, render animation definition, transforms, options, combat level, and client-script data.
3. Use model fingerprints to correlate variants that share most of the same body models.
4. Specifically investigate the observed Santa-hat RoTS models as possible seasonal variants. A normal RoTS variant may share most models while differing only by a hat/head model.
5. Resolve stand/walk/run render animations and their cache timing information.
6. Visually/runtime-test candidates before classifying them as the actual RoTS NPC or assigning special-move semantics.
7. Research special attacks separately because NPC model/render definitions do not necessarily reference scripted special animations, GFX, projectiles, or encounter mechanics.

## Tool

`Server/src/main/java/com/rs/tools/RotsCacheScanner.java`

Run it in Eclipse as a Java Application. It is read-only and prints findings to the console.

Default search terms:

- Dharok
- Ahrim
- Karil
- Torag
- Guthan
- Verac

Optional command-line arguments replace the default search terms.

## Research areas

### Encounter

Track arena setup, team split, side ownership, brother selection, revival/death bond, side transitions, completion, escape, and cleanup in `mechanics/encounter.txt`.

### Brothers

Brother-specific mechanics should eventually be split into:

- `mechanics/dharok.txt`
- `mechanics/torag.txt`
- `mechanics/guthan.txt`
- `mechanics/verac.txt`
- `mechanics/ahrim.txt`
- `mechanics/karil.txt`

### Assets

Use the `assets/` folder for verified-static cache IDs and evidence. Do not put guessed special-move mappings into the verified lists.

Planned asset groups:

- NPC IDs / variants
- animations
- GFX / spot animations
- projectiles
- objects
- sounds
- maps/regions

### Timings

Use `timings/` for measured attack cadence, animation duration, special windups, hit timing, cooldowns, and encounter timers.

## Important current hypothesis

The Santa-hat versions observed in the revision-830 data may be seasonal RoTS NPC variants. Model-fingerprint correlation is intended to find definitions that share the same RoTS body/weapon models but use a different head/hat model.

Status: `HYPOTHESIS` until the scanner output and runtime appearance confirm the relationship.
