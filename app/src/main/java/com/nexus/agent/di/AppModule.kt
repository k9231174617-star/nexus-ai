package com.nexus.agent.di

import android.content.Context
import androidx.room.Room
import com.nexus.agent.core.cache.CacheDao
import com.nexus.agent.core.cache.ResponseCache
import com.nexus.agent.core.chat.ChatEngine
import com.nexus.agent.core.cli.CLIExecutor
import com.nexus.agent.core.context.ContextManager
import com.nexus.agent.core.files.FileManager
import com.nexus.agent.core.graph.GraphDao
import com.nexus.agent.core.graph.GraphMemory
import com.nexus.agent.core.llm.CustomAPIProvider
import com.nexus.agent.core.llm.FreeLLMProvider
import com.nexus.agent.core.llm.LLMBridge
import com.nexus.agent.core.llm.ModelRouter
import com.nexus.agent.core.llm.PromptEngineer
import com.nexus.agent.core.llm.ResponseParser
import com.nexus.agent.core.llm.TokenCounter
import com.nexus.agent.core.memory.AgentMemory
import com.nexus.agent.core.memory.LocalEmbedder
import com.nexus.agent.core.memory.MemoryDao
import com.nexus.agent.core.memory.VectorStore
import com.nexus.agent.core.planner.TaskPlanner
import com.nexus.agent.core.rag.RAGSystem
import com.nexus.agent.core.router.CostEstimator
import com.nexus.agent.core.router.FallbackChain
import com.nexus.agent.core.router.LatencyTracker
import com.nexus.agent.core.router.ProviderHealth
import com.nexus.agent.core.router.RoutePreferences
import com.nexus.agent.core.sandbox.CodeSandbox
import com.nexus.agent.core.workers.WorkQueue
import com.nexus.agent.data.local.AppDatabase
import com.nexus.agent.data.local.BrowserDao
import com.nexus.agent.data.local.CICDDao
import com.nexus.agent.data.local.CacheDao as LocalCacheDao
import com.nexus.agent.data.local.ChatDao
import com.nexus.agent.data.local.GraphDao as LocalGraphDao
import com.nexus.agent.data.local.PlannerDao
import com.nexus.agent.data.local.ProjectDao
import com.nexus.agent.data.local.RAGDao
import com.nexus.agent.data.local.SandboxDao
import com.nexus.agent.data.local.SettingsDao
import com.nexus.agent.data.local.SpanDao
import com.nexus.agent.data.local.WorkerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "nexus_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideMemoryDao(db: AppDatabase): MemoryDao = db.memoryDao()
    @Provides fun providePlannerDao(db: AppDatabase): PlannerDao = db.plannerDao()
    @Provides fun provideGraphDao(db: AppDatabase): LocalGraphDao = db.graphDao()
    @Provides fun provideCacheDao(db: AppDatabase): LocalCacheDao = db.cacheDao()
    @Provides fun provideSpanDao(db: AppDatabase): SpanDao = db.spanDao()
    @Provides fun provideWorkerDao(db: AppDatabase): WorkerDao = db.workerDao()
    @Provides fun provideCicdDao(db: AppDatabase): CICDDao = db.cicdDao()
    @Provides fun provideSandboxDao(db: AppDatabase): SandboxDao = db.sandboxDao()
    @Provides fun provideBrowserDao(db: AppDatabase): BrowserDao = db.browserDao()
    @Provides fun provideRagDao(db: AppDatabase): RAGDao = db.ragDao()
    @Provides fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides @Singleton
    fun provideFreeLLMProvider(client: OkHttpClient): FreeLLMProvider =
        FreeLLMProvider(client)

    @Provides @Singleton
    fun provideCustomAPIProvider(
        settingsDao: SettingsDao,
        client: OkHttpClient,
    ): CustomAPIProvider = CustomAPIProvider(settingsDao, client)

    @Provides @Singleton
    fun providePromptEngineer(): PromptEngineer = PromptEngineer()

    @Provides @Singleton
    fun provideResponseParser(): ResponseParser = ResponseParser()

    @Provides @Singleton
    fun provideTokenCounter(): TokenCounter = TokenCounter()

    @Provides @Singleton
    fun provideModelRouter(): ModelRouter = ModelRouter()


    @Provides @Singleton
    fun provideMCPClient(client: OkHttpClient): com.nexus.agent.core.mcp.MCPClient =
        com.nexus.agent.core.mcp.MCPClient(client)


    @Provides @Singleton
    fun provideAgentCoordinator(): com.nexus.agent.core.agents.AgentCoordinator =
        com.nexus.agent.core.agents.AgentCoordinator()

    @Provides @Singleton
    fun provideLearningLoop(): com.nexus.agent.core.learning.LearningLoop =
        com.nexus.agent.core.learning.LearningLoop()


    @Provides @Singleton
    fun provideLlamaJNI(@ApplicationContext ctx: Context): com.nexus.agent.core.llama.LlamaJNI {
        com.nexus.agent.core.llama.LlamaJNI.loadNative()
        return com.nexus.agent.core.llama.LlamaJNI(ctx)
    }

    @Provides @Singleton
    fun provideWakeWordDetector(): com.nexus.agent.core.voice.WakeWordDetector =
        com.nexus.agent.core.voice.WakeWordDetector()

    @Provides @Singleton
    fun provideDecompiler(@ApplicationContext ctx: Context): com.nexus.agent.core.apk.Decompiler =
        com.nexus.agent.core.apk.Decompiler(ctx)

    @Provides @Singleton
    fun provideApkPatcher(@ApplicationContext ctx: Context): com.nexus.agent.core.apk.ApkPatcher =
        com.nexus.agent.core.apk.ApkPatcher(ctx)

    @Provides @Singleton
    fun provideLLMBridge(
        api: com.nexus.agent.data.remote.LLMAPI,
        freeLLMProvider: FreeLLMProvider,
        customAPIProvider: CustomAPIProvider,
        promptEngineer: PromptEngineer,
        responseParser: ResponseParser,
    ): LLMBridge = LLMBridge(api, freeLLMProvider, customAPIProvider, promptEngineer, responseParser)

    @Provides @Singleton
    fun provideAgentMemory(
        memoryDao: MemoryDao,
        embedder: LocalEmbedder,
        vectorStore: VectorStore,
    ): AgentMemory = AgentMemory(memoryDao, embedder, vectorStore)

    @Provides @Singleton
    fun provideLocalEmbedder(): LocalEmbedder = LocalEmbedder()

    @Provides @Singleton
    fun provideVectorStore(): VectorStore = VectorStore()

    @Provides @Singleton
    fun provideChatEngine(
        bridge: LLMBridge,
        tokenCounter: TokenCounter,
    ): ChatEngine = ChatEngine(bridge, tokenCounter)

    @Provides @Singleton
    fun provideContextManager(): ContextManager = ContextManager()

    @Provides @Singleton
    fun provideFileManager(@ApplicationContext ctx: Context): FileManager =
        FileManager(ctx)

    @Provides @Singleton
    fun provideCLIExecutor(): CLIExecutor = CLIExecutor()

    @Provides @Singleton
    fun provideTaskPlanner(plannerDao: PlannerDao): TaskPlanner =
        TaskPlanner(plannerDao)

    @Provides @Singleton
    fun provideCodeSandbox(@ApplicationContext ctx: Context): CodeSandbox =
        CodeSandbox(ctx)

    @Provides @Singleton
    fun provideGraphMemory(graphDao: LocalGraphDao): GraphMemory =
        GraphMemory(graphDao)

    @Provides @Singleton
    fun provideRAGSystem(ragDao: RAGDao): RAGSystem =
        RAGSystem(ragDao)

    @Provides @Singleton
    fun provideWorkQueue(workerDao: WorkerDao): WorkQueue =
        WorkQueue(workerDao)

    @Provides @Singleton
    fun provideResponseCache(cacheDao: LocalCacheDao): ResponseCache =
        ResponseCache(cacheDao)

    // Advanced Router (core/router package)
    @Provides @Singleton
    fun provideRoutePreferences(): RoutePreferences = RoutePreferences()

    @Provides @Singleton
    fun provideProviderHealth(): ProviderHealth = ProviderHealth()

    @Provides @Singleton
    fun provideCostEstimator(): CostEstimator = CostEstimator()

    @Provides @Singleton
    fun provideLatencyTracker(): LatencyTracker = LatencyTracker()

    @Provides @Singleton
    fun provideFallbackChain(): FallbackChain = FallbackChain()

    @Provides @Singleton
    fun provideAdvancedRouter(
        prefs: RoutePreferences,
        health: ProviderHealth,
        cost: CostEstimator,
        latency: LatencyTracker,
        fallback: FallbackChain,
    ): com.nexus.agent.core.router.ModelRouter =
        com.nexus.agent.core.router.ModelRouter(prefs, health, cost, latency, fallback)
}
