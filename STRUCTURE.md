nexus-ai/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/nexus/agent/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── NexusApplication.kt
│   │   │   │   │
│   │   │   │   ├── core/
│   │   │   │   │   ├── chat/
│   │   │   │   │   │   ├── ChatViewModel.kt
│   │   │   │   │   │   ├── ChatRepository.kt
│   │   │   │   │   │   ├── MessageAdapter.kt
│   │   │   │   │   │   ├── MessageModel.kt
│   │   │   │   │   │   ├── ChatEngine.kt
│   │   │   │   │   │   └── StreamingHandler.kt
│   │   │   │   │   │
│   │   │   │   │   ├── cli/
│   │   │   │   │   │   ├── CLIExecutor.kt
│   │   │   │   │   │   ├── ShellSession.kt
│   │   │   │   │   │   ├── CommandParser.kt
│   │   │   │   │   │   ├── PermissionHandler.kt
│   │   │   │   │   │   ├── AutocompleteEngine.kt
│   │   │   │   │   │   └── CommandHistory.kt
│   │   │   │   │   │
│   │   │   │   │   ├── files/
│   │   │   │   │   │   ├── FileManager.kt
│   │   │   │   │   │   ├── FileUploader.kt
│   │   │   │   │   │   ├── FileAnalyzer.kt
│   │   │   │   │   │   ├── ShareManager.kt
│   │   │   │   │   │   └── FilePreviewProvider.kt
│   │   │   │   │   │
│   │   │   │   │   ├── root/
│   │   │   │   │   │   ├── RootBridge.kt
│   │   │   │   │   │   ├── SuChecker.kt
│   │   │   │   │   │   ├── RootCommand.kt
│   │   │   │   │   │   └── SystemModifier.kt
│   │   │   │   │   │
│   │   │   │   │   ├── media/
│   │   │   │   │   │   ├── ImageProcessor.kt
│   │   │   │   │   │   ├── VideoGenerator.kt
│   │   │   │   │   │   ├── DocumentReader.kt
│   │   │   │   │   │   ├── DocumentEditor.kt
│   │   │   │   │   │   ├── AnimateImage.kt
│   │   │   │   │   │   └── MediaDropHandler.kt
│   │   │   │   │   │
│   │   │   │   │   ├── apk/
│   │   │   │   │   │   ├── APKTool.kt
│   │   │   │   │   │   ├── Decompiler.kt
│   │   │   │   │   │   ├── Recompiler.kt
│   │   │   │   │   │   ├── ManifestEditor.kt
│   │   │   │   │   │   └── SmaliEditor.kt
│   │   │   │   │   │
│   │   │   │   │   ├── context/
│   │   │   │   │   │   ├── ContextManager.kt
│   │   │   │   │   │   ├── SessionMemory.kt
│   │   │   │   │   │   ├── EnvironmentState.kt
│   │   │   │   │   │   ├── HistoryTracker.kt
│   │   │   │   │   │   └── ContextInjector.kt
│   │   │   │   │   │
│   │   │   │   │   ├── voice/
│   │   │   │   │   │   ├── VoiceInputManager.kt
│   │   │   │   │   │   ├── SpeechRecognizer.kt
│   │   │   │   │   │   └── VoicePermissionHelper.kt
│   │   │   │   │   │
│   │   │   │   │   ├── llm/
│   │   │   │   │   │   ├── LLMBridge.kt
│   │   │   │   │   │   ├── FreeLLMProvider.kt
│   │   │   │   │   │   ├── CustomAPIProvider.kt
│   │   │   │   │   │   ├── ModelRouter.kt
│   │   │   │   │   │   ├── PromptEngineer.kt
│   │   │   │   │   │   ├── ResponseParser.kt
│   │   │   │   │   │   └── TokenCounter.kt
│   │   │   │   │   │
│   │   │   │   │   ├── memory/
│   │   │   │   │   │   ├── AgentMemory.kt
│   │   │   │   │   │   ├── MemoryEntry.kt
│   │   │   │   │   │   ├── MemoryDao.kt
│   │   │   │   │   │   ├── LocalEmbedder.kt
│   │   │   │   │   │   ├── VectorStore.kt
│   │   │   │   │   │   ├── ImportanceScorer.kt
│   │   │   │   │   │   └── MemoryPruner.kt
│   │   │   │   │   │
│   │   │   │   │   ├── planner/
│   │   │   │   │   │   ├── TaskPlanner.kt
│   │   │   │   │   │   ├── TaskModel.kt
│   │   │   │   │   │   ├── TopologicalSorter.kt
│   │   │   │   │   │   ├── WorkflowEngine.kt
│   │   │   │   │   │   ├── ParallelExecutor.kt
│   │   │   │   │   │   └── TaskRegistry.kt
│   │   │   │   │   │
│   │   │   │   │   ├── sandbox/
│   │   │   │   │   │   ├── CodeSandbox.kt
│   │   │   │   │   │   ├── SandboxConfig.kt
│   │   │   │   │   │   ├── NamespaceContainer.kt
│   │   │   │   │   │   ├── ProcessContainer.kt
│   │   │   │   │   │   ├── ResourceLimiter.kt
│   │   │   │   │   │   ├── SandboxResult.kt
│   │   │   │   │   │   └── LanguageRunner.kt
│   │   │   │   │   │
│   │   │   │   │   ├── router/
│   │   │   │   │   │   ├── ModelRouter.kt
│   │   │   │   │   │   ├── LLMProvider.kt
│   │   │   │   │   │   ├── ProviderHealth.kt
│   │   │   │   │   │   ├── RoutePreferences.kt
│   │   │   │   │   │   ├── FallbackChain.kt
│   │   │   │   │   │   ├── CostEstimator.kt
│   │   │   │   │   │   └── LatencyTracker.kt
│   │   │   │   │   │
│   │   │   │   │   ├── browser/
│   │   │   │   │   │   ├── BrowserAgent.kt
│   │   │   │   │   │   ├── WebViewPool.kt
│   │   │   │   │   │   ├── JsBridge.kt
│   │   │   │   │   │   ├── PageNavigator.kt
│   │   │   │   │   │   ├── ContentExtractor.kt
│   │   │   │   │   │   ├── ScreenshotCapture.kt
│   │   │   │   │   │   ├── SearchEngine.kt
│   │   │   │   │   │   └── CookieJar.kt
│   │   │   │   │   │
│   │   │   │   │   ├── rag/
│   │   │   │   │   │   ├── RAGSystem.kt
│   │   │   │   │   │   ├── DocumentIngestor.kt
│   │   │   │   │   │   ├── ChunkEmbedder.kt
│   │   │   │   │   │   ├── VectorSearch.kt
│   │   │   │   │   │   ├── RetrievalResult.kt
│   │   │   │   │   │   ├── ContextAssembler.kt
│   │   │   │   │   │   └── DocumentParser.kt
│   │   │   │   │   │
│   │   │   │   │   ├── graph/                             ← NEW: Graph Memory
│   │   │   │   │   │   ├── GraphMemory.kt
│   │   │   │   │   │   ├── Neo4jDriver.kt
│   │   │   │   │   │   ├── EntityNode.kt
│   │   │   │   │   │   ├── RelationEdge.kt
│   │   │   │   │   │   └── GraphQueryBuilder.kt
│   │   │   │   │   │
│   │   │   │   │   ├── cache/                             ← NEW: Response Cache
│   │   │   │   │   │   ├── ResponseCache.kt
│   │   │   │   │   │   ├── SemanticCache.kt
│   │   │   │   │   │   ├── CacheEntry.kt
│   │   │   │   │   │   └── CacheDao.kt
│   │   │   │   │   │
│   │   │   │   │   ├── workers/                           ← NEW: Distributed Workers
│   │   │   │   │   │   ├── WorkQueue.kt
│   │   │   │   │   │   ├── Worker.kt
│   │   │   │   │   │   ├── Task.kt
│   │   │   │   │   │   ├── TaskHandler.kt
│   │   │   │   │   │   └── WorkerRegistry.kt
│   │   │   │   │   │
│   │   │   │   │   ├── observability/                     ← NEW: Tracing & Metrics
│   │   │   │   │   │   ├── Tracer.kt
│   │   │   │   │   │   ├── Span.kt
│   │   │   │   │   │   ├── MetricsCollector.kt
│   │   │   │   │   │   ├── BottleneckAnalyzer.kt
│   │   │   │   │   │   └── PrometheusExporter.kt
│   │   │   │   │   │
│   │   │   │   │   └── cicd/                              ← NEW: CI/CD Integration
│   │   │   │   │       ├── CICDIntegration.kt
│   │   │   │   │       ├── BuildTrigger.kt
│   │   │   │   │       ├── DeployManager.kt
│   │   │   │   │       └── PipelineParser.kt
│   │   │   │   │
│   │   │   │   ├── ui/
│   │   │   │   │   ├── main/
│   │   │   │   │   │   ├── MainAgentFragment.kt
│   │   │   │   │   │   ├── MainChatFragment.kt
│   │   │   │   │   │   ├── CLIOverlay.kt
│   │   │   │   │   │   └── StatsBarView.kt
│   │   │   │   │   │
│   │   │   │   │   ├── code/
│   │   │   │   │   │   ├── CodeAgentFragment.kt
│   │   │   │   │   │   ├── ProjectBrowser.kt
│   │   │   │   │   │   ├── CodeEditorView.kt
│   │   │   │   │   │   ├── APKWorkspace.kt
│   │   │   │   │   │   ├── FileTreeAdapter.kt
│   │   │   │   │   │   ├── SyntaxHighlighter.kt
│   │   │   │   │   │   ├── MultiTabEditor.kt
│   │   │   │   │   │   └── CodeAIDrawer.kt
│   │   │   │   │   │
│   │   │   │   │   ├── universal/
│   │   │   │   │   │   ├── UniversalAgentFragment.kt
│   │   │   │   │   │   ├── MediaToolbar.kt
│   │   │   │   │   │   ├── ImageEditor.kt
│   │   │   │   │   │   ├── VideoCreator.kt
│   │   │   │   │   │   └── DocumentViewer.kt
│   │   │   │   │   │
│   │   │   │   │   ├── cli/
│   │   │   │   │   │   ├── CLIFragment.kt
│   │   │   │   │   │   ├── TerminalView.kt
│   │   │   │   │   │   ├── QuickCommandsBar.kt
│   │   │   │   │   │   └── RootBadgeView.kt
│   │   │   │   │   │
│   │   │   │   │   ├── files/
│   │   │   │   │   │   ├── FilesFragment.kt
│   │   │   │   │   │   ├── FilesGridView.kt
│   │   │   │   │   │   ├── BreadcrumbView.kt
│   │   │   │   │   │   └── FilePreviewPanel.kt
│   │   │   │   │   │
│   │   │   │   │   ├── memory/
│   │   │   │   │   │   ├── MemoryFragment.kt
│   │   │   │   │   │   ├── MemoryTimelineView.kt
│   │   │   │   │   │   └── ContextInjectPanel.kt
│   │   │   │   │   │
│   │   │   │   │   ├── planner/
│   │   │   │   │   │   ├── PlannerFragment.kt
│   │   │   │   │   │   ├── TaskGraphView.kt
│   │   │   │   │   │   ├── TaskCardView.kt
│   │   │   │   │   │   └── ExecutionLogView.kt
│   │   │   │   │   │
│   │   │   │   │   ├── sandbox/
│   │   │   │   │   │   ├── SandboxFragment.kt
│   │   │   │   │   │   ├── CodeEditorPanel.kt
│   │   │   │   │   │   ├── OutputConsole.kt
│   │   │   │   │   │   ├── ResourceMonitorView.kt
│   │   │   │   │   │   └── LanguageSelector.kt
│   │   │   │   │   │
│   │   │   │   │   ├── browser/
│   │   │   │   │   │   ├── BrowserFragment.kt
│   │   │   │   │   │   ├── AddressBarView.kt
│   │   │   │   │   │   ├── WebContentView.kt
│   │   │   │   │   │   ├── ActionRecorderView.kt
│   │   │   │   │   │   └── SearchResultPanel.kt
│   │   │   │   │   │
│   │   │   │   │   ├── rag/
│   │   │   │   │   │   ├── RAGFragment.kt
│   │   │   │   │   │   ├── DocumentUploadPanel.kt
│   │   │   │   │   │   ├── ChunkPreviewView.kt
│   │   │   │   │   │   ├── SearchQueryView.kt
│   │   │   │   │   │   └── SourceAttributionView.kt
│   │   │   │   │   │
│   │   │   │   │   ├── graph/                             ← NEW: UI для Graph Memory
│   │   │   │   │   │   ├── GraphFragment.kt
│   │   │   │   │   │   ├── EntityGraphView.kt
│   │   │   │   │   │   ├── RelationEditor.kt
│   │   │   │   │   │   └── GraphQueryPanel.kt
│   │   │   │   │   │
│   │   │   │   │   ├── observability/                     ← NEW: UI для мониторинга
│   │   │   │   │   │   ├── ObservabilityFragment.kt
│   │   │   │   │   │   ├── TraceTimelineView.kt
│   │   │   │   │   │   ├── MetricsDashboard.kt
│   │   │   │   │   │   └── BottleneckAlertView.kt
│   │   │   │   │   │
│   │   │   │   │   ├── settings/
│ │ │ │ │ │ ├── SettingsFragment.kt
│ │ │ │ │ │ ├── LLMConfigFragment.kt
│ │ │ │ │ │ ├── APIKeyManager.kt
│ │ │ │ │ │ ├── ModelSelector.kt
│ │ │ │ │ │ ├── AgentConfigFragment.kt
│ │ │ │ │ │ ├── RangeSliderView.kt
│ │ │ │ │ │ └── SystemInfoPanel.kt
│ │ │ │ │ │
│ │ │ │ │ └── common/
│ │ │ │ │ ├── DrawerAdapter.kt
│ │ │ │ │ ├── ChatInputBar.kt
│ │ │ │ │ ├── FileAttachmentView.kt
│ │ │ │ │ ├── CopyButton.kt
│ │ │ │ │ ├── MarkdownRenderer.kt
│ │ │ │ │ ├── ContextModeBar.kt
│ │ │ │ │ ├── TypingIndicatorView.kt
│ │ │ │ │ ├── ToastManager.kt
│ │ │ │ │ └── NeonToggleView.kt
│   │   │   │   │   │
│   │   │   │   │   └── common/
│   │   │   │   │       ├── DrawerAdapter.kt
│   │   │   │   │       ├── ChatInputBar.kt
│   │   │   │   │       ├── FileAttachmentView.kt
│   │   │   │   │       ├── CopyButton.kt
│   │   │   │   │       ├── MarkdownRenderer.kt
│   │   │   │   │       ├── ContextModeBar.kt
│   │   │   │   │       ├── TypingIndicatorView.kt
│   │   │   │   │       ├── ToastManager.kt
│   │   │   │   │       └── NeonToggleView.kt
│   │   │   │   │
│   │   │   │   └── data/
│   │   │   │       ├── local/
│   │   │   │       │   ├── AppDatabase.kt
│   │   │   │       │   ├── ChatDao.kt
│   │   │   │       │   ├── ProjectDao.kt
│   │   │   │       │   ├── SettingsDao.kt
│   │   │   │       │   ├── MemoryDao.kt
│   │   │   │       │   ├── PlannerDao.kt
│   │   │   │       │   ├── SandboxDao.kt
│   │   │   │       │   ├── BrowserDao.kt
│   │   │   │       │   ├── RAGDao.kt
│   │   │   │       │   ├── GraphDao.kt                    ← NEW
│   │   │   │       │   ├── CacheDao.kt                    ← NEW
│   │   │   │       │   ├── WorkerDao.kt                   ← NEW
│   │   │   │       │   ├── SpanDao.kt                     ← NEW
│   │   │   │       │   └── CICDDao.kt                     ← NEW
│   │   │   │       │
│   │   │   │       └── remote/
│   │   │   │           ├── LLMAPI.kt
│   │   │   │           ├── FreeLLMEndpoints.kt
│   │   │   │           ├── CustomEndpoints.kt
│   │   │   │           └── BrowserAPI.kt
│   │   │   │
│   │   │   ├── jni/
│   │   │   │   ├── native-shell.c
│   │   │   │   ├── root-bridge.c
│   │   │   │   ├── file-ops.c
│   │   │   │   ├── sandbox-bridge.c
│   │   │   │   └── Android.mk
│   │   │   │
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   ├── fragment_main_agent.xml
│   │   │   │   │   ├── fragment_code_agent.xml
│   │   │   │   │   ├── fragment_universal_agent.xml
│   │   │   │   │   ├── fragment_cli.xml
│   │   │   │   │   ├── fragment_files.xml
│   │   │   │   │   ├── fragment_memory.xml
│   │   │   │   │   ├── fragment_planner.xml
│   │   │   │   │   ├── fragment_sandbox.xml
│   │   │   │   │   ├── fragment_browser.xml
│   │   │   │   │   ├── fragment_rag.xml
│   │   │   │   │   ├── fragment_graph.xml                ← NEW
│   │   │   │   │   ├── fragment_observability.xml          ← NEW
│   │   │   │   │   ├── fragment_settings.xml
│   │   │   │   │   ├── item_message.xml
│   │   │   │   │   ├── item_file.xml
│   │   │   │   │   ├── item_task.xml
│   │   │   │   │   ├── item_chunk.xml
│   │   │   │   │   └── view_stats_bar.xml
│   │   │   │   │
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── dimens.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   │
│   │   │   │   ├── drawable/
│   │   │   │   │   ├── ic_nexus_logo.xml
│   │   │   │   │   ├── ic_main_agent.xml
│   │   │   │   │   ├── ic_code_agent.xml
│   │   │   │   │   ├── ic_universal.xml
│   │   │   │   │   ├── ic_cli.xml
│   │   │   │   │   ├── ic_files.xml
│   │   │   │   │   ├── ic_memory.xml
│   │   │   │   │   ├── ic_planner.xml
│   │   │   │   │   ├── ic_sandbox.xml
│   │   │   │   │   ├── ic_browser.xml
│   │   │   │   │   ├── ic_rag.xml
│   │   │   │   │   ├── ic_graph.xml                     ← NEW
│   │   │   │   │   ├── ic_observability.xml               ← NEW
│   │   │   │   │   ├── ic_settings.xml
│   │   │   │   │   ├── bg_neon_border.xml
│   │   │   │   │   ├── bg_card_dark.xml
│   │   │   │   │   └── bg_input_dark.xml
│   │   │   │   │
│   │   │   │   ├── menu/
│   │   │   │   │   ├── drawer_menu.xml
│   │   │   │   │   └── topbar_menu.xml
│   │   │   │   │
│   │   │   │   └── font/
│   │   │   │       └── sf_pro.xml
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   └── test/
│   │       ├── ChatEngineTest.kt
│   │       ├── CLIExecutorTest.kt
│   │       ├── LLMBridgeTest.kt
│   │       ├── TokenCounterTest.kt
│   │       ├── MemoryTest.kt
│   │       ├── PlannerTest.kt
│   │       ├── SandboxTest.kt
│   │       ├── RouterTest.kt
│   │       ├── BrowserTest.kt
│   │       ├── RAGTest.kt
│   │       ├── GraphMemoryTest.kt                         ← NEW
│   │       ├── ResponseCacheTest.kt                       ← NEW
│   │       ├── WorkerQueueTest.kt                         ← NEW
│   │       ├── TracerTest.kt                              ← NEW
│   │       └── CICDIntegrationTest.kt                     ← NEW
│   │
│   ├── build.gradle.kts
│   └── proguard-rules.pro
│
├── dashboard/
│   ├── index.html
│   ├── styles/
│   │   ├── main.css
│   │   ├── components.css
│   │   └── animations.css
│   └── js/
│       ├── app.js
│       ├── chat.js
│       ├── cli.js
│       ├── files.js
│       ├── settings.js
│       ├── memory.js
│       ├── planner.js
│       ├── sandbox.js
│       ├── browser.js
│       ├── rag.js
│       ├── graph.js                                       ← NEW
│       ├── observability.js                               ← NEW
│       └── cicd.js                                        ← NEW
│
├── nativelibs/
│   ├── apktool/
│   ├── smali/
│   ├── busybox/
│   ├── aapt2/
│   └── faiss/
│
├── build.gradle.kts
└── README.md
