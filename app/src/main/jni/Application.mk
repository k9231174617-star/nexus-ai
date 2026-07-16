# Application.mk — Global build settings for Nexus AI native modules

# Target Android API level (minimum supported)
APP_PLATFORM := android-24

# Supported architectures
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64

# Build type: release with debug info
APP_OPTIM := release

# C++ standard (not used for C modules but good practice)
APP_CPPFLAGS := -std=c++17
APP_STL := c++_shared

# Enable NEON for ARM
APP_CFLAGS += -mfpu=neon -mfloat-abi=softfp

# Linker flags
APP_LDFLAGS := -Wl,--build-id=sha1 -Wl,--no-undefined -Wl,-z,noexecstack

# Enable LTO for smaller binaries
APP_CFLAGS += -flto
APP_LDFLAGS += -flto
