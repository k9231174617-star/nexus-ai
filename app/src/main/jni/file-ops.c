/*
 * file-ops.c — High-performance file operations for Nexus AI
 * 
 * Provides native file I/O with mmap support, fast hashing,
 * recursive operations, and binary analysis capabilities.
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <sys/sendfile.h>
#include <sys/types.h>
#include <android/log.h>
#include <openssl/md5.h>
#include <openssl/sha.h>

#define LOG_TAG "NexusFileOps"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define BUFFER_SIZE (64 * 1024)      // 64KB buffer
#define LARGE_FILE_THRESHOLD (10 * 1024 * 1024)  // 10MB

// Utility: Convert bytes to hex string
static void bytes_to_hex(const unsigned char *bytes, size_t len, char *hex_str) {
    const char *hex_chars = "0123456789abcdef";
    for (size_t i = 0; i < len; i++) {
        hex_str[i * 2] = hex_chars[(bytes[i] >> 4) & 0xF];
        hex_str[i * 2 + 1] = hex_chars[bytes[i] & 0xF];
    }
    hex_str[len * 2] = '\0';
}

JNIEXPORT jlong JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeGetFileSize(JNIEnv *env, jobject thiz, jstring path) {
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!file_path) return -1;
    
    struct stat st;
    jlong result = -1;
    if (stat(file_path, &st) == 0) {
        result = (jlong)st.st_size;
    }
    
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    return result;
}

JNIEXPORT jobject JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeGetFileInfo(JNIEnv *env, jobject thiz, jstring path) {
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!file_path) return NULL;
    
    struct stat st;
    jobject result = NULL;
    
    if (stat(file_path, &st) == 0) {
        jclass infoClass = (*env)->FindClass(env, "com/nexus/agent/core/files/FileManager$FileInfo");
        jmethodID constructor = (*env)->GetMethodID(env, infoClass, "<init>", 
            "(JLjava/lang/String;JJZ)V");
        
        char mode_str[11];
        snprintf(mode_str, sizeof(mode_str), "%o", (unsigned)(st.st_mode & 0777));
        
        jstring jMode = (*env)->NewStringUTF(env, mode_str);
        result = (*env)->NewObject(env, infoClass, constructor,
            (jlong)st.st_size,
            jMode,
            (jlong)st.st_mtime * 1000,
            (jlong)st.st_ctime * 1000,
            S_ISDIR(st.st_mode) ? JNI_TRUE : JNI_FALSE);
    }
    
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeCopyFile(
        JNIEnv *env, jobject thiz, jstring src, jstring dst, jboolean overwrite) {
    
    const char *src_path = (*env)->GetStringUTFChars(env, src, NULL);
    const char *dst_path = (*env)->GetStringUTFChars(env, dst, NULL);
    
    if (!src_path || !dst_path) {
        if (src_path) (*env)->ReleaseStringUTFChars(env, src, src_path);
        if (dst_path) (*env)->ReleaseStringUTFChars(env, dst, dst_path);
        return JNI_FALSE;
    }
    
    // Check if destination exists
    if (!overwrite && access(dst_path, F_OK) == 0) {
        (*env)->ReleaseStringUTFChars(env, src, src_path);
        (*env)->ReleaseStringUTFChars(env, dst, dst_path);
        return JNI_FALSE;
    }
    
    int src_fd = open(src_path, O_RDONLY);
    if (src_fd < 0) {
        LOGE("Failed to open source: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, src, src_path);
        (*env)->ReleaseStringUTFChars(env, dst, dst_path);
        return JNI_FALSE;
    }
    
    struct stat st;
    if (fstat(src_fd, &st) < 0) {
        close(src_fd);
        (*env)->ReleaseStringUTFChars(env, src, src_path);
        (*env)->ReleaseStringUTFChars(env, dst, dst_path);
        return JNI_FALSE;
    }
    
    int dst_fd = open(dst_path, O_WRONLY | O_CREAT | O_TRUNC, st.st_mode & 0777);
    if (dst_fd < 0) {
        LOGE("Failed to create destination: %s", strerror(errno));
        close(src_fd);
        (*env)->ReleaseStringUTFChars(env, src, src_path);
        (*env)->ReleaseStringUTFChars(env, dst, dst_path);
        return JNI_FALSE;
    }
    
    jboolean success = JNI_TRUE;
    
    // Use sendfile for large files, buffered copy for small
    if (st.st_size > LARGE_FILE_THRESHOLD) {
        off_t offset = 0;
        ssize_t sent;
        while ((sent = sendfile(dst_fd, src_fd, &offset, BUFFER_SIZE)) > 0) {
            // Continue sending
        }
        if (sent < 0 && errno != EINTR) {
            LOGE("sendfile failed: %s", strerror(errno));
            success = JNI_FALSE;
        }
    } else {
        char buffer[BUFFER_SIZE];
        ssize_t n;
        while ((n = read(src_fd, buffer, sizeof(buffer))) > 0) {
            ssize_t written = 0;
            while (written < n) {
                ssize_t w = write(dst_fd, buffer + written, n - written);
                if (w < 0) {
                    if (errno == EINTR) continue;
                    success = JNI_FALSE;
                    break;
                }
                written += w;
            }
            if (!success) break;
        }
    }
    
    // Preserve timestamps
    struct timespec times[2];
    times[0] = st.st_atim;
    times[1] = st.st_mtim;
    futimens(dst_fd, times);
    
    close(src_fd);
    close(dst_fd);
    
    (*env)->ReleaseStringUTFChars(env, src, src_path);
    (*env)->ReleaseStringUTFChars(env, dst, dst_path);
    
    return success;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeMoveFile(
        JNIEnv *env, jobject thiz, jstring src, jstring dst) {
    
    const char *src_path = (*env)->GetStringUTFChars(env, src, NULL);
    const char *dst_path = (*env)->GetStringUTFChars(env, dst, NULL);
    
    if (!src_path || !dst_path) {
        if (src_path) (*env)->ReleaseStringUTFChars(env, src, src_path);
        if (dst_path) (*env)->ReleaseStringUTFChars(env, dst, dst_path);
        return JNI_FALSE;
    }
    
    // Try rename first (atomic, same filesystem)
    if (rename(src_path, dst_path) == 0) {
        (*env)->ReleaseStringUTFChars(env, src, src_path);
        (*env)->ReleaseStringUTFChars(env, dst, dst_path);
        return JNI_TRUE;
    }
    
    // Cross-device: copy then delete
    jboolean copied = Java_com_nexus_agent_core_files_FileManager_nativeCopyFile(
        env, thiz, src, dst, JNI_TRUE);
    
    if (copied) {
        unlink(src_path);
    }
    
    (*env)->ReleaseStringUTFChars(env, src, src_path);
    (*env)->ReleaseStringUTFChars(env, dst, dst_path);
    
    return copied;
}

JNIEXPORT jstring JNICALL
Java_com_nexus_agent_core_files_FileAnalyzer_nativeCalculateHash(
        JNIEnv *env, jobject thiz, jstring path, jstring algorithm) {
    
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    const char *algo = (*env)->GetStringUTFChars(env, algorithm, NULL);
    
    if (!file_path || !algo) {
        if (file_path) (*env)->ReleaseStringUTFChars(env, path, file_path);
        if (algo) (*env)->ReleaseStringUTFChars(env, algorithm, algo);
        return NULL;
    }
    
    int fd = open(file_path, O_RDONLY);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        (*env)->ReleaseStringUTFChars(env, algorithm, algo);
        return NULL;
    }
    
    jstring result = NULL;
    
    if (strcasecmp(algo, "MD5") == 0) {
        MD5_CTX md5_ctx;
        MD5_Init(&md5_ctx);
        
        char buffer[BUFFER_SIZE];
        ssize_t n;
        while ((n = read(fd, buffer, sizeof(buffer))) > 0) {
            MD5_Update(&md5_ctx, buffer, n);
        }
        
        unsigned char digest[MD5_DIGEST_LENGTH];
        MD5_Final(digest, &md5_ctx);
        
        char hex_str[MD5_DIGEST_LENGTH * 2 + 1];
        bytes_to_hex(digest, MD5_DIGEST_LENGTH, hex_str);
        result = (*env)->NewStringUTF(env, hex_str);
        
    } else if (strcasecmp(algo, "SHA1") == 0) {
        SHA_CTX sha1_ctx;
        SHA1_Init(&sha1_ctx);
        
        char buffer[BUFFER_SIZE];
        ssize_t n;
        while ((n = read(fd, buffer, sizeof(buffer))) > 0) {
            SHA1_Update(&sha1_ctx, buffer, n);
        }
        
        unsigned char digest[SHA_DIGEST_LENGTH];
        SHA1_Final(digest, &sha1_ctx);
        
        char hex_str[SHA_DIGEST_LENGTH * 2 + 1];
        bytes_to_hex(digest, SHA_DIGEST_LENGTH, hex_str);
        result = (*env)->NewStringUTF(env, hex_str);
        
    } else if (strcasecmp(algo, "SHA256") == 0) {
        SHA256_CTX sha256_ctx;
        SHA256_Init(&sha256_ctx);
        
        char buffer[BUFFER_SIZE];
        ssize_t n;
        while ((n = read(fd, buffer, sizeof(buffer))) > 0) {
            SHA256_Update(&sha256_ctx, buffer, n);
        }
        
        unsigned char digest[SHA256_DIGEST_LENGTH];
        SHA256_Final(digest, &sha256_ctx);
        
        char hex_str[SHA256_DIGEST_LENGTH * 2 + 1];
        bytes_to_hex(digest, SHA256_DIGEST_LENGTH, hex_str);
        result = (*env)->NewStringUTF(env, hex_str);
    }
    
    close(fd);
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    (*env)->ReleaseStringUTFChars(env, algorithm, algo);
    
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeReadFileMmap(
        JNIEnv *env, jobject thiz, jstring path) {
    
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!file_path) return NULL;
    
    int fd = open(file_path, O_RDONLY);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return NULL;
    }
    
    struct stat st;
    if (fstat(fd, &st) < 0 || st.st_size == 0) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return NULL;
    }
    
    // Limit mmap size for safety
    size_t map_size = (st.st_size > 100 * 1024 * 1024) ? 100 * 1024 * 1024 : (size_t)st.st_size;
    
    void *mapped = mmap(NULL, map_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (mapped == MAP_FAILED) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return NULL;
    }
    
    jbyteArray result = (*env)->NewByteArray(env, (jsize)map_size);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize)map_size, (jbyte*)mapped);
    }
    
    munmap(mapped, map_size);
    close(fd);
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeWriteFileMmap(
        JNIEnv *env, jobject thiz, jstring path, jbyteArray data) {
    
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    jsize len = (*env)->GetArrayLength(env, data);
    
    if (!file_path || len < 0) {
        if (file_path) (*env)->ReleaseStringUTFChars(env, path, file_path);
        return JNI_FALSE;
    }
    
    int fd = open(file_path, O_RDWR | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return JNI_FALSE;
    }
    
    if (ftruncate(fd, len) < 0) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return JNI_FALSE;
    }
    
    void *mapped = mmap(NULL, len, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (mapped == MAP_FAILED) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return JNI_FALSE;
    }
    
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (bytes) {
        memcpy(mapped, bytes, len);
        (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    }
    
    msync(mapped, len, MS_SYNC);
    munmap(mapped, len);
    close(fd);
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    
    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeListDirectory(
        JNIEnv *env, jobject thiz, jstring path) {
    
    const char *dir_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!dir_path) return NULL;
    
    DIR *dir = opendir(dir_path);
    if (!dir) {
        (*env)->ReleaseStringUTFChars(env, path, dir_path);
        return NULL;
    }
    
    // Count entries first
    int count = 0;
    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0) {
            count++;
        }
    }
    rewinddir(dir);
    
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, count, stringClass, NULL);
    
    int i = 0;
    while ((entry = readdir(dir)) != NULL && i < count) {
        if (strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0) {
            jstring name = (*env)->NewStringUTF(env, entry->d_name);
            (*env)->SetObjectArrayElement(env, result, i++, name);
        }
    }
    
    closedir(dir);
    (*env)->ReleaseStringUTFChars(env, path, dir_path);
    
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeGetDirectorySize(
        JNIEnv *env, jobject thiz, jstring path) {
    
    const char *dir_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!dir_path) return -1;
    
    jlong total_size = 0;
    DIR *dir = opendir(dir_path);
    if (!dir) {
        (*env)->ReleaseStringUTFChars(env, path, dir_path);
        return -1;
    }
    
    struct dirent *entry;
    char full_path[PATH_MAX];
    
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }
        
        snprintf(full_path, sizeof(full_path), "%s/%s", dir_path, entry->d_name);
        
        struct stat st;
        if (stat(full_path, &st) == 0) {
            if (S_ISDIR(st.st_mode)) {
                // Recursive - simplified, in production use stack-based approach
                // For now, just add directory size
                total_size += st.st_size;
            } else {
                total_size += st.st_size;
            }
        }
    }
    
    closedir(dir);
    (*env)->ReleaseStringUTFChars(env, path, dir_path);
    
    return total_size;
}

JNIEXPORT jboolean JNICALL
Java_com_nexus_agent_core_files_FileManager_nativeDeleteRecursive(
        JNIEnv *env, jobject thiz, jstring path) {
    
    const char *target_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!target_path) return JNI_FALSE;
    
    struct stat st;
    if (stat(target_path, &st) < 0) {
        (*env)->ReleaseStringUTFChars(env, path, target_path);
        return JNI_FALSE;
    }
    
    if (!S_ISDIR(st.st_mode)) {
        int result = unlink(target_path);
        (*env)->ReleaseStringUTFChars(env, path, target_path);
        return (result == 0) ? JNI_TRUE : JNI_FALSE;
    }
    
    DIR *dir = opendir(target_path);
    if (!dir) {
        (*env)->ReleaseStringUTFChars(env, path, target_path);
        return JNI_FALSE;
    }
    
    struct dirent *entry;
    char full_path[PATH_MAX];
    jboolean success = JNI_TRUE;
    
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }
        
        snprintf(full_path, sizeof(full_path), "%s/%s", target_path, entry->d_name);
        
        // Create temporary JNI string for recursive call
        jstring child_path = (*env)->NewStringUTF(env, full_path);
        if (!Java_com_nexus_agent_core_files_FileManager_nativeDeleteRecursive(env, thiz, child_path)) {
            success = JNI_FALSE;
        }
    }
    
    closedir(dir);
    rmdir(target_path);
    
    (*env)->ReleaseStringUTFChars(env, path, target_path);
    return success;
}

JNIEXPORT jstring JNICALL
Java_com_nexus_agent_core_files_FileAnalyzer_nativeDetectMimeType(
        JNIEnv *env, jobject thiz, jstring path) {
    
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!file_path) return NULL;
    
    int fd = open(file_path, O_RDONLY);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return (*env)->NewStringUTF(env, "application/octet-stream");
    }
    
    unsigned char magic[16];
    ssize_t n = read(fd, magic, sizeof(magic));
    close(fd);
    
    const char *mime = "application/octet-stream";
    
    if (n >= 4) {
        // PNG
        if (magic[0] == 0x89 && magic[1] == 0x50 && magic[2] == 0x4E && magic[3] == 0x47) {
            mime = "image/png";
        }
        // JPEG
        else if (magic[0] == 0xFF && magic[1] == 0xD8) {
            mime = "image/jpeg";
        }
        // GIF
        else if (magic[0] == 'G' && magic[1] == 'I' && magic[2] == 'F') {
            mime = "image/gif";
        }
        // WebP
        else if (magic[0] == 'R' && magic[1] == 'I' && magic[2] == 'F' && magic[3] == 'F') {
            if (n >= 12 && magic[8] == 'W' && magic[9] == 'E' && magic[10] == 'B' && magic[11] == 'P') {
                mime = "image/webp";
            }
        }
        // PDF
        else if (magic[0] == '%' && magic[1] == 'P' && magic[2] == 'D' && magic[3] == 'F') {
            mime = "application/pdf";
        }
        // ZIP / JAR / APK
        else if (magic[0] == 'P' && magic[1] == 'K' && magic[2] == 0x03 && magic[3] == 0x04) {
            mime = "application/zip";
        }
        // MP4
        else if (n >= 8 && ((magic[4] == 'f' && magic[5] == 't' && magic[6] == 'y' && magic[7] == 'p') ||
                            (magic[4] == 'm' && magic[5] == 'o' && magic[6] == 'o' && magic[7] == 'v'))) {
            mime = "video/mp4";
        }
        // ELF
        else if (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') {
            mime = "application/x-elf";
        }
        // XML
        else if (magic[0] == '<' && magic[1] == '?' && magic[2] == 'x' && magic[3] == 'm') {
            mime = "application/xml";
        }
    }
    
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    return (*env)->NewStringUTF(env, mime);
}

JNIEXPORT jstring JNICALL
Java_com_nexus_agent_core_files_FileUploader_nativeGetFileSignature(
        JNIEnv *env, jobject thiz, jstring path) {
    
    const char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!file_path) return NULL;
    
    int fd = open(file_path, O_RDONLY);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return NULL;
    }
    
    struct stat st;
    if (fstat(fd, &st) < 0) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return NULL;
    }
    
    // Read first and last 4KB for signature
    size_t sig_size = 8192 + 32; // head + tail + metadata
    unsigned char *sig_data = malloc(sig_size);
    
    // File size (8 bytes)
    memcpy(sig_data, &st.st_size, 8);
    
    // First 4KB
    size_t head_size = (st.st_size < 4096) ? st.st_size : 4096;
    read(fd, sig_data + 8, head_size);
    
    // Last 4KB
    if (st.st_size > 4096) {
        size_t tail_size = (st.st_size < 8192) ? (st.st_size - 4096) : 4096;
        off_t offset = st.st_size - tail_size;
        lseek(fd, offset, SEEK_SET);
        read(fd, sig_data + 8 + head_size, tail_size);
    }
    
    close(fd);
    
    / Hash the signature data
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256(sig_data, 8 + head_size + ((st.st_size > 4096) ? 
        ((st.st_size < 8192) ? (st.st_size - 4096) : 4096) : 0), hash);
    
    free(sig_data);
    
    char hex_str[SHA256_DIGEST_LENGTH * 2 + 1];
    bytes_to_hex(hash, SHA256_DIGEST_LENGTH, hex_str);
    
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    return (*env)->NewStringUTF(env, hex_str);
}