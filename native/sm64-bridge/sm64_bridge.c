#define _CRT_SECURE_NO_WARNINGS 1

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#endif

#include "libsm64.h"
#include "combat_overlay.h"

#define BRIDGE_PROTOCOL_VERSION 1
#define BINARY_PROTOCOL_VERSION 3
#define BINARY_CMD_STEP 1
#define BINARY_CMD_QUIT 2
#define SM64_ACT_IDLE 0x0C400201u
#define SM64_ACT_START_SLEEPING 0x0C400202u
#define SM64_ACT_SLEEPING 0x0C000203u
#define FLOOR_EXTENT 4096

static float s_geo_positions[9 * SM64_GEO_MAX_TRIANGLES];
static float s_geo_normals[9 * SM64_GEO_MAX_TRIANGLES];
static float s_geo_colors[9 * SM64_GEO_MAX_TRIANGLES];
static float s_geo_uvs[6 * SM64_GEO_MAX_TRIANGLES];
static float s_geo_local_positions[9 * SM64_GEO_MAX_TRIANGLES];
static uint8_t s_geo_part_ids[SM64_GEO_MAX_TRIANGLES];

static struct SM64MarioCombatPose s_combat_pose;
static struct CombatOverlay s_combat;

static struct SM64MarioGeometryBuffers s_geometry = {
    s_geo_positions,
    s_geo_normals,
    s_geo_colors,
    s_geo_uvs,
    s_geo_local_positions,
    s_geo_part_ids,
    0,
    &s_combat_pose
};

/*
 * Temporary bridge collision only. The triangle winding produces an upward
 * floor normal for libsm64. RuneScape terrain replaces this in the Matrix
 * collision-adapter phase.
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
static uint32_t s_last_mario_action = SM64_ACT_IDLE;

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
    s_last_mario_action = SM64_ACT_IDLE;
    memset(&s_combat, 0, sizeof(s_combat));
    memset(&s_combat_pose, 0, sizeof(s_combat_pose));
    return 1;
}

static int is_sleep_action(uint32_t action)
{
    return action == SM64_ACT_START_SLEEPING || action == SM64_ACT_SLEEPING;
}

static void fill_mario_input(
        struct SM64MarioInputs *input,
        float cam_look_x, float cam_look_z,
        float stick_x, float stick_y,
        int button_a, int button_b, int button_z)
{
    memset(input, 0, sizeof(*input));
    input->camLookX = cam_look_x;
    input->camLookZ = cam_look_z;
    input->stickX = stick_x;
    input->stickY = stick_y;
    input->buttonA = button_a != 0;
    input->buttonB = button_b != 0;
    input->buttonZ = button_z != 0;
}

static void tick_mario(
        float cam_look_x, float cam_look_z,
        float stick_x, float stick_y,
        int button_a, int button_b, int button_z,
        struct SM64MarioState *state)
{
    struct SM64MarioInputs input;

    /*
     * Matrix3 owns the surrounding game/session lifecycle, so autonomous SM64
     * sleeping has no useful gameplay role here. Runtime evidence showed the
     * sidecar remained alive while binary frame publication stopped after
     * libsm64 entered ACT_SLEEPING (0x0C000203).
     *
     * Guard both sides of the native tick. The pre-tick guard repairs any sleep
     * action inherited from the previous frame. The post-tick guard catches the
     * transition on the exact tick where libsm64 decides to start sleeping, resets
     * to normal idle, and immediately re-ticks before a frame is published. That
     * means Matrix never receives a long-lived START_SLEEPING/SLEEPING frame.
     */
    if (is_sleep_action(s_last_mario_action)) {
        sm64_set_mario_action(s_mario_id, SM64_ACT_IDLE);
        s_last_mario_action = SM64_ACT_IDLE;
    }

    fill_mario_input(&input,
            cam_look_x, cam_look_z,
            stick_x, stick_y,
            button_a, button_b, button_z);

    memset(state, 0, sizeof(*state));
    s_geometry.numTrianglesUsed = 0;
    sm64_mario_tick(s_mario_id, &input, state, &s_geometry);

    if (is_sleep_action(state->action)) {
        uint32_t blocked_action = state->action;
        fprintf(stderr,
                "[SM64 Bridge] blocked autonomous sleep 0x%08X -> idle\n",
                (unsigned int) blocked_action);
        sm64_set_mario_action(s_mario_id, SM64_ACT_IDLE);

        memset(state, 0, sizeof(*state));
        s_geometry.numTrianglesUsed = 0;
        sm64_mario_tick(s_mario_id, &input, state, &s_geometry);
    }

    s_last_mario_action = state->action;
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

static int write_bytes(const void *data, size_t size)
{
    return fwrite(data, 1, size, stdout) == size;
}

static int write_u16_le(uint16_t value)
{
    uint8_t bytes[2];
    bytes[0] = (uint8_t) (value & 0xffu);
    bytes[1] = (uint8_t) ((value >> 8) & 0xffu);
    return write_bytes(bytes, sizeof(bytes));
}

static int write_u32_le(uint32_t value)
{
    uint8_t bytes[4];
    bytes[0] = (uint8_t) (value & 0xffu);
    bytes[1] = (uint8_t) ((value >> 8) & 0xffu);
    bytes[2] = (uint8_t) ((value >> 16) & 0xffu);
    bytes[3] = (uint8_t) ((value >> 24) & 0xffu);
    return write_bytes(bytes, sizeof(bytes));
}

static int write_f32_le(float value)
{
    uint32_t bits;
    memcpy(&bits, &value, sizeof(bits));
    return write_u32_le(bits);
}

static int read_u32_le(uint32_t *value)
{
    uint8_t bytes[4];
    if (fread(bytes, 1, sizeof(bytes), stdin) != sizeof(bytes)) {
        return 0;
    }
    *value = ((uint32_t) bytes[0])
        | ((uint32_t) bytes[1] << 8)
        | ((uint32_t) bytes[2] << 16)
        | ((uint32_t) bytes[3] << 24);
    return 1;
}

static int read_f32_le(float *value)
{
    uint32_t bits;
    if (!read_u32_le(&bits)) {
        return 0;
    }
    memcpy(value, &bits, sizeof(bits));
    return 1;
}

static int write_float_array(const float *values, size_t count)
{
    size_t i;
    for (i = 0; i < count; i++) {
        if (!write_f32_le(values[i])) {
            return 0;
        }
    }
    return 1;
}

static int write_binary_handshake(const uint8_t *texture)
{
    uint32_t texture_bytes = 4u * SM64_TEXTURE_WIDTH * SM64_TEXTURE_HEIGHT;
    return write_bytes("M64B", 4)
        && write_u32_le(BINARY_PROTOCOL_VERSION)
        && write_u32_le((uint32_t) SM64_TEXTURE_WIDTH)
        && write_u32_le((uint32_t) SM64_TEXTURE_HEIGHT)
        && write_u32_le(texture_bytes)
        && write_bytes(texture, texture_bytes)
        && fflush(stdout) == 0;
}

static int write_binary_frame(
        uint32_t sequence,
        const struct SM64MarioState *state)
{
    uint16_t triangles = s_geometry.numTrianglesUsed;
    size_t position_count;
    size_t color_count;
    size_t uv_count;

    if (triangles > SM64_GEO_MAX_TRIANGLES) {
        return 0;
    }

    position_count = (size_t) triangles * 9u;
    color_count = (size_t) triangles * 9u;
    uv_count = (size_t) triangles * 6u;

    if (!write_bytes("M64F", 4)
            || !write_u32_le(sequence)
            || !write_f32_le(state->position[0])
            || !write_f32_le(state->position[1])
            || !write_f32_le(state->position[2])
            || !write_f32_le(state->velocity[0])
            || !write_f32_le(state->velocity[1])
            || !write_f32_le(state->velocity[2])
            || !write_f32_le(state->faceAngle)
            || !write_f32_le(state->forwardVelocity)
            || !write_u32_le(state->action)
            || !write_u32_le((uint32_t) state->animID)
            || !write_u16_le((uint16_t) state->animFrame)
            || !write_u32_le(state->flags)
            || !write_u32_le(state->particleFlags)
            || !write_u16_le(triangles)
            || !write_float_array(s_geometry.position, position_count)
            || !write_float_array(s_geometry.color, color_count)
            || !write_float_array(s_geometry.uv, uv_count)
            || !write_float_array(s_geometry.localPosition, position_count)
            || !write_bytes(s_geometry.partId, (size_t) triangles)
            || !write_u32_le(s_combat_pose.rightHandAvailable)
            || !write_float_array(s_combat_pose.rightHand, 16)
            || !write_u32_le(s_combat.active ? 1u : 0u)
            || !write_f32_le(s_combat.time)
            || !write_f32_le(s_combat.weight)) {
        return 0;
    }
    return fflush(stdout) == 0;
}

static int run_binary_bridge(const uint8_t *texture)
{
    uint32_t sequence = 0;

#ifdef _WIN32
    _setmode(_fileno(stdin), _O_BINARY);
    _setmode(_fileno(stdout), _O_BINARY);
#endif

    fprintf(stderr, "[SM64 Bridge] combat-socket-v3 + semantic-geometry-v2 + sleep-guard-v2 active\n");

    if (!write_binary_handshake(texture)) {
        return 0;
    }

    for (;;) {
        int command = fgetc(stdin);
        if (command == EOF) {
            return 1;
        }
        if (command == BINARY_CMD_QUIT) {
            return 1;
        }
        if (command == BINARY_CMD_STEP) {
            struct SM64MarioState state;
            float cam_look_x;
            float cam_look_z;
            float stick_x;
            float stick_y;
            int button_a;
            int button_b;
            int button_z;
            int combat_mode;
            uint32_t combat_request;

            if (!read_f32_le(&cam_look_x)
                    || !read_f32_le(&cam_look_z)
                    || !read_f32_le(&stick_x)
                    || !read_f32_le(&stick_y)) {
                return 0;
            }
            button_a = fgetc(stdin);
            button_b = fgetc(stdin);
            button_z = fgetc(stdin);
            if (button_a == EOF || button_b == EOF || button_z == EOF) {
                return 0;
            }

            combat_mode = fgetc(stdin);
            if (combat_mode < 0 || combat_mode > 1 || !read_u32_le(&combat_request)) return 0;
            combat_overlay_evaluate(&s_combat, combat_mode, combat_request, s_combat_pose.rotation);
            tick_mario(
                    cam_look_x, cam_look_z, stick_x, stick_y,
                    button_a, button_b, button_z, &state);
            sequence++;
            if (!write_binary_frame(sequence, &state)) {
                return 0;
            }
            continue;
        }

        fprintf(stderr, "Unknown binary command: %d\n", command);
        return 0;
    }
}

static void run_text_bridge(void)
{
    char line[512];

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

            tick_mario(
                    cam_look_x, cam_look_z, stick_x, stick_y,
                    button_a, button_b, button_z, &state);
            print_state(&state);
            continue;
        }

        printf("ERR unknown_command\n");
        fflush(stdout);
    }
}

int main(int argc, char **argv)
{
    uint8_t *rom;
    uint8_t *texture;
    size_t rom_size = 0;
    const char *rom_path;
    int binary_mode = 0;
    int binary_ok = 1;

    if (argc == 3 && strcmp(argv[1], "--binary") == 0) {
        binary_mode = 1;
        rom_path = argv[2];
    } else if (argc == 2) {
        rom_path = argv[1];
    } else {
        fprintf(stderr, "Usage: %s [--binary] <path-to-sm64-us-rom>\n", argv[0]);
        return 2;
    }

    rom = read_file(rom_path, &rom_size);
    if (rom == NULL) {
        fprintf(stderr, "Failed to read ROM '%s': %s\n", rom_path, strerror(errno));
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

    if (binary_mode) {
        binary_ok = run_binary_bridge(texture);
    } else {
        run_text_bridge();
    }

    if (s_mario_id >= 0) {
        sm64_mario_delete(s_mario_id);
    }
    sm64_global_terminate();
    free(texture);
    free(rom);
    return binary_ok ? 0 : 7;
}

