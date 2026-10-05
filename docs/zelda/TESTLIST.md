# Zelda / OoT Test List

## Runtime baseline already accepted

The user's exact NTSC-U 1.2 ROM remains the local OoT source. ROM bytes and ROM-derived textures must never be committed.

Verified runtime baseline:

- adult OoT Link renders inside Matrix3;
- Adult Link is uniformly scaled into approximately the revision-830 player height/floor envelope;
- the local 830 player body is suppressed only while Link presentation is healthy and returns fail-open on exit/failure;
- protocol V2 geometry/material transport works;
- the active CPU RGBA bake visibly uses real OoT texture data;
- the later V4 filter/smoothing pass did not materially improve the look, but the signed-short overflow correction restored stable Link rendering;
- Link B/sword still does not issue Matrix NPC damage/XP.

The current equipment work deliberately leaves Adult Link at this larger 830-compatible scale. The point is to keep revision-830 equipment close to native Matrix scale rather than heavily shrinking every item around native-size OoT Link.

---

## Adult Link skeleton sockets + first 830 helmet proof

### Architecture under test

This is **not** the Mario equipment architecture.

liboot already exports Adult Link's real animated skeleton pose in world space. Its joint array is indexed as `PLAYER_LIMB_* - 1`, specifically for semantic host attachments. Protocol V3 now streams that parent table + all world-space joint positions every native frame.

Current semantic indices include:

```text
HEAD       = 10
HAT        = 11
COLLAR     = 12
L_SHOULDER = 13
L_HAND     = 15
R_SHOULDER = 16
R_HAND     = 18
SHEATH     = 19
TORSO      = 20
```

`LinkSkeletonSockets` converts those native joints through the same `LinkCharacterFit` transform used by the body renderer. The first equipment proof uses the live HEAD/HAT/COLLAR/shoulder pose to place/orient the currently equipped revision-830 hat-slot item.

The helmet model itself remains near native Matrix scale:

```text
matrix3.oot.helmetScale = 1.0   (default)
```

The worn model is recentered, but it is **not** auto-fitted through Mario's geometry envelope or Mario equipment workbench.

### Native rebuild required once

Protocol changed from V2 -> **V3** to carry the skeleton pose, so the local OoT sidecar must be rebuilt once after pulling.

1. Pull:

```sh
git pull origin main
```

2. Run the repository's root **Native Builder.bat**.
3. Choose **BUILD OOT**.
4. Confirm the OoT build ends successfully and produces the fresh `native/oot-bridge/build/dist/oot_bridge.exe`.
5. In Eclipse/Java 8: Refresh Client -> Clean Client -> Run.

Do not manually open MSYS2 unless the one-click builder itself reports a toolchain failure; the builder already launches the proven UCRT64 environment.

### Before Ctrl+L

Equip a normal revision-830 **hat/head-slot item** on the RuneScape player. The proof intentionally reads the actual currently equipped visible hat-slot worn model.

Then press **Ctrl+L**.

### Expected protocol proof

```text
[Alternate Character] Controller mode: LINK
[OoT Bridge] persistent NTSC-U 1.2 session READY (20 Hz, protocol v3 + materials + skeleton)
[OoT Bridge] Persistent session READY (20 Hz + Link materials + skeleton, protocol v3)
[OoT Fit] ADULT Link profile source=830-auto ...
```

If the equipped item is recognized and the real OoT HEAD socket resolves, expect:

```text
[OoT Equipment] 830 HELMET -> ADULT Link HEAD ACTIVE item=... name=... joint=10 skeletonJoints=21 scale=1.0 rawW/H/D=... offset=0.0/0.0/0.0 rotDeg=0.0/180.0/0.0 source=liboot-skeleton-v1
```

If no valid hat-slot item is equipped, expect:

```text
[OoT Equipment] No revision-830 hat-slot item equipped; equip a helmet to test the Adult Link HEAD socket
```

### Acceptance

