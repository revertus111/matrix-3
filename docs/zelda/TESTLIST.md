# Zelda / OoT Test List

## Phase 0 — NTSC-U 1.2 native compatibility

### Scope

This is the bounded NTSC-U 1.2 compatibility proof. Phase 0.2/0.3 have already passed against the user's exact ROM.

The bridge pins:

- `Cycl0o0/liboot` at `25208734c8ca388638f0ce63841e166fac9e5acb`
- `zeldaret/oot` at `269d03016cd0e3d7a0b8925e02b97a319c1d0e8d`

ROM bytes and extracted assets stay local under the user's control and are never copied into the repository.

### Prerequisites

Use an MSYS2 UCRT64 shell on Windows with:

- Git
- Python 3
- CMake
- a C11 compiler/toolchain

The supplied retail ROM must be the 32 MiB NTSC-U 1.2 image already identified as MD5 `57a9719ad547c516342e1a15d5c28c3d`.

### Repeat Phase 0 proof if needed

From `native/oot-bridge`:

```sh
make probe OOT_ROM="Legend of Zelda, The - Ocarina of Time (U) (V1.2) [!].z64"
```

Acceptance remains the final line:

```text
[OoT NTSC12] RESULT: PASS
```

A successful compile or engine-create alone is not a compatibility proof.

## Phase 1 — Matrix3 Link presentation

### Current runtime state

The first live Matrix render is already observed: recognizable OoT Link geometry rendered at the local player, but the original proof scale was far too small. The next acceptance slice is **adult Link auto-fit** against the live 830 local-player height.

The native sidecar explicitly sets:

```text
OOT_AGE_ADULT
```

Do not switch this fit test to child Link.

### Build

Pull the approved bundle:

```sh
git pull origin main
```

Build through the one-click OoT native builder or in **MSYS2 UCRT64**:

```sh
cd /c/Users/rever/Desktop/Matrix3/native/oot-bridge
make bootstrap
```

The build must produce:

```text
native/oot-bridge/build/dist/oot_bridge.exe
```

The exact NTSC-U 1.2 ROM may remain beside the bridge source as:

```text
native/oot-bridge/Legend of Zelda, The - Ocarina of Time (U) (V1.2) [!].z64
```

ROM extensions are ignored by the bridge-local `.gitignore` and must remain untracked.

### Launch / activation

Launch the Matrix3 client normally from Eclipse/Java 8.

Use:

- **Ctrl+L** — toggle Link mode on/off.
- **WASD** — camera-relative movement request. Matrix's existing walk packet/server collision remains X/Z authority.
- **Space** — OoT A/action input.
- **F** — OoT B/sword input. This phase must **not** issue a Matrix NPC attack packet.
- **Shift** — OoT Z-target input. Full Matrix target binding is not implemented yet.
- **Ctrl+M** — Mario mode remains separate and must still work independently.

### Expected console proof

On Ctrl+L activation, expect lines equivalent to:

```text
[Alternate Character] Controller mode: LINK
[OoT] Controls: camera-relative WASD move, Space A/action, F B/sword, Shift Z-target
[OoT Bridge] persistent NTSC-U 1.2 session READY (20 Hz, protocol v1)
[OoT Bridge] Persistent session READY (20 Hz + Link geometry, protocol v1)
[OoT Fit] ADULT Link profile source=830-auto matrixHeight=... nativeH/W/D=... floorLocalY=... scale=...
[OoT Visual] Native ADULT Link -> Matrix Model ACTIVE triangles=... scale=... fit=830-auto ...
```

If the live Matrix player height is not ready on the very first frame, one temporary fallback line is allowed:

```text
[OoT Fit] Adult Link waiting for live 830 height; temporary fallback scale=...
```

It should then upgrade to `source=830-auto` once the RuneScape player bound is available.

### Adult auto-fit acceptance

For this proof the RuneScape local-player model intentionally remains visible as the comparison reference.

Verify in one runtime session:

- [ ] Ctrl+L enters Link mode and starts the native sidecar without crashing/freezing the client.
- [x] Recognizable Link geometry renders inside Matrix3. Previously runtime observed; original scale was too small.
- [ ] Console reports `[OoT Fit] ADULT Link profile source=830-auto ...`.
- [ ] Adult Link's **feet are approximately on the same Matrix ground plane** as the 830 player.
- [ ] Adult Link's **head height is close to the 830 character's visible height** instead of the previous tiny scale.
- [ ] Link remains uniformly scaled: OoT proportions look intact rather than tall/thin or wide/squashed.
- [ ] Link remains upright and centered around the Matrix player transform.
- [ ] Walking/idle animation does not cause the whole model to pump larger/smaller; the fit stays fixed for the session.
- [ ] Space/root-height animation still moves vertically instead of being flattened by ground alignment.
- [ ] Ctrl+L exit/re-entry recalibrates cleanly and does not reuse stale scale/floor state.

If the automatic height is visually close but needs a small global correction, use a JVM property rather than hardcoding a new scale:

```text
-Dmatrix3.oot.fitMultiplier=1.05
```

`matrix3.oot.modelScale` is still supported as an explicit forced-scale diagnostic override, but normal testing should leave it unset so `830-auto` is exercised.

### Remaining first-live behavior checks

- [ ] WASD does not drive the detached/RTS camera while Link owns movement input.
- [ ] WASD movement remains camera-relative and actual X/Z travel follows Matrix/server walking and collision.
- [ ] Link's animation responds to movement rather than remaining frozen.
- [ ] Space changes Link's native A/action animation/state where the flat liboot proof world allows it.
- [ ] F produces native B/sword behavior/animation and does **not** cause a RuneScape NPC attack packet/damage.
- [ ] Shift can be held/released without breaking input; full host target behavior is not required in this slice.
- [ ] Ctrl+L exits Link mode cleanly, releases the sidecar/input wrapper, and returns vanilla RuneScape controls.
- [ ] Re-entering with Ctrl+L starts a fresh usable Link session.
- [ ] Ctrl+M after Link exit still activates Mario normally.

### Known intentional limitations

- RuneScape local-player model is still rendered as a fail-safe/comparison baseline.
- Link uses liboot vertex colours only; OoT texture/material fidelity is not wired yet.
- The new fit matches overall height/floor with one uniform scale. It does **not** yet deform Link to match RuneScape shoulders/hips.
- `LinkCharacterFit` records Link native height/width/depth as groundwork, but 830 helmet/body/glove/boot/cape sockets are a later equipment slice.
- Matrix terrain/objects are not loaded into liboot yet; the native sidecar uses a broad flat proof floor.
- Matrix/server walking owns actual X/Z travel for this phase.
- Link B/sword input has no RuneScape damage/XP bridge yet.
- Z-target has no Matrix NPC target binding yet.

### Failure handling

If anything fails, preserve the full client/native console output from Link activation through the failure.

Useful failure distinctions:

- `OoT sidecar not found` -> native build/path issue.
- `OoT NTSC-U 1.2 ROM not found` -> local ROM path issue.
- `invalid OoT sidecar protocol magic` -> native/Java protocol mismatch or stdout contamination.
- `OoT Link geometry truncated` / invalid triangle count -> native geometry-capacity/protocol issue.
- bridge READY but no `[OoT Visual] ... ACTIVE` -> Matrix render-model path issue.
- `[OoT Visual] ... ACTIVE` plus `fit=fallback` only -> Matrix player height reference was not resolved.
- `fit=830-auto` but wrong size/floor -> auto-fit calibration issue, not bridge compatibility failure.

## Later Phase 1 acceptance

After adult Link scale/floor presentation is accepted:

- [ ] Add fail-open RuneScape local-player suppression only while fresh Link frames render successfully.
- [ ] Exit/failure restores the RuneScape player model/input immediately.
- [ ] Add OoT texture/material fidelity without regressing geometry stability.
- [ ] Expose stable Link skeleton/socket transforms for equipment fitting.

## Later combat and menu acceptance

- [ ] Sword contact requires an active swing window and valid reach/target contact.
- [ ] Weapon speed changes attack cadence while RuneScape stats determine hit and damage outcomes.
- [ ] Valid damage grants the configured per-hit skill XP; misses and blocked hits follow explicit XP rules.
- [ ] Inventory opens and closes while the Matrix world continues.
- [ ] Ranged, magic, and item actions remain usable through the same Link control owner.
