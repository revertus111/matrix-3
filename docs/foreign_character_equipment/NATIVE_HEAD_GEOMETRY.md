# Mario helmet fitting: native semantic geometry checkpoint

2026-10-04. Approved continuation; inspection only, no runtime implementation in this checkpoint.
Matrix3 main inspected at 928892a295ba79d310faa25669084eda8ae02a0f.
Only root AGENTS.md used, as explicitly requested.

## Decision

Preserve native display-list identity before flattening, then let the existing Java presentation owners apply coverage profiles. Combine semantic masking with one shared head-local fitting reference. Full helmets should replace covered head shell rather than contain the cap/hair/full original skull. Never automatically remove Mario's nose.

Do not replace MarioVisualRenderer, MarioEquipmentAdapter, the native controller, or the accepted equipment transform convention. No new equipment slots.

## Current pipeline — verified-static

- MarioEquipmentAdapter loads ItemDefinitions.method7531 with appearance gender/customization; centers raw geometry on its outer AABB, builds/caches the Matrix model.
- Adapter reference capture selects the upper 50% of the current body-height bounds within a central radial limit, then trims 6% X/Z extremes. Despite comments calling this upright, capture has no explicit upright-pose gate.
- Reference head span plus 5% per-side clearance is divided by outer helmet horizontal span. Statius's reported 150.9768 / 190.0 = 0.7946148 is consistent with that formula.
- MarioHelmetAutoFit separately measures CURRENT geometry, uses 72% horizontal/82% vertical estimated cavity fractions, and divides its desired scale by a separately reconstructed base scale. This is not the adapter's captured reference measurement. Pose/reference mismatch can therefore affect the final multiplier. The helper also lacks the adapter's candidate fallback.
- Calibration resolves per item once; manual edits lock it. Its measurement does not establish a real helmet cavity.
- MarioHeadOrientationTracker captures top/bottom/side groups from the upper selected region and tracks stream indices. Triangle-count equality is its topology check; equal count alone cannot prove identical semantic vertex ordering.
- Tracker side comes from the wider X/Z extent; its variable named forward is a cross-product basis axis, NOT a proven anatomical forward vector. faceAngle is used separately for helmet base yaw. Do not reuse that basis axis as a nose/front classifier.
- Existing head delta uses the documented transpose correction; leave it intact until an independently validated replacement exists.
- Workbench mask computes a height/radius cylinder from each frame's whole-body bounds. It tests triangle centroids and has no explicit face/nose exclusion. It is not a head-local or semantic mask.
- MarioVisualRenderer filters before tessellation. Adapter still sees original geometry. This is the correct existing masking seam to retain.
- Sm64BridgeSession and sm64_bridge.c use binary protocol v1, exporting state and positions/colors/UVs without part IDs or local geometry.

## Why the fit looks wrong

VERIFIED by the supplied user handoff: full-head containment produces oversized/egg-like helmets; smaller helmets intersect Mario's original head.

HYPOTHESIS explaining that result: cap, hair and skull envelope dominate a stylized character's required containment volume. Estimated cavity fractions increase containment scale without solving the incompatible silhouette. Outer-bound center also includes decoration and does not establish the wearer/socket center. Oriented bounds fix pose-dependent measurements but cannot fix that design mismatch alone.

## Native evidence — verified-static

Inspected libsm64 source:
- src/gfx_adapter.c (blob ba7fc1ded9f0af60ea548fc8542914f5ee1e86d2).
- src/gfx_adapter.h.
- import-mario-geo.py at fd11813208272b4271d92bd92feb8f3fdbe61be5.

The importer fetches these exact n64decomp/sm64 files at 06ec56df7f951f88da05f468cdcacecba496145a:
- actors/mario/geo.inc.c
- actors/mario/model.inc.c

Generated libsm64 location is src/decomp/mario, not src/decomp/actors/mario.

process_display_list retains the current display-list pointer and local Vtx coordinates while handling GFXCMD_Triangle. It applies s_curMatrix to local positions, writes flattened geometry, and recurses for GFXCMD_SubDisplayList. That is a concrete export seam: capture list identity and optional local geometry BEFORE the transform. Scope identity across recursion; never emit process addresses as portable IDs.

