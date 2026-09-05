# <Workstream Name>

## Goal

Describe the finished outcome in plain language.

## Scope

### In scope

- 

### Out of scope

- 

## Architecture / ownership

- Matrix3 authority involved:
- Content/tool ownership:
- Existing systems that must remain authoritative:
- Important boundaries:

## Verified foundation

### VERIFIED

- Runtime-confirmed behavior only.

### verified-static

- Source/data behavior established statically but not runtime-confirmed.

## Unknown / research needed

### HYPOTHESIS

- Plausible claims that still require proof.

### UNKNOWN

- Facts/behavior not yet established enough to classify.

## Dependencies

- Required systems/features:
- Optional supporting tools:
- Runtime/data dependencies:

## Development plan

Use `Idea -> Bundle -> Patch`.

A bundle groups related work sharing dependencies, ownership, files, implementation sequence, or testing. Keep unrelated workstreams separate.

### Bundle 1 - <Name>

**Purpose:**

**Status:** READY

**Dependencies:**

- 

**Patches:**

1. <Patch>
   - Goal:
   - Likely files/systems:
   - Verification:
2. <Patch>
   - Goal:
   - Likely files/systems:
   - Verification:

**Runtime tests:**

- 

### Bundle 2 - <Name>

**Purpose:**

**Status:** PLANNED

**Dependencies:**

- 

**Patches:**

1. 

**Runtime tests:**

- 

## Current bundle

- Bundle:
- Approval state:
- Current patch:
- Current objective:

## Patch status

Use these states consistently:

- `READY` - understood and ready to enter an approved bundle.
- `ACTIVE` - currently being worked.
- `NEEDS TEST` - implementation complete but waiting on runtime verification.
- `CARRYOVER` - partially complete; preserve context and continue in a later bundle/session.
- `BLOCKED` - cannot safely proceed until the documented blocker is resolved.
- `DONE` - implementation and required verification for the defined patch are complete.

| Patch | Bundle | Status | Notes |
| --- | --- | --- | --- |
|  |  | READY |  |

## Decisions / new ideas

Capture new ideas here without automatically interrupting active work.

For each idea, classify it as one of:

- current bundle dependency,
- future bundle,
- backlog/optional improvement,
- rejected/out of scope.

### Decision log

- 

## Testing

Keep runtime testing concise and optimize around limited PC time.

### Quick/high-value checks

1. 

### Deeper checks

1. 

### Smoke/regression checks

- Relevant `docs/rs3/SMOKE_TEST.md` coverage:

## Carryover / blockers

### CARRYOVER

- Task:
- Current state:
- Remaining work:
- Likely files/systems:
- Next action:

### BLOCKED

- Task:
- Blocker:
- What is already verified:
- What still needs verification:
- Safe work that can continue independently:

## Resume Here

Keep this section current whenever work stops before the workstream is complete.

**Last completed:**

- 

**Current state:**

- 

**Next action:**

- 

**Files/systems already inspected:**

- 

**Do not re-scan without new evidence:**

- 

**Pending runtime verification:**

- 

**Blockers:**

- None / 

**Important remaining uncertainty:**

- 

## Next recommended work

State the single next bundle/patch/checkpoint that best advances the main goal.
