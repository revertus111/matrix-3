#define _CRT_SECURE_NO_WARNINGS 1

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libsm64.h"

#define BRIDGE_PROTOCOL_VERSION 1
#define SM64_ACT_IDLE 0x0C400201u
#define FLOOR_EXTENT 4096

static float s_geo_positions[9 * SM64_GEO_MAX_TRIANGLES];
static float s_geo_normals[9 * SM64_GEO_MAX_TRIANGLES];
static float s_geo_colors[9 * SM64_GEO_MAX_TRIANGLES];
static float s_geo_uvs[6 * SM64_GEO_MAX_TRIANGLES];

static struct SM64MarioGeometryBuffers s_geometry = {
    s_geo_positions,
    s_geo_normals,
    s_geo_colors,
    s_geo_uvs,
    0
};

/*
 * Temporary Bridge Spike A collision only. The triangle winding produces an
 * upward floor normal for libsm64. RuneScape terrain replaces this in the
 * Matrix collision-adapter phase.
 */
static const struct SM64Surface s_flat_floor[2] = {
    {
        0, 0, 0,
        {
            { -FLOOR_EXTENT, 0, -FLOOR_EXTENT },
            {  FLOOR_EXTENT, 0,  FLOOR_EXTENT },
            {  FLOOR_EXTENT, 0, -FLOOR_EXTENT }
        }
    },
    {
        0, 0, 0,
        {
            { -FLOOR_EXTENT, 0, -FLOOR_EXTENT },
            { -FLOOR_EXTENT, 0,  FLOOR_EXTENT },
            {  FLOOR_EXTENT, 0,  FLOOR_EXTENT }
        }
    }
};

static int32_t s_mario_id = -1;

static void bridge_debug_print(const char *message)
{
    if (message != NULL) {
        fprintf(stderr, "[libsm64] %s\n", message);
    }
}

static uint8_t *read_file(const char *path, size_t *out_size)
{
    FILE *file = fopen(path, "rb");
    uint8_t *buffer;
    long length;

    if (file == NULL) {
        return NULL;
    }

    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        return NULL;
    }
    length = ftell(file);
    if (length <= 0 || fseek(file, 0, SEEK_SET) != 0) {
        fclose(file);
        return NULL;
    }

    buffer = (uint8_t *) malloc((size_t) length);
    if (buffer == NULL) {
        fclose(file);
        return NULL;
    }

    if (fread(buffer, 1, (size_t) length, file) != (size_t) length) {
        free(buffer);
        fclose(file);
        return NULL;
    }

    fclose(file);
    if (out_size != NULL) {
        *out_size = (size_t) length;
    }
    return buffer;
}

static int reset_mario(void)
{
    if (s_mario_id >= 0) {
        sm64_mario_delete(s_mario_id);
        s_mario_id = -1;
    }

    s_mario_id = sm64_mario_create(0.0f, 100.0f, 0.0f);
    if (s_mario_id < 0) {
        return 0;
    }

    sm64_set_mario_position(s_mario_id, 0.0f, 0.0f, 0.0f);
    sm64_set_mario_velocity(s_mario_id, 0.0f, 0.0f, 0.0f);
    sm64_set_mario_forward_velocity(s_mario_id, 0.0f);
    sm64_set_mario_action(s_mario_id, SM64_ACT_IDLE);
    return 1;
}

static void print_state(const struct SM64MarioState *state)
{
    printf(
        "STATE %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f %u %d %d %u\n",
        state->position[0], state->position[1], state->position[2],
        state->velocity[0], state->velocity[1], state->velocity[2],
        state->faceAngle, state->forwardVelocity,
        (unsigned int) state->action,
        (int) state->animID,
        (int) state->animFrame,
        (unsigned int) state->flags
    );
    fflush(stdout);
}

int main(int argc, char **argv)
{
    uint8_t *rom;
    uint8_t *texture;
    size_t rom_size = 0;
    char line[512];

    if (argc != 2) {
        fprintf(stderr, "Usage: %s <path-to-sm64-us-rom>\n", argv[0]);
        return 2;
    }

    rom = read_file(argv[1], &rom_size);
    if (rom == NULL) {
        fprintf(stderr, "Failed to read ROM '%s': %s\n", argv[1], strerror(errno));
        return 3;
    }
    if (rom_size < 8u * 1024u * 1024u) {
        fprintf(stderr, "ROM is unexpectedly small: %lu bytes\n", (unsigned long) rom_size);
        free(rom);
        return 4;
    }

    texture = (uint8_t *) malloc(4u * SM64_TEXTURE_WIDTH * SM64_TEXTURE_HEIGHT);
    if (texture == NULL) {
        fprintf(stderr, "Failed to allocate libsm64 texture buffer\n");
        free(rom);
        return 5;
    }

    sm64_register_debug_print_function(bridge_debug_print);
    sm64_global_init(rom, texture);
    sm64_static_surfaces_load(s_flat_floor, 2);

    if (!reset_mario()) {
        fprintf(stderr, "sm64_mario_create failed\n");
        sm64_global_terminate();
        free(texture);
        free(rom);
        return 6;
    }

    printf("READY %d\n", BRIDGE_PROTOCOL_VERSION);
    fflush(stdout);

    while (fgets(line, sizeof(line), stdin) != NULL) {
        if (strncmp(line, "PING", 4) == 0) {
            printf("PONG %d\n", BRIDGE_PROTOCOL_VERSION);
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "RESET", 5) == 0) {
            if (!reset_mario()) {
                printf("ERR reset_failed\n");
            } else {
                printf("RESET_OK\n");
            }
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "QUIT", 4) == 0) {
            printf("BYE\n");
            fflush(stdout);
            break;
        }

        if (strncmp(line, "STEP ", 5) == 0) {
            struct SM64MarioInputs input;
            struct SM64MarioState state;
            float cam_look_x, cam_look_z, stick_x, stick_y;
            int button_a, button_b, button_z;

            if (sscanf(
                    line,
                    "STEP %f %f %f %f %d %d %d",
                    &cam_look_x, &cam_look_z,
                    &stick_x, &stick_y,
                    &button_a, &button_b, &button_z) != 7) {
                printf("ERR bad_step\n");
                fflush(stdout);
                continue;
            }

            memset(&input, 0, sizeof(input));
            memset(&state, 0, sizeof(state));
            input.camLookX = cam_look_x;
            input.camLookZ = cam_look_z;
            input.stickX = stick_x;
            input.stickY = stick_y;
            input.buttonA = button_a != 0;
            input.buttonB = button_b != 0;
            input.buttonZ = button_z != 0;

            s_geometry.numTrianglesUsed = 0;
            sm64_mario_tick(s_mario_id, &input, &state, &s_geometry);
            print_state(&state);
            continue;
        }

        printf("ERR unknown_command\n");
        fflush(stdout);
    }

    if (s_mario_id >= 0) {
        sm64_mario_delete(s_mario_id);
    }
    sm64_global_terminate();
    free(texture);
    free(rom);
    return 0;
}
