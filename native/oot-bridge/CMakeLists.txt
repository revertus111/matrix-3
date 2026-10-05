cmake_minimum_required(VERSION 3.16)

project(matrix_oot_ntsc12_probe LANGUAGES C)

set(LIBOOT_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/.deps/liboot" CACHE PATH
    "Path to the pinned liboot checkout")

if(NOT EXISTS "${LIBOOT_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "liboot checkout not found. Run the bridge Makefile bootstrap/probe target first.")
endif()

# This bridge intentionally selects the exact ROM revision used by the project.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(LIBOOT_OOT_VERSION "NTSC_1_2" CACHE STRING "" FORCE)
set(LIBOOT_OOT_REVISION "2" CACHE STRING "" FORCE)

add_subdirectory("${LIBOOT_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/liboot")

add_executable(oot_probe oot_probe.c)
set_target_properties(oot_probe PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED YES
    C_EXTENSIONS NO
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/dist"
)
target_link_libraries(oot_probe PRIVATE liboot::oot)

# Keep the native library beside the probe. This avoids PATH/rpath setup during
# the compatibility proof and mirrors the self-contained Mario sidecar layout.
set_target_properties(oot PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/dist"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/dist"
    ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
)

if(UNIX AND NOT APPLE)
    set_target_properties(oot_probe PROPERTIES BUILD_RPATH "$ORIGIN")
endif()
