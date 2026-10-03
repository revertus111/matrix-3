# Matrix3 Developer Tool and Editor Rules

Read root `AGENTS.md` first. This file applies to ForgeLabs, Live Object Editor, inspectors, asset tools, Owner Console tooling, and other developer-only workflows.

## Tool ownership

- Developer tools serve Matrix3; they do not become alternate owners of engine/gameplay behavior.
- Tools should call stable Matrix3 APIs instead of duplicating runtime logic privately.
- Keep developer-only mutation clearly separated from player-facing behavior and production ownership.
- Prefer reusable editor workflows when repeated content work exposes the same need.
- Avoid building speculative tools with no current content/workstream need.

## Editor UX

- Keep controls compact, fast, and predictable.
- Reuse hotkeys consistently across Matrix3 tools where the same action means the same thing.
- Avoid key conflicts with existing client/game controls.
- Provide clear selection/focus state, safe save/load behavior, and strong feedback for destructive actions.
- Prefer undo/redo for reversible editor mutations when practical.
- Persist reusable editor configuration/definitions when that materially reduces repeated setup.
- Debug/inspection overlays should expose enough evidence to diagnose ownership without becoming permanent player UI.

## Safety

- Do not silently mutate unrelated persistent game state, cache data, or global definitions while previewing/testing editor behavior.
- If a tool operates on live runtime objects, define the ownership boundary and cleanup path.
- Keep temporary/prototype workflows clearly distinguished from production-ready tooling.

## Cross-domain work

- For cache/model/animation/sprite tooling, also read `agents/cache-assets.md`.
- For client rendering/input/editor overlays, also read `agents/client-engine.md`.
- For unfamiliar obfuscated client internals, also read `agents/reverse-engineering.md`.
