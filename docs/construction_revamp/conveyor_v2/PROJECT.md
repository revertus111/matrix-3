# Conveyor V2 — Prefab I/O Logistics Architecture

This is the authoritative scoped design/resume document for Conveyor V2 and prefab factory logistics.

The canonical Construction phase, main-goal status table and overall roadmap remain in `docs/construction_revamp/PROJECT.md`.

## Current direction

**Status: PIO-1 COMPILE VERIFIED; PIO-2A IMPLEMENTED / NEEDS ECLIPSE SERVER CLEAN-BUILD; PIO-2B LIVE TICK WIRING NEXT**

Conveyor visual polish is intentionally **deferred / non-blocking**. The current straight 1-tile repeated visual is good enough to continue gameplay development. Custom corner, splitter, merger, supports and seam polish can return later.

The active goal is no longer "make belts link with command buttons." The active goal is a real production/logistics contract:

> **Production buildings expose physical INPUT and OUTPUT ports. Conveyors move physical items from compatible OUTPUTs into compatible INPUTs.**

The first real factory loop is:

```text
finite trees
    |
    v
[LUMBERYARD]
 OUTPUT: Logs
    |
    v
 CONVEYOR
    |
    v
 INPUT: Logs
  [SAWMILL]
 OUTPUT: Planks
    |
    v
 CONVEYOR
    |
    v
 [CHEST]
    |
    v
 WORKERS
    |
    v
 BUILD MORE
```

No normal gameplay step in that loop should require `Link First Two Chests`, `Build Sawmill Test Line`, or another command-button topology shortcut. Those may remain developer diagnostics only.

---

# 1. Ownership rules

## Server owns gameplay

The server remains authoritative for:

- persistent `SettlementConveyorRun` identity and geometry,
- payload item id / amount / physical-inventory provenance,
- belt speed,
- payload spacing,
- backpressure,
- production-building buffers,
- input/output port identity,
- machine recipes,
- finite resource-node consumption,
- chest inventory,
- port/conveyor connections,
- persistence and recovery after re-entry/relog.

The client owns only:

- placement preview,
- port highlighting/snapping presentation,
- conveyor visual generation,
- payload rendering/interpolation,
- prefab visual presentation.

Never move item ownership or production authority into the client renderer/editor.

---

# 2. Generic logistics endpoint contract

All logistics participants should expose a common server-side endpoint abstraction instead of conveyor code knowing every building type.

Conceptual endpoint operations:

```text
canExtract(item / request)
extract(...)

canAccept(itemId, amount)
accept(itemId, amount)
```

An endpoint also exposes stable metadata:

```text
endpointId / portKey
owner persistent id
direction = INPUT | OUTPUT
world tile
facing
accepted/provided item filter
buffer/capacity ownership
```

Potential endpoint owners:

- prefab production building,
- physical chest/storage,
- conveyor run,
- future minecart loader/unloader,
- future furnace/smelter/cooker/etc.

The conveyor transport layer should not need special recipe logic for Lumberyard vs Sawmill vs Chest.

### PIO-1 implementation state

PIO-1 now exists as a server-owned adapter framework:

- `SettlementLogisticsEndpointRef` is the serializable stable identity: owner type + persistent owner id + stable port key. Runtime world coordinates are not persisted as endpoint identity.
- `SettlementLogisticsEndpoint` exposes INPUT/OUTPUT direction, plot position, facing, item compatibility, available amount/capacity, extract and accept operations.
- `SettlementLogisticsPortDefinition` owns prefab-local offset/facing/item-filter metadata and resolves those through placed-piece quarter-turn rotation.
- `SettlementLogisticsEndpointResolver` resolves the current persistent owners without replacing them: `SettlementStorageContainer`, `SettlementMachineBuffer`, and `SettlementConveyorRun` remain authoritative.
- Chest keys are `INPUT` / `OUTPUT`; the first Sawmill/workbench keys are `INPUT_LOGS` / `OUTPUT_PLANKS`; ConveyorRun exposes stable `OUTPUT`.
- Conveyor OUTPUT exposes only `physicalInventoryOwned` payloads through the production endpoint contract so developer-only synthetic payloads cannot leak into physical factory ownership.
- Missing/deleted piece or ConveyorRun refs resolve to blocked/null without mutating the source inventory or payload.
- Eclipse Java 8 Server clean/build was user-verified with zero errors on 2026-10-03.
- PIO-1 deliberately does **not** replace the current live conveyor tick yet. PIO-2 owns that integration so the framework can be validated before the proven transport path is changed.

