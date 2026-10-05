#ifndef MATRIX_COMBAT_OVERLAY_H
#define MATRIX_COMBAT_OVERLAY_H
#include <stdint.h>
#include <string.h>

#define COMBAT_SLASH_TICKS 16
struct CombatOverlay {
    uint32_t lastRequest;
    int tick;
    int active;
    float time;
    float weight;
};

/* XYZ Euler channel offsets, degrees. The native evaluator retains its own
 * XYZ matrix order and parent composition. All four curves start/end at zero.
 * V2 keeps the proven additive seam but reads as a faster sword cut: stronger
 * torso/shoulder wind-up, visible forearm/wrist participation and earlier hit. */
static void combat_overlay_evaluate(struct CombatOverlay *state, int mode,
        uint32_t request, int16_t rotation[4][3])
{
    static const float times[5] = {0, .18f, .42f, .68f, 1};
    static const float poses[5][4][3] = {
        {{0,0,0},{0,0,0},{0,0,0},{0,0,0}},
        {{-24,6,-18},{-58,-30,-90},{32,10,-58},{20,0,28}},
        {{28,-6,20},{72,38,92},{-30,-8,42},{-24,0,-30}},
        {{18,-3,12},{45,24,66},{-16,-4,24},{-12,0,-18}},
        {{0,0,0},{0,0,0},{0,0,0},{0,0,0}}
    };
    memset(rotation, 0, 4 * 3 * sizeof(int16_t));
    if (mode != 1) {
        state->active = 0;
        state->lastRequest = request;
        state->time = state->weight = 0;
        return;
    }
    if (request != state->lastRequest) {
        state->lastRequest = request;
        if (!state->active) { state->active = 1; state->tick = 0; }
    }
    state->time = state->weight = 0;
    if (!state->active) return;
    state->time = (float)state->tick / COMBAT_SLASH_TICKS;
    int k = 0;
    while (k < 3 && state->time > times[k+1]) k++;
    float t = (state->time-times[k])/(times[k+1]-times[k]);
    t = t*t*(3-2*t);
    state->weight = k == 0 ? t : k == 3 ? 1-t : 1;
    for (int j=0;j<4;j++) for(int a=0;a<3;a++) {
        float degrees = poses[k][j][a] + (poses[k+1][j][a]-poses[k][j][a])*t;
        rotation[j][a] = (int16_t)(degrees*(65536.0f/360.0f));
    }
    if (++state->tick > COMBAT_SLASH_TICKS) state->active = 0;
}
#endif
