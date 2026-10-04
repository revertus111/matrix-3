LIBSM64_ROOT ?= .deps/libsm64
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

.PHONY: all bootstrap deps clean

all: $(TARGET)

bootstrap: deps all

deps:
	@if [ ! -d "$(LIBSM64_ROOT)/.git" ]; then \
		git clone https://github.com/libsm64/libsm64.git "$(LIBSM64_ROOT)"; \
	fi
	$(MAKE) -C "$(LIBSM64_ROOT)" lib

$(TARGET): sm64_bridge.c $(LIBSM64_LIB)
	@mkdir -p "$(DIST_DIR)"
	$(CC) $(CFLAGS) $(INCLUDES) sm64_bridge.c "$(LIBSM64_LIB)" $(EXTRA_LINK) -o "$(TARGET)"
	cp -f "$(LIBSM64_LIB)" "$(COPIED_LIB)"

clean:
	rm -rf "$(DIST_DIR)"