Current Sawmill port definitions are temporarily anchor-local `(0,0)` while the endpoint identity/API stabilizes. PIO-3 may move those ports to authored prefab-local offsets without changing their stable port keys.

---

# 3. Prefab building port definition

A prefabbed production building owns its port definitions relative to one stable prefab anchor.

Conceptual definition:

```text
Prefab: SAWMILL
Anchor: local 0,0
Footprint: authored prefab footprint

Port INPUT_LOGS
  local offset: (-3, 0)
  type: INPUT
  facing: WEST
  accepts: Logs 1511
  buffer: machine input

Port OUTPUT_PLANKS
  local offset: (+3, 0)
  type: OUTPUT
  facing: EAST
  provides: Planks 960
  buffer: machine output
```

Prefab rotation rotates all of the following together:

- visual components,
- footprint,
- port local offsets,
- port facing,
- work-area orientation if orientation-specific.

A port must be addressable by stable prefab/piece identity + stable port key. Do not persist only a temporary runtime world coordinate as port identity.

---

# 4. Conveyor I/O rules

## 4.1 One forward OUTPUT

Every conveyor run has exactly one normal forward flow direction.

Point B / the forward end is its OUTPUT.

```text
A ===============================> B
                                    OUTPUT
```

Items move only toward that forward output.

## 4.2 Distributed INPUT surface

A conveyor may accept an incoming conveyor at **any valid position along the receiving belt**, including:

- its rear/start,
- its middle,
- near its end.

The receiving belt does not need a special dedicated input object for every possible merge point.

The entire straight run acts as a distributed input surface with one directional rule:

> **An item may enter from any side except through the receiver's forward/output side.**

Example receiver flowing EAST:

```text
                     receiver flow / OUTPUT ->
WEST/rear input  ->  ============================>
                           ^             ^
                           |             |
                       south input   south input

                       north input
                           |
                           v
                    =============================>
```

Allowed approach sides relative to receiver heading:

- rear,
- left,
- right.

Rejected approach:

- through the receiver's forward/output side.

This keeps flow ownership unambiguous.

## 4.3 Belt-to-belt insertion anywhere

A source conveyor output can terminate on the receiving run at any valid insertion distance:

```text
Source Run #14 output
        |
        v
==================+====================> Receiver Run #7
                  ^
             insertionDistance
```

Persistent/logical connection:

```text
sourceRunId -> receiverRunId @ insertionDistance
```

When the source payload reaches its output:

1. resolve the receiver and insertion distance,
2. validate approach direction,
3. validate spacing around that insertion point,
4. if accepted, transfer payload ownership to receiver,
5. receiver now owns direction/speed/progress,
6. if blocked, payload remains at source output and backpressures the source run.

This is the temporary turn system too.

A 90-degree turn does **not** require custom curved transport logic:

```text
A ==========>+
             |
             v
             B
             |
             v
```

Run A outputs into the side/rear input surface of Run B. Later `CORNER_1T` can hide/polish the visual seam without changing transport behavior.

## 4.4 Multiple inputs / merges

Multiple source runs may feed one receiver at different positions.

```text
              v source B
              |
==============+==========================> MAIN
         ^
         |
      source A
```

If two sources contend for the same insertion space, acceptance is deterministic. The accepted payload transfers; the other source remains backpressured until spacing opens.

No payload may overlap, teleport through another payload, duplicate, or disappear.

## 4.5 Crossing is not connection

Two conveyor spans crossing visually do not connect by themselves.

A connection exists only when a source OUTPUT actually terminates on a valid receiver INPUT surface/port.

### PIO-2A implementation state

