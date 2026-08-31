# BossLabs NPC Search and Existing-Boss Inspection

This document refines the BossLabs NPC-selection/editor workflow under `docs/bosslabs/BOSSLABS.md`.

## One search field

BossLabs must use one NPC search field. Do not add a dropdown, radio button, toggle, tab, or other mode control for switching between NPC id and NPC name search.

Input interpretation is automatic:

```text
13447
  -> numeric input
  -> direct NPC id lookup

nex
  -> text input
  -> case-insensitive NPC name search
```

Numeric input must not require building/scanning the name index.

Name results should rank exact-name matches before prefix matches, then broader substring matches. When multiple NPC definitions share a name, show each result with enough identity data to distinguish it, including at minimum NPC id and name; combat level may also be shown where useful.

## Existing NPC/boss loading

Selecting an NPC should inspect Matrix3's current authorities and auto-populate every verified value BossLabs can read safely.

Initial verified inspection values:

- NPC id,
- NPC name,
- combat level,
- size,
- model ids,
- hitpoints,
- attack speed,
- attack animation,
- defence animation,
- death animation,
- respawn delay,
- attack graphic,
- attack projectile,
- aggressive flag,
- aggression range,
- poison immunity,
- current combat ownership/source,
- current resolved combat-script class,
- current BossLabs definition when one is registered.

Do not spawn a temporary NPC merely to populate these fields when cache/combat-definition authorities already expose the data.

## Combat ownership display

BossLabs must distinguish at least:

```text
BossLabs
Matrix3 Java Script
Matrix3 Default
```

For a Matrix3 Java-script-controlled NPC, display the resolved existing `CombatScript` class.

For a BossLabs-controlled NPC, display the BossLabs runtime adapter and auto-populate the registered immutable `BossDefinition`, including its phases and attacks.

## Editing existing bosses

BossLabs must be able to open an already-existing boss/NPC as the starting point for editing.

Read-only auto-population is not the same as automatic conversion.

A hand-written Matrix3 Java boss script may contain arbitrary conditions, scheduled mechanics, teleports, minion logic, world mutations, or encounter-specific behavior. BossLabs must not claim that such behavior has been converted into editable BossLabs phases/actions unless that conversion has actually been implemented and verified.

Conceptually:

```text
Existing Matrix3 Java boss
  -> inspect/cache-populate known data
  -> identify current CombatScript
  -> preserve Java behavior
  -> deliberate Convert to BossLabs workflow later
```

Already-registered BossLabs bosses may be edited from their current BossDefinition directly.

## Search/index performance

Matrix3's authoritative NPC-definition count is `Utils.getNPCDefinitionsSize()`.

The name index should be built lazily from valid cache index-18 files and reused. The future Swing window must build/refresh this index away from the Swing event-dispatch thread, consistent with the Client Console threading rules.

Direct numeric lookup should remain immediate and should not trigger full name indexing.

If NPC/cache content is imported or changed at runtime, invalidate/rebuild the BossLabs name index deliberately rather than rescanning on every keystroke.

## Ownership

BossLabs NPC search is discovery/inspection tooling only.

It does not own:

- NPC definition decoding,
- NPC combat-script discovery,
- NPC spawning/lifecycle,
- combat calculations,
- drops,
- persistence,
- world state.

It reads those existing Matrix3 authorities and presents them to the editor.
