# Android.mk — Native build configuration for Nexus AI
#
# Builds all native modules for the Nexus AI Android application:
#   - nexus_shell:    Shell execution engine
#   - nexus_root:     Root access bridge
#   - nexus_fileops:  High-performance file operations
#   - nexus_sandbox:  Linux namespace sandbox isolation
#

LOCAL_PATH := $(call my-dir)

#=============================================================================
# Common flags and includes
#=============================================================================
COMMON_CFLAGS := -Wall -Wextra -Werror -O3 -fPIC -fvisibility=hidden
COMMON_CFLAGS += -DANDROID -DNDEBUG
COMMON_CFLAGS += -Wno-unused-parameter -Wno-missing-field-initializers
COMMON_CFLAGS += -fno-stack-protector -fomit-frame-pointer

COMMON_LDLIBS := -llog -landroid

#=============================================================================
# Module: nexus_shell (native-shell.c)
#   Safe command execution with piping, timeout, and process isolation
#=============================================================================
include $(CLEAR_VARS)

LOCAL_MODULE    := nexus_shell
LOCAL_SRC_FILES := native-shell.c
LOCAL_CFLAGS    := $(COMMON_CFLAGS)
LOCAL_LDLIBS    := $(COMMON_LDLIBS)

# Export JNI functions
LOCAL_CFLAGS += -DJNI_EXPORT='__attribute__ ((visibility ("default")))'

include $(BUILD_SHARED_LIBRARY)

#=============================================================================
# Module: nexus_root (root-bridge.c)
#   Root access bridge with privilege verification
#=============================================================================
include $(CLEAR_VARS)

LOCAL_MODULE    := nexus_root
LOCAL_SRC_FILES := root-bridge.c
LOCAL_CFLAGS    := $(COMMON_CFLAGS)
LOCAL_LDLIBS    := $(COMMON_LDLIBS) -lcrypto -lssl

# OpenSSL for hashing in root operations
LOCAL_CFLAGS += -DOPENSSL_NO_DEPRECATED

include $(BUILD_SHARED_LIBRARY)

#=============================================================================
# Module: nexus_fileops (file-ops.c)
#   High-performance file I/O with mmap, hashing, and recursive ops
#=============================================================================
include $(CLEAR_VARS)

LOCAL_MODULE    := nexus_fileops
LOCAL_SRC_FILES := file-ops.c
LOCAL_CFLAGS    := $(COMMON_CFLAGS)
LOCAL_LDLIBS    := $(COMMON_LDLIBS) -lcrypto -lssl

# Enable large file support
LOCAL_CFLAGS += -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -D_LARGEFILE64_SOURCE

include $(BUILD_SHARED_LIBRARY)

#=============================================================================
# Module: nexus_sandbox (sandbox-bridge.c)
#   Linux namespace isolation with seccomp and cgroups
#=============================================================================
include $(CLEAR_VARS)

LOCAL_MODULE    := nexus_sandbox
LOCAL_SRC_FILES := sandbox-bridge.c
LOCAL_CFLAGS    := $(COMMON_CFLAGS)
LOCAL_LDLIBS    := $(COMMON_LDLIBS)

# libseccomp for syscall filtering
LOCAL_STATIC_LIBRARIES := libseccomp

# Required for namespace operations
LOCAL_CFLAGS += -D_GNU_SOURCE

# Enable all namespace flags
LOCAL_CFLAGS += -DCLONE_NEWNS -DCLONE_NEWPID -DCLONE_NEWNET \
                -DCLONE_NEWIPC -DCLONE_NEWUTS -DCLONE_NEWUSER \
                -DCLONE_NEWCGROUP

include $(BUILD_SHARED_LIBRARY)

#=============================================================================
# Optional: Static library dependencies
#=============================================================================
# If libseccomp is available as prebuilt:
#
# include $(CLEAR_VARS)
# LOCAL_MODULE := libseccomp
# LOCAL_SRC_FILES := libs/$(TARGET_ARCH_ABI)/libseccomp.a
# include $(PREBUILT_STATIC_LIBRARY)

#=============================================================================
# Build all modules
#=============================================================================
.PHONY: all
all: $(LOCAL_PATH)/../libs/$(TARGET_ARCH_ABI)/libnexus_shell.so \
     $(LOCAL_PATH)/../libs/$(TARGET_ARCH_ABI)/libnexus_root.so \
     $(LOCAL_PATH)/../libs/$(TARGET_ARCH_ABI)/libnexus_fileops.so \
     $(LOCAL_PATH)/../libs/$(TARGET_ARCH_ABI)/libnexus_sandbox.so
