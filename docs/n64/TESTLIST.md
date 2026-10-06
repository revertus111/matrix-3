# N64 Client Console Runtime Test List

## Normal-camera direction read - 2026-10-05

Status: implemented / `verified-static`; rendered native gameplay acceptance pending. The shared reader now dispatches through Class411.method4968/method4997 for normal orbit/target and detached cameras. The old RTS-only casts silently lost normal-camera rotation and fell back to unchanged north yaw.

One short acceptance session:

1. [ ] `git pull origin main`; Eclipse Java 8 refresh/clean/build Client, fully close/relaunch and login. No native rebuild required for this Java-only camera read.
2. [ ] Clipping OFF, Shift/Z released, normal player camera: Ctrl+M and test W/S/A/D at north/east/south/west. W must follow screen-up, S screen-down, A left and D right regardless of character facing. Hold W while turning the camera in both directions through a full circle; travel must turn with the view instead of staying north. Repeat with Ctrl+L.
3. [ ] Check diagonals/opposing keys, then repeat the cardinal/rotation checks in Construction RTS and Free views. Camera ownership must remain correct. Test Space/F and Link Shift/Z release; native actions/contact timing remain separate acceptance items.
4. [ ] Switch Mario -> Link -> RuneScape, then re-enter: no stale camera basis/offset. Normal RuneScape walking and camera control, a representative interface and teleport still work. This is the relevant startup/movement/lifecycle subset of docs/rs3/SMOKE_TEST.md.

Focused regression: `python3 tests/n64/test_shared_movement.py` passes 440,138 checks across 361 headings/all 16 WASD combinations in normal Class423_Sub3/Class658_Sub5 orbit, normal point/target, Construction and stock detached views, with inactive vanilla yaw held north. Both production native-input methods are exercised. The old shared controller fails at the first rotated normal-camera heading. Missing camera owners retain safe fallback. `test_free_movement.py` passes 6,089 production-driver checks and 112 production OoT interpolation samples. Java 8 target; these engine stubs are not rendered/native gameplay proof.

## Universal horizontal controller - 2026-10-05

Status: implemented / `verified-static`; rendered native gameplay acceptance pending. AlternateCharacterController owns one sampled control frame, all movement encodings and one shared horizontal state. Both native drivers delegate direction, free/clipped routing and restoration; native physics/actions remain engine-owned.

One consolidated acceptance session:

1. [ ] `git pull origin main`; Eclipse Java 8 refresh/clean/build Client and Server, fully restart and login. No native rebuild required. The newer shared combat refactor is preserved.
2. [ ] Clipping OFF, Shift/Z released: Ctrl+M, test W/S/A/D at opposite and quarter-turn camera headings. Hold W while orbiting; short taps must stop at sub-tile positions and long travel must have no tile clamp/walk requests. Test diagonals/opposing keys.
3. [ ] Switch directly to Ctrl+L and repeat the same direction/free-travel checks. Switch Link -> Mario -> Link while moving; no leftover offset, fixed-heading input or cleanup from the previous driver may affect the new character.
4. [ ] Enable clipping for each character. Both now use the same boundary-lead/pending-tile-approval path, not Link's former immediate tile-step path. Check blocked edges, legal crossings and reversal; disable clipping and confirm continuous free motion resumes without accumulated native-distance jumps.
5. [ ] Mario jump/backflip/ground-pound and F remain native. Toggle clipping during a jump: horizontal baseline changes must not reset native jump height. In Link, hold/release Shift/Z on an NPC; target-relative directions and F contact routing remain intact, and free camera steering resumes on release.
6. [ ] Exit to ordinary RuneScape: normal walking, click-to-walk and camera WASD return with no native offset; teleport and representative interface open/close still work. Logout/relog, re-enter both characters and verify clean baselines/bridge failure fallback. This is the relevant build/lifecycle/movement subset of docs/rs3/SMOKE_TEST.md; no server/cache/network changes are included.

Focused automation: `test_shared_movement.py` rejects duplicate movement ownership in the old drivers and passes 440,138 input/native-axis checks, including normal and detached camera types. `test_free_movement.py` compiles complete production controller/helper/drivers and passes 6,089 checks, including identical clipped traces, pending approvals, blocked-delta reversal, multi-tile corrections, late old-driver cleanup, jump preservation and target-relative clipping. OoT bridge interpolation: 112 samples pass. Java 8 target; no full client/native gameplay proof from these stubs.

Limits: native turning/acceleration/action rules still differ by source game. This controller unifies direction and horizontal host routing, not the native physics implementations. Free movement remains local presentation; clipping ON still uses stock tile authority and may restrict motion. Proper continuous RuneScape collision is not implemented by this consolidation.

## Native-axis steering correction - 2026-10-05

Status: `verified-static`; full client/native gameplay acceptance pending. Mario now sends neutral-camera world input (-X,-Z); Link negates only horizontal stick input and preserves its live camera/Z-target basis. Free movement and the clipping toggle are unchanged.

1. [ ] `git pull origin main`; Eclipse Java 8 refresh/clean/build the client, fully restart and login. No native rebuild is required for this input-only patch.
2. [ ] Keep `RuneScape clipping (tile-based)` OFF and release Shift/Z. Ctrl+M: separately test W/S/A/D at north/east/south/west camera headings. Travel must be screen-up/down/left/right regardless of initial character facing; allow native turning/acceleration.
3. [ ] Ctrl+L: repeat the same four directions and camera headings. Specifically verify A goes left and D goes right; W/S must retain their correct directions.
4. [ ] For both characters, hold W and orbit the rendered camera in both directions. Travel must follow the visible view, not keep one fixed world heading. Test all diagonals and opposing keys.
5. [ ] In Link, hold Shift/Z on an NPC and repeat A/D plus W/S. Target-relative input and native Z must remain intact. Release Shift/Z and verify ordinary camera-relative directions immediately resume.
6. [ ] Recheck sub-tile stops/long free travel, clipping ON/OFF, and native actions using the sections below. Do not mark gameplay accepted solely from the ROM-free checks.

## OoT Link Z-targeting / manual melee - 2026-10-05

Status: `verified-static`; runtime acceptance pending. Exact native sword-contact frame is a `HYPOTHESIS` until visually tested.

1. [ ] `git pull origin main`; Eclipse Java 8 refresh/clean/build client and server, then launch/login. No liboot/native rebuild is required for this Java-only slice.
2. [ ] Ctrl+L into Link near two attackable NPCs. Hold Shift/Z while facing one NPC. Console should print `[Alt Character Combat] target lock -> NPC index=...`; Link should keep the same target while it remains loaded and within the drop radius.
3. [ ] While still holding Shift, use W/A/S/D. Link/native movement should remain target-relative so left/right read as lock-on strafing/orbit behavior rather than reverting to the free camera basis.
4. [ ] Release Shift. Console should print `[Alt Character Combat] target lock released`, and ordinary camera-relative Link steering should resume immediately.
5. [ ] Hold Shift on an NPC and press F once. Native OoT sword animation must begin first; exactly one Matrix melee hit may be requested when the animation reaches the contact gate. Holding F must not create repeating RuneScape auto-attacks.
6. [ ] With Shift released, face an attackable NPC within roughly 3 tiles and press F. Forward fallback may hit that NPC; an NPC behind Link/camera-forward should not be selected by the fallback cone.
7. [ ] Move the locked NPC out of range, kill/despawn it, or leave its loaded region. Lock must drop/reacquire cleanly and no stale-index hit may occur.
8. [ ] Equip a ranged/magic weapon and press F in Link mode. Server must reject Link manual melee. Re-equip melee/unarmed and confirm melee authority resumes.
9. [ ] Verify normal right-click `Examine` still works. Stock NPC examine packets use reserved flag values 0/1; Link contact uses only flag 2 and must never display examine text.
10. [ ] Exit Link mode and attack/examine NPCs normally in RuneScape mode. Stock `ATTACK_NPC_PACKET`, normal examine, click-to-attack, damage/XP/death and other NPC options must remain unchanged.
11. [ ] Tune only if visual contact is early/late: JVM property `-Dmatrix3.oot.combatContactFrame=<frame>`. Record the accepted value before marking contact timing `VERIFIED`.

Expected authority: client owns target presentation and native animation gating; server owns attack legality, range, weapon style, cooldown, accuracy, damage, XP, NPC death and drops. The server runs one `PlayerCombatNew` cycle directly and does not install it into `ActionManager`.

## Live camera-relative steering correction - 2026-10-05

The controller now reads the rendered detached camera transform first. Verify held W while orbiting with mouse/Q/E; the travel vector must curve with the visible camera each tick.

## Default free movement / optional RuneScape clipping - 2026-10-05

Current priority: restore continuous native X/Z for Mario AND Link. The earlier tile handoff is runtime-rejected for free-movement feel; it remains available only through the optional checkbox.

1. [ ] Pull, Eclipse Java 8 refresh/clean/build, launch/login. No native rebuild required by this patch.
2. [ ] Client Console -> N64 header: `RuneScape clipping (tile-based)` starts unchecked. It controls both characters.
3. [ ] Ctrl+M: make short taps, reverse direction and move across 10+ tiles. Free movement must stop at arbitrary positions with no tile-boundary pause, clamp or tile-walk request. Recheck WASD while rotating the camera.
4. [ ] Jump/backflip/ground-pound and F retain native action behavior. World scenery intentionally does not block X/Z with clipping OFF.
5. [ ] Ctrl+M out, Ctrl+L in: repeat taps, diagonal/reverse motion and long movement. Link uses interpolated native X/Z and its existing uniform model scale.
6. [ ] With each character, enable clipping: local presentation returns to its tracked Matrix/server baseline and existing tile clipping resumes. This mode deliberately retains the old tile restrictions; it is not continuous world collision.
7. [ ] Disable clipping again, release/repress movement: free motion resumes without accumulated native-distance jumps or pending tile requests from the free path.
8. [ ] Exit/re-enter each character, switch Mario/Link, and logout/relog. No local free offset carries into normal RuneScape mode or another player instance.

Known limits: free movement is local presentation, not authoritative multiplayer travel. Normal Matrix corrections remain authoritative; an outstanding stock route can still correct presentation. Toggling clipping ON or exiting restores the underlying baseline. Native proof-floor/world bounds still exist. Proper continuous RuneScape world collision is future work.

Automated: `python3 tests/n64/test_free_movement.py` with JDK on PATH (or JAVA/JAVAC overrides). Production shared controller, movement helper and both drivers compile against engine stubs with Java 8 target; 6,089 checks pass, including identical free/clipped ownership paths. Complete OoT Java bridge compiles and 112 production interpolation samples pass. Full client/native runtime acceptance remains pending.

## Shared screen-relative WASD - Mario and Link (2026-10-05)

Status: implemented / verified-static; runtime acceptance pending. This steering patch does not fix the separately tracked tile-handoff hitching.

Automated check: `python3 tests/n64/test_shared_movement.py` with a JDK available (`JAVA`/`JAVAC` overrides supported). Compiles the complete shared controller/helper and extracts the actual Construction/native-input methods into dependency stubs. 440,138 checks pass, including one control sample per frame, identical world intent, 361 headings/all key combinations across normal and detached camera types, native-equation decoding to screen axes, and 12 Z-target headings/all key combinations. An ownership guard rejects duplicate native encoding/horizontal routing in the old drivers. This does not exercise the complete client, native simulation, rendered camera or server.

One runtime session; repeat for both Mario and Link:
1. [ ] `git pull origin main`; Eclipse Java 8 refresh/clean/build, then launch/login. No native rebuild for this patch.
2. [ ] With clipping OFF, in RTS camera face north/east/south/west and an intermediate heading. W/S/A/D must request up/down/left/right on screen regardless of the character's initial facing. Both characters use continuous native X/Z by default, not Matrix tile walking.
3. [ ] Hold W and rotate the camera through a full circle in both directions. Direction follows the view without a south-facing reversal; allow native turn/acceleration behavior.
4. [ ] Test W+D, W+A, S+D, S+A, then W+S and A+D. Diagonal input has unit magnitude; opposing keys cancel their axis.
5. [ ] Repeat cardinal directions in Construction Free and ordinary RuneScape camera. Rotate while moving; verify both travel and native facing/animation agree.
6. [ ] WASD does not also pan the camera while a character owns it. Exit to RuneScape and confirm ordinary camera WASD returns; switch Mario/Link and repeat.
7. [ ] Check Space/F/Shift actions, collision blocking and release/re-entry. Record native lock-on/action-specific steering separately; this patch preserves source-game action rules.

### Contract for future character drivers

Use shared `ControlState` for screen input, camera basis and `worldMoveX/worldMoveZ` movement intent. Never rotate by character facing. At the native boundary, encode either world intent with a neutral native camera or local input with the matching native camera basis; verify that engine's actual yaw/sign equations rather than assuming axes match.

Drivers MUST delegate to `AlternateCharacterController.movementInput`, `applyHorizontalMovement`, `restoreHorizontalMovement` and `resetHorizontalMovement`. Provide native X/Z and scale; keep actions/vertical pose in the adapter. Register the native input profile centrally. Do not create a per-driver mover, transform WASD, send walk packets, or implement another clipping/toggle/reset path. An explicit combat basis goes through the same world-intent resolver and horizontal policy.

- Mario: neutral camera (0,+1), stick (-worldX,-worldZ), verified-static libsm64 fd118132 input contract.
- Link: live camera/locked-target `inputForward`, stick (-moveX,+moveY). Pinned liboot 25208734 `src/liboot.c` scales stick by +67; `z_player.c` adds camera yaw to the control-stick angle; `z_lib.c` computes `Math_Atan2S(relY,-relX)`; `sys_math_atan.c` uses arguments (x,y), unlike C atan2(y,x). This yields the shared world intent when freely moving and preserves the existing target-relative basis with Z held. verified-static; native lock-on/action behavior still needs gameplay acceptance.
- Camera basis: the active rendered Class411 camera supplies general position/look getters for both normal and detached owners; Construction ownership/RTS pan convention and Matrix fallbacks are retained. Never cast every camera to Construction's Sub2 types.


## Temporary vanilla RS3 collision handoff

Runtime finding 2026-10-05: vanilla RS3 collision itself is `VERIFIED`, but the first continuous hybrid pass is runtime-rejected because every accepted tile visibly hitched when the collision baseline recentered. The current gate is the zero-snap pending-authority handoff.

1. [ ] `git pull origin main`, Eclipse Java 8 refresh/clean/build, launch/login once. No native sidecar rebuild is required for this Java-only collision/presentation slice.
2. [ ] Ctrl+M into Mario and tap W/A/S/D briefly. Mario must stop at genuine sub-tile positions instead of committing an immediate full tile step.
3. [ ] Hold one direction across at least 8-10 open tiles. There must be no periodic hitch, tile-centre tug, snap, pause cadence or visible rebase each time vanilla RS accepts another tile.
4. [x] Run directly into normal RuneScape collision. Vanilla collision blocks Mario from crossing scenery. `VERIFIED`; recheck after the zero-snap handoff.
5. [ ] Hold movement into a wall/solid object for several seconds. Mario may reach the legal half-tile boundary but must not visually cross it while the vanilla tile request is rejected.
6. [ ] Turn away from the blocked wall after holding into it. Mario must leave smoothly with no accumulated native-X/Z teleport or delayed snap.
7. [ ] Test repeated legal tile crossings followed by sudden direction changes. An accepted next tile must not queue additional hidden tiles before Mario reaches its shared boundary.
8. [ ] Test diagonal movement at a clipped corner. Illegal diagonal crossing must remain blocked; a legal cardinal component must not manufacture a through-corner jump.
9. [ ] Jump, backflip and ground-pound while moving into collision. Native action/animation/Y may continue, but visible X/Z must remain collision-valid and hitch-free.
10. [ ] Walk over normal terrain height changes; Matrix terrain/Y rebasing must remain stable under the native jump overlay.
11. [ ] Release movement just before, on and just after a legal tile boundary. There must be no correction toward either tile centre when movement stops.
12. [ ] Ctrl+M out and confirm normal RuneScape presentation restores to the current legal server-owned baseline without leaving Mario's local presentation offset behind.
13. [ ] Re-enter Mario mode and confirm there is no stale presentation offset, pending authority tile or unsolicited walk request.
14. [ ] Verify F/custom combat, Shift crouch/ground-pound, N64 diagnostics and sleep-guard behavior remain unchanged.

