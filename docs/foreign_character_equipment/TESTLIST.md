# Foreign Character Equipment Runtime Test List

## Mario replacement-shell helmet bundle

Run this as **one client session**. V8 is Java/workbench-only. If semantic protocol v2 is already built and launching, **do not rebuild the native bridge**.

### Build

1. [ ] `git pull origin main`.
2. [ ] Eclipse clean/build succeeds under Java 8.
3. [ ] Client launches/login succeeds normally.

### Protocol / metadata sanity

1. [ ] Enter Mario mode with Ctrl+M.
2. [ ] Java reports `Persistent session READY (30 Hz + semantic geometry v2)`.
3. [ ] `N64 -> Mario 64 -> Equipment Workbench` reports `Bridge protocol = v2`.
4. [ ] `Semantic geometry = AVAILABLE`.
5. [ ] Shared FACE W/H/D is finite and no longer raw-local `538/320/530` scale.
6. [ ] Native part counts show non-zero FACE and expected head-part counts.

### Replacement-shell proof

Use Statius or a helmet with a visible face opening.

1. [ ] Freeze Pose.
2. [ ] `Keep all` restores Mario's normal complete head beneath the helmet.
3. [ ] Press `Head shell closed`.
4. [ ] FACE/EYES/MOUSTACHE/CAP/HAIR semantic triangles are all removed from Mario.
5. [ ] The 830 helmet remains attached and becomes the visible head shell.
6. [ ] Auto-fit logs `fitMode=replacement-shell` and `manualScale=1.0` after the shell profile causes recalculation.
7. [ ] Helmet is materially smaller/more intentional than the old cavity-inflated egg result.
8. [ ] Press `Head shell + Mario face`.
9. [ ] Mario eyes and moustache return.
10. [ ] Only the front portion of mixed FACE returns; cap/hair and rear/side FACE shell stay removed.
11. [ ] Mario's recognizable face/nose is visible through or in front of the equipment opening.
12. [ ] `Face-insert source triangles` is greater than zero.
13. [ ] Raise `Mario FACE front slice %`; fewer rear/side FACE triangles survive.
14. [ ] Lower the percentage; more cheeks/side face return.
15. [ ] Changes rebuild immediately while pose remains frozen.

### Helmet seating / animation

1. [ ] Use helmet Scale/X/Y/Z/yaw only for small shell corrections.
2. [ ] Unfreeze and verify idle/turn/run attachment.
3. [ ] Jump/backflip/ground-pound preserve shell orientation/seat.
4. [ ] Unequip helmet with helmet-only coverage enabled; Mario head returns immediately.
5. [ ] Re-equip; selected shell profile resumes without stale geometry.
6. [ ] `Keep all` restores all semantic Mario head parts.
7. [ ] Ctrl+M exit/re-entry leaves no floating helmet, frozen pose or stale shell mask.

### Decide the next refinement from evidence

- [ ] If `HEAD_SHELL_FACE` looks good with helmet transform alone, **do not add a separate face renderer/transform**.
- [ ] If the helmet opening is right but Mario's face itself is clearly too large/small or misplaced, add independent face scale/XYZ as the next isolated refinement.
- [ ] If closed shell looks strong on closed helmets, promote it as a helmet-family replacement profile rather than returning to cavity math.

### Legacy compatibility / fail-open

- [ ] Protocol-v1 still renders Mario and reports semantic metadata unavailable.
- [ ] Semantic replacement profiles do not delete unknown triangles.
- [ ] Legacy geometric cut remains debug fallback only.
- [ ] Invalid semantic reference falls back to established legacy fit rather than breaking Mario.
- [ ] Normal RuneScape appearance is unchanged outside Mario mode.

## Runtime evidence already established

- [x] Real revision-830 helmet renders on Mario.
- [x] Old Statius fit `0.7946148` looked wrong when treated as a containment problem.
- [x] Full-head containment can produce an unacceptable oversized silhouette.
- [x] Protocol-v2 semantic bridge rebuilt and launched successfully on Windows.
- [x] First semantic fit exposed raw-local FACE W/H/D `538/320/530` being compared against Matrix item bounds, causing Statius `fit=5.20218` and similarly oversized helmets across multiple items.
- [x] V4 direct animated head delta moved opposite at runtime; inverse/transpose correction remains authoritative.
