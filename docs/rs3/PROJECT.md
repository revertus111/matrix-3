# Matrix3 RS3 Project Constitution

## Purpose

This document is the primary development authority for the Matrix3 RS3 project. Its purpose is to preserve a stable working base while adding custom content and tooling without repeating the ownership confusion, overlapping implementations, speculative fixes, and uncontrolled tool expansion that made the older 718 project difficult to maintain.

## Core philosophy

**Matrix3 is the game. Our code extends Matrix3. The 718 project is reference material. Revision-830 resources are data. Developer tools are helpers. Custom content is the product.**

When a Matrix3 system already works, preserve it unless a requested feature provides concrete evidence that it must change.

## Known-good baseline

The initial protected runtime baseline is recorded in `BASELINE.md`.

All meaningful engine work is judged against that baseline and the permanent checks in `SMOKE_TEST.md`.

## System ownership

Every major behavior must have one documented authority. See `SYSTEM_OWNERSHIP.md`.

Do not create a second hidden implementation beside an existing owner. If ownership must change, document the old owner, new owner, reason, migration boundary, and required regression tests.

## Three development lanes

### 1. Matrix3 core

Examples: networking, login, cache loading, world lifecycle, map/object handling, player update protocol, combat engine, persistence infrastructure.

Core changes receive the highest scrutiny. Prefer small extensions around stable code over replacements.

### 2. Gameplay and content

Examples: bosses, NPC mechanics, drops, areas, items, abilities, quests, encounters.

This is the primary product lane. New systems should exist because content needs them.

### 3. Developer tools

Examples: Owner Console, command browser, NPC/item editors, animation/model viewers, boss tools, FX tools.

Tools must serve real development needs. They should invoke documented game/content APIs rather than becoming alternate owners of engine behavior.

## Evidence rule

No speculative fixes.

Use:

- `VERIFIED` for behavior confirmed at runtime.
- `verified-static` for behavior directly established from source/data without runtime confirmation.
- `HYPOTHESIS` for plausible interpretations still needing proof.

The normal sequence is:

**Evidence -> classification -> smallest implementation -> targeted test -> smoke test when required.**

## Change-size rule

Work in vertical slices that reach a usable checkpoint before moving on.

A feature should not casually combine permissions, persistence, UI, launcher changes, cache work, and gameplay changes into one patch. Keep each slice independently understandable and revertible.

## 718 reference rule

The old 718 project may be inspected to recover ideas, UX lessons, algorithms, tool workflows, or proven behavior.

It is not an authority for Matrix3 architecture. Never copy a 718 subsystem wholesale just because it already exists. Re-evaluate the requirement against Matrix3 first and build the cleaner Matrix3-native version.

## Obfuscated/decompiled source rule

Preserve original Matrix3 names unless renaming is explicitly approved. Prefer verified comments over semantic renaming while behavior is still being learned. Keep donor/source comparison possible.

## Git rule

Use `main` as the working branch unless explicitly instructed otherwise. Make commits narrow and descriptive so regressions can be traced to a feature slice instead of an ambiguous collection of changes.

## Eclipse / Java rule

Eclipse + Java 8 is the protected development target. Existing Gradle files may remain as build plumbing, but Eclipse clean/build behavior is part of acceptance for server changes. Do not change Java level or IDE assumptions incidentally.

## Documentation rule

Every code change requires `docs/<subject>/patchnotes.txt`.

Runtime-affecting subjects should also maintain a concise test list. When a change alters ownership or project status, update the relevant RS3 authority document in this folder.

## Tooling discipline

Do not allow tool development to replace content development.

Add tooling when a real content task exposes a repeated pain point or missing capability. The first complete custom boss is the main proof that the content pipeline is healthy; tool features should help reach that milestone rather than postpone it.

## Stop conditions

Stop expanding a task when:

- the requested behavior is implemented,
- the ownership path is established,
- the relevant regression checks are defined,
- or additional investigation would move into adjacent systems without evidence that they are involved.

Report uncertainty instead of scanning indefinitely.
