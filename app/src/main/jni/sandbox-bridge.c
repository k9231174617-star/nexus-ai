/*
 * sandbox-bridge.c — Linux namespace isolation for Nexus AI Code Sandbox
 * 
 * Implements process isolation using Linux namespaces (CLONE_NEWNS,
 * CLONE_NEWPID, CLONE_NEWNET, CLONE_NEWIPC, CLONE_NEWUTS, CLONE_NEWUSER).
 * Provides secure code execution with resource limits.
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sched.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/prctl.h>
#include <seccomp.h>
#include <linux/seccomp.h>
#include <linux/filter.h>
#include <linux/audit.h>
#include <android/log.h>

#define LOG_TAG "NexusSandbox"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define STACK_SIZE (1024 * 1024)  // 1MB stack for sandbox process
#define MAX_OUTPUT_SIZE (512 * 1024)
#define SANDBOX_UID 99999
#define SANDBOX_GID 99999

// Namespace flags
#define NS_FLAGS (CLONE_NEWNS | CLONE_NEWPID | CLONE_NEWNET | \
                  CLONE_NEWIPC | CLONE_NEWUTS | CLONE_NEWUSER)

typedef struct {
    char *rootfs_path;
    char *work_dir;
    char *command;
    char **envp;
    uid_t uid;
    gid_t gid;
    
    // Resource limits
    long max_memory_mb;
    long max_cpu_seconds;
    long max_file_size_mb;
    int max_processes;
    
    // Network isolation
    int allow_network;
    
    // Output
    char *stdout_buffer;
    size_t stdout_size;
    char *stderr_buffer;
    size_t stderr_size;
    int exit_code;
    
    // Pipes
    int stdin_pipe[2];
    int stdout_pipe[2];
    int stderr_pipe[2];
} SandboxConfig;
typedef struct {
    char *rootfs_path;
    char *work_dir;
    char *command;
    char **envp;
    uid_t uid;
    gid_t gid;
    
    // Resource limits
    long max_memory_mb;
    long max_cpu_seconds;
    long max_file_size_mb;
    int max_processes;
    
    // Network isolation
    int allow_network;
    
    // Output
    char *stdout_buffer;
    size_t stdout_size;
    size_t stdout_capacity;  // ← добавить это поле
    char *stderr_buffer;
    size_t stderr_size;
    size_t stderr_capacity;  // ← добавить это поле
    int exit_code;
    
    // Pipes
    int stdin_pipe[2];
    int stdout_pipe[2];
    int stderr_pipe[2];
} SandboxConfig;


typedef struct {
    pid_t child_pid;
    SandboxConfig *config;
    int active;
} SandboxInstance;

static int write_file(const char *path, const char *content) {
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return -1;
    size_t len = strlen(content);
    ssize_t written = write(fd, content, len);
    close(fd);
    return (written == (ssize_t)len) ? 0 : -1;
}

static int setup_uid_map(pid_t pid, uid_t inside_uid, uid_t outside_uid) {
    char path[256];
    char content[64];
    
    snprintf(path, sizeof(path), "/proc/%d/uid_map", pid);
    snprintf(content, sizeof(content), "%d %d 1", inside_uid, outside_uid);
    
    if (write_file(path, content) < 0) {
        LOGE("Failed to write uid_map");
        return -1;
    }
    
    // Disable setgroups
    snprintf(path, sizeof(path), "/proc/%d/setgroups", pid);
    write_file(path, "deny");
    
    snprintf(path, sizeof(path), "/proc/%d/gid_map", pid);
    snprintf(content, sizeof(content), "%d %d 1", inside_uid, outside_uid);
    
    if (write_file(path, content) < 0) {
        LOGE("Failed to write gid_map");
        return -1;
    }
    
    return 0;
}

static int setup_rootfs(SandboxConfig *cfg) {
    // Create minimal root filesystem
    const char *dirs[] = {
        "bin", "lib", "lib64", "usr", "usr/bin", 
        "tmp", "dev", "proc", "sys", "etc", NULL
    };
    
    for (int i = 0; dirs[i]; i++) {
        char path[512];
        snprintf(path, sizeof(path), "%s/%s", cfg->rootfs_path, dirs[i]);
        mkdir(path, 0755);
    }
    
    // Create minimal devices
    mknodat(AT_FDCWD, "/dev/null", S_IFCHR | 0666, makedev(1, 3));
    mknodat(AT_FDCWD, "/dev/zero", S_IFCHR | 0666, makedev(1, 5));
    mknodat(AT_FDCWD, "/dev/random", S_IFCHR | 0666, makedev(1, 8));
    mknodat(AT_FDCWD, "/dev/urandom", S_IFCHR | 0666, makedev(1, 9));
    
    // Mount proc
    char proc_path[512];
    snprintf(proc_path, sizeof(proc_path), "%s/proc", cfg->rootfs_path);
    mount("proc", proc_path, "proc", MS_NOSUID | MS_NOEXEC | MS_NODEV, NULL);
    
    // Mount tmpfs for tmp
    char tmp_path[512];
    snprintf(tmp_path, sizeof(tmp_path), "%s/tmp", cfg->rootfs_path);
    mount("tmpfs", tmp_path, "tmpfs", MS_NOSUID | MS_NODEV, "size=100M,mode=1777");
    
    // Bind mount necessary binaries
    const char *bins[] = {"/system/bin/sh", NULL};
    for (int i = 0; bins[i]; i++) {
        char src[512], dst[512];
        snprintf(src, sizeof(src), "%s", bins[i]);
        snprintf(dst, sizeof(dst), "%s/bin/%s", cfg->rootfs_path, strrchr(bins[i], '/') + 1);
        int fd = open(src, O_RDONLY);
        if (fd >= 0) {
            close(fd);
            mount(src, dst, NULL, MS_BIND | MS_RDONLY, NULL);
        }
    }
    
    // Chroot
    if (chroot(cfg->rootfs_path) < 0) {
        LOGE("chroot failed: %s", strerror(errno));
        return -1;
    }
    
    chdir("/");
    
    return 0;
}

static int setup_seccomp(void) {
    scmp_filter_ctx ctx = seccomp_init(SCMP_ACT_ERRNO(EPERM));
    if (!ctx) {
        LOGE("seccomp_init failed");
        return -1;
    }
    
    // Allow basic syscalls
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(read), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(write), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(open), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(openat), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(close), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(fstat), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(lseek), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(mmap), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(munmap), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(brk), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(exit), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(exit_group), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getpid), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getuid), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getgid), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(gettimeofday), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(clock_gettime), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(nanosleep), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rt_sigaction), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rt_sigreturn), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(ioctl), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(fcntl), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(access), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(dup), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(dup2), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(pipe), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(poll), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(select), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(readv), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(writev), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(pread64), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(pwrite64), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(mprotect), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(madvise), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(clone), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(wait4), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(waitid), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(kill), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(tgkill), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getrandom), 0);
    
    // File operations
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(stat), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(lstat), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(newfstatat), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getdents), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getdents64), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(chdir), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getcwd), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rename), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(unlink), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rmdir), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(mkdir), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(chmod), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(chown), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(umask), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(truncate), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(ftruncate), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(sync), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(fsync), 0);
    
    // Memory
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(mremap), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(msync), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(mincore), 0);
    
    // Time
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(time), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(times), 0);
    
    // Process info
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getppid), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getpgrp), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getsid), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getrlimit), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(prlimit64), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(getrusage), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(sysinfo), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(uname), 0);
    
    // Signals
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(sigaltstack), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(signalfd), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(signalfd4), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rt_sigprocmask), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rt_sigpending), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rt_sigtimedwait), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rt_sigqueueinfo), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(rt_tgsigqueueinfo), 0);
    
    // Threads
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(set_tid_address), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(set_robust_list), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(get_robust_list), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(futex), 0);
    
    // Arch-specific
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(arch_prctl), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(set_thread_area), 0);
    seccomp_rule_add(ctx, SCMP_ACT_ALLOW, SCMP_SYS(get_thread_area), 0);
    
    // Block dangerous syscalls explicitly
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(execveat), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(ptrace), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(mount), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(umount2), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(pivot_root), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(chroot), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(open_by_handle_at), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(init_module), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(finit_module), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(delete_module), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(iopl), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(ioperm), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(swapon), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(swapoff), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(reboot), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(kexec_load), 0);
    seccomp_rule_add(ctx, SCMP_ACT_KILL, SCMP_SYS(kexec_file_load), 0);
    
    int ret = seccomp_load(ctx);
    seccomp_release(ctx);
    
    if (ret < 0) {
        LOGE("seccomp_load failed: %d", ret);
        return -1;
    }
    
    LOGI("seccomp filter loaded successfully");
    return 0;
}

static int sandbox_child(void *arg) {
    SandboxConfig *cfg = (SandboxConfig*)arg;
    
    // Setup namespaces
    if (setup_rootfs(cfg) < 0) {
        LOGE("Rootfs setup failed");
        _exit(1);
    }
    
    // Set resource limits
    struct rlimit rlim;
    
    // Memory limit
    if (cfg->max_memory_mb > 0) {
        rlim.rlim_cur
        rlim.rlim_max = cfg->max_memory_mb * 1024 * 1024;
        rlim.rlim_cur = rlim.rlim_max;
        setrlimit(RLIMIT_AS, &rlim);
    }
    
    // CPU time limit
    if (cfg->max_cpu_seconds > 0) {
        rlim.rlim_cur = cfg->max_cpu_seconds;
        rlim.rlim_max = cfg->max_cpu_seconds + 1;
        setrlimit(RLIMIT_CPU, &rlim);
    }
    
    // File size limit
    if (cfg->max_file_size_mb > 0) {
        rlim.rlim_cur = cfg->max_file_size_mb * 1024 * 1024;
        rlim.rlim_max = rlim.rlim_cur;
        setrlimit(RLIMIT_FSIZE, &rlim);
    }
    
    // Process limit
    if (cfg->max_processes > 0) {
        rlim.rlim_cur = cfg->max_processes;
        rlim.rlim_max = cfg->max_processes;
        setrlimit(RLIMIT_NPROC, &rlim);
    }
    
    // Stack limit
    rlim.rlim_cur = 8 * 1024 * 1024;  // 8MB stack
    rlim.rlim_max = 8 * 1024 * 1024;
    setrlimit(RLIMIT_STACK, &rlim);
    
    // Core dump limit (disable)
    rlim.rlim_cur = 0;
    rlim.rlim_max = 0;
    setrlimit(RLIMIT_CORE, &rlim);
    
    // No file descriptors > 64
    rlim.rlim_cur = 64;
    rlim.rlim_max = 64;
    setrlimit(RLIMIT_NOFILE, &rlim);
    
    // Setup seccomp
    if (setup_seccomp() < 0) {
        LOGE("seccomp setup failed");
        _exit(1);
    }
    
    // Redirect stdio
    dup2(cfg->stdin_pipe[0], STDIN_FILENO);
    dup2(cfg->stdout_pipe[1], STDOUT_FILENO);
    dup2(cfg->stderr_pipe[1], STDERR_FILENO);
    
    close(cfg->stdin_pipe[0]);
    close(cfg->stdin_pipe[1]);
    close(cfg->stdout_pipe[0]);
    close(cfg->stdout_pipe[1]);
    close(cfg->stderr_pipe[0]);
    close(cfg->stderr_pipe[1]);
    
    // Set working directory
    if (cfg->work_dir && chdir(cfg->work_dir) < 0) {
        chdir("/tmp");
    }
    
    // Execute command
    char *args[] = {"/bin/sh", "-c", cfg->command, NULL};
    execv("/bin/sh", args);
    
    // If exec fails
    fprintf(stderr, "Failed to execute: %s\n", strerror(errno));
    _exit(127);
}

static int read_pipe_nonblock(int fd, char **buffer, size_t *size, size_t *capacity) {
    char tmp[4096];
    ssize_t n;
    
    while ((n = read(fd, tmp, sizeof(tmp))) > 0) {
        if (*size + n > MAX_OUTPUT_SIZE) {
            n = MAX_OUTPUT_SIZE - *size;
            if (n <= 0) return -1;
        }
        
        if (*size + n > *capacity) {
            size_t new_cap = *capacity * 2;
            while (new_cap < *size + n) new_cap *= 2;
            if (new_cap > MAX_OUTPUT_SIZE) new_cap = MAX_OUTPUT_SIZE;
            
            char *new_buf = realloc(*buffer, new_cap);
            if (!new_buf) return -1;
            
            *buffer = new_buf;
            *capacity = new_cap;
        }
        
        memcpy(*buffer + *size, tmp, n);
        *size += n;
    }
    
    return (n < 0 && errno == EAGAIN) ? 0 : (int)n;
}

JNIEXPORT jlong JNICALL
Java_com_nexus_agent_core_sandbox_CodeSandbox_nativeCreateSandbox(
        JNIEnv *env, jobject thiz, jstring rootfsPath, jstring workDir,
        jlong maxMemoryMb, jlong maxCpuSeconds, jlong maxFileSizeMb,
        jint maxProcesses, jboolean allowNetwork) {
    
    SandboxConfig *cfg = calloc(1, sizeof(SandboxConfig));
    if (!cfg) return 0;
    
    if (rootfsPath) {
        const char *path = (*env)->GetStringUTFChars(env, rootfsPath, NULL);
        cfg->rootfs_path = strdup(path);
        (*env)->ReleaseStringUTFChars(env, rootfsPath, path);
    } else {
        cfg->rootfs_path = strdup("/data/local/tmp/nexus_sandbox");
    }
    
    if (workDir) {
        const char *wd = (*env)->GetStringUTFChars(env, workDir, NULL);
        cfg->work_dir = strdup(wd);
        (*env)->ReleaseStringUTFChars(env, workDir, wd);
    }
    
    cfg->max_memory_mb = (long)maxMemoryMb;
    cfg->max_cpu_seconds = (long)maxCpuSeconds;
    cfg->max_file_size_mb = (long)maxFileSizeMb;
    cfg->max_processes = maxProcesses;
    cfg->allow_network = allowNetwork ? 1 : 0;
    
    cfg->stdout_capacity = 4096;
    cfg->stderr_capacity = 4096;
    cfg->stdout_buffer = malloc(cfg->stdout_capacity);
    cfg->stderr_buffer = malloc(cfg->stderr_capacity);
    
    if (!cfg->stdout_buffer || !cfg->stderr_buffer) {
        free(cfg->stdout_buffer);
        free(cfg->stderr_buffer);
        free(cfg->rootfs_path);
        free(cfg->work_dir);
        free(cfg);
        return 0;
    }
    
    LOGI("Sandbox config created: mem=%ldMB, cpu=%lds, files=%ldMB, procs=%d",
         cfg->max_memory_mb, cfg->max_cpu_seconds, cfg->max_file_size_mb, cfg->max_processes);
    
    return (jlong)(uintptr_t)cfg;
}

JNIEXPORT jint JNICALL
Java_com_nexus_agent_core_sandbox_CodeSandbox_nativeExecuteInSandbox(
        JNIEnv *env, jobject thiz, jlong handle, jstring command, jint timeoutMs) {
    
    SandboxConfig *cfg = (SandboxConfig*)(uintptr_t)handle;
    if (!cfg) return -1;
    
    const char *cmd = (*env)->GetStringUTFChars(env, command, NULL);
    if (!cmd) return -1;
    
    cfg->command = strdup(cmd);
    (*env)->ReleaseStringUTFChars(env, command, cmd);
    
    // Create pipes
    if (pipe(cfg->stdin_pipe) < 0 || pipe(cfg->stdout_pipe) < 0 || pipe(cfg->stderr_pipe) < 0) {
        LOGE("Pipe creation failed");
        free(cfg->command);
        cfg->command = NULL;
        return -1;
    }
    
    // Set non-blocking on read ends
    int flags;
    flags = fcntl(cfg->stdout_pipe[0], F_GETFL, 0);
    fcntl(cfg->stdout_pipe[0], F_SETFL, flags | O_NONBLOCK);
    flags = fcntl(cfg->stderr_pipe[0], F_GETFL, 0);
    fcntl(cfg->stderr_pipe[0], F_SETFL, flags | O_NONBLOCK);
    
    // Allocate stack for child
    char *stack = malloc(STACK_SIZE);
    if (!stack) {
        close(cfg->stdin_pipe[0]); close(cfg->stdin_pipe[1]);
        close(cfg->stdout_pipe[0]); close(cfg->stdout_pipe[1]);
        close(cfg->stderr_pipe[0]); close(cfg->stderr_pipe[1]);
        free(cfg->command);
        cfg->command = NULL;
        return -1;
    }
    
    // Create sandbox process with namespaces
    pid_t child_pid = clone(sandbox_child, stack + STACK_SIZE, 
        NS_FLAGS | SIGCHLD, cfg);
    
    if (child_pid < 0) {
        LOGE("clone() failed: %s", strerror(errno));
        free(stack);
        close(cfg->stdin_pipe[0]); close(cfg->stdin_pipe[1]);
        close(cfg->stdout_pipe[0]); close(cfg->stdout_pipe[1]);
        close(cfg->stderr_pipe[0]); close(cfg->stderr_pipe[1]);
        free(cfg->command);
        cfg->command = NULL;
        return -1;
    }
    
    LOGI("Sandbox process created: pid=%d", child_pid);
    
    // Setup UID/GID mapping for user namespace
    if (setup_uid_map(child_pid, 0, getuid()) < 0) {
        LOGE("UID map setup failed");
        kill(child_pid, SIGKILL);
        waitpid(child_pid, NULL, 0);
        free(stack);
        close(cfg->stdin_pipe[0]); close(cfg->stdin_pipe[1]);
        close(cfg->stdout_pipe[0]); close(cfg->stdout_pipe[1]);
        close(cfg->stderr_pipe[0]); close(cfg->stderr_pipe[1]);
        free(cfg->command);
        cfg->command = NULL;
        return -1;
    }
    
    // Close unused pipe ends in parent
    close(cfg->stdin_pipe[0]);
    cfg->stdin_pipe[0] = -1;
    close(cfg->stdout_pipe[1]);
    cfg->stdout_pipe[1] = -1;
    close(cfg->stderr_pipe[1]);
    cfg->stderr_pipe[1] = -1;
    
    // Wait for process with timeout
    int timeout = (timeoutMs > 0) ? timeoutMs : 30000;
    struct timeval start, now;
    gettimeofday(&start, NULL);
    
    int status;
    pid_t result;
    int stdout_open = 1, stderr_open = 1;
    fd_set read_fds;
    int max_fd = (cfg->stdout_pipe[0] > cfg->stderr_pipe[0]) 
                 ? cfg->stdout_pipe[0] : cfg->stderr_pipe[0];
    
    cfg->stdout_size = 0;
    cfg->stderr_size = 0;
    
    while (1) {
        FD_ZERO(&read_fds);
        if (stdout_open) FD_SET(cfg->stdout_pipe[0], &read_fds);
        if (stderr_open) FD_SET(cfg->stderr_pipe[0], &read_fds);
        
        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 50000; // 50ms
        
        int ret = select(max_fd + 1, &read_fds, NULL, NULL, &tv);
        
        if (ret > 0) {
            if (stdout_open && FD_ISSET(cfg->stdout_pipe[0], &read_fds)) {
                if (read_pipe_nonblock(cfg->stdout_pipe[0], 
                    &cfg->stdout_buffer, &cfg->stdout_size, 
                    &cfg->stdout_capacity) < 0) {
                    stdout_open = 0;
                }
            }
            if (stderr_open && FD_ISSET(cfg->stderr_pipe[0], &read_fds)) {
                if (read_pipe_nonblock(cfg->stderr_pipe[0],
                    &cfg->stderr_buffer, &cfg->stderr_size,
                    &cfg->stderr_capacity) < 0) {
                    stderr_open = 0;
                }
            }
        }
        
        // Check if process exited
        result = waitpid(child_pid, &status, WNOHANG);
        if (result == child_pid) {
            break;
        }
        
        // Check timeout
        gettimeofday(&now, NULL);
        long elapsed = (now.tv_sec - start.tv_sec) * 1000 + 
                      (now.tv_usec - start.tv_usec) / 1000;
        
        if (elapsed >= timeout) {
            LOGW("Sandbox timeout after %ld ms", elapsed);
            kill(child_pid, SIGTERM);
            usleep(200000);
            kill(child_pid, SIGKILL);
            waitpid(child_pid, &status, 0);
            cfg->exit_code = -1;
            break;
        }
    }
    
    // Read remaining output
    while (stdout_open || stderr_open) {
        FD_ZERO(&read_fds);
        if (stdout_open) FD_SET(cfg->stdout_pipe[0], &read_fds);
        if (stderr_open) FD_SET(cfg->stderr_pipe[0], &read_fds);
        
        struct timeval tv = {0, 100000};
        int ret = select(max_fd + 1, &read_fds, NULL, NULL, &tv);
        
        if (ret <= 0) break;
        
        if (stdout_open && FD_ISSET(cfg->stdout_pipe[0], &read_fds)) {
            if (read_pipe_nonblock(cfg->stdout_pipe[0],
                &cfg->stdout_buffer, &cfg->stdout_size,
                &cfg->stdout_capacity) <= 0) {
                stdout_open = 0;
            }
        }
        if (stderr_open && FD_ISSET(cfg->stderr_pipe[0], &read_fds)) {
            if (read_pipe_nonblock(cfg->stderr_pipe[0],
                &cfg->stderr_buffer, &cfg->stderr_size,
                &cfg->stderr_capacity) <= 0) {
                stderr_open = 0;
            }
        }
    }
    
    if (result == child_pid) {
        if (WIFEXITED(status)) {
            cfg->exit_code = WEXITSTATUS(status);
        } else if (WIFSIGNALED(status)) {
            cfg->exit_code = 128 + WTERMSIG(status);
        }
    }
    
    // Cleanup pipes
    if (cfg->stdin_pipe[1] >= 0) close(cfg->stdin_pipe[1]);
    if (cfg->stdout_pipe[0] >= 0) close(cfg->stdout_pipe[0]);
    if (cfg->stderr_pipe[0] >= 0) close(cfg->stderr_pipe[0]);
    
    cfg->stdin_pipe[0] = -1;
    cfg->stdin_pipe[1] = -1;
    cfg->stdout_pipe[0] = -1;
    cfg->stdout_pipe[1] = -1;
    cfg->stderr_pipe[0] = -1;
    cfg->stderr_pipe[1] = -1;
    
    free(cfg->command);
    cfg->command = NULL;
    free(stack);
    
    // Null-terminate buffers
    if (cfg->stdout_size < cfg->stdout_capacity) {
        cfg->stdout_buffer[cfg->stdout_size] = '\0';
    }
    if (cfg->stderr_size < cfg->stderr_capacity) {
        cfg->stderr_buffer[cfg->stderr_size] = '\0';
    }
    
    LOGI("Sandbox execution finished: exit_code=%d, stdout=%zu, stderr=%zu",
         cfg->exit_code, cfg->stdout_size, cfg->stderr_size);
    
    return cfg->exit_code;
}

JNIEXPORT jstring JNICALL
Java_com_nexus_agent_core_sandbox_CodeSandbox_nativeGetStdout(JNIEnv *env, jobject thiz, jlong handle) {
    SandboxConfig *cfg = (SandboxConfig*)(uintptr_t)handle;
    if (!cfg || !cfg->stdout_buffer) return NULL;
    
    // Ensure null-terminated
    if (cfg->stdout_size > 0 && cfg->stdout_size < cfg->stdout_capacity) {
        cfg->stdout_buffer[cfg->stdout_size] = '\0';
    }
    
    return (*env)->NewStringUTF(env, cfg->stdout_buffer);
}

JNIEXPORT jstring JNICALL
Java_com_nexus_agent_core_sandbox_CodeSandbox_nativeGetStderr(JNIEnv *env, jobject thiz, jlong handle) {
    SandboxConfig *cfg = (SandboxConfig*)(uintptr_t)handle;
    if (!cfg || !cfg->stderr_buffer) return NULL;
    
    if (cfg->stderr_size > 0 && cfg->stderr_size < cfg->stderr_capacity) {
        cfg->stderr_buffer[cfg->stderr_size] = '\0';
    }
    
    return (*env)->NewStringUTF(env, cfg->stderr_buffer);
}

JNIEXPORT void JNICALL
Java_com_nexus_agent_core_sandbox_CodeSandbox_nativeWriteStdin(
        JNIEnv *env, jobject thiz, jlong handle, jstring input) {
    
    SandboxConfig *cfg = (SandboxConfig*)(uintptr_t)handle;
    if (!cfg || cfg->stdin_pipe[1] < 0) return;
    
    const char *data = (*env)->GetStringUTFChars(env, input, NULL);
    if (!data) return;
    
    size_t len = strlen(data);
    ssize_t written = write(cfg->stdin_pipe[1], data, len);
    if (written < 0) {
        LOGE("Failed to write to sandbox stdin: %s", strerror(errno));
    }
    
    (*env)->ReleaseStringUTFChars(env, input, data);
}

JNIEXPORT void JNICALL
Java_com_nexus_agent_core_sandbox_CodeSandbox_nativeDestroySandbox(
        JNIEnv *env, jobject thiz, jlong handle) {
    
    SandboxConfig *cfg = (SandboxConfig*)(uintptr_t)handle;
    if (!cfg) return;
    
    // Cleanup rootfs if it was temporary
    if (cfg->rootfs_path && strstr(cfg->rootfs_path, "nexus_sandbox") != NULL) {
        // Recursively remove
        char cmd[512];
        snprintf(cmd, sizeof(cmd), "rm -rf \"%s\"", cfg->rootfs_path);
        system(cmd);
    }
    
    free(cfg->stdout_buffer);
    free(cfg->stderr_buffer);
    free(cfg->rootfs_path);
    free(cfg->work_dir);
    free(cfg);
    
    LOGI("Sandbox destroyed");
}

JNIEXPORT jlong JNICALL
Java_com_nexus_agent_core_sandbox_NamespaceContainer_nativeCreateNamespace(
        JNIEnv *env, jobject thiz, jint namespaceFlags) {
    
    // Create a new namespace and return PID
    char *stack = malloc(STACK_SIZE);
    if (!stack) return -1;
    
    // Simple child function that just sleeps and waits for commands
    int child_fn(void *arg) {
        (void)arg;
        // Mount proc in new PID namespace
        mount("proc", "/proc", "proc", 0, NULL);
        
        // Keep process alive for namespace operations
        while (1) {
            pause();
        }
        return 0;
    }
    
    pid_t pid = clone(child_fn, stack + STACK_SIZE,
        namespaceFlags | SIGCHLD, NULL);
    
    if (pid < 0) {
        free(stack);
        return -1;
    }
    
    // Give child time to setup
    usleep(100000);
    
    free(stack);
    return (jlong)pid;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_sandbox_NamespaceContainer_nativeEnterNamespace(
        JNIEnv *env, jobject thiz, jlong namespacePid, jint namespaceType) {
    
    pid_t pid = (pid_t)namespacePid;
    
    const char *ns_names[] = {"mnt", "net", "pid", "ipc", "uts", "user", "cgroup"};
    if (namespaceType < 0 || namespaceType > 6) return JNI_FALSE;
    
    char path[256];
    snprintf(path, sizeof(path), "/proc/%d/ns/%s", pid, ns_names[namespaceType]);
    
    int fd = open(path, O_RDONLY);
    if (fd < 0) return JNI_FALSE;
    
    int ret = setns(fd, 0);
    close(fd);
    
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_sandbox_ProcessContainer_nativeSetResourceLimits(
        JNIEnv *env, jobject thiz, jlong pid, jlong maxMemory, jlong maxCpu, jlong maxFileSize) {
    
    // Write to cgroup v2 if available
    char cgroup_path[256];
    snprintf(cgroup_path, sizeof(cgroup_path), 
        "/sys/fs/cgroup/nexus_sandbox_%ld", (long)pid);
    
    mkdir(cgroup_path, 0755);
    
    // Memory limit
    if (maxMemory > 0) {
        char mem_path[512];
        char mem_val[64];
        snprintf(mem_path, sizeof(mem_path), "%s/memory.max", cgroup_path);
        snprintf(mem_val, sizeof(mem_val), "%ld", maxMemory * 1024 * 1024);
        write_file(mem_path, mem_val);
        
        // Also set swap limit
        snprintf(mem_path, sizeof(mem_path), "%s/memory.swap.max", cgroup_path);
        write_file(mem_path, "0");
    }
    
    // CPU limit (quota in microseconds)
    if (maxCpu > 0) {
        char cpu_path[512];
        char cpu_val[64];
        snprintf(cpu_path, sizeof(cpu_path), "%s/cpu.max", cgroup_path);
        snprintf(cpu_val, sizeof(cpu_val), "%ld 1000000", maxCpu * 1000000);
        write_file(cpu_path, cpu_val);
    }
    
    // Add process to cgroup
    char procs_path[512];
    snprintf(procs_path, sizeof(procs_path), "%s/cgroup.procs", cgroup_path);
    char pid_str[32];
    snprintf(pid_str, sizeof(pid_str), "%ld", (long)pid);
    write_file(procs_path, pid_str);
    
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_sandbox_ProcessContainer_nativeKillProcessTree(
        JNIEnv *env, jobject thiz, jlong rootPid, jint signal) {
    
    pid_t pid = (pid_t)rootPid;
    
    // Read child processes from /proc
    DIR *proc_dir = opendir("/proc");
    if (!proc_dir) return JNI_FALSE;
    
    struct dirent *entry;
    pid_t children[1024];
    int child_count = 0;
    
    // Simple approach: kill all processes in the same session
    kill(-pid, signal);
    
    // Also kill the root process
    kill(pid, signal);
    
    closedir(proc_dir);
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_nexus_agent_core_sandbox_ResourceLimiter_nativeGetMemoryUsage(
        JNIEnv *env, jobject thiz, jlong pid) {
    
    char path[256];
    snprintf(path, sizeof(path), "/proc/%ld/status", (long)pid);
    
    FILE *fp = fopen(path, "r");
    if (!fp) return -1;
    
    char line[256];
    long vm_rss = -1;
    
    while (fgets(line, sizeof(line), fp)) {
        if (strncmp(line, "VmRSS:", 6) == 0) {
            sscanf(line, "VmRSS: %ld", &vm_rss);
            break;
        }
    }
    
    fclose(fp);
    return (jlong)(vm_rss * 1024); // Convert KB to bytes
}

JNIEXPORT jlong JNICALL
Java_com_nexus_agent_core_sandbox_ResourceLimiter_nativeGetCpuTime(
        JNIEnv *env, jobject thiz, jlong pid) {
    
    char path[256];
    snprintf(path, sizeof(path), "/proc/%ld/stat", (long)pid);
    
    FILE *fp = fopen(path, "r");
    if (!fp) return -1;
    
    unsigned long utime, stime;
    // Format: pid comm state ppid pgrp session tty_nr tpgid flags minflt cminflt majflt cmajflt utime stime...
    // We need fields 14 (utime) and 15 (stime)
    char comm[256];
    char state;
    int dummy_int;
    long dummy_long;
    unsigned long dummy_ulong;
    
    fscanf(fp, "%d %s %c %d %d %d %d %d %lu %lu %lu %lu %lu %lu %lu",
        &dummy_int, comm, &state, &dummy_int, &dummy_int, &dummy_int,
        &dummy_int, &dummy_int, &dummy_ulong, &dummy_ulong, &dummy_ulong,
        &dummy_ulong, &dummy_ulong, &utime, &stime);
    
    fclose(fp);
    
    // Convert clock ticks to milliseconds
    long clock_ticks = sysconf(_SC_CLK_TCK);
    if (clock_ticks <= 0) clock_ticks = 100;
    
    return (jlong)(((utime + stime) * 1000) / clock_ticks);
}
