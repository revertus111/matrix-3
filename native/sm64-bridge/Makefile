LIBSM64_ROOT ?= .deps/libsm64
LIBSM64_COMMIT := fd11813208272b4271d92bd92feb8f3fdbe61be5
LIBSM64_PATCH := $(abspath libsm64-semantic-parts.patch)
DIST_DIR := dist
CC ?= cc
CFLAGS ?= -O2 -Wall -Wextra -std=c11
INCLUDES := -I$(LIBSM64_ROOT)/dist/include

ifeq ($(OS),Windows_NT)
  TARGET := $(DIST_DIR)/sm64_bridge.exe
  LIBSM64_LIB := $(LIBSM64_ROOT)/dist/sm64.dll
  COPIED_LIB := $(DIST_DIR)/sm64.dll
  EXTRA_LINK := -lm
else
  TARGET := $(DIST_DIR)/sm64_bridge
  LIBSM64_LIB := $(LIBSM64_ROOT)/dist/libsm64.so
  COPIED_LIB := $(DIST_DIR)/libsm64.so
  EXTRA_LINK := -Wl,-rpath,'$$ORIGIN' -lm
endif

.PHONY: all bootstrap deps clean test-combat

all: $(TARGET)

bootstrap: deps all

deps:
	@if [ ! -d "$(LIBSM64_ROOT)/.git" ]; then \
		git clone https://github.com/libsm64/libsm64.git "$(LIBSM64_ROOT)"; \
	fi
	@# Keep the pinned dependency byte-compatible with the tracked LF patch even
	@# when the user's global Git-for-Windows config enables CRLF checkout.
	@git -C "$(LIBSM64_ROOT)" config core.autocrlf false
	@git -C "$(LIBSM64_ROOT)" config core.eol lf
	@git -C "$(LIBSM64_ROOT)" fetch --quiet origin "$(LIBSM64_COMMIT)"
	@git -C "$(LIBSM64_ROOT)" checkout --quiet --force --detach "$(LIBSM64_COMMIT)"
	@git -C "$(LIBSM64_ROOT)" reset --quiet --hard "$(LIBSM64_COMMIT)"
	@git -C "$(LIBSM64_ROOT)" clean -fdx >/dev/null
	@git -C "$(LIBSM64_ROOT)" apply --check --ignore-space-change --ignore-whitespace "$(LIBSM64_PATCH)"
	@git -C "$(LIBSM64_ROOT)" apply --ignore-space-change --ignore-whitespace "$(LIBSM64_PATCH)"
	$(MAKE) -C "$(LIBSM64_ROOT)" clean
	$(MAKE) -C "$(LIBSM64_ROOT)" lib

$(TARGET): sm64_bridge.c combat_overlay.h $(LIBSM64_LIB)
	@mkdir -p "$(DIST_DIR)"
	$(CC) $(CFLAGS) $(INCLUDES) sm64_bridge.c "$(LIBSM64_LIB)" $(EXTRA_LINK) -o "$(TARGET)"
	cp -f "$(LIBSM64_LIB)" "$(COPIED_LIB)"

clean:
	rm -rf "$(DIST_DIR)"


# ROM-free production curve + native frame fixture; Java parser test is documented.
test-combat: $(TARGET)
	$(CC) $(CFLAGS) $(INCLUDES) tests/combat_test.c "$(LIBSM64_LIB)" $(EXTRA_LINK) -o "$(DIST_DIR)/combat_test"
	./$(DIST_DIR)/combat_test "$(DIST_DIR)/combat-frame.bin"