In one session verify:

- [ ] Both native and Java READY lines report **protocol v3**.
- [ ] Adult Link still renders alone at the previously accepted 830-compatible scale.
- [ ] `skeletonJoints=21` is reported for the normal Adult Link frame.
- [ ] A currently equipped revision-830 helmet appears with Link.
- [ ] Helmet starts near native Matrix size (`scale=1.0`) rather than being heavily auto-shrunk.
- [ ] Helmet is anchored to Link's head rather than the RuneScape player's hidden body origin.
- [ ] Idle/turn/action motion moves the helmet with the animated OoT head socket instead of leaving it world-fixed.
- [ ] Ctrl+L exit restores the normal 830 player and its normal equipment presentation.
- [ ] Mario Ctrl+M behavior is unchanged.

For this first proof, **visual misalignment is not a compatibility failure**. If the helmet is visibly present but slightly too high/low/rotated, capture the screenshot before calibration.

### Calibration knobs (only after preserving the first screenshot)

```text
-Dmatrix3.oot.helmetScale=<positive float>
-Dmatrix3.oot.helmetOffsetX=<float>
-Dmatrix3.oot.helmetOffsetY=<float>
-Dmatrix3.oot.helmetOffsetZ=<float>
-Dmatrix3.oot.helmetPitchDegrees=<float>
-Dmatrix3.oot.helmetYawDegrees=<float>
-Dmatrix3.oot.helmetRollDegrees=<float>
-Dmatrix3.oot.headSocketHatBlend=<float from -1 to 2>
```

Defaults:

```text
scale = 1.0
X/Y/Z offset = 0
pitch = 0 degrees
yaw = 180 degrees
roll = 0 degrees
head->hat anchor blend = 0.35
```

Do not randomly tune multiple values at once. A screenshot showing where the helmet lands relative to Link's head is enough to make a targeted correction.

### Failure distinctions

- Java reports `unsupported OoT bridge protocol version: 2 ... protocol v3` -> the old sidecar is still present; rerun Native Builder -> BUILD OOT.
- native build fails -> preserve the builder output; do not patch Java first.
- `Adult Link HEAD socket unavailable joints=...` -> inspect streamed skeleton count/semantic joint data; do not fall back to Mario geometry fitting.
- no helmet message -> equip an actual hat-slot item and retest.
- `worn raw model unavailable` -> test a different normal 830 helmet and preserve the item ID/name.
- helmet appears at roughly correct location but faces backward -> orientation calibration only; preserve screenshot, then adjust yaw.
- helmet tracks position but rolls/pitches strangely -> head basis calibration; preserve screenshot and animation/action used.
- helmet stays fixed while Link animates -> skeleton/socket consumption regression.
- Link disappears and normal 830 body returns -> fail-open presentation activated because Link render became unhealthy; preserve the first preceding OoT error.

---

## Next equipment sockets after helmet proof

Do not implement these until the HEAD proof is runtime accepted/calibrated:

- [ ] Right hand -> 830 weapon.
- [ ] Left hand/forearm -> 830 shield.
- [ ] Feet -> boots.
- [ ] Upper back/sheath -> cape/back equipment.
- [ ] Hands -> gloves.
- [ ] Neck/collar -> amulet.
- [ ] Torso/waist multi-joint fitting for body/legs last.

The goal remains: **Adult Link stays OoT-shaped and 830-scale; 830 gear stays near native scale and follows real OoT skeleton sockets, with only small saved corrections where an item needs them.**

## Later combat/menu acceptance

- [ ] Sword contact requires an active swing window and valid reach/target contact.
- [ ] Weapon speed changes Zelda-style attack cadence while RuneScape stats determine hit/damage outcomes.
- [ ] Valid damage grants configured per-hit skill XP; misses/blocks follow explicit XP rules.
- [ ] Inventory opens/closes while the Matrix world continues.
- [ ] Ranged, magic, and item actions remain usable through the same Link control owner.