Acceptance note: this remains an interim Matrix/server collision owner. It does **not** mark the planned RuneScape-surface -> libsm64 Phase 3 collision adapter as implemented.

## Custom combat v3 - one consolidated acceptance session

### Runtime evidence accepted 2026-10-05

- [x] Equipped revision-830 sword visibly renders attached to Mario's hand in live Mario mode. `VERIFIED`.
- [x] F triggers the custom native MARIO_COMBAT_1H_SLASH animation. `VERIFIED`.
- [x] Core protocol-v3/socket/request path is active at runtime; Java cannot enter custom weapon combat without v3 + live hand socket + rendered weapon readiness.
- [x] V1 visual result: slash works but reads as a slow little punch. `VERIFIED`.
- [x] V2 was rebuilt successfully with the Native Builder but still reads as a custom punch. `VERIFIED` rejection.
- [x] V3 runtime video shows Mario folding forward and the weapon hand collapsing toward the floor. `VERIFIED` rejection.
- [x] Source follow-up confirms native B is already suppressed while custom weapon mode is active; the V3 deform is the custom pose, not stock punch leakage. `verified-static`.
- [ ] V4 visual acceptance: torso remains upright while the shoulder/forearm/wrist produce a readable one-handed sword cut.
- [ ] Moving slash / native leg continuity is still pending unless separately confirmed.
- [ ] Held-F no-replay, preview-only button behavior, freeze/unfreeze, unequip fallback and Ctrl+M/relog cleanup remain pending.
- [ ] Remote-player isolation, nearby-NPC server combat/damage/XP and sustained stability remain pending.

The first attempted acceptance run did **not** exercise v3: Windows failed to relink `dist/sm64_bridge.exe` with `Permission denied` because the executable was locked. The root Native Builder now handles the correct directory and stale-process shutdown automatically.

1. [ ] `git pull origin main`.
2. [ ] Double-click root `Native Builder.bat`, then click `BUILD + TEST MARIO`; wait for `MARIO BUILD + TEST SUCCESS`.
3. [ ] Launch/login once.
4. [ ] Equip a normal one-handed sword, longsword or scimitar; Ctrl+M into Mario.
5. [ ] Console startup reports `combat-socket-v3`; N64 -> Mario 64 -> Custom combat shows RIGHT_HAND AVAILABLE and weapon ID/name.
6. [ ] Sword follows the right wrist while idle/turning/running. Adjust grip scale/XYZ/angles if needed; the bounds-based starting grip is NOT visually verified.
7. [ ] Press F once: V4 should finish in about half a second. Mario's torso must stay upright; the right shoulder should carry the main sweep, with only a restrained forearm bend and wrist roll. The weapon must no longer dive toward the floor or fold Mario's upper body.
8. [ ] Run and press F: legs continue native running; sword follows hand through the complete swing and recovery.
9. [ ] Play 1H slash button previews only (no new server attack request); optional equipped-weapon override is explicit.
10. [ ] Freeze in Equipment Workbench: body and sword freeze together. Unfreeze before further attacks.
11. [ ] Unequip or disable custom combat: current native F/punch behavior returns. Re-equip/re-enable without F: no unsolicited slash.
12. [ ] Ctrl+M out/re-enter; logout/relog: no floating/stale weapon, normal RuneScape body restored.
13. [ ] WASD at different headings, jump, backflip, ground-pound, helmet/head-shell workbench and ordinary remote-player rendering remain correct.
14. [ ] Nearby-NPC F still uses existing server combat rules; visual blade contact timing is not part of this proof.
15. [ ] Long idle: native frames continue and sleep guard remains active; no model-build error spam.

Older v1/v2 sidecar: Java connects with original STEP layout, custom combat reports native v3 required, native Mario remains usable. Older Java cannot use the v3 sidecar; update both together.

## Earlier semantic/helmet acceptance checks (retained)

## One required V7 rebuild / acceptance session

### Build

1. [ ] `git pull origin main`.
2. [ ] From `native/sm64-bridge`, run `make bootstrap`.
3. [ ] Confirm the pinned libsm64 dependency is rebuilt and the semantic bridge executable/runtime library are copied into `dist`.
4. [ ] Eclipse Java 8 clean/build succeeds.
5. [ ] Launch/login normally.

