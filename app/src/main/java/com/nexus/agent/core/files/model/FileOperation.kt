// app/src/main/java/com/nexus/agent/core/files/model/FileOperation.kt
package com.nexus.agent.core.files.model

sealed class FileOperation {
    abstract val id: String

    data class Copy(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val sources: List<FileItem>,
        val destinationDir: String,
        val newName: String? = null
    ) : FileOperation()

    data class Move(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val sources: List<FileItem>,
        val destinationDir: String
    ) : FileOperation()

    data class Delete(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val files: List<FileItem>,
        val permanent: Boolean = false
    ) : FileOperation()

    data class Rename(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val file: FileItem,
        val newName: String
    ) : FileOperation()

    data class Compress(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val files: List<FileItem>,
        val outputPath: String,
        val format: ArchiveFormat = ArchiveFormat.ZIP
    ) : FileOperation()

    data class CreateFolder(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val parentPath: String,
        val folderName: String
    ) : FileOperation()

    data class CreateFile(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val parentPath: String,
        val fileName: String,
        val content: String = ""
    ) : FileOperation()
}

enum class ArchiveFormat {
    ZIP, TAR_GZ
}
