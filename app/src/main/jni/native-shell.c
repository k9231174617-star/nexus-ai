/*
 * native-shell.c — Native shell execution bridge for Nexus AI
 * 
 * Provides safe command execution with stdin/stdout/stderr piping,
 * timeout control, and process isolation.
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/time.h>
#include <android/log.h>

#define LOG_TAG "NexusNativeShell"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define BUFFER_SIZE 4096
#define MAX_OUTPUT_SIZE (1024 * 1024)  // 1MB max output
#define DEFAULT_TIMEOUT_MS 30000       // 30 seconds

typedef struct {
    pid_t pid;
    int stdin_pipe[2];
    int stdout_pipe[2];
    int stderr_pipe[2];
    int exit_code;
    char *output_buffer;
    size_t output_size;
    size_t output_capacity;
    char *error_buffer;
    size_t error_size;
    size_t error_capacity;
    volatile int terminated;
} ShellSession;

static void set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags >= 0) {
        fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    }
}

static int append_buffer(char **buffer, size_t *size, size_t *capacity, 
                         const char *data, size_t len) {
    if (*size + len > MAX_OUTPUT_SIZE) {
        len = MAX_OUTPUT_SIZE - *size;
        if (len == 0) return -1;
    }
    
    if (*size + len > *capacity) {
        size_t new_capacity = *capacity * 2;
        while (new_capacity < *size + len) new_capacity *= 2;
        if (new_capacity > MAX_OUTPUT_SIZE) new_capacity = MAX_OUTPUT_SIZE;
        
        char *new_buf = realloc(*buffer, new_capacity);
        if (!new_buf) return -1;
        
        *buffer = new_buf;
        *capacity = new_capacity;
    }
    
    memcpy(*buffer + *size, data, len);
    *size += len;
    return 0;
}

static void close_pipes(ShellSession *session) {
    if (session->stdin_pipe[1] >= 0) {
        close(session->stdin_pipe[1]);
        session->stdin_pipe[1] = -1;
    }
    if (session->stdout_pipe[0] >= 0) {
        close(session->stdout_pipe[0]);
        session->stdout_pipe[0] = -1;
    }
    if (session->stderr_pipe[0] >= 0) {
        close(session->stderr_pipe[0]);
        session->stderr_pipe[0] = -1;
    }
}

JNIEXPORT jlong JNICALL
Java_com_nexus_agent_core_cli_ShellSession_nativeCreateSession(JNIEnv *env, jobject thiz) {
    ShellSession *session = calloc(1, sizeof(ShellSession));
    if (!session) {
        LOGE("Failed to allocate ShellSession");
        return 0;
    }
    
    session->stdin_pipe[0] = -1;
    session->stdin_pipe[1] = -1;
    session->stdout_pipe[0] = -1;
    session->stdout_pipe[1] = -1;
    session->stderr_pipe[0] = -1;
    session->stderr_pipe[1] = -1;
    session->pid = -1;
    session->output_capacity = BUFFER_SIZE;
    session->error_capacity = BUFFER_SIZE;
    
    session->output_buffer = malloc(session->output_capacity);
    session->error_buffer = malloc(session->error_capacity);
    
    if (!session->output_buffer || !session->error_buffer) {
        free(session->output_buffer);
        free(session->error_buffer);
        free(session);
        return 0;
    }
    
    LOGI("ShellSession created: %p", (void*)session);
    return (jlong)(uintptr_t)session;
}

JNIEXPORT jint JNICALL
Java_com_nexus_agent_core_cli_ShellSession_nativeExecuteCommand(
        JNIEnv *env, jobject thiz, jlong handle, jstring command, 
        jstring workingDir, jobjectArray envArray, jint timeoutMs) {
    
    ShellSession *session = (ShellSession*)(uintptr_t)handle;
    if (!session) {
        LOGE("Invalid session handle");
        return -1;
    }
    
    const char *cmd_str = (*env)->GetStringUTFChars(env, command, NULL);
    if (!cmd_str) return -1;
    
    const char *work_dir = NULL;
    if (workingDir) {
        work_dir = (*env)->GetStringUTFChars(env, workingDir, NULL);
    }
    
    // Reset buffers
    session->output_size = 0;
    session->error_size = 0;
    session->terminated = 0;
    session->exit_code = -1;
    
    // Create pipes
    if (pipe(session->stdin_pipe) < 0 || 
        pipe(session->stdout_pipe) < 0 || 
        pipe(session->stderr_pipe) < 0) {
        LOGE("Failed to create pipes: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, command, cmd_str);
        if (work_dir) (*env)->ReleaseStringUTFChars(env, workingDir, work_dir);
        return -1;
    }
    
    // Fork
    session->pid = fork();
    if (session->pid < 0) {
        LOGE("Fork failed: %s", strerror(errno));
        close_pipes(session);
        (*env)->ReleaseStringUTFChars(env, command, cmd_str);
        if (work_dir) (*env)->ReleaseStringUTFChars(env, workingDir, work_dir);
        return -1;
    }
    
    if (session->pid == 0) {
        // Child process
        
        // Close write end of stdin, read ends of stdout/stderr
        close(session->stdin_pipe[1]);
        close(session->stdout_pipe[0]);
        close(session->stderr_pipe[0]);
        
        // Redirect stdin
        dup2(session->stdin_pipe[0], STDIN_FILENO);
        close(session->stdin_pipe[0]);
        
        // Redirect stdout
        dup2(session->stdout_pipe[1], STDOUT_FILENO);
        close(session->stdout_pipe[1]);
        
        // Redirect stderr
        dup2(session->stderr_pipe[1], STDERR_FILENO);
        close(session->stderr_pipe[1]);
        
        // Change working directory
        if (work_dir && chdir(work_dir) < 0) {
            fprintf(stderr, "Failed to chdir to %s: %s\n", work_dir, strerror(errno));
            _exit(126);
        }
        
        // Parse command for execvp
        // Use /system/bin/sh -c for complex commands
        execl("/system/bin/sh", "sh", "-c", cmd_str, (char*)NULL);
        
        // If exec fails
        fprintf(stderr, "Failed to execute: %s\n", strerror(errno));
        _exit(127);
    }
    
    // Parent process
    (*env)->ReleaseStringUTFChars(env, command, cmd_str);
    if (work_dir) (*env)->ReleaseStringUTFChars(env, workingDir, work_dir);
    
    // Close unused pipe ends
    close(session->stdin_pipe[0]);
    session->stdin_pipe[0] = -1;
    close(session->stdout_pipe[1]);
    session->stdout_pipe[1] = -1;
    close(session->stderr_pipe[1]);
    session->stderr_pipe[1] = -1;
    
    // Set non-blocking
    set_nonblocking(session->stdout_pipe[0]);
    set_nonblocking(session->stderr_pipe[0]);
    
    int timeout = (timeoutMs > 0) ? timeoutMs : DEFAULT_TIMEOUT_MS;
    struct timeval start_time, current_time;
    gettimeofday(&start_time, NULL);
    
    char buffer[BUFFER_SIZE];
    fd_set read_fds;
    int max_fd = (session->stdout_pipe[0] > session->stderr_pipe[0]) 
                 ? session->stdout_pipe[0] : session->stderr_pipe[0];
    
    int stdout_open = 1;
    int stderr_open = 1;
    
    while ((stdout_open || stderr_open) && !session->terminated) {
        FD_ZERO(&read_fds);
        if (stdout_open) FD_SET(session->stdout_pipe[0], &read_fds);
        if (stderr_open) FD_SET(session->stderr_pipe[0], &read_fds);
        
        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 100000; // 100ms
        
        int ret = select(max_fd + 1, &read_fds, NULL, NULL, &tv);
        
        if (ret < 0) {
            if (errno == EINTR) continue;
            LOGE("select() failed: %s", strerror(errno));
            break;
        }
        
        // Read stdout
        if (stdout_open && FD_ISSET(session->stdout_pipe[0], &read_fds)) {
            ssize_t n = read(session->stdout_pipe[0], buffer, BUFFER_SIZE - 1);
            if (n > 0) {
                buffer[n] = '\0';
                append_buffer(&session->output_buffer, &session->output_size,
                             &session->output_capacity, buffer, n);
            } else if (n == 0 || (n < 0 && errno != EAGAIN)) {
                stdout_open = 0;
            }
        }
        
        // Read stderr
        if (stderr_open && FD_ISSET(session->stderr_pipe[0], &read_fds)) {
            ssize_t n = read(session->stderr_pipe[0], buffer, BUFFER_SIZE - 1);
            if (n > 0) {
                buffer[n] = '\0';
                append_buffer(&session->error_buffer, &session->error_size,
                             &session->error_capacity, buffer, n);
            } else if (n == 0 || (n < 0 && errno != EAGAIN)) {
                stderr_open = 0;
            }
        }
        
        // Check timeout
        gettimeofday(&current_time, NULL);
        long elapsed_ms = (current_time.tv_sec - start_time.tv_sec) * 1000 +
                         (current_time.tv_usec - start_time.tv_usec) / 1000;
        
        if (elapsed_ms >= timeout) {
            LOGW("Command timed out after %ld ms", elapsed_ms);
            kill(session->pid, SIGTERM);
            usleep(100000); // 100ms grace period
            kill(session->pid, SIGKILL);
            session->terminated = 1;
            break;
        }
        
        // Check if process exited
        int status;
        pid_t result = waitpid(session->pid, &status, WNOHANG);
        if (result == session->pid) {
            if (WIFEXITED(status)) {
                session->exit_code = WEXITSTATUS(status);
            } else if (WIFSIGNALED(status)) {
                session->exit_code = 128 + WTERMSIG(status);
            }
            // Process exited, but pipes may still have data
            // Continue reading until pipes are empty
        }
    }
    
    // Ensure process is reaped
    if (session->pid > 0) {
        int status;
        if (waitpid(session->pid, &status, WNOHANG) == 0) {
            kill(session->pid, SIGKILL);
            waitpid(session->pid, &status, 0);
        }
        if (session->exit_code < 0) {
            if (WIFEXITED(status)) {
                session->exit_code = WEXITSTATUS(status);
            } else if (WIFSIGNALED(status)) {
                session->exit_code = 128 + WTERMSIG(status);
            }
        }
        session->pid = -1;
    }
    
    close_pipes(session);
    
    // Null-terminate buffers
    if (session->output_size < session->output_capacity) {
        session->output_buffer[session->output_size] = '\0';
    }
    if (session->error_size < session->error_capacity) {
        session->error_buffer[session->error_size] = '\0';
    }
    
    LOGI("Command finished with exit code %d, output: %zu bytes, error: %zu bytes",
         session->exit_code, session->output_size, session->error_size);
    
    return session->exit_code;
}

JNIEXPORT jstring JNICALL
Java_com_nexus_agent_core_cli_ShellSession_nativeGetOutput(JNIEnv *env, jobject thiz, jlong handle) {
    ShellSession *session = (ShellSession*)(uintptr_t)handle;
    if (!session || !session->output_buffer) return NULL;
    
    return (*env)->NewStringUTF(env, session->output_buffer);
}

JNIEXPORT jstring JNICALL
Java_com_nexus_agent_core_cli_ShellSession_nativeGetError(JNIEnv *env, jobject thiz, jlong handle) {
    ShellSession *session = (ShellSession*)(uintptr_t)handle;
    if (!session || !session->error_buffer) return NULL;
    
    return (*env)->NewStringUTF(env, session->error_buffer);
}

JNIEXPORT void JNICALL
Java_com_nexus_agent_core_cli_ShellSession_nativeWriteStdin(
        JNIEnv *env, jobject thiz, jlong handle, jstring input) {
    
    ShellSession *session = (ShellSession*)(uintptr_t)handle;
    if (!session || session->stdin_pipe[1] < 0) return;
    
    const char *data = (*env)->GetStringUTFChars(env, input, NULL);
    if (!data) return;
    
    size_t len = strlen(data);
    ssize_t written = write(session->stdin_pipe[1], data, len);
    if (written < 0) {
        LOGE("Failed to write to stdin: %s", strerror(errno));
    }
    
    (*env)->ReleaseStringUTFChars(env, input, data);
}

JNIEXPORT void JNICALL
Java_com_nexus_agent_core_cli_ShellSession_nativeSendSignal(
        JNIEnv *env, jobject thiz, jlong handle, jint signal) {
    
    ShellSession *session = (ShellSession*)(uintptr_t)handle;
    if (!session || session->pid <= 0) return;
    
    if (kill(session->pid, signal) < 0) {
        LOGE("Failed to send signal %d: %s", signal, strerror(errno));
    }
}

JNIEXPORT void JNICALL
Java_com_nexus_agent_core_cli_ShellSession_nativeDestroySession(JNIEnv *env, jobject thiz, jlong handle) {
    ShellSession *session = (ShellSession*)(uintptr_t)handle;
    if (!session) return;
    
    if (session->pid > 0) {
        kill(session->pid, SIGKILL);
        waitpid(session->pid, NULL, 0);
    }
    
    close_pipes(session);
    
    free(session->output_buffer);
    free(session->error_buffer);
    free(session);
    
    LOGI("ShellSession destroyed");
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_cli_ShellSession_nativeIsRunning(JNIEnv *env, jobject thiz, jlong handle) {
    ShellSession *session = (ShellSession*)(uintptr_t)handle;
    if (!session || session->pid <= 0) return JNI_FALSE;
    
    int status;
    pid_t result = waitpid(session->pid, &status, WNOHANG);
    return (result == 0) ? JNI_TRUE : JNI_FALSE;
}