PIO-2A now establishes the server-side conveyor endpoint semantics without touching the proven live tick yet:

- `SettlementLogisticsEndpoint.Facing` now exposes `opposite()` and endpoint acceptance can be source-aware through `canAcceptFrom(...)`.
- a straight ConveyorRun has a stable `CONVEYOR:<runId>/INPUT` distributed-input identity; the concrete insertion point is resolved from the current persistent run geometry rather than persisted as a temporary world coordinate.
- `resolveConveyorInput(...)` resolves start/middle/near-end contact points and reuses `SettlementConveyorRun.canAcceptPayloadAt(...)` for the existing 0.85-tile spacing/backpressure rule.
- source flow matching receiver flow is valid rear entry; perpendicular source flow is valid left/right entry; source flow opposite receiver flow is rejected as forward/output-side entry.
- `canConnectConveyorRuns(...)` requires source Point B to actually lie on the receiver, so visual span crossings remain non-connections.
- `connectConveyorRuns(...)` persists the existing receiver run id + insertion distance only after geometry/approach validation.
- `transferConnectedConveyorOutput(...)` stages a physical receiver payload at the exact insertion distance, then extracts the source payload; failed extraction rolls the staged payload back, preserving single ownership.
- synthetic development payloads remain excluded from the production endpoint transfer helper.

PIO-2B is the remaining live seam: replace the direct belt-to-belt branch in `SettlementInstance.processConveyorOutput(...)` and connection creation helpers with these endpoint methods after the PIO-2A clean-build is confirmed.

---

# 5. Backpressure contract

Backpressure must propagate through the complete factory.

Example:

```text
Chest full
   ^
Plank belt blocked
   ^
Sawmill output buffer full
   ^
Sawmill stops processing
   ^
Sawmill input fills
   ^
Log belt backs up
   ^
Lumberyard output fills
   ^
Lumberyard stops chopping
```

Rules:

- output inventory is never deleted because downstream is blocked,
- a belt payload remains belt-owned until the destination accepts it,
- a building does not consume recipe input unless its output transaction can remain safely owned,
- Lumberyard does not consume finite tree resource when its produced Logs cannot remain safely owned,
- reopening capacity naturally resumes the chain.

---

# 6. Lumberyard prefab V1

The Lumberyard is the first resource-production prefab.

## Purpose

- owns a configurable work radius,
- finds valid finite persistent tree nodes in that radius,
- reserves one valid node while operating,
- consumes the same authoritative finite tree-node state already used by player/worker gathering,
- produces physical Logs item `1511`,
- stores produced Logs in a machine-local/output buffer,
- exposes one `OUTPUT_LOGS` port.

Conceptual flow:

```text
      trees inside work radius
        T       T
     T    T  T
          |
          v
   +---------------+
   |  LUMBERYARD   |
   +-------+-------+
           |
      OUTPUT_LOGS
           |
           v
        conveyor
```

Important rules:

- do not create a second fake tree-resource system,
- do not directly spawn Logs onto a belt without output-buffer ownership,
- if output is full/backpressured, chopping pauses before unsafe resource consumption,
- exact chop duration/yield tuning is balance data and can be tuned later without changing the port framework.

---

# 7. Sawmill prefab V1

The Sawmill is the first processor prefab.

Ports:

```text
INPUT_LOGS -> machine input buffer
OUTPUT_PLANKS -> machine output buffer
```

Current accepted physical recipe remains:

```text
1 x Logs 1511 -> 2 x Planks 960
```

The existing machine-buffer / atomic-processing work should be reused rather than replaced.

Processing contract:

1. belt delivers Logs through `INPUT_LOGS`,
2. machine input owns accepted Logs,
3. when recipe input exists and output has safe capacity, processing runs,
4. one Log is atomically consumed,
5. two Planks are created in machine output,
6. `OUTPUT_PLANKS` exposes those Planks to a connected conveyor,
7. downstream blockage fills output and eventually pauses processing.

Worker-operated and automated ownership must not double-process the same machine at the same time.

---

# 8. Physical chest endpoint

A physical settlement chest remains real inventory storage.

