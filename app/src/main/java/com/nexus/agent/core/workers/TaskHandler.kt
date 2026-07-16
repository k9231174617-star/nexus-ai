package com.nexus.agent.core.workers

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.CoroutineContext

/**
 * TaskHandler — абстрактный базовый класс для обработчиков задач.
 * Каждый конкретный тип задачи реализует свой handler.
 */
abstract class TaskHandler {

    /**
     * Результат выполнения задачи.
     */
    sealed class TaskResult {
        data class Success(
            val output: JsonObject,
            val executionTimeMs: Long,
            val metadata: Map<String, String> = emptyMap()
        ) : TaskResult()

        data class Failure(
            val error: String,
            val errorType: ErrorType,
            val isRetryable: Boolean = true,
            val executionTimeMs: Long = 0
        ) : TaskResult()

        data class Cancelled(val reason: String) : TaskResult()

        enum class ErrorType {
            TIMEOUT,
            NETWORK_ERROR,
            RATE_LIMIT,
            INVALID_INPUT,
            EXECUTION_ERROR,
            RESOURCE_EXHAUSTED,
            UNKNOWN
        }
    }

    /**
     * Контекст выполнения задачи.
     */
    data class ExecutionContext(
        val taskId: String,
        val workerId: String,
        val attemptNumber: Int,
        val cancellationToken: CancellationToken,
        val progressReporter: ProgressReporter
    )

    /**
     * Токен отмены задачи.
     */
    class CancellationToken {
        @Volatile
        private var _isCancelled = false
        val isCancelled: Boolean get() = _isCancelled

        fun cancel() {
            _isCancelled = true
        }
    }

    /**
     * Репортёр прогресса задачи.
     */
    interface ProgressReporter {
        suspend fun report(progress: Double, message: String = "")
        suspend fun reportStage(stage: String, detail: String = "")
    }

    /**
     * Тип задач, которые обрабатывает этот handler.
     */
    abstract val supportedTypes: Set<Task.TaskType>

    /**
     * Выполняет задачу. Должен быть реализован в подклассах.
     */
    abstract suspend fun execute(
        task: Task,
        context: ExecutionContext
    ): TaskResult

    /**
     * Проверяет, может ли handler обработать задачу данного типа.
     */
    fun canHandle(type: Task.TaskType): Boolean = type in supportedTypes

    /**
     * Предобработка перед выполнением (может быть переопределена).
     */
    open suspend fun beforeExecute(task: Task, context: ExecutionContext): Boolean = true

    /**
     * Постобработка после выполнения (может быть переопределена).
     */
    open suspend fun afterExecute(
        task: Task,
        context: ExecutionContext,
        result: TaskResult
    ) { /* no-op */ }

    /**
     * Валидация payload задачи.
     */
    open fun validatePayload(task: Task): TaskResult? = null
}

/**
 * Реализация ProgressReporter через SharedFlow.
 */
class FlowProgressReporter : TaskHandler.ProgressReporter {
    private val _progressFlow = MutableSharedFlow<ProgressUpdate>(extraBufferCapacity = 64)
    val progressFlow: SharedFlow<ProgressUpdate> = _progressFlow.asSharedFlow()

    data class ProgressUpdate(
        val progress: Double,
        val message: String,
        val stage: String? = null
    )

    override suspend fun report(progress: Double, message: String) {
        _progressFlow.emit(ProgressUpdate(progress.coerceIn(0.0, 1.0), message))
    }

