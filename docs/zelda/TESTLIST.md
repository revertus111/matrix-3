# Zelda / OoT Test List

## Phase 0 — NTSC-U 1.2 native compatibility

Phase 0.2/0.3 already passed against the user's exact 32 MiB NTSC-U 1.2 ROM (MD5 `57a9719ad547c516342e1a15d5c28c3d`).

Pinned dependencies:

- `Cycl0o0/liboot` — `25208734c8ca388638f0ce63841e166fac9e5acb`
- `zeldaret/oot` — `269d03016cd0e3d7a0b8925e02b97a319c1d0e8d`

ROM bytes and ROM-derived textures remain local and must never be committed.

To repeat the native compatibility proof if needed:

```sh
cd native/oot-bridge
make probe OOT_ROM="Legend of Zelda, The - Ocarina of Time (U) (V1.2) [!].z64"
```

Acceptance remains:

```text
[OoT NTSC12] RESULT: PASS
```

## Phase 1 — Adult Link material V2 runtime proof

### Current accepted baseline

Already runtime observed:

- recognizable adult OoT Link renders inside Matrix3;
- adult auto-fit now places Link approximately in the 830 player height/floor envelope;
- the user considers Link slightly large and his OoT head proportionally large, but that is **not a blocker** for the equipment-fit architecture;
- keep Link uniformly scaled and fit future 830 gear to Link rather than deforming Link into RuneScape proportions.

The native sidecar must continue to use:

```text
OOT_AGE_ADULT
```

### Mandatory rebuild for this slice

Material fidelity changed the native binary protocol from V1 to **V2**. Pulling Java alone is not enough.

```sh
git pull origin main
```

Then rebuild the OoT bridge with the one-click OoT native builder, or in **MSYS2 UCRT64**:

```sh
cd /c/Users/rever/Desktop/Matrix3/native/oot-bridge
make bootstrap
```

Required output:

```text
native/oot-bridge/build/dist/oot_bridge.exe
```

Then refresh/clean the Client project in Eclipse and launch normally with Java 8.

If Java reports an unsupported protocol version, the old V1 `oot_bridge.exe` is still being used; rebuild the native sidecar before changing code.

### Controls

- **Ctrl+L** — Link mode on/off.
- **WASD** — camera-relative movement request; Matrix/server walking remains actual X/Z authority for this phase.
- **Space** — OoT A/action.
- **F** — OoT B/sword; must not issue Matrix NPC damage yet.
- **Shift** — OoT Z input; host target binding is not implemented yet.
- **Ctrl+M** — Mario remains separate.

### Expected console proof

On Ctrl+L activation expect lines equivalent to:

```text
[Alternate Character] Controller mode: LINK
[OoT] Controls: camera-relative WASD move, Space A/action, F B/sword, Shift Z-target
[OoT Bridge] persistent NTSC-U 1.2 session READY (20 Hz, protocol v2 + materials)
[OoT Bridge] Persistent session READY (20 Hz + Link materials, protocol v2)
[OoT Fit] ADULT Link profile source=830-auto matrixHeight=... nativeH/W/D=... floorLocalY=... scale=...
[OoT Material] Runtime texture bridge ACTIVE materials=... materialBase=... source=local-ROM-RGBA
[OoT Visual] Native ADULT Link -> Matrix Model ACTIVE triangles=... uniqueVerts=... texturedFaces=... anim=... action=... scale=... fit=830-auto material=oot-uv-texture-v2
```

The exact counts may vary with pose/equipment. For the material proof, `materials` and `texturedFaces` must both be greater than zero.

### Material V2 acceptance

Keep the normal RuneScape local-player model visible for this test; it is still the fail-safe/reference model.

Verify in one runtime session:

