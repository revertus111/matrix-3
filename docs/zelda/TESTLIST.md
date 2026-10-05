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

## Phase 1 — First live Matrix3 Link presentation

### Build

Pull the approved bundle:

```sh
git pull origin main
```

In **MSYS2 UCRT64**:

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
- **F** — OoT B/sword input. This first slice must **not** issue a Matrix NPC attack packet.
- **Shift** — OoT Z-target input. Full Matrix target binding is not implemented yet.
- **Ctrl+M** — Mario mode remains separate and must still work independently.

### Expected console proof

On Ctrl+L activation, expect lines equivalent to:

```text
[Alternate Character] Controller mode: LINK
[OoT] Controls: camera-relative WASD move, Space A/action, F B/sword, Shift Z-target
[OoT Bridge] persistent NTSC-U 1.2 session READY (20 Hz, protocol v1)
[OoT Bridge] Persistent session READY (20 Hz + Link geometry, protocol v1)
[OoT Visual] Native Link -> Matrix Model ACTIVE triangles=...
```

The first two bridge READY lines come from the native sidecar and Java session respectively.

### First-live visual acceptance

For this V1 proof, the RuneScape local-player model intentionally remains visible. Do not report that as a suppression bug yet.

Verify in one runtime session:

- [ ] Ctrl+L enters Link mode and starts the native sidecar without crashing/freezing the client.
- [ ] Animated Link geometry becomes visible at/around the local Matrix player.
- [ ] Link geometry is recognizably upright and reasonably scaled. Record if mirrored, upside-down, rotated, too large, or too small.
- [ ] WASD does not drive the detached/RTS camera while Link owns movement input.
- [ ] WASD movement remains camera-relative and actual X/Z travel follows Matrix/server walking and collision.
- [ ] Link's animation responds to movement rather than remaining frozen.
- [ ] Space changes Link's native A/action animation/state where the flat liboot proof world allows it.
- [ ] F produces native B/sword behavior/animation and does **not** cause a RuneScape NPC attack packet/damage.
- [ ] Shift can be held/released without breaking input; full host target behavior is not required in this slice.
- [ ] Ctrl+L exits Link mode cleanly, releases the sidecar/input wrapper, and returns vanilla RuneScape controls.
- [ ] Re-entering with Ctrl+L starts a fresh usable Link session.
- [ ] Ctrl+M after Link exit still activates Mario normally.

### Known intentional limitations of this first proof

- RuneScape local-player model is still rendered as a fail-safe baseline.
- Link uses liboot vertex colours only; OoT texture/material fidelity is not wired yet.
- Matrix terrain/objects are not loaded into liboot yet; the native sidecar uses a broad flat proof floor.
- Matrix/server walking owns actual X/Z travel for this phase.
- Link B/sword input has no RuneScape damage/XP bridge yet.
- Z-target has no Matrix NPC target binding yet.

### Failure handling

If anything fails, copy the full client/native console output from Link activation through the failure. Do not make speculative fixes before preserving the first failure.

Useful failure distinctions:

- `OoT sidecar not found` -> native build/path issue.
- `OoT NTSC-U 1.2 ROM not found` -> local ROM path issue.
- `invalid OoT sidecar protocol magic` -> native/Java protocol mismatch or stdout contamination.
- `OoT Link geometry truncated` / invalid triangle count -> native geometry-capacity/protocol issue.
- bridge READY but no `[OoT Visual] ... ACTIVE` -> Matrix render-model path issue.
- `[OoT Visual] ... ACTIVE` but bad orientation/scale -> presentation calibration issue, not bridge compatibility failure.

## Later Phase 1 acceptance

After the first visible Link proof is accepted:

- [ ] Add fail-open RuneScape local-player suppression only while fresh Link frames render successfully.
- [ ] Exit/failure restores the RuneScape player model/input immediately.
- [ ] Add OoT texture/material fidelity without regressing geometry stability.

## Later combat and menu acceptance

- [ ] Sword contact requires an active swing window and valid reach/target contact.
- [ ] Weapon speed changes attack cadence while RuneScape stats determine hit and damage outcomes.
- [ ] Valid damage grants the configured per-hit skill XP; misses and blocked hits follow explicit XP rules.
- [ ] Inventory opens and closes while the Matrix world continues.
- [ ] Ranged, magic, and item actions remain usable through the same Link control owner.
