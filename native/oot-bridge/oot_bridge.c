#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#endif

#include "liboot_engine.h"

#define EXPECTED_ROM_SIZE 33554432u
#define BINARY_PROTOCOL_VERSION 3u
#define BINARY_CMD_STEP 1u
#define MAX_STREAM_TRIANGLES 4096u
#define MAX_STREAM_TEXTURES OOT_ENGINE_MAX_TEXTURES
#define MAX_STREAM_JOINTS OOT_SKELETON_MAX_JOINTS

static uint8_t g_texture_sent[MAX_STREAM_TEXTURES];
static uint32_t g_texture_revision[MAX_STREAM_TEXTURES];

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

static int configure_binary_stdio(void)
{
#ifdef _WIN32
    if (_setmode(_fileno(stdin), _O_BINARY) == -1) {
        return 0;
    }
    if (_setmode(_fileno(stdout), _O_BINARY) == -1) {
        return 0;
    }
#endif
    return setvbuf(stdout, NULL, _IONBF, 0) == 0;
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

static int write_u16_array_as_u32(const uint16_t *values, size_t count)
{
    size_t i;
    for (i = 0; i < count; ++i) {
        if (!write_u32_le((uint32_t)values[i])) {
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

static int send_skeleton(const OoTEngineFrame *frame)
{
    uint32_t joint_count;
    uint32_t joint;

    if (frame == NULL || frame->skeletonAvailable == 0u) {
        return write_u32_le(0u);
    }

    joint_count = (uint32_t)frame->skeleton.numJoints;
    if (joint_count == 0u || joint_count > MAX_STREAM_JOINTS) {
        fprintf(stderr, "[OoT Bridge] invalid skeleton joint count: %u\n", joint_count);
        return 0;
    }

    if (!write_u32_le(joint_count)) {
        return 0;
    }
    for (joint = 0u; joint < joint_count; ++joint) {
        if (!write_u32_le((uint32_t)frame->skeleton.parent[joint])) {
            return 0;
        }
    }
    return write_float_array(&frame->skeleton.jointPos[0][0], (size_t)joint_count * 3u);
}

static int texture_is_referenced(const OoTEngineFrame *frame, uint32_t texture_index)
{
    uint32_t triangle;
    if (frame == NULL || frame->geometry.triTexture == NULL) {
        return 0;
    }
    for (triangle = 0u; triangle < frame->geometry.numTriangles; ++triangle) {
        if ((uint32_t)frame->geometry.triTexture[triangle] == texture_index) {
            return 1;
        }
    }
    return 0;
}

static int send_texture_updates(OoTEngine *engine, const OoTEngineFrame *frame)
{
    uint32_t texture_count = 0u;
    uint32_t update_count = 0u;
    uint32_t index;
    OoTResult result;

    result = oot_engine_texture_count(engine, &texture_count);
    if (result != OOT_ENGINE_RESULT_OK) {
        fprintf(stderr, "[OoT Bridge] texture count failed: %s\n",
                oot_engine_result_string(result));
        return 0;
    }
    if (texture_count > MAX_STREAM_TEXTURES) {
        fprintf(stderr, "[OoT Bridge] texture count exceeds protocol capacity: %u\n",
                texture_count);
        return 0;
    }

    for (index = 0u; index < texture_count; ++index) {
        OoTEngineTexture texture;
        if (!texture_is_referenced(frame, index)) {
            continue;
        }
        memset(&texture, 0, sizeof(texture));
        result = oot_engine_texture_get(engine, index, &texture);
        if (result != OOT_ENGINE_RESULT_OK || texture.rgbaPixels == NULL) {
            continue;
        }
        if (!g_texture_sent[index] || g_texture_revision[index] != texture.revision) {
            update_count++;
        }
    }

    if (!write_u32_le(update_count)) {
        return 0;
    }

    for (index = 0u; index < texture_count; ++index) {
        OoTEngineTexture texture;
        size_t expected_size;
        if (!texture_is_referenced(frame, index)) {
            continue;
        }
        memset(&texture, 0, sizeof(texture));
        result = oot_engine_texture_get(engine, index, &texture);
        if (result != OOT_ENGINE_RESULT_OK || texture.rgbaPixels == NULL) {
            continue;
        }
        if (g_texture_sent[index] && g_texture_revision[index] == texture.revision) {
            continue;
        }
        expected_size = (size_t)texture.width * (size_t)texture.height * 4u;
        if (texture.width == 0u || texture.height == 0u
                || texture.rgbaSize != expected_size
                || expected_size > 16u * 1024u * 1024u) {
            fprintf(stderr, "[OoT Bridge] invalid texture %u payload %ux%u size=%zu\n",
                    index, texture.width, texture.height, texture.rgbaSize);
            return 0;
        }
        if (!write_u32_le(index)
                || !write_u32_le((uint32_t)texture.width)
                || !write_u32_le((uint32_t)texture.height)
                || !write_u32_le((uint32_t)texture.wrapS)
                || !write_u32_le((uint32_t)texture.wrapT)
                || !write_u32_le(texture.revision)
                || !write_u32_le((uint32_t)texture.rgbaSize)
                || !write_bytes(texture.rgbaPixels, texture.rgbaSize)) {
            return 0;
        }
        g_texture_sent[index] = 1u;
        g_texture_revision[index] = texture.revision;
    }
    return 1;
}

static int send_frame(OoTEngine *engine, uint32_t sequence, const OoTEngineFrame *frame)
{
    static const uint8_t magic[4] = { 'O', 'O', 'T', 'F' };
    uint32_t triangles;
    size_t vertex_floats;
    size_t uv_floats;
    uint8_t flags[4];

    if (frame == NULL
            || frame->geometry.position == NULL
            || frame->geometry.normal == NULL
            || frame->geometry.color == NULL
            || frame->geometry.uv == NULL
            || frame->geometry.triTexture == NULL) {
        fprintf(stderr, "[OoT Bridge] material geometry unavailable\n");
        return 0;
    }
    triangles = frame->geometry.numTriangles;
    if (triangles == 0u || triangles > MAX_STREAM_TRIANGLES) {
        fprintf(stderr, "[OoT Bridge] invalid triangle count: %u\n", triangles);
        return 0;
    }
    vertex_floats = (size_t)triangles * 9u;
    uv_floats = (size_t)triangles * 6u;
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
            || !send_skeleton(frame)
            || !write_u32_le(triangles)
            || !write_float_array(frame->geometry.position, vertex_floats)
            || !write_float_array(frame->geometry.normal, vertex_floats)
            || !write_float_array(frame->geometry.color, vertex_floats)
            || !write_float_array(frame->geometry.uv, uv_floats)
            || !write_u16_array_as_u32(frame->geometry.triTexture, triangles)
            || !send_texture_updates(engine, frame)) {
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
    if (!configure_binary_stdio()) {
        fprintf(stderr, "[OoT Bridge] failed to configure binary stdio\n");
        return 1;
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

    memset(g_texture_sent, 0, sizeof(g_texture_sent));
    memset(g_texture_revision, 0, sizeof(g_texture_revision));

    if (!send_handshake()) {
        goto done;
    }
    fprintf(stderr,
            "[OoT Bridge] persistent NTSC-U 1.2 session READY (20 Hz, protocol v%u + materials + skeleton)\n",
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
        if (!send_frame(engine, ++sequence, frame)) {
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