For the first factory vertical slice it must support:

- conveyor INPUT depositing physical items,
- existing player storage UI,
- worker withdrawal of physical materials.

A later/optional chest OUTPUT may expose chest inventory back to conveyor logistics, but the first Lumberyard -> Sawmill -> Chest loop only requires the destination input plus worker withdrawal.

Example:

```text
Sawmill OUTPUT_PLANKS
        |
        v
     conveyor
        |
        v
   CHEST INPUT
        |
   Planks x N
        |
        v
     workers
```

The chest inventory is the single owner. Do not mirror the same Planks into a legacy settlement-resource counter.

---

# 9. Worker/build integration target

The eventual Construction material loop is physical:

```text
factory -> chest -> worker -> construction job
```

Workers should be able to acquire required physical materials from eligible settlement chests and carry/use them for prefab/build jobs.

The port/logistics framework must not hardcode worker building into conveyor logic. Workers consume from storage through the storage/Construction job layer.

This is a follow-on after the first production chain is mechanically accepted.

---

# 10. Player-facing conveyor placement

Normal gameplay should connect endpoints spatially, not through debug buttons.

Intended Build Palette flow:

1. choose Conveyor,
2. hover/click near a compatible OUTPUT port to choose source,
3. drag/extend the belt,
4. hover a compatible building/chest INPUT port or valid existing conveyor input surface,
5. target highlights/snaps,
6. click to commit,
7. server validates endpoint identity, direction, item compatibility, territory and geometry,
8. persistent connection is created.

Valid source examples:

- Lumberyard `OUTPUT_LOGS`,
- Sawmill `OUTPUT_PLANKS`,
- Chest OUTPUT later,
- Conveyor forward OUTPUT.

Valid destination examples:

- Sawmill `INPUT_LOGS`,
- Chest INPUT,
- Conveyor rear/left/right distributed input surface.

Player-facing presentation decision: use simple green/red directional arrows to distinguish INPUT and OUTPUT ports rather than another large overlay. Exact color-to-direction assignment can remain presentation data until PIO-6; server endpoint direction is authoritative.

Developer commands may create diagnostic topology, but they are not the gameplay contract and should not be required for acceptance.

---

# 11. Persistence model

Connections need stable references.

Building-port reference concept:

```text
ownerPieceId
portKey
```

Conveyor connection concept:

```text
sourceRunId
receiverRunId
receiverInsertionDistance
```

A persisted connection must safely recover when:

- settlement instance rebuilds,
- player exits/re-enters,
- player logout/relog occurs,
- target building/belt was removed,
- prefab was rotated/moved through an approved gameplay edit.

If a destination no longer resolves, upstream ownership must become safely blocked/disconnected. Never delete the item as repair behavior.

---

# 12. Conveyor visuals are deferred

Current visual rule is intentionally sufficient, not final:

- `STRAIGHT_1T` repeated module stays as the current straight visual baseline,
- ugly 90-degree side connections are acceptable temporarily,
- custom `CORNER_1T` can return later,
- splitter/merger/support/end-cap polish is deferred,
- visual seams must not block I/O mechanics work.

Do not return to stretch-based straight-belt rendering unless runtime evidence specifically requires it.

Transport topology and item ownership must remain independent from whichever visual mesh is eventually used.

---

# 13. Implementation order

## PIO-1 — Generic endpoint/port framework

**Status: IMPLEMENTED / ECLIPSE SERVER CLEAN-BUILD VERIFIED — 2026-10-03.**

- stable INPUT/OUTPUT endpoint identity — implemented,
- local/world position + facing — implemented at server plot-position level; runtime world projection stays outside persistent identity,
- compatible item filter — implemented,
- extract/accept contract — implemented,
- prefab rotation transform — implemented / runtime rotation sanity still required before non-zero authored offsets,
- safe persistence reference — implemented,
- chest/machine/conveyor adapters — implemented without replacing existing inventory/payload owners,
- Eclipse Java 8 Server clean/build — user-verified zero errors,
- live conveyor tick integration — owned by PIO-2.

