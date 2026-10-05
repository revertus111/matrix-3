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

## Phase 1 — Adult Link RGBA micro-bake V3 runtime proof

### Runtime baseline already accepted

- recognizable adult OoT Link renders inside Matrix3;
- adult auto-fit places Link approximately in the 830 player height/floor envelope;
- the user considers Link slightly large and his OoT head proportionally large, but this is not a blocker for the equipment-fit architecture;
- the local 830 player body is successfully suppressed while fresh Link presentation is healthy;
- the 830 body returns through the fail-open path rather than being deleted/mutated;
- the user's root `Native Builder.bat` -> **BUILD OOT** path is valid and successfully rebuilt the protocol-V2 sidecar under MSYS2 UCRT64.

The native sidecar must continue to use:

```text
OOT_AGE_ADULT
```

### Why this V3 test exists

The first protocol-V2 renderer attempted to bind local-ROM OoT RGBA textures as synthetic Matrix runtime materials using direct UV mode. Runtime screenshot rejected that approach: Link was visible alone, but his face was nearly solid white and the body remained broad flat green/white/gray polygons.

That screenshot proves the 830 suppression/geometry/fit paths work, but the synthetic Matrix material presentation did not visibly apply OoT textures.

V3 bypasses that material registration path. It subdivides textured OoT triangles, samples the actual retained RGBA texture with the original UVs/wrap mode, multiplies it by liboot's vertex RGB lighting/tint, then emits normal Matrix face colours.

### Build steps for this exact recovery patch

The user's latest Native Builder log already ended in `OOT BUILD SUCCESS` with protocol V2. **This V3 recovery patch is Java-side only. Do not rebuild native again unless the client reports protocol V1 or the sidecar binary is missing.**

Pull:

```sh
git pull origin main
```

Then in Eclipse/Java 8:

1. Refresh the Client project.
2. Clean the Client project.
3. Run normally.
4. Toggle adult Link with **Ctrl+L**.

### Controls

- **Ctrl+L** — Link mode on/off.
- **WASD** — screen-relative N64 movement request; Matrix/server walking remains actual X/Z authority for this phase.
- **Space** — OoT A/action.
- **F** — OoT B/sword; must not issue Matrix NPC damage yet.
- **Shift** — OoT Z input; host target binding is not implemented yet.
- **Ctrl+M** — Mario remains separate.

### Expected console proof

The native/Java session should still report protocol V2:

```text
[Alternate Character] Controller mode: LINK
[OoT] Controls: camera-relative WASD move, Space A/action, F B/sword, Shift Z-target
[OoT Bridge] persistent NTSC-U 1.2 session READY (20 Hz, protocol v2 + materials)
[OoT Bridge] Persistent session READY (20 Hz + Link materials, protocol v2)
[OoT Fit] ADULT Link profile source=830-auto matrixHeight=... nativeH/W/D=... floorLocalY=... scale=...
```

The important new renderer line is:

```text
[OoT Visual] Native ADULT Link -> Matrix Model ACTIVE triangles=... matrixTriangles=... uniqueVerts=... texturedSource=... textureCatalog=... textureSubdivisions=... anim=... action=... scale=... fit=830-auto material=oot-rgba-micro-v3
```

For a successful texture bake:

- `textureCatalog > 0`
- `texturedSource > 0`
- `textureSubdivisions > 0`
- `matrixTriangles > triangles` for the normal textured adult-Link frame

The old `[OoT Material] Runtime texture bridge ACTIVE ...` line is **not required** by V3 because the active renderer no longer depends on the synthetic runtime-material registry.

### RGBA micro-bake V3 acceptance

Verify in one runtime session:

