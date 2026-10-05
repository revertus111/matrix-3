# Mario custom combat proof

Status: RUNTIME PROOF ACCEPTED / V3 VISUAL TUNING PENDING. Approved by the 2026-10-04 SAP AAA handoff.
Primary target: MARIO_COMBAT_1H_SLASH (wire family 1) is runtime-VERIFIED 2026-10-04. Stab remains deferred until explicitly selected as the next scope.

## Source-established ownership (verified-static)

Dependency: libsm64 `fd11813208272b4271d92bd92feb8f3fdbe61be5`.
Its `import-mario-geo.py` imports Mario geo/model from n64decomp/sm64
`06ec56df7f951f88da05f468cdcacecba496145a`, `actors/mario/geo.inc.c`.
The generated `src/decomp/mario/geo.inc.c` is not a tracked libsm64 source file.

- A/B: `AnimInfo.curAnim` owns the base animation; `geo_set_animation_globals` selects its values/index/frame. `geo_process_animated_part` samples three signed 16-bit rotation channels into a LOCAL `Vec3s`.
- C: offsets are added after channel sampling and before `mtxf_rotate_xyz_and_translate`; animation tables, node translations, persistent AnimInfo and action state are untouched by the overlay.
- D: active animated-part order is root 0, pelvis 1, torso 2, head 3, left shoulder/arm/forearm/hand 4..7, right shoulder/arm/forearm/hand 8..11, legs 12..19. Separate forearm and hand articulation exists.
- E: closed/open right-hand branches contain `GEO_HELD_OBJECT` and `geo_switch_mario_hand_grab_pos`. Other hand variants do not, so the proof captures the common animated hand matrix BEFORE child hand scaling/held-object handling. This is a wrist socket, with an explicit grip calibration, not a claimed universal held-item grip.
- F: legs branch from pelvis beside torso, so torso/right-arm offsets do not replace leg channels. Every sampled leg rotation remains native.
- G: each native tick runs movement first, then geo traversal. `geo_set_animation_globals` advances the normal animation frame; the overlay edits temporary sampled rotations only. It is not a second simulation/render tick.
- H/I: signed angle units span 65536 per turn. Animated parts use XYZ construction (`Rz*Ry*Rx` for column vectors), unlike the object's ZXY transform. Adding offsets means additive local Euler CHANNEL offsets, not world-space rotations. The original matrix builder/composition remains authoritative.
- J: capture `gMatStack[gMatStackIndex]` immediately after the animated right-hand matrix is composed with its parents. The single-Mario root starts from identity and then includes object pose/scale; output is the same native world space as exported triangles, not a camera-relative transform.
- K: four pinned dependency files are extended by the existing tracked patch: gfx adapter C/header, libsm64 header and rendering_graph_node.c. No action/physics fork is introduced.

Torso/arm/forearm display-list identity guards cover normal, metal and LOD variants before hand index 11 is accepted. Output availability resets every bind. An unexpected layout cannot reuse the previous hand matrix.

## Implementation and boundaries

`combat_overlay.h` owns the keyframed slash. V1 used 24 ticks (0.8 seconds) and was runtime-reported as a slow punch. V2 reduced that to 16 ticks (~0.53 seconds) and added more forearm/wrist motion, but runtime feedback still classified the result as a custom punch rather than a sword cut.

V3 corrects the pose using the established local geometry rather than another arbitrary axis mix. The right-hand node translation is `(60,0,0)`, so the articulated right-arm chain advances along local +X. Rotation around local X therefore primarily rolls/twists that chain, while local Y/Z produce visible hand displacement arcs. V2 over-weighted X/vertical motion and under-weighted Y sweep. V3 uses a large local-Y shoulder/forearm sweep for the cross-body cut, Z for lift/drop, and X mainly for forearm/wrist blade roll. Duration is 14 ticks (~0.47 seconds). These V3 artistic angles remain HYPOTHESIS until runtime-accepted.

Smoothstep segments begin and end with zero offsets. Native request numbers latch short Java attack edges; held requests cannot replay. Requests while the slash is active are consumed without restart or queuing. Disabling mode cancels the overlay.

