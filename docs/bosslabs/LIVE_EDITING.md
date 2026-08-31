# BossLabs Live Editing and Persistence

This document refines the BossLabs editing workflow under `docs/bosslabs/BOSSLABS.md`.

## Goal

BossLabs must support fast in-game iteration without turning the UI into a gameplay owner or writing every keystroke to disk.

The editor has three distinct states:

```text
DRAFT
  -> values currently being edited in BossLabs

LIVE
  -> immutable BossDefinition currently published in BossDefinitionRegistry

SAVED
  -> immutable BossDefinition persisted by BossDefinitionStore
```

These states must remain explicit. A draft is not live until the user applies it. A live definition is not permanent until the user saves it.

## Apply Live

`Apply Live` validates/builds an immutable `BossDefinition` from the editor draft and publishes it through `BossDefinitionPublisher.applyLive()`.

It must not write to disk.

The active NPC does not need to be recreated merely because definition-backed combat values changed. `BossCombatScript` resolves the current registered definition when it attacks, so a newly published definition is available to subsequent attack/phase resolution.

Examples intended to be live-applicable when represented by BossDefinition data:

- phase health ranges,
- enabled phase attacks,
- attack animation,
- attack graphic,
- projectile,
- max-hit override,
- combat-delay override,
- later reusable mechanic parameters that are explicitly designed for runtime replacement.

Do not apply partially typed field values on each keystroke. Draft editing remains local until an explicit Apply action.

## One-level live rollback

Every successful `Apply Live` or `Save & Apply` retains the immediately previous live registration state for that NPC id.

`Undo Last Apply` restores that state.

The previous state may be:

- another BossLabs definition, or
- no BossLabs registration at all, in which case rollback returns the NPC to the normal Matrix3 Java/default combat-script path.

Rollback is intentionally one level for V1. Do not build a large history/version-control subsystem before the first boss proves a need.

## Save & Apply

`Save & Apply` follows this order:

```text
validate immutable definition
  -> persist definition successfully
  -> publish the exact same definition live
```

Persistence failure is a hard stop. If the save fails, the current live definition must remain untouched.

The BossLabs V1 persistent store is:

```text
Server/data/bosslabs/definitions.bld
```

The store is BossLabs-owned, versioned binary content. It does not replace or modify Matrix3's existing NPC combat-definition, drop, examine, or spawn files.

This storage choice follows the existing Matrix3 pattern of dedicated `data/...` content locations with explicit Java loader/writer ownership while avoiding a new JSON dependency/parser authority.

Saved definitions are loaded into `BossDefinitionRegistry` during `CombatScriptsHandler.init()` before live combat-script use.

## Apply Saved

BossLabs may expose an `Apply Saved` / `Revert Live to Saved` action.

This republishes the persisted definition without rewriting the store. It is useful when live experimentation has diverged from the last permanent version.

## Status presentation

The future BossLabs UI should make state obvious, for example:

```text
DRAFT MODIFIED
LIVE
SAVED
```

or equivalent concise indicators.

The UI should be able to distinguish:

- draft matches live,
- live differs from saved,
- draft differs from live,
- no saved BossLabs definition exists.

Do not infer equality from display labels alone; compare the actual definition content/state when the UI implementation reaches this phase.

## Existing Matrix3 Java bosses

Live BossLabs publishing must not silently replace a hand-written Matrix3 combat script merely because an NPC was inspected in BossLabs.

Opening a legacy Matrix3 boss remains read-only inspection until the user deliberately converts/publishes a BossLabs definition for that NPC.

Once a BossLabs definition is explicitly published, the existing `CombatScriptsHandler` BossLabs delegation rule applies. Undoing the first live apply can restore the NPC to its original Matrix3 script/default path.

## Changes that may require controlled respawn

Not every future editable field should mutate an already-alive NPC instance.

Definition-backed combat behavior can generally be picked up on subsequent runtime resolution. Identity/cache/world properties may require a controlled refresh or respawn, including candidates such as:

- NPC id,
- model/transform identity,
- size when owned by cache identity,
- spawn position,
- arena placement/bounds,
- other properties whose owner is the concrete NPC/world instance rather than BossDefinition combat data.

The future UI should report this clearly and offer an explicit `Apply + Respawn` style action only after the authoritative respawn/testing bridge is verified.

Do not force live instance mutation merely to make every field appear instant.

## Threading

Disk persistence must not run on the Swing event-dispatch thread.

The future BossLabs client/server bridge must submit `Apply Live`, `Save & Apply`, rollback, respawn, and other state-changing actions through the verified Matrix3 development/owner path. The Swing UI must not directly mutate server world state.

## Ownership

- BossLabs draft state: developer-tool UI.
- Live immutable definition: `BossDefinitionRegistry`.
- Persistent BossLabs definition data: `BossDefinitionStore`.
- Publish orchestration: `BossDefinitionPublisher`.
- Combat execution: existing Matrix3 `NPCCombat` / `CombatScriptsHandler` / `CombatScript` path.
- NPC lifecycle, movement, death, respawn, drops, world state: existing Matrix3 authorities.

No ownership of Matrix3 combat calculations or NPC lifecycle moves into the editor or persistence layer.