    override suspend fun reportStage(stage: String, detail: String) {
        _progressFlow.emit(ProgressUpdate(-1.0, detail, stage))
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Конкретные реализации TaskHandler
// ─────────────────────────────────────────────────────────────────────────

/**
 * Handler для LLM-инференса.
 */
class LLMInferenceHandler(
    private val llmBridge: com.nexus.agent.core.llm.LLMBridge
) : TaskHandler() {

    override val supportedTypes: Set<Task.TaskType> = setOf(Task.TaskType.LLM_INFERENCE)

    override suspend fun execute(task: Task, context: ExecutionContext): TaskResult {
        val prompt = task.payload["prompt"]?.toString()
            ?: return TaskResult.Failure(
                "Missing 'prompt' in payload",
                TaskResult.ErrorType.INVALID_INPUT,
                isRetryable = false
            )

        val model = task.payload["model"]?.toString() ?: "default"
        val maxTokens = task.payload["maxTokens"]?.toString()?.toIntOrNull() ?: 2048

        return try {
            val startTime = System.currentTimeMillis()

            // Симуляция LLM-вызова (заменить на реальный вызов llmBridge)
            val response = withTimeout(task.timeoutMs) {
                llmBridge.generate(prompt, model, maxTokens, context.cancellationToken)
            }

            val executionTime = System.currentTimeMillis() - startTime

            TaskResult.Success(
                output = buildJsonObject {
                    put("response", response)
                    put("model", model)
                    put("tokensUsed", task.payload["maxTokens"]?.toString() ?: "0")
                },
                executionTimeMs = executionTime,
                metadata = mapOf("handler" to "LLMInferenceHandler")
            )
        } catch (e: TimeoutCancellationException) {
            TaskResult.Failure(
                "LLM inference timed out after ${task.timeoutMs}ms",
                TaskResult.ErrorType.TIMEOUT,
                isRetryable = true
            )
        } catch (e: Exception) {
            TaskResult.Failure(
                e.message ?: "Unknown error during LLM inference",
                TaskResult.ErrorType.EXECUTION_ERROR,
                isRetryable = true
            )
        }
    }
}

/**
 * Handler для выполнения кода в sandbox.
 */
class CodeExecutionHandler : TaskHandler() {

    override val supportedTypes: Set<Task.TaskType> = setOf(Task.TaskType.CODE_EXECUTION)

    override suspend fun execute(task: Task, context: ExecutionContext): TaskResult {
        val code = task.payload["code"]?.toString()
            ?: return TaskResult.Failure(
                "Missing 'code' in payload",
                TaskResult.ErrorType.INVALID_INPUT,
                isRetryable = false
            )

        val language = task.payload["language"]?.toString() ?: "kotlin"

        return try {
            val startTime = System.currentTimeMillis()

            // Интеграция с sandbox модулем
            val sandbox = com.nexus.agent.core.sandbox.CodeSandbox()
            val result = withTimeout(task.timeoutMs) {
                sandbox.execute(code, language, context.cancellationToken)
            }

            val executionTime = System.currentTimeMillis() - startTime

            TaskResult.Success(
                output = buildJsonObject {
                    put("stdout", result.stdout)
                    put("stderr", result.stderr)
                    put("exitCode", result.exitCode)
                    put("language", language)
                },
                executionTimeMs = executionTime,
                metadata = mapOf("handler" to "CodeExecutionHandler")
            )
        } catch (e: TimeoutCancellationException) {
            TaskResult.Failure(
                "Code execution timed out",
                TaskResult.ErrorType.TIMEOUT,
                isRetryable = true
            )
        } catch (e: Exception) {
            TaskResult.Failure(
                e.message ?: "Code execution failed",
                TaskResult.ErrorType.EXECUTION_ERROR,
                isRetryable = e !is SecurityException
            )
        }
    }
}

/**
 * Handler для обработки файлов.
 */
class FileProcessingHandler : TaskHandler() {

    override val supportedTypes: Set<Task.TaskType> = setOf(Task.TaskType.FILE_PROCESSING)

    override suspend fun execute(task: Task, context: ExecutionContext): TaskResult {
        val filePath = task.payload["filePath"]?.toString()
            ?: return TaskResult.Failure(
                "Missing 'filePath' in payload",
                TaskResult.ErrorType.INVALID_INPUT,
                isRetryable = false
            )

        val operation = task.payload["operation"]?.toString() ?: "analyze"

        return try {
            val startTime = System.currentTimeMillis()

            // Интеграция с FileManager
            val fileManager = com.nexus.agent.core.files.FileManager()
            val result = withTimeout(task.timeoutMs) {
                when (operation) {
                    "analyze" -> fileManager.analyze(filePath)
                    "convert" -> fileManager.convert(
                        filePath,
                        task.payload["targetFormat"]?.toString() ?: "txt"
                    )
                    "compress" -> fileManager.compress(filePath)
                    else -> throw IllegalArgumentException("Unknown operation: $operation")
                }
            }

            val executionTime = System.currentTimeMillis() - startTime

            TaskResult.Success(
                output = buildJsonObject {
                    put("result", result.toString())
                    put("operation", operation)
                    put("filePath", filePath)
                },
                executionTimeMs = executionTime
            )
        } catch (e: Exception) {
            TaskResult.Failure(
                e.message ?: "File processing failed",
                if (e is java.io.FileNotFoundException) TaskResult.ErrorType.INVALID_INPUT
                else TaskResult.ErrorType.EXECUTION_ERROR,
                isRetryable = e !is java.io.FileNotFoundException
            )
        }
    }
}

/**
 * Handler для embedding-задач (RAG).
 */
class EmbeddingHandler : TaskHandler() {

    override val supportedTypes: Set<Task.TaskType> = setOf(
        Task.TaskType.EMBEDDING,
        Task.TaskType.INDEXING
    )

    override suspend fun execute(task: Task, context: ExecutionContext): TaskResult {
        return when (task.type) {
            Task.TaskType.EMBEDDING -> executeEmbedding(task, context)
            Task.TaskType.INDEXING -> executeIndexing(task, context)
            else -> TaskResult.Failure(
                "Unsupported task type: ${task.type}",
                TaskResult.ErrorType.INVALID_INPUT,
                isRetryable = false
            )
        }
    }

    private suspend fun executeEmbedding(task: Task, context: ExecutionContext): TaskResult {
        val texts = task.payload["texts"]?.toString()?.split("\n")
            ?: listOf(task.payload["text"]?.toString() ?: "")

        return try {
            val startTime = System.currentTimeMillis()
            val embedder = com.nexus.agent.core.memory.LocalEmbedder()

            val embeddings = withTimeout(task.timeoutMs) {
                texts.map { embedder.embed(it) }
            }

            TaskResult.Success(
                output = buildJsonObject {
                    put("embeddingsCount", embeddings.size)
                    put("dimension", embeddings.firstOrNull()?.size ?: 0)
                },
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TaskResult.Failure(
                e.message ?: "Embedding failed",
                TaskResult.ErrorType.EXECUTION_ERROR,
                isRetryable = true
            )
        }
    }

    private suspend fun executeIndexing(task: Task, context: ExecutionContext): TaskResult {
        val documentId = task.payload["documentId"]?.toString()
            ?: return TaskResult.Failure(
                "Missing 'documentId'",
                TaskResult.ErrorType.INVALID_INPUT,
                isRetryable = false
            )

        return try {
            val startTime = System.currentTimeMillis()
            val ragSystem = com.nexus.agent.core.rag.RAGSystem()

            withTimeout(task.timeoutMs) {
                ragSystem.indexDocument(documentId)
            }

            TaskResult.Success(
                output = buildJsonObject {
                    put("documentId", documentId)
                    put("indexed", true)
                },
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TaskResult.Failure(
                e.message ?: "Indexing failed",
                TaskResult.ErrorType.EXECUTION_ERROR,
                isRetryable = true
            )
        }
    }
}

/**
 * Handler для CI/CD задач.
 */
class CIBuildHandler : TaskHandler() {

    override val supportedTypes: Set<Task.TaskType> = setOf(Task.TaskType.CI_BUILD)

    override suspend fun execute(task: Task, context: ExecutionContext): TaskResult {
        val repoUrl = task.payload["repoUrl"]?.toString()
            ?: return TaskResult.Failure(
                "Missing 'repoUrl'",
                TaskResult.ErrorType.INVALID_INPUT,
                isRetryable = false
            )

        val branch = task.payload["branch"]?.toString() ?: "main"

        return try {
            val startTime = System.currentTimeMillis()
            val ciIntegration = com.nexus.agent.core.cicd.CICDIntegration()

            val buildResult = withTimeout(task.timeoutMs) {
                ciIntegration.triggerBuild(repoUrl, branch)
            }

            TaskResult.Success(
                output = buildJsonObject {
                    put("buildId", buildResult.buildId)
                    put("status", buildResult.status)
                    put("logs", buildResult.logs)
                },
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TaskResult.Failure(
                e.message ?: "CI build failed",
                TaskResult.ErrorType.EXECUTION_ERROR,
                isRetryable = true
            )
        }
    }
}