- [ ] Both native and Java READY lines report **protocol v2**.
- [ ] New `[OoT Visual] ... material=oot-rgba-micro-v3` line appears.
- [ ] `textureCatalog > 0`.
- [ ] `texturedSource > 0`.
- [ ] `matrixTriangles > triangles` and `textureSubdivisions > 0`.
- [ ] Adult Link remains at approximately the accepted 830 height/floor alignment.
- [ ] The local 830 body disappears after Link successfully renders, leaving Link alone.
- [ ] Link's face has recognizable OoT facial texture detail instead of the previous blank-white polygon face.
- [ ] Tunic/body shows texture variation rather than only broad flat green triangles.
- [ ] Boots/sword/shield surfaces show recognizable source detail where the current OoT pose/equipment exposes them.
- [ ] Triangle-by-triangle/crystalline appearance is substantially reduced compared with the failed V2 screenshot.
- [ ] Intended silhouettes remain stable; no model explosion, pumping scale, or texture swimming during idle/movement.
- [ ] Space and F still change native Link action/animation state where the flat proof world allows it.
- [ ] F still causes **no Matrix NPC damage/XP**.
- [ ] Ctrl+L exit restores the normal 830 player body immediately.

### What to capture

Preserve:

1. the console output from Ctrl+L activation through the first new `[OoT Visual]` line;
2. one close front/three-quarter screenshot of Link alone;
3. if texture detail appears but looks flipped/mirrored, one close screenshot of the face/tunic/shield before another patch.

Do not make random UV tweaks before preserving the first V3 result.

### Failure distinctions

- Java compile error -> fix the exact V3 renderer compile seam; do not rebuild native first.
- `unsupported OoT bridge protocol version: 1 ... rebuild ... protocol v2` -> old native sidecar; use root `Native Builder.bat` -> **BUILD OOT**.
- `OoT sidecar not found` -> native build/path problem.
- `OoT NTSC-U 1.2 ROM not found` -> local ROM path problem.
- `invalid OoT sidecar protocol magic` -> protocol/stdout corruption; preserve full output.
- `material geometry unavailable` -> liboot did not provide normals/UVs/texture indices for the frame.
- `textureCatalog=0` -> Java did not retain any local-ROM texture payloads; inspect bridge texture delivery/catalog next.
- `textureCatalog>0` but `texturedSource=0` -> triangle texture indices do not resolve against the retained texture catalog; inspect native texture-index delivery next.
- `texturedSource>0`, `matrixTriangles>triangles`, but Link still looks completely flat -> CPU texture sample/tint path is wrong; inspect sampled RGBA/UV values next.
- texture detail is visible but vertically inverted -> V-axis convention issue; preserve screenshot, then flip only V sampling.
- texture detail is visible but mirrored/repeated incorrectly -> wrap-mode convention issue; preserve screenshot, then fix only repeat/mirror/clamp handling.
- texture detail is recognizable but still too coarse -> raise/retune micro-bake sampling only after correctness is proven; default is 4 subdivisions.
- Link visible + 830 body visible continuously -> suppression readiness/gate regression.
- both Link and 830 body disappear -> suppression failed closed; exit Ctrl+L and preserve console before patching.

### Diagnostic override

V3 subdivision density can be overridden without code changes:

```text
-Dmatrix3.oot.textureSubdivisions=0..4
```

Default is `4`. `0` intentionally disables texture micro-baking and returns to source-triangle vertex-colour fallback for diagnosis only.

## Remaining Phase 1 behavior checks

After V3 texture fidelity is accepted:

- [ ] Verify screen-relative WASD does not drive the detached/RTS camera while Link owns input.
- [ ] Verify actual X/Z travel continues through Matrix/server walking and collision.
- [ ] Consolidate idle, movement/turn, one A/action/jump, and B/sword animation in one session.
- [ ] Expose stable adult-Link skeleton/socket transforms for helmet, sword, shield, gloves, boots, amulet, cape, then torso/legs fitting.

## Later combat and menu acceptance

- [ ] Sword contact requires an active swing window and valid reach/target contact.
- [ ] Weapon speed changes attack cadence while RuneScape stats determine hit and damage outcomes.
- [ ] Valid damage grants configured per-hit skill XP; misses/blocks follow explicit XP rules.
- [ ] Inventory opens/closes while the Matrix world continues.
- [ ] Ranged, magic, and item actions remain usable through the same Link control owner.