#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "liboot_engine.h"

#define EXPECTED_ROM_SIZE 33554432u
#define BINARY_PROTOCOL_VERSION 1u
#define BINARY_CMD_STEP 1u
#define MAX_STREAM_TRIANGLES 4096u

static int read_rom(const char *path, uint8_t **out_data, size_t *out_size)
{
    FILE *file;
    long length;
    uint8_t *data;

    *out_data = NULL;
    *out_size = 0u;
    file = fopen(path, "rb");
    if (file == NULL) {
        return 0;
    }
    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        return 0;
    }
    length = ftell(file);
    if (length <= 0 || fseek(file, 0, SEEK_SET) != 0) {
        fclose(file);
        return 0;
    }
    data = (uint8_t *)malloc((size_t)length);
    if (data == NULL) {
        fclose(file);
        return 0;
    }
    if (fread(data, 1, (size_t)length, file) != (size_t)length) {
        free(data);
        fclose(file);
        return 0;
    }
    fclose(file);
    *out_data = data;
    *out_size = (size_t)length;
    return 1;
}

static int read_exact(void *buffer, size_t length)
{
    return fread(buffer, 1, length, stdin) == length;
}

static int read_u32_le(uint32_t *out)
{
    uint8_t b[4];
    if (!read_exact(b, sizeof(b))) {
        return 0;
    }
    *out = (uint32_t)b[0]
         | ((uint32_t)b[1] << 8)
         | ((uint32_t)b[2] << 16)
         | ((uint32_t)b[3] << 24);
    return 1;
}

static int read_float_le(float *out)
{
    union {
        uint32_t u;
        float f;
    } bits;
    if (!read_u32_le(&bits.u)) {
        return 0;
    }
    *out = bits.f;
    return 1;
}

static int write_bytes(const void *data, size_t length)
{
    return fwrite(data, 1, length, stdout) == length;
}

static int write_u32_le(uint32_t value)
{
    uint8_t b[4];
    b[0] = (uint8_t)(value & 0xffu);
    b[1] = (uint8_t)((value >> 8) & 0xffu);
    b[2] = (uint8_t)((value >> 16) & 0xffu);
    b[3] = (uint8_t)((value >> 24) & 0xffu);
    return write_bytes(b, sizeof(b));
}

static int write_u64_le(uint64_t value)
{
    uint8_t b[8];
    unsigned int i;
    for (i = 0; i < 8; ++i) {
        b[i] = (uint8_t)((value >> (i * 8)) & 0xffu);
    }
    return write_bytes(b, sizeof(b));
}

static int write_float_le(float value)
{
    union {
        uint32_t u;
        float f;
    } bits;
    bits.f = value;
    return write_u32_le(bits.u);
}

static int write_float_array(const float *values, size_t count)
{
    size_t i;
    for (i = 0; i < count; ++i) {
        if (!write_float_le(values[i])) {
            return 0;
        }
    }
    return 1;
}

static int send_handshake(void)
{
    static const uint8_t magic[4] = { 'O', 'O', 'T', 'B' };
    return write_bytes(magic, sizeof(magic))
        && write_u32_le(BINARY_PROTOCOL_VERSION)
        && fflush(stdout) == 0;
}

static int send_frame(uint32_t sequence, const OoTEngineFrame *frame)
{
    static const uint8_t magic[4] = { 'O', 'O', 'T', 'F' };
    uint32_t triangles;
    size_t vertex_floats;
    uint8_t flags[4];

    if (frame == NULL || frame->geometry.position == NULL || frame->geometry.color == NULL) {
        fprintf(stderr, "[OoT Bridge] frame geometry unavailable\n");
        return 0;
    }
    triangles = frame->geometry.numTriangles;
    if (triangles == 0u || triangles > MAX_STREAM_TRIANGLES) {
        fprintf(stderr, "[OoT Bridge] invalid triangle count: %u\n", triangles);
        return 0;
    }
    vertex_floats = (size_t)triangles * 9u;
    flags[0] = frame->skeletonAvailable ? 1u : 0u;
    flags[1] = frame->linkGeometryTruncated ? 1u : 0u;
    flags[2] = 0u;
    flags[3] = 0u;

    if (!write_bytes(magic, sizeof(magic))
            || !write_u32_le(sequence)
            || !write_u64_le(frame->simulationTick)
            || !write_float_le(frame->link.position[0])
            || !write_float_le(frame->link.position[1])
            || !write_float_le(frame->link.position[2])
            || !write_u32_le((uint32_t)(int32_t)frame->link.faceAngle)
            || !write_u32_le(frame->link.action)
            || !write_u32_le((uint32_t)(int32_t)frame->link.animId)
            || !write_float_le(frame->link.animFrame)
            || !write_bytes(flags, sizeof(flags))
            || !write_u32_le(triangles)
            || !write_float_array(frame->geometry.position, vertex_floats)
            || !write_float_array(frame->geometry.color, vertex_floats)) {
        return 0;
    }
    return fflush(stdout) == 0;
}