## PIO-2 — Conveyor endpoint integration

**Status: PIO-2A IMPLEMENTED / NEEDS ECLIPSE SERVER CLEAN-BUILD; PIO-2B LIVE WIRING NEXT — 2026-10-03.**

PIO-2A implemented:
- forward belt OUTPUT endpoint metadata,
- distributed straight-run INPUT contact resolution,
- arbitrary receiver insertion distance from persistent geometry,
- rear/left/right approach validation,
- receiver forward/output-side rejection,
- existing spacing/backpressure reuse,
- atomic physical belt handoff helper with rollback,
- crossing-without-output-contact rejection.

PIO-2B next:
- route the live `SettlementInstance` belt-to-belt output branch through `transferConnectedConveyorOutput(...)`,
- route new/existing run connection creation through `connectConveyorRuns(...)`,
- runtime-test straight chaining, 90-degree transfers, mid-belt merge, forward-side rejection, merge contention and crossing safety.

## PIO-3 — Sawmill physical ports

- `INPUT_LOGS`,
- `OUTPUT_PLANKS`,
- reuse existing persistent machine buffers,
- preserve `1 Log -> 2 Planks`,
- no debug-line command required.

## PIO-4 — Chest input adapter

- belt -> real chest inventory,
- capacity/filter rejection backpressures belt,
- worker/player storage ownership unchanged.

## PIO-5 — Lumberyard prefab

- persistent prefab identity/footprint,
- work radius,
- finite tree-node reservation/consumption,
- physical Log output buffer,
- `OUTPUT_LOGS` port,
- output backpressure pauses harvesting.

## PIO-6 — Build Palette port snapping

- output source highlighting,
- input destination highlighting,
- conveyor input-surface highlighting,
- simple red/green directional-arrow presentation instead of a large port overlay,
- server-authoritative commit,
- remove command-button dependency from normal test flow.

## PIO-7 — Worker material/build bridge

- workers withdraw physical construction materials from eligible chests,
- carry them to approved construction job,
- consume only through authoritative build transaction.

## Deferred polish

- `CORNER_1T`,
- splitters/filters,
- mergers,
- supports,
- final prefab artwork,
- belt tiers/speeds,
- advanced UI/throughput overlays.

---

# 14. First real factory acceptance target

The first end-to-end acceptance is:

```text
finite tree node
      |
      v
 LUMBERYARD
      |
 physical Logs 1511
      |
      v
   CONVEYOR
      |
      v
 SAWMILL INPUT
      |
 1 Log -> 2 Planks
      |
 SAWMILL OUTPUT
      |
      v
   CONVEYOR
      |
      v
    CHEST
      |
 physical Planks 960
      |
      v
    WORKER
```

Acceptance requires:

- finite tree amount decreases only for real Lumberyard harvest,
- Logs become physical owned items,
- belt transfer conserves item ownership,
- Sawmill receives only compatible Logs,
- exact `1 Log -> 2 Planks` processing remains atomic,
- Planks leave through Sawmill output,
- destination chest owns the final physical Planks,
- downstream blockage propagates backward without loss/duplication,
- no command-button link/build-line action is required,
- exit/re-entry and relog preserve all durable inventories/connections,
- workers can later withdraw those chest materials without conveyor special-casing.

---

# Resume Here

**Resume Here — Prefab I/O Logistics:** PIO-1 is Eclipse Java 8 Server clean-build verified. PIO-2A is implemented on `main`: generic endpoint semantics now include distributed ConveyorRun INPUT contacts, source-aware rear/left/right approach validation, forward-side rejection, validated connection helpers and rollback-safe physical belt handoff while preserving existing payload ownership.

Next: Eclipse Java 8 clean/build Server once. If clean, execute PIO-2B only: replace the existing direct belt-to-belt transfer branch and run-link creation calls in `SettlementInstance` with the new resolver helpers. Do not reopen chest/machine I/O yet; PIO-3/PIO-4 own those adapters. After PIO-2B, runtime-test straight chaining, 90-degree transfer, mid-belt merge, forward-side rejection, merge backpressure and crossing safety in one session.
