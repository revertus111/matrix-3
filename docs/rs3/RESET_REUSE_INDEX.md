# Matrix3 Reset / Reuse Index

Reset date: 2026-09-12

## Active runtime baseline

- Protected baseline commit: `e86851b95e1d2927d58463b67f600153b9166f6a`
- Baseline tree: `4c9b7ae1c1b194e336d736ea3b0ecc3e0a5e440b`
- Reset commit: `379e376bb0fd52f4cefd33512ed7f2a0c1d8e310`

The active Client and Server runtime trees were restored to the protected baseline. Current `AGENTS.md` and the current `docs/` tree were intentionally preserved.

## Pre-reset recovery point

- Pre-reset `main` HEAD: `8c524fa5949dc436484e49144e736215b42586d4`
- Commit message: `Register CacheEditor workstream status`

All later systems, experiments, implementations, and deleted runtime files remain recoverable from Git history through this commit and its ancestors. Do not reintroduce them wholesale. Inspect and selectively reuse only the parts that still fit the rebuilt Matrix3 architecture.

## Reuse policy

- Preserved planning and idea documents are reference material, not proof that their runtime implementation is still active after the reset.
- Existing feature/workstream docs may describe code that now exists only in pre-reset history.
- Before rebuilding a preserved idea, inspect its documentation first, then inspect only the relevant old commits/files needed for reuse.
- Prefer rebuilding against the clean Matrix3 baseline over restoring a large old subsystem unchanged.
- Verified research, mappings, IDs, timings, UX decisions, and architecture lessons may be reused when still applicable.

## High-value preserved reference areas

Examples include BossLabs, Client Atlas, Client Console, cache/editor work, Rise of the Six research, combat framework experiments, backpack/inventory work, dev-mode tooling, presets, custom item actions, and `docs/ideas/` concepts such as Minime and Combat Mastery.

These are preserved for selective reuse; they are not active runtime ownership after the reset unless deliberately reintroduced and tested.