### Semantic bridge

1. [ ] Enter Mario mode with Ctrl+M.
2. [ ] Native stderr prints `[SM64 Bridge] semantic-geometry-v2 + sleep-guard-v2 active`.
3. [ ] Java prints `[SM64 Bridge] Persistent session READY (30 Hz + semantic geometry v2)`.
4. [ ] Open `Client Console -> N64 -> Mario 64 -> Equipment Workbench`.
5. [ ] `Bridge protocol` shows `v2`.
6. [ ] `Semantic geometry` shows `AVAILABLE`.
7. [ ] `Shared FACE W/H/D` contains finite values.
8. [ ] Protected part counts show FACE and the current eye/moustache geometry; removable counts show available CAP / SIDEBURN / BACK HAIR geometry.

### Nose-safe full helmet proof

1. [ ] Equip Statius's full helm.
2. [ ] Start with `Keep all Mario head parts`; Mario's original head remains complete.
3. [ ] Press `Full helm safe`.
4. [ ] Mario's tagged CAP and named hair geometry disappear.
5. [ ] FACE, EYES and MOUSTACHE remain.
6. [ ] Mario's nose remains because the complete mixed FACE mesh is protected.
7. [ ] Masked source triangle count is greater than zero.
8. [ ] Press `Keep all Mario head parts`; removed pieces return immediately.
9. [ ] Freeze Pose, switch between Keep All / Full Helm Safe, and confirm the same frozen frame rebuilds immediately.
10. [ ] With `Apply semantic coverage only while a helmet is equipped` enabled, unequip Statius and confirm all Mario head parts return.

### Fit / animation

1. [ ] Console helmet log says `fitReference=semantic-face`.
2. [ ] Auto-fit log says `reference=semantic-face`.
3. [ ] Initial scale is based on shared FACE width rather than full cartoon-head containment.
4. [ ] Freeze Pose and use Scale / head-local X/Y/Z / Yaw only for small final corrections.
5. [ ] Unfreeze and verify idle/turn/run follows the accepted head direction.
6. [ ] Jump/backflip/ground-pound keep the helmet attached with no inverse/doubled rotation.
7. [ ] `Reset / recalc fit` restores the mathematical baseline for the active helmet.
8. [ ] `Save profile .md` records semantic protocol, coverage, part counts and transform values.

## Protocol-v1 fail-open

- [ ] An intentionally old v1 bridge still allows Mario to render.
- [ ] Workbench reports semantic metadata unavailable rather than crashing.
- [ ] Semantic coverage does not hide anonymous triangles on v1.
- [ ] The old height/radius geometric cutter is available only as an explicit v1 debug fallback.
- [ ] Unknown semantic part ids are always preserved.

## Long-idle / sleep-state regression

- [ ] Leave Mario idle for at least 90 seconds.
- [ ] Native sequence keeps advancing and frame age stays low.
- [ ] Published stream does not remain in `ACT_START_SLEEPING (0x0C400202)` or `ACT_SLEEPING (0x0C000203)`.
- [ ] RuneScape body suppression remains active throughout Mario mode.
- [ ] Jump/movement/attack/crouch still work after the long idle.

## Recorder / lifecycle regression

- [ ] Runtime recorder Clear/Copy/Pause/Auto-scroll controls still work.
- [ ] Ctrl+M back to RuneScape restores normal player body/input.
- [ ] No floating helmet, frozen pose, stale coverage, or Mario masking remains after exit.
- [ ] Re-enter Mario mode and semantic metadata/helmet attachment initialize cleanly.
- [ ] Normal RuneScape appearance/server authority remain unchanged outside Mario mode.
## Camera-relative movement regression

- [ ] Normal player camera, Shift/Z released: rotate north/east/south/west and hold W in Mario and Link; camera-relative intent must rotate while inactive vanilla yaw may remain fixed.
- [ ] Hold W, rotate the RTS camera 90 degrees, and confirm Link follows screen-up immediately.
- [ ] Repeat with A/S/D and Mario; no direction may continue along the previous world heading.