int main(int argc, char **argv)
{
    static const struct OoTSurface floor[] = {
        { 0, {{ -10000, 0, -10000 }, { -10000, 0, 10000 }, { 10000, 0, 10000 }} },
        { 0, {{ -10000, 0, -10000 }, { 10000, 0, 10000 }, { 10000, 0, -10000 }} },
    };
    uint8_t *rom = NULL;
    size_t rom_size = 0u;
    OoTEngineConfig config;
    OoTEngineInput input;
    OoTEngine *engine = NULL;
    const OoTEngineFrame *frame = NULL;
    OoTResult result;
    uint32_t sequence = 0u;
    int exit_code = 1;

    if (argc != 3 || strcmp(argv[1], "--binary") != 0) {
        fprintf(stderr, "usage: %s --binary <legally-obtained-ntsc-u-1.2-rom>\n", argv[0]);
        return 2;
    }
    if (!read_rom(argv[2], &rom, &rom_size) || rom_size != EXPECTED_ROM_SIZE) {
        fprintf(stderr, "[OoT Bridge] expected exact 32 MiB NTSC-U 1.2 ROM\n");
        goto done;
    }
    if (oot_engine_api_version() != OOT_ENGINE_API_VERSION) {
        fprintf(stderr, "[OoT Bridge] engine API version mismatch\n");
        goto done;
    }

    result = oot_engine_config_init(&config);
    if (result != OOT_ENGINE_RESULT_OK) {
        fprintf(stderr, "[OoT Bridge] config init failed: %s\n", oot_engine_result_string(result));
        goto done;
    }
    config.romData = rom;
    config.romSize = rom_size;
    config.fixedStepSeconds = 1.0f / 20.0f;

    result = oot_engine_create(&config, &engine);
    if (result != OOT_ENGINE_RESULT_OK) {
        fprintf(stderr, "[OoT Bridge] engine create failed: %s\n", oot_engine_result_string(result));
        goto done;
    }
    free(rom);
    rom = NULL;

    result = oot_engine_static_world_load(
            engine, floor, sizeof(floor) / sizeof(floor[0]), NULL, 0u);
    if (result != OOT_ENGINE_RESULT_OK) {
        fprintf(stderr, "[OoT Bridge] static floor failed: %s\n", oot_engine_result_string(result));
        goto done;
    }
    result = oot_engine_link_create(engine, 0.0f, 0.0f, 0.0f);
    if (result != OOT_ENGINE_RESULT_OK) {
        fprintf(stderr, "[OoT Bridge] Link create failed: %s\n", oot_engine_result_string(result));
        goto done;
    }
    result = oot_engine_link_set_age(engine, OOT_AGE_ADULT);
    if (result != OOT_ENGINE_RESULT_OK) {
        fprintf(stderr, "[OoT Bridge] adult age failed: %s\n", oot_engine_result_string(result));
        goto done;
    }
    result = oot_engine_link_set_equipment(
            engine, OOT_SWORD_MASTER, OOT_SHIELD_HYLIAN,
            OOT_TUNIC_KOKIRI, OOT_BOOTS_KOKIRI);
    if (result != OOT_ENGINE_RESULT_OK) {
        fprintf(stderr, "[OoT Bridge] equipment failed: %s\n", oot_engine_result_string(result));
        goto done;
    }
    result = oot_engine_input_init(&input);
    if (result != OOT_ENGINE_RESULT_OK) {
        fprintf(stderr, "[OoT Bridge] input init failed: %s\n", oot_engine_result_string(result));
        goto done;
    }

    /* Binary stdout is protocol-only. All diagnostics go to stderr. */
    if (setvbuf(stdout, NULL, _IONBF, 0) != 0) {
        fprintf(stderr, "[OoT Bridge] failed to configure binary stdout\n");
        goto done;
    }
    if (!send_handshake()) {
        goto done;
    }
    fprintf(stderr, "[OoT Bridge] persistent NTSC-U 1.2 session READY (20 Hz, protocol v%u)\n",
            BINARY_PROTOCOL_VERSION);

    for (;;) {
        int command = fgetc(stdin);
        uint32_t buttons;
        if (command == EOF) {
            exit_code = 0;
            break;
        }
        if ((uint32_t)(uint8_t)command != BINARY_CMD_STEP) {
            fprintf(stderr, "[OoT Bridge] unknown command: %d\n", command);
            break;
        }
        if (!read_float_le(&input.camLookX)
                || !read_float_le(&input.camLookZ)
                || !read_float_le(&input.stickX)
                || !read_float_le(&input.stickY)
                || !read_u32_le(&buttons)) {
            fprintf(stderr, "[OoT Bridge] truncated step command\n");
            break;
        }
        input.buttons = buttons;
        result = oot_engine_step(engine, &input, &frame);
        if (result != OOT_ENGINE_RESULT_OK) {
            fprintf(stderr, "[OoT Bridge] step failed: %s\n", oot_engine_result_string(result));
            break;
        }
        if (frame->linkGeometryTruncated != 0u) {
            fprintf(stderr, "[OoT Bridge] Link geometry truncated\n");
            break;
        }
        if (!send_frame(++sequence, frame)) {
            break;
        }
    }

done:
    if (engine != NULL) {
        OoTResult destroy_result = oot_engine_destroy(engine);
        if (destroy_result != OOT_ENGINE_RESULT_OK) {
            fprintf(stderr, "[OoT Bridge] engine destroy failed: %s\n",
                    oot_engine_result_string(destroy_result));
            exit_code = 1;
        }
    }
    free(rom);
    return exit_code;
}