Bridge protocol 3 retains all v2 semantic fields. After A/B/Z, STEP adds mode u8 and request u32 LE. After frame part ids, FRAME adds socket available u32, 16 float32 matrix elements in native Mat4 memory order, animation u32, normalized time float32, and weight float32. Java still accepts v1/v2 and sends their original 20-byte STEP. V3 STEP is 25 bytes. Rebuild library AND sidecar together with the root `Native Builder.bat` -> `BUILD + TEST MARIO` button.

`MarioWeaponCombat` uses the existing equipped appearance, slot 3 and `ItemDefinitions.method7531` worn-model/customization path. V1 automatically selects conservative sword/longsword/scimitar names; this is a temporary proof policy, NOT a verified cache combat-family mapping. The explicit developer override permits other equipped weapons for preview. No item-ID list or invented sword mesh is used.

F suppresses native B only when v3/socket/weapon rendering are ready. The existing server combat-intent call remains in place; this proof does NOT synchronize damage to blade contact or add a new damage formula. Play slash is presentation-only. No supported weapon or an old bridge retains native B behavior.

Matrix transform is `S * handBasis * calibrationRotation * S`, where `S=diag(1,-1,1)`. Native translation is rebased against the SAME frame's native Mario position and uses the existing Mario mesh scale. `Class261.method3572` takes columns; `method3582` establishes the actual point multiplication. Two Y mirrors preserve weapon handedness. Rendering occurs immediately after Mario's successful body render using that exact immutable GeometryFrame; freeze preserves the socket too.

Grip origin from worn-model bounds (85% down Y), initial length and artistic slash keyframes are HYPOTHESIS / visual calibration seeds. They are not verified hilt metadata. Custom combat tab exposes scale, hand-local XYZ and yaw/pitch/roll. Settings are session-only. Torso/head/equipment follow their existing articulated paths; no Java triangle animation is introduced.

## Checks completed

- Exact pinned patch applies; patched libsm64 and sidecar compile on Linux GCC.
- Production slash endpoints, bounded weights and request lifecycle pass `make test-combat`.
- The committed ROM-free frame fixture manually seeds `rightHandAvailable` and a synthetic hand matrix; it validates v3 writer/parser alignment but does NOT independently prove live Mario joint/socket traversal.
- Production Java parser consumes two C-written v3 frames with exact alignment; v1/v2 compatibility, semantic/socket fields, frozen metadata and truncation checks pass.
- Actual Class261 transform produces the expected rotated/scaled/Y-converted point.
- Java 8 release compile: bridge and new weapon class (weapon class uses isolated Matrix API stubs); Java 8 syntax parse: all changed Java files.
- First Windows `make bootstrap` attempt failed at the final sidecar link because `dist/sm64_bridge.exe` was locked (`Permission denied`), so that first in-game attempt did not exercise v3 custom combat.
- Runtime 2026-10-04: equipped revision-830 sword renders attached to Mario's hand in the live Matrix3 client. `VERIFIED`.
- Runtime 2026-10-04: pressing F triggers the custom native MARIO_COMBAT_1H_SLASH animation. `VERIFIED`.
- Runtime 2026-10-04: V1 works but reads as a slow punch. `VERIFIED` visual feedback.
- Runtime 2026-10-05: after rebuilding the V2 profile successfully through the verified Native Builder, the attack still reads as a custom punch. `VERIFIED` visual rejection of V2.
- Source follow-up for V3: hand translation `(60,0,0)` plus the established XYZ composition proves the arm chain is +X-aligned; dominant local-Y sweep is therefore the correct axis family for a horizontal cross-body hand arc. `verified-static`.
- Moving-slash leg continuity, held-F no-replay, freeze/unfreeze, unequip fallback, Ctrl+M/relog cleanup, remote-player isolation, NPC damage/XP integration and sustained stability remain pending unless separately runtime-confirmed.

## Optional repeatable checks

Use root `Native Builder.bat` -> `BUILD + TEST MARIO`; the launcher rebuilds the patched library + sidecar and runs `make test-combat` automatically.

Runtime checklist: `docs/n64/TESTLIST.md`, custom combat section. Resume state: `docs/mario/PROJECT.md`.
