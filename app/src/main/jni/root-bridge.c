/*
 * root-bridge.c — Root access bridge for Nexus AI
 * 
 * Provides safe root command execution via su binary,
 * with privilege verification and system-level operations.
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/mount.h>
#include <sys/syscall.h>
#include <android/log.h>

#define LOG_TAG "NexusRootBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define SU_PATHS_COUNT 6
static const char *SU_PATHS[SU_PATHS_COUNT] = {
    "/system/bin/su",
    "/system/xbin/su",
    "/su/bin/su",
    "/sbin/su",
    "/magisk/.core/bin/su",
    "/data/local/xbin/su"
};

#define BUFFER_SIZE 8192
#define MAX_OUTPUT (512 * 1024)

typedef struct {
    char *su_path;
    int has_root;
    pid_t current_pid;
    int stdin_fd;
    int stdout_fd;
    int stderr_fd;
} RootContext;

static char *find_su_binary() {
    for (int i = 0; i < SU_PATHS_COUNT; i++) {
        if (access(SU_PATHS[i], X_OK) == 0) {
            LOGI("Found su at: %s", SU_PATHS[i]);
            return strdup(SU_PATHS[i]);
        }
    }
    return NULL;
}

static int check_root_access(const char *su_path) {
    if (!su_path) return 0;
    
    int pipefd[2];
    if (pipe(pipefd) < 0) return 0;
    
    pid_t pid = fork();
    if (pid < 0) {
        close(pipefd[0]);
        close(pipefd[1]);
        return 0;
    }
    
    if (pid == 0) {
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        close(pipefd[1]);
        
        execl(su_path, "su", "-c", "id", NULL);
        _exit(1);
    }
    
    close(pipefd[1]);
    
    char buffer[256];
    ssize_t n = read(pipefd[0], buffer, sizeof(buffer) - 1);
    close(pipefd[0]);
    
    int status;
    waitpid(pid, &status, 0);
    
    if (n > 0) {
        buffer[n] = '\0';
        if (strstr(buffer, "uid=0") != NULL) {
            return 1;
        }
    }
    
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_nexus_agent_core_root_RootBridge_nativeInit(JNIEnv *env, jobject thiz) {
    RootContext *ctx = calloc(1, sizeof(RootContext));
    if (!ctx) return 0;
    
    ctx->su_path = find_su_binary();
    if (ctx->su_path) {
        ctx->has_root = check_root_access(ctx->su_path);
    }
    
    ctx->stdin_fd = -1;
    ctx->stdout_fd = -1;
    ctx->stderr_fd = -1;
    ctx->current_pid = -1;
    
    LOGI("RootBridge initialized, root access: %s", ctx->has_root ? "yes" : "no");
    return (jlong)(uintptr_t)ctx;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_root_SuChecker_nativeHasRootAccess(JNIEnv *env, jclass clazz) {
    char *su = find_su_binary();
    if (!su) return JNI_FALSE;
    
    int has_root = check_root_access(su);
    free(su);
    return has_root ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_root_SuChecker_nativeIsSystemRooted(JNIEnv *env, jclass clazz) {
    // Check multiple indicators of root
    const char *indicators[] = {
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin",
        "/magisk",
        "/.magisk",
        "/system/bin/.ext/.su",
        "/system/etc/init.d",
        NULL
    };
    
    for (int i = 0; indicators[i] != NULL; i++) {
        if (access(indicators[i], F_OK) == 0) {
            return JNI_TRUE;
        }
    }
    
    // Check for Magisk-specific
    if (access("/dev/.magisk.unblock", F_OK) == 0) {
        return JNI_TRUE;
    }
    
    // Check build tags
    char build_tags[256] = {0};
    FILE *fp = fopen("/system/build.prop", "r");
    if (fp) {
        char line[512];
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "ro.build.tags=test-keys") ||
                strstr(line, "ro.build.tags=dev-keys")) {
                fclose(fp);
                return JNI_TRUE;
            }
        }
        fclose(fp);
    }
    
    return JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_com_nexus_agent_core_root_RootCommand_nativeExecuteRoot(
        JNIEnv *env, jobject thiz, jlong handle, jstring command) {
    
    RootContext *ctx = (RootContext*)(uintptr_t)handle;
    if (!ctx || !ctx->has_root || !ctx->su_path) {
        jclass exClass = (*env)->FindClass(env, "java/lang/SecurityException");
        (*env)->ThrowNew(env, exClass, "Root access not available");
        return NULL;
    }
    
    const char *cmd = (*env)->GetStringUTFChars(env, command, NULL);
    if (!cmd) return NULL;
    
    int stdout_pipe[2], stderr_pipe[2];
    if (pipe(stdout_pipe) < 0 || pipe(stderr_pipe) < 0) {
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        return NULL;
    }
    
    pid_t pid = fork();
    if (pid < 0) {
        close(stdout_pipe[0]); close(stdout_pipe[1]);
        close(stderr_pipe[0]); close(stderr_pipe[1]);
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        return NULL;
    }
    
    if (pid == 0) {
        close(stdout_pipe[0]);
        close(stderr_pipe[0]);
        dup2(stdout_pipe[1], STDOUT_FILENO);
        dup2(stderr_pipe[1], STDERR_FILENO);
        close(stdout_pipe[1]);
        close(stderr_pipe[1]);
        
        execl(ctx->su_path, "su", "-c", cmd, NULL);
        _exit(127);
    }
    
    close(stdout_pipe[1]);
    close(stderr_pipe[1]);
    
    (*env)->ReleaseStringUTFChars(env, command, cmd);
    
    // Read output
    char *output = malloc(MAX_OUTPUT);
    char *error = malloc(MAX_OUTPUT);
    size_t out_len = 0, err_len = 0;
    
    if (output && error) {
        fd_set fds;
        struct timeval tv;
        int max_fd = (stdout_pipe[0] > stderr_pipe[0]) ? stdout_pipe[0] : stderr_pipe[0];
        int stdout_done = 0, stderr_done = 0;
        
        while (!stdout_done || !stderr_done) {
            FD_ZERO(&fds);
            if (!stdout_done) FD_SET(stdout_pipe[0], &fds);
            if (!stderr_done) FD_SET(stderr_pipe[0], &fds);
            
            tv.tv_sec = 5;
            tv.tv_usec = 0;
            
            int ret = select(max_fd + 1, &fds, NULL, NULL, &tv);
            if (ret <= 0) break;
            
            if (!stdout_done && FD_ISSET(stdout_pipe[0], &fds)) {
                char buf[BUFFER_SIZE];
                ssize_t n = read(stdout_pipe[0], buf, sizeof(buf));
                if (n > 0 && out_len + n < MAX_OUTPUT - 1) {
                    memcpy(output + out_len, buf, n);
                    out_len += n;
                } else {
                    stdout_done = 1;
                }
            }
            
            if (!stderr_done && FD_ISSET(stderr_pipe[0], &fds)) {
                char buf[BUFFER_SIZE];
                ssize_t n = read(stderr_pipe[0], buf, sizeof(buf));
                if (n > 0 && err_len + n < MAX_OUTPUT - 1) {
                    memcpy(error + err_len, buf, n);
                    err_len += n;
                } else {
                    stderr_done = 1;
                }
            }
        }
    }
    
    close(stdout_pipe[0]);
    close(stderr_pipe[0]);
    
    int status;
    waitpid(pid, &status, 0);
    int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    
    output[out_len] = '\0';
    error[err_len] = '\0';
    
    // Create result object
    jclass resultClass = (*env)->FindClass(env, "com/nexus/agent/core/root/RootCommand$Result");
    jmethodID constructor = (*env)->GetMethodID(env, resultClass, "<init>", 
        "(ILjava/lang/String;Ljava/lang/String;)V");
    
    jstring jOutput = (*env)->NewStringUTF(env, output ? output : "");
    jstring jError = (*env)->NewStringUTF(env, error ? error : "");
    
    jobject result = (*env)->NewObject(env, resultClass, constructor, 
        exit_code, jOutput, jError);
    
    free(output);
    free(error);
    
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_root_SystemModifier_nativeRemountSystem(
        JNIEnv *env, jobject thiz, jlong handle, jboolean writable) {
    
    RootContext *ctx = (RootContext*)(uintptr_t)handle;
    if (!ctx || !ctx->has_root) return JNI_FALSE;
    
    const char *mount_cmd = writable 
        ? "mount -o remount,rw /system"
        : "mount -o remount,ro /system";
    
    int pipefd[2];
    if (pipe(pipefd) < 0) return JNI_FALSE;
    
    pid_t pid = fork();
    if (pid < 0) {
        close(pipefd[0]); close(pipefd[1]);
        return JNI_FALSE;
    }
    
    if (pid == 0) {
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        close(pipefd[1]);
        execl(ctx->su_path, "su", "-c", mount_cmd, NULL);
        _exit(1);
    }
    
    close(pipefd[1]);
    int status;
    waitpid(pid, &status, 0);
    close(pipefd[0]);
    
    return (WIFEXITED(status) && WEXITSTATUS(status) == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_root_SystemModifier_nativeSetFilePermissions(
        JNIEnv *env, jobject thiz, jlong handle, jstring path, jint mode) {
    
    RootContext *ctx = (RootContext*)(uintptr_t)handle;
    if (!ctx || !ctx->has_root) return JNI_FALSE;
    
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!file_path) return JNI_FALSE;
    
    char cmd[512];
    snprintf(cmd, sizeof(cmd), "chmod %o \"%s\"", mode, file_path);
    
    int pipefd[2];
    pipe(pipefd);
    
    pid_t pid = fork();
    if (pid == 0) {
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        close(pipefd[1]);
        execl(ctx->su_path, "su", "-c", cmd, NULL);
        _exit(1);
    }
    
    close(pipefd[0]); close(pipefd[1]);
    
    int status;
    waitpid(pid, &status, 0);
    
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    
    return (WIFEXITED(status) && WEXITSTATUS(status) == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_root_SystemModifier_nativeWriteSystemFile(
        JNIEnv *env, jobject thiz, jlong handle, jstring path, jbyteArray data) {
    
    RootContext *ctx = (RootContext*)(uintptr_t)handle;
    if (!ctx || !ctx->has_root) return JNI_FALSE;
    
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    
    if (!file_path || !bytes) {
        if (file_path) (*env)->ReleaseStringUTFChars(env, path, file_path);
        if (bytes) (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
        return JNI_FALSE;
    }
    
    // Write to temp file first, then move with root
    char temp_path[256];
    snprintf(temp_path, sizeof(temp_path), "/data/local/tmp/nexus_write_%d", getpid());
    
    FILE *fp = fopen(temp_path, "wb");
    if (!fp) {
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
        return JNI_FALSE;
    }
    
    fwrite(bytes, 1, len, fp);
    fclose(fp);
    
    char cmd[512];
    snprintf(cmd, sizeof(cmd), "cp \"%s\" \"%s\" && chmod 644 \"%s\" && rm \"%s\"",
             temp_path, file_path, file_path, temp_path);
    
    int pipefd[2];
    pipe(pipefd);
    
    pid_t pid = fork();
    if (pid == 0) {
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        close(pipefd[1]);
        execl(ctx->su_path, "su", "-c", cmd, NULL);
        _exit(1);
    }
    
    close(pipefd[0]); close(pipefd[1]);
    
    int status;
    waitpid(pid, &status, 0);
    
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    
    return (WIFEXITED(status) && WEXITSTATUS(status) == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_nexus_agent_core_root_RootBridge_nativeRelease(JNIEnv *env, jobject thiz, jlong handle) {
    RootContext *ctx = (RootContext*)(uintptr_t)handle;
    if (!ctx) return;
    
    if (ctx->current_pid > 0) {
        kill(ctx->current_pid, SIGTERM);
        waitpid(ctx->current_pid, NULL, 0);
    }
    
    if (ctx->stdin_fd >= 0) close(ctx->stdin_fd);
    if (ctx->stdout_fd >= 0) close(ctx->stdout_fd);
    if (ctx->stderr_fd >= 0) close(ctx->stderr_fd);
    
    free(ctx->su_path);
    free(ctx);
    
    LOGI("RootBridge released");
}