Source has explicit display lists:
- mario_face_cap_dl
- mario_face_back_hair_cap_on_dl
- mario_hair_sideburn_cap_on_dl / cap_off equivalent
- mario_face_hair_cap_off_dl
- mario_eyes_cap_on_dl / cap_off equivalent
- mario_mustache_cap_on_dl / cap_off equivalent
- mario_face_part_cap_on_dl / cap_off equivalent
- low-poly equivalents (names vary, including mario_low_poly_mario_eyes_cap_off_dl).

The geo layout switches cap-on/off and eye states; medium-poly uses the high-poly face. Metal paths reuse several head lists. Held-cap geometry is separate and must not be mistaken for head cap geometry.

No separately named nose list was found in these inspected head display lists. The face-part lists contain multiple vertex batches; batch boundaries are NOT anatomical labels. Do not claim that ears, nose and skull are independently identified.

Matrix's Makefile clones upstream libsm64 into ignored .deps/libsm64 without a pinned revision. The user's installed dependency revision remains UNKNOWN. Native changes must be reproducible from tracked Matrix files and validate/pin a compatible upstream version; never rely on an uncommitted .deps edit.

## Practical coherent implementation bundle

1. Add a reproducible native adapter patch against an explicit supported dependency revision. Export compact stable part IDs with each triangle; unknown IDs preserve geometry. Include topology/variant identity and version/capability handling. Retain v1 compatibility or provide an explicit actionable rebuild error.
2. Preserve original head-local positions or a validated head socket transform from the same native render path. Do not infer anatomical forward from the existing cross product; validate against native facing and source landmarks.
3. Resolve face/skull segmentation in the pinned source mesh. Protect eyes, moustache and the entire mixed face mesh until a nose-safe subdivision is established. Cap/hair removal alone is safe initial coverage but does NOT complete full-skull replacement.
4. Add coverage policies (keep head, remove cap/hair, replace shell) separate from fitting/seat settings. Unknown head-slot items default to non-destructive coverage. Do not hardcode Statius or classify every head-slot item as full helmet.
5. Feed attachment and auto-fit from ONE cached head-local reference/profile. Measure an intended replacement shell/face frame for full helmets rather than containing geometry that will be hidden. Keep uniform scale and cached worn models. Helmet outer bounds remain a fallback, not a verified cavity.
6. Extend existing workbench with coverage override, protected-region/part visualization, metadata availability and profile export. Keep per-item corrections for genuine exceptions.
7. Deliver native + Java + workbench + docs together; one rebuild and consolidated visual test after static protocol/geometry checks.

Likely files: native/sm64-bridge/Makefile, tracked native patch/export helper, sm64_bridge.c; Sm64BridgeSession, MarioEquipmentAdapter, MarioHeadOrientationTracker, MarioHelmetAutoFit, MarioEquipmentWorkbench, console/N64Panel, and minimal MarioVisualRenderer hook only if needed. Subject PROJECT/TESTLIST/patchnotes and transform docs must reflect actual implementation.

## Exact remaining uncertainty / next trace

Full automatic shell replacement is not sufficiently established to patch safely yet. The next bounded trace is ONLY the mixed face-part vertex/triangle lists above: establish nose/central-face versus side/back skull regions using their actual mesh connectivity and local coordinates, with a visual part preview. Do not interpret a batch number as a body part. Separately establish the native socket transform's conversion using known faceAngle; no new guessed yaw/sign changes.

No further Matrix scene/controller/cache scan is needed. Existing equipment/render owners and native export seam are established.

## Consolidated acceptance after implementation

- Static: metadata length/count bounds, known/unknown IDs, recursive identity restoration, protocol v1/v2 behavior, frozen-frame metadata preservation, cap/eye/LOD variants, no part leakage to torso/held cap, no per-frame helmet rebuild.
- Runtime: Statius plus an open helmet and hat/crown; default silhouette, visible nose/face, cap/hair coverage; rotate/nod/jump/backflip/ground-pound; frozen edits; swaps/unequip; Mario mode exit/re-entry.
- Explicitly judge visual fit, not only transform correctness. Numeric category defaults remain HYPOTHESIS until accepted visually.

No client restart or runtime test is requested for this documentation-only checkpoint.
