/* Exercises production curve and binary writer without ROM/client resources. */
#define main sm64_sidecar_main
#include "../sm64_bridge.c"
#undef main
#include <assert.h>
#include <math.h>

static void assert_zero(int16_t rotation[4][3]) {
    for (int j=0;j<4;j++) for(int a=0;a<3;a++) assert(rotation[j][a] == 0);
}
int main(int argc, char **argv) {
    struct CombatOverlay test = {0};
    int16_t pose[4][3];
    combat_overlay_evaluate(&test, 1, 1, pose);
    assert_zero(pose);
    assert(test.active && test.time == 0);
    int limbMotion = 0;
    int shoulderSweep = 0;
    for(int frame=1;frame<=COMBAT_SLASH_TICKS;frame++) {
        combat_overlay_evaluate(&test, 1, 1, pose);
        assert(test.time >= 0 && test.time <= 1);
        assert(test.weight >= 0 && test.weight <= 1);
        /* V4 intentionally keeps the torso neutral; motion belongs to the right limb. */
        for(int a=0;a<3;a++) assert(pose[0][a] == 0);
        for(int j=1;j<4;j++) for(int a=0;a<3;a++) {
            if (pose[j][a] != 0) limbMotion = 1;
        }
        if (pose[1][1] != 0 || pose[1][2] != 0) shoulderSweep = 1;
    }
    assert(limbMotion && shoulderSweep && !test.active);
    assert_zero(pose);
    combat_overlay_evaluate(&test, 1, 1, pose);
    assert(!test.active); /* Held request cannot replay the slash. */
    combat_overlay_evaluate(&test, 1, 2, pose);
    assert(test.active);
    combat_overlay_evaluate(&test, 0, 2, pose);
    assert_zero(pose);
    assert(!test.active);
    combat_overlay_evaluate(&test, 1, 2, pose);
    assert(!test.active); /* Re-enable cannot replay a consumed request. */
    if(argc == 2) {
        struct SM64MarioState state;
        memset(&state,0,sizeof(state));
        state.position[0]=123;
        s_geometry.numTrianglesUsed=1;
        s_geo_part_ids[0]=SM64_MARIO_GEOMETRY_PART_FACE;
        s_geo_local_positions[0]=42;
        s_combat_pose.rightHandAvailable=1;
        s_combat_pose.rightHand[0]=s_combat_pose.rightHand[5]=s_combat_pose.rightHand[10]=.25f;
        s_combat_pose.rightHand[15]=1;
        s_combat_pose.rightHand[12]=147;
        s_combat.active=1; s_combat.time=.5f; s_combat.weight=1;
        assert(freopen(argv[1],"wb",stdout) != NULL);
        assert(write_binary_frame(7,&state));
        assert(write_binary_frame(8,&state));
    }
    fprintf(stderr,"PASS production slash endpoints, V4 neutral torso/limb motion, bounded weights, request lifecycle and v3 frame writer\n");
    return 0;
}
