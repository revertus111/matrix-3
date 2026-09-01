# Player Replay / Audit System Idea

## Status

IDEA ONLY — not an approved implementation plan yet.

This document captures the concept so it can be revisited later without committing the project to building it now.

## Goal

Create a player investigation system that can reconstruct what a player did before, during, and after a suspected dupe, exploit, bug, abuse case, or strange account-state change.

The system should make it possible to inspect both a chronological event history and, later, a visual replay of the player's session.

The audit/event history must be the authority. Visual replay is a presentation layer built on top of recorded server events, not a second gameplay engine.

## Core Principle

Do not try to permanently record raw game packets or video-like frames for every player.

Instead, record structured semantic events emitted from authoritative Matrix3 systems.

Examples:

- movement and teleports
- login/logout/session boundaries
- NPC interactions
- object interactions
- player interactions
- interface opens/closes
- interface/component/button clicks
- commands
- inventory changes
- equipment equip/unequip/switches
- bank deposits/withdrawals
- trades and transfers
- item drops and pickups
- shops
- Grand Exchange/economy events where applicable
- combat targets/actions
- XP/stat changes
- currency changes
- item creation/deletion
- important container changes

## Event Record

Each important event should eventually carry enough context to explain what happened without relying only on a visual replay.

Conceptual example:

```text
Event: #928572
Time: 20:43:12.311
Player: Bob
Type: INVENTORY_ADD

Item: 4151
Amount: 1
Slot: 4

Source: TRADE_COMPLETE
Source Player: Jim

Before: 0
After: 1
Related Event: #928568
```

Useful common fields may include:

- event ID
- timestamp
- player/account identity
- session ID
- world tile/plane
- event type
- source system/action
- target player/NPC/object where applicable
- interface ID/component ID where applicable
- item ID/quantity/slot where applicable
- before/after values
- related event IDs

Exact fields should be designed only after the authoritative Matrix3 event paths are scanned.

## Item Provenance / Dupe Investigation

A major purpose of this system is answering:

> Where did this item or currency actually come from?

Important economy events should be linkable so an investigator can trace a chain backward and forward.

Example chain:

```text
ITEM_ADD #4008
    <- TRADE_COMPLETE #4007
        <- PLAYER_TRADE_OFFER #3999
            <- BANK_WITHDRAW #3981
```

This would help distinguish legitimate transfers from duplication, rollback bugs, malformed container updates, or unexpected item creation.

## State Checkpoints

Pure event replay can become expensive if reconstruction always starts at login or account creation.

Periodically record lightweight checkpoints of important replay state, such as:

- position
- inventory
- equipment
- relevant stats
- currencies
- active interface where useful
- other state proven necessary for reconstruction

A replay could load the nearest checkpoint and apply subsequent events instead of processing an entire historical stream.

Checkpoint frequency and contents must be chosen based on real storage/performance measurements.

## Investigation UI Idea

A future Owner/Developer tool could provide:

- search player by name
- select session/date/time range
- chronological event timeline
- filter by event type
- search by item ID
- search by interface/component ID
- search by another involved player
- inspect event details
- follow related events
- jump to suspicious inventory/economy changes
- export or copy a concise investigation sequence

Possible timeline controls:

```text
[Play] [Pause] [Previous Event] [Next Event]
[0.5x] [1x] [2x] [5x]
```

## Visual Replay — Later Phase

Once structured event logging is proven, a visual replay layer could reconstruct selected activity in-game.

Potentially replayable behavior:

- movement
- teleports
- equipment changes
- player/NPC/object interactions
- combat targets/actions
- selected interface activity
- selected animations/graphics where recorded context is sufficient

The visual replay does not need to perfectly reproduce every frame to be useful.

If an obscure interface or animation cannot be visually reconstructed, the authoritative event timeline should still show exactly what server-side action occurred.

## Suggested Development Slices

### Phase 1 — Audit Foundation

Record a very small set of high-value structured events.

Start with economy/account-state changes rather than everything at once.

Candidate first events:

- login/logout
- inventory add/remove
- equipment changes
- bank deposit/withdraw
- trade completion
- drop/pickup
- currency changes

### Phase 2 — Interaction Timeline

Add:

- movement/teleports
- NPC/object/player interactions
- interface/component clicks
- commands

### Phase 3 — Investigation Viewer

Build searchable session/event inspection tooling.

No visual replay required yet.

### Phase 4 — State Checkpoints

Add efficient reconstruction checkpoints if measurements show they are needed.

### Phase 5 — Visual Replay

Build a replay presentation layer over the proven event history.

### Phase 6 — Provenance / Exploit Analysis

Add richer linkage between related item/economy events and investigation shortcuts for dupes or account-state corruption.

## Performance / Storage Rules

The final design should avoid:

- blocking the game thread on disk I/O
- writing every insignificant frame/update as a permanent record
- unbounded log growth
- making the replay system authoritative for gameplay
- duplicating existing Matrix3 ownership

Likely direction:

- structured event records
- asynchronous/buffered persistence where safe
- log rotation/retention
- longer retention for high-value economy/security events
- shorter retention for extremely detailed interaction history

Exact retention periods should be based on measured storage cost rather than guessed now.

## Investigation Value

The main win is not simply watching a player move around.

The system should let an owner answer questions such as:

- What did this player click immediately before the bug?
- Where did this duplicated item first appear?
- Which system added or removed this item?
- What was in the player's inventory before and after the event?
- Was another player involved?
- Did the player bank, trade, drop, or equip the item?
- Did a suspicious sequence happen repeatedly?
- What happened in the minutes immediately before a report or disconnect?

## Important Boundary

This is intentionally saved as an idea rather than current architecture.

Before implementation, Matrix3's real inventory, equipment, banking, interaction, networking, persistence, and economy ownership paths must be scanned narrowly enough to identify safe event-hook boundaries.

Do not instrument every system blindly and do not let this idea delay the current content roadmap unless there is a real development or live-server need for it.
