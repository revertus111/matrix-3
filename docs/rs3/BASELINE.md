# Matrix3 Baseline 0

Established: 2026-08-30 (America/New_York)
Protected source commit: `e86851b95e1d2927d58463b67f600153b9166f6a`
Branch: `main`

## Meaning

This commit is the protected pre-governance Matrix3 runtime checkpoint. The governance documentation added after it does not intentionally alter runtime behavior.

The user reported the Matrix3 server as solid/working at this point. This baseline is therefore the comparison point for future regressions unless a later baseline is explicitly promoted after a deliberate test pass.

## Current documented foundation

- Matrix3 server/client source is present in the repository.
- Eclipse/Gradle server source parsing has explicit legacy encoding handling.
- Eclipse JDT configuration targets Java 8 for the server.
- Local non-master accounts have the current development owner-rights bootstrap behavior.
- Completed local player-file writes flush the login account store immediately.
- Local `GameLauncher` starts and stops the embedded local login core around game-world startup/shutdown.
- Hosted login/server behavior remains separate from the local bootstrap path.

## Baseline policy

- Do not move this baseline marker merely because new work was committed.
- Promote a new baseline only after the relevant smoke test is completed and the new state is intentionally accepted as known-good.
- If a regression appears, compare the failing state against this commit and the smallest feature commits after it.
- Documentation-only commits do not require a baseline promotion.

## Verification note

This record distinguishes repository evidence from runtime evidence. The source/patchnote facts above are `verified-static`; the overall working-state claim is based on the user's current runtime report and has not been independently executed by ChatGPT.
