#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#include "liboot_engine.h"

#define EXPECTED_ROM_SIZE 33554432u

static int g_failures = 0;

static void result_line(const char *name, int pass)
{
    printf("[OoT NTSC12] %s: %s\n", name, pass ? "PASS" : "FAIL");
    if (!pass) {
        ++g_failures;
    }
}

static int require_ok(const char *name, OoTResult result)
{
    int pass = result == OOT_ENGINE_RESULT_OK;
    if (!pass) {
        fprintf(stderr, "[OoT NTSC12] %s error: %s\n",
                name, oot_engine_result_string(result));
    }
    result_line(name, pass);
    return pass;
}

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

int main(int argc, char **argv)
{
    static const struct OoTSurface floor[] = {
        { 0, {{ -1000, 0, -1000 }, { -1000, 0, 1000 }, { 1000, 0, 1000 }} },
        { 0, {{ -1000, 0, -1000 }, { 1000, 0, 1000 }, { 1000, 0, -1000 }} },
    };
    uint8_t *rom = NULL;
    size_t rom_size = 0u;
    OoTEngineConfig config;
    OoTEngineInput input;
    OoTEngine *engine = NULL;
    const OoTEngineFrame *frame = NULL;
    float start_x = 0.0f;
    float start_z = 0.0f;
    float start_anim_frame = 0.0f;
    int16_t start_anim_id = 0;
    uint32_t start_action = 0u;
    uint32_t adult_triangles = 0u;
    uint32_t child_triangles = 0u;
    int moved = 0;
    int animation_changed = 0;
    int i;

    if (argc != 2) {
        fprintf(stderr, "usage: %s <legally-obtained-ntsc-u-1.2-rom>\n", argv[0]);
        return 2;
    }

    result_line("ROM read", read_rom(argv[1], &rom, &rom_size));
    if (rom == NULL) {
        result_line("RESULT", 0);
        return 1;
    }
    result_line("ROM size 32 MiB", rom_size == EXPECTED_ROM_SIZE);
    result_line("Engine API version", oot_engine_api_version() == OOT_ENGINE_API_VERSION);

    if (!require_ok("Config init", oot_engine_config_init(&config))) {
        goto done;
    }
    config.romData = rom;
    config.romSize = rom_size;
    config.fixedStepSeconds = 1.0f / 20.0f;

    if (!require_ok("Engine create", oot_engine_create(&config, &engine))) {
        goto done;
    }

    free(rom);
    rom = NULL;

    if (!require_ok("Static floor", oot_engine_static_world_load(
            engine, floor, sizeof(floor) / sizeof(floor[0]), NULL, 0u))) {
        goto done;
    }
    if (!require_ok("Adult Link create", oot_engine_link_create(
            engine, 0.0f, 0.0f, 0.0f))) {
        goto done;
    }
    if (!require_ok("Adult age", oot_engine_link_set_age(engine, OOT_AGE_ADULT))) {
        goto done;
    }
    if (!require_ok("Adult equipment", oot_engine_link_set_equipment(
            engine, OOT_SWORD_MASTER, OOT_SHIELD_HYLIAN,
            OOT_TUNIC_KOKIRI, OOT_BOOTS_KOKIRI))) {
        goto done;
    }
    if (!require_ok("Input init", oot_engine_input_init(&input))) {
        goto done;
    }
    if (!require_ok("Initial step", oot_engine_step(engine, &input, &frame))) {
        goto done;
    }

    adult_triangles = frame->geometry.numTriangles;
    start_x = frame->link.position[0];
    start_z = frame->link.position[2];
    start_anim_id = frame->link.animId;
    start_anim_frame = frame->link.animFrame;
    start_action = frame->link.action;

    result_line("Adult age state", frame->link.age == OOT_AGE_ADULT);
    result_line("Adult skeleton", frame->skeletonAvailable != 0u);
    result_line("Adult geometry", adult_triangles > 0u &&
                                   frame->linkGeometryTruncated == 0u);

    for (i = 0; i < 18; ++i) {
        input.stickY = 1.0f;
        input.buttons = 0u;
        if (!require_ok("Movement step", oot_engine_step(engine, &input, &frame))) {
            goto done;
        }
    }

    {
        float dx = frame->link.position[0] - start_x;
        float dz = frame->link.position[2] - start_z;
        moved = (dx * dx + dz * dz) > 0.0001f;
        animation_changed =
            frame->link.animId != start_anim_id ||
            frame->link.action != start_action ||
            frame->link.animFrame != start_anim_frame;
    }
    result_line("Movement changed position", moved);
    result_line("Animation state advanced", animation_changed);
    result_line("Adult geometry after movement",
                frame->geometry.numTriangles > 0u &&
                frame->linkGeometryTruncated == 0u);

    input.stickX = 0.0f;
    input.stickY = 0.0f;
    input.buttons = 0u;

    if (!require_ok("Child age switch",
                    oot_engine_link_set_age(engine, OOT_AGE_CHILD))) {
        goto done;
    }
    if (!require_ok("Child equipment", oot_engine_link_set_equipment(
            engine, OOT_SWORD_KOKIRI, OOT_SHIELD_DEKU,
            OOT_TUNIC_KOKIRI, OOT_BOOTS_KOKIRI))) {
        goto done;
    }

    for (i = 0; i < 3; ++i) {
        if (!require_ok("Child step", oot_engine_step(engine, &input, &frame))) {
            goto done;
        }
    }

    child_triangles = frame->geometry.numTriangles;
    result_line("Child age state", frame->link.age == OOT_AGE_CHILD);
    result_line("Child skeleton", frame->skeletonAvailable != 0u);
    result_line("Child geometry", child_triangles > 0u &&
                                   frame->linkGeometryTruncated == 0u);
    result_line("Simulation tick advanced", frame->simulationTick >= 22u);

    printf("[OoT NTSC12] Adult triangles: %u\n", adult_triangles);
    printf("[OoT NTSC12] Child triangles: %u\n", child_triangles);
    printf("[OoT NTSC12] Final position: %.3f %.3f %.3f\n",
           frame->link.position[0],
           frame->link.position[1],
           frame->link.position[2]);

done:
    if (engine != NULL) {
        OoTResult destroy_result = oot_engine_destroy(engine);
        require_ok("Engine destroy", destroy_result);
    }
    free(rom);

    if (g_failures == 0) {
        printf("[OoT NTSC12] RESULT: PASS\n");
        return 0;
    }

    printf("[OoT NTSC12] RESULT: FAIL (%d checks)\n", g_failures);
    return 1;
}