- [ ] Both native and Java READY lines report **protocol v2**.
- [ ] `[OoT Material] Runtime texture bridge ACTIVE ...` appears with `materials > 0`.
- [ ] `[OoT Visual] ... material=oot-uv-texture-v2` appears with `texturedFaces > 0`.
- [ ] Adult Link remains at approximately the accepted 830 height/floor alignment.
- [ ] Link now has recognizably mapped OoT textures instead of broad flat triangle colours.
- [ ] Face/tunic/boots/sword/shield surfaces are not obviously scrambled across unrelated polygons.
- [ ] Triangle-by-triangle/crystalline shading is substantially reduced compared with the V1 screenshot.
- [ ] Intended hard edges remain hard; smoothing must not visibly melt sword/shield/body silhouettes together.
- [ ] Idle/movement animation continues without model explosion, pumping scale, or texture swimming.
- [ ] Space and F still change native Link action/animation state where the flat proof world allows it.
- [ ] F still causes **no Matrix NPC damage/XP**.
- [ ] Ctrl+L exits cleanly and vanilla RuneScape control returns.
- [ ] Ctrl+L re-entry starts a fresh native session and textures return instead of remaining missing/stale.

### What to capture

For the first V2 run, preserve:

1. the console output from Ctrl+L activation through the first `[OoT Visual]` line;
2. one front/three-quarter screenshot of adult Link;
3. if textures look wrong, one close screenshot showing the face/tunic/shield orientation.

Do **not** make random UV tweaks before preserving the first result.

### Known V2 approximations

- liboot UVs are sent as their original normalized coordinates and Matrix direct-UV mode is used. Runtime will establish whether Matrix's V axis matches liboot; a vertically flipped texture is a convention issue, not a failed native bridge.
- liboot texture wrap `repeat` and `clamp` are mapped; `mirror` currently degrades to Matrix repeat at this adapter seam.
- Full OoT/N64 alpha-test, decal-depth, and cull-flag parity is not implemented in this V2 slice.
- Runtime textures are synthetic Matrix materials backed by GPU textures only; no OoT texture files are written into the 830 cache.
- Matrix may evict GPU textures through its normal LRU; the Link texture registry is expected to re-upload retained local-ROM pixels automatically.
- The RuneScape local-player model is intentionally still visible for this proof.

### Failure distinctions

- `unsupported OoT bridge protocol version: 1 ... rebuild ... protocol v2` -> old native sidecar; rebuild `native/oot-bridge`.
- `OoT sidecar not found` -> native build/path problem.
- `OoT NTSC-U 1.2 ROM not found` -> local ROM path problem.
- `invalid OoT sidecar protocol magic` -> protocol/stdout corruption, preserve full output.
- `material geometry unavailable` -> liboot did not provide one of normals/UVs/texture indices for the frame.
- `[OoT Material] texture bridge unavailable: ...` -> Matrix runtime material/GPU seam unavailable; Link should fall back to vertex colour rather than crash.
- material bridge ACTIVE but `texturedFaces=0` -> triangle texture -> Matrix material mapping problem.
- textures visible but vertically inverted -> UV V-axis convention problem; preserve screenshot before patching.
- textures visible but badly scrambled -> direct UV/material-index problem, not an auto-fit problem.
- Link fits correctly but remains visually faceted -> smoothing/topology issue after materials are proven.

## Remaining Phase 1 behavior checks

After material V2 is accepted:

- [ ] Verify camera-relative WASD does not drive the detached/RTS camera while Link owns input.
- [ ] Verify actual X/Z travel continues through Matrix/server walking and collision.
- [ ] Consolidate idle, movement/turn, one A/action/jump, and B/sword animation in one session.
- [ ] Add fail-open suppression of the local RuneScape model only while fresh Link frames render successfully.
- [ ] Exit/failure must restore RuneScape player model/input immediately.
- [ ] Expose stable adult-Link skeleton/socket transforms for helmet, sword, shield, gloves, boots, amulet, cape, then torso/legs fitting.

## Later combat and menu acceptance

- [ ] Sword contact requires an active swing window and valid reach/target contact.
- [ ] Weapon speed changes attack cadence while RuneScape stats determine hit and damage outcomes.
- [ ] Valid damage grants configured per-hit skill XP; misses/blocks follow explicit XP rules.
- [ ] Inventory opens/closes while the Matrix world continues.
- [ ] Ranged, magic, and item actions remain usable through the same Link control owner.
