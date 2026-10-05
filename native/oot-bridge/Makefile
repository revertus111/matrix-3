LIBOOT_ROOT ?= .deps/liboot
OOT_ROOT ?= .deps/oot
LIBOOT_COMMIT := 25208734c8ca388638f0ce63841e166fac9e5acb
OOT_COMMIT := 269d03016cd0e3d7a0b8925e02b97a319c1d0e8d
LIBOOT_PATCH := $(abspath liboot-ntsc12.patch)
BUILD_DIR := build
DIST_DIR := $(BUILD_DIR)/dist
PYTHON3 ?= python3
CMAKE ?= cmake
OOT_ROM ?=

ifeq ($(OS),Windows_NT)
  PROBE := $(DIST_DIR)/oot_probe.exe
else
  PROBE := $(DIST_DIR)/oot_probe
endif

.PHONY: all bootstrap deps prepare configure build probe clean

all: build

bootstrap: build

deps:
	@if [ ! -d "$(LIBOOT_ROOT)/.git" ]; then \
		git clone https://github.com/Cycl0o0/liboot.git "$(LIBOOT_ROOT)"; \
	fi
	@if [ ! -d "$(OOT_ROOT)/.git" ]; then \
		git clone https://github.com/zeldaret/oot.git "$(OOT_ROOT)"; \
	fi
	@# Keep tracked patches stable when Git for Windows has CRLF checkout enabled.
	@git -C "$(LIBOOT_ROOT)" config core.autocrlf false
	@git -C "$(LIBOOT_ROOT)" config core.eol lf
	@git -C "$(OOT_ROOT)" config core.autocrlf false
	@git -C "$(OOT_ROOT)" config core.eol lf
	@git -C "$(LIBOOT_ROOT)" fetch --quiet origin "$(LIBOOT_COMMIT)"
	@git -C "$(LIBOOT_ROOT)" checkout --quiet --force --detach "$(LIBOOT_COMMIT)"
	@git -C "$(LIBOOT_ROOT)" reset --quiet --hard "$(LIBOOT_COMMIT)"
	@git -C "$(LIBOOT_ROOT)" clean -fdx >/dev/null
	@git -C "$(OOT_ROOT)" fetch --quiet origin "$(OOT_COMMIT)"
	@git -C "$(OOT_ROOT)" checkout --quiet --force --detach "$(OOT_COMMIT)"
	@git -C "$(OOT_ROOT)" reset --quiet --hard "$(OOT_COMMIT)"
	@git -C "$(OOT_ROOT)" clean -fdx >/dev/null
	@git -C "$(LIBOOT_ROOT)" apply --check --ignore-space-change --ignore-whitespace "$(LIBOOT_PATCH)"
	@git -C "$(LIBOOT_ROOT)" apply --ignore-space-change --ignore-whitespace "$(LIBOOT_PATCH)"

prepare: deps
	cd "$(LIBOOT_ROOT)" && OOT="$(abspath $(OOT_ROOT))" LIBOOT_ASSET_PROFILE=ntsc-1.2 "$(PYTHON3)" tools/gen_asset_symbols.py
	cd "$(LIBOOT_ROOT)" && OOT="$(abspath $(OOT_ROOT))" "$(PYTHON3)" tools/gen_anim_headers.py

configure: prepare
	"$(CMAKE)" -S . -B "$(BUILD_DIR)" \
		-DLIBOOT_SOURCE_DIR="$(abspath $(LIBOOT_ROOT))" \
		-DCMAKE_BUILD_TYPE=RelWithDebInfo

build: configure
	"$(CMAKE)" --build "$(BUILD_DIR)" --config RelWithDebInfo --parallel

probe: build
	@if [ -z "$(OOT_ROM)" ]; then \
		echo 'OOT_ROM is required. Example: make probe OOT_ROM="/c/roms/oot-ntsc12.z64"'; \
		exit 2; \
	fi
	"$(PYTHON3)" "$(LIBOOT_ROOT)/tools/identify-rom.py" "$(OOT_ROM)"
	"$(PROBE)" "$(OOT_ROM)"

clean:
	rm -rf "$(BUILD_DIR)" "$(LIBOOT_ROOT)" "$(OOT_ROOT)"
