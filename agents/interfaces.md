# Matrix3 Interface Rules

Read root `AGENTS.md` first. This file applies to player-facing RuneScape interfaces, HUDs, overlays, interface components, tabs, context actions, and interface input behavior.

## Interface standard

- Prefer native RuneScape/Matrix3 interface ownership and conventions over standalone overlay behavior.
- Before building an unfamiliar player-facing interface pattern, inspect the smallest relevant existing Jagex/Matrix3 interface that solves a similar UX problem.
- Reuse established interface infrastructure where practical instead of creating a parallel UI framework.
- Keep player-facing UI visually and behaviorally consistent with RuneScape: compact information density, predictable tabs, native fonts/components, consistent spacing, context menus/tooltips where appropriate, and correct input ownership.
- Avoid clipped controls, inaccessible actions, overlapping content, and layouts that only work at one size/state.
- Resizable or movable behavior must remain usable and must not steal unrelated camera/game input.
- Separate developer/debug UI from polished player-facing UI unless the user explicitly requests otherwise.
- Keep hotkeys consistent with existing Matrix3 conventions and avoid conflicts with established client keys.
- When multiple selected entities share an interface, design for group actions without hiding per-entity state that materially affects the action.

## Client interaction

If the interface task requires engine/input/rendering changes rather than normal interface ownership, also read `agents/client-engine.md`.

If the task enters unfamiliar obfuscated interface internals, also read `agents/reverse-engineering.md`.
