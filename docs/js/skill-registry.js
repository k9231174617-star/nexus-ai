/**
 * Nexus AI — Skill Registry
 * Loads and manages AI agent skills from awesome-agent-skills catalog.
 * Skills are fetched from officialskills.sh at runtime.
 * Integrates with PluginManager, MCPClient, and AgentCoordinator.
 */
'use strict';

const SkillRegistry = {
  // ── Skill Database ─────────────────────────────────────────
  skills: [],

  // Cached loaded skills
  _loaded: {},
  _categoryOrder: [
    'mcp', 'llm-ai', 'security', 'android', 'browser',
    'database', 'testing', 'observability', 'media', 'cicd', 'community'
  ],

  categoryNames: {
    'mcp': { icon: '🔌', name: 'MCP & Интеграция', color: '#6366f1' },
    'llm-ai': { icon: '🧠', name: 'LLM / AI / RAG', color: '#10b981' },
    'security': { icon: '🔒', name: 'Безопасность', color: '#ef4444' },
    'android': { icon: '📱', name: 'Android / Mobile', color: '#22c55e' },
    'browser': { icon: '🌐', name: 'Браузерная автоматизация', color: '#f59e0b' },
    'database': { icon: '🗄️', name: 'Базы данных', color: '#3b82f6' },
    'testing': { icon: '🧪', name: 'Тестирование', color: '#8b5cf6' },
    'observability': { icon: '📊', name: 'Мониторинг', color: '#ec4899' },
    'media': { icon: '🎬', name: 'Медиа / Документы', color: '#14b8a6' },
    'cicd': { icon: '🔄', name: 'CI/CD & DevOps', color: '#f97316' },
    'community': { icon: '🌟', name: 'Community', color: '#eab308' },
  },

  // ── Register all skills from awesome-agent-skills ─────────
  registerAll() {
    this.skills = [
      // 🔌 MCP & Tool Integration
      { id: 'mcp-builder', company: 'anthropics', category: 'mcp', tier: 'free',
        name: 'MCP Builder', url: 'https://officialskills.sh/anthropics/skills/mcp-builder',
        description: 'Создание MCP серверов для интеграции внешних API и сервисов',
        tags: ['mcp', 'tools', 'api'], priority: 10,
        load: () => this._loadSkill('https://officialskills.sh/anthropics/skills/mcp-builder') },

      { id: 'composio', company: 'composiohq', category: 'mcp', tier: 'free',
        name: 'Composio', url: 'https://officialskills.sh/composiohq/skills/composio',
        description: 'Подключение AI агентов к 1000+ внешних приложений',
        tags: ['mcp', 'integration'], priority: 9,
        load: () => this._loadSkill('https://officialskills.sh/composiohq/skills/composio') },

      { id: 'agents-sdk', company: 'cloudflare', category: 'mcp', tier: 'free',
        name: 'Cloudflare Agents SDK', url: 'https://officialskills.sh/cloudflare/skills/agents-sdk',
        description: 'Построение stateful AI агентов с планировщиком, RPC и MCP',
        tags: ['mcp', 'agents', 'serverless'], priority: 9 },

      { id: 'openai-docs', company: 'openai', category: 'mcp', tier: 'free',
        name: 'OpenAI Docs', url: 'https://officialskills.sh/openai/skills/openai-docs',
        description: 'Официальная документация OpenAI API для LLM интеграции',
        tags: ['llm', 'api', 'docs'], priority: 8 },

      // 🧠 LLM / AI / RAG
      { id: 'gemini-api', company: 'google-gemini', category: 'llm-ai', tier: 'free',
        name: 'Gemini API', url: 'https://officialskills.sh/google-gemini/skills/gemini-interactions-api',
        description: 'Gemini API: текст, чат, стриминг, генерация изображений',
        tags: ['llm', 'ai', 'google', 'multimodal'], priority: 10 },

      { id: 'transformers', company: 'huggingface', category: 'llm-ai', tier: 'free',
        name: 'Hugging Face Transformers', url: 'https://officialskills.sh/huggingface/skills/transformers',
        description: '🤗 Transformers для NLP, компьютерного зрения, аудио',
        tags: ['llm', 'nlp', 'models', 'huggingface'], priority: 9 },

      { id: 'diffusion', company: 'huggingface', category: 'llm-ai', tier: 'free',
        name: 'Hugging Face Diffusers', url: 'https://officialskills.sh/huggingface/skills/diffusion',
        description: '🧨 Diffusers для генерации изображений (Stable Diffusion)',
        tags: ['ai', 'image', 'generation'], priority: 8 },

      { id: 'qdrant-skills', company: 'qdrant', category: 'llm-ai', tier: 'free',
        name: 'Qdrant Vector Search', url: 'https://officialskills.sh/qdrant/skills',
        description: 'Векторный поиск: масштабирование, оптимизация, качество, мониторинг',
        tags: ['vector', 'search', 'rag', 'embeddings'], priority: 9 },

      { id: 'nvidia-rag', company: 'NVIDIA', category: 'llm-ai', tier: 'free',
        name: 'NVIDIA RAG Pipeline', url: 'https://github.com/NVIDIA/skills/tree/main/skills/rag',
        description: 'Retrieval-Augmented Generation пайплайн от NVIDIA',
        tags: ['rag', 'retrieval', 'generation'], priority: 8 },

      { id: 'huggingface-hub', company: 'huggingface', category: 'llm-ai', tier: 'free',
        name: 'Hugging Face Hub', url: 'https://officialskills.sh/huggingface/skills/hub',
        description: 'Управление датасетами, моделями и Spaces на Hugging Face Hub',
        tags: ['models', 'datasets', 'hub'], priority: 8 },

      // 🔒 Security & Code Audit
      { id: 'trailofbits-static-analysis', company: 'trailofbits', category: 'security', tier: 'free',
        name: 'Static Analysis', url: 'https://officialskills.sh/trailofbits/skills/static-analysis',
        description: 'Статический анализ безопасности кода от Trail of Bits',
        tags: ['security', 'code-review', 'static-analysis'], priority: 10 },

      { id: 'trailofbits-supply-chain', company: 'trailofbits', category: 'security', tier: 'free',
        name: 'Supply Chain Audit', url: 'https://officialskills.sh/trailofbits/skills/supply-chain',
        description: 'Анализ цепочки поставок и зависимостей на уязвимости',
        tags: ['security', 'dependencies', 'audit'], priority: 9 },

      { id: 'trailofbits-fuzzing', company: 'trailofbits', category: 'security', tier: 'free',
        name: 'Fuzz Testing', url: 'https://officialskills.sh/trailofbits/skills/fuzzing',
        description: 'Фаззинг-тестирование для поиска уязвимостей',
        tags: ['security', 'testing', 'fuzzing'], priority: 9 },

      { id: 'trailofbits-crypto-review', company: 'trailofbits', category: 'security', tier: 'free',
        name: 'Crypto Review', url: 'https://officialskills.sh/trailofbits/skills/crypto-review',
        description: 'Криптографический аудит и best practices',
        tags: ['security', 'crypto', 'audit'], priority: 8 },

      { id: 'trailofbits-taint', company: 'trailofbits', category: 'security', tier: 'free',
        name: 'Taint Analysis', url: 'https://officialskills.sh/trailofbits/skills/taint',
        description: 'Трекинг данных для поиска уязвимостей',
        tags: ['security', 'data-flow', 'vulnerability'], priority: 8 },

      // 📱 Android & Mobile
      { id: 'espresso-skill', company: 'testmu-ai', category: 'android', tier: 'free',
        name: 'Espresso UI Tests', url: 'https://github.com/LambdaTest/agent-skills/tree/main/espresso-skill',
        description: 'Espresso UI тесты для Android на Kotlin/Java',
        tags: ['android', 'testing', 'espresso', 'kotlin'], priority: 9 },

      { id: 'appium-skill', company: 'testmu-ai', category: 'android', tier: 'free',
        name: 'Appium Mobile Tests', url: 'https://github.com/LambdaTest/agent-skills/tree/main/appium-skill',
        description: 'Appium автоматизация Android и iOS в Java, Python, JS',
        tags: ['mobile', 'testing', 'appium'], priority: 9 },

      { id: 'expo-sdk', company: 'expo', category: 'android', tier: 'free',
        name: 'Expo SDK', url: 'https://officialskills.sh/expo/skills/expo-sdk',
        description: 'Expo SDK: камера, геолокация, уведомления, сенсоры',
        tags: ['mobile', 'react-native', 'expo'], priority: 7 },

      { id: 'react-native-best-practices', company: 'callstackincubator', category: 'android', tier: 'free',
        name: 'React Native Best Practices', url: 'https://officialskills.sh/callstackincubator/skills/react-native-best-practices',
        description: 'Оптимизация производительности React Native от Callstack',
        tags: ['mobile', 'react-native', 'performance'], priority: 8 },

      // 🌐 Browser Automation
      { id: 'stagehand', company: 'browserbase', category: 'browser', tier: 'free',
        name: 'Stagehand Browser Automation', url: 'https://officialskills.sh/browserbase/skills/stagehand',
        description: 'AI-управляемая автоматизация браузера Stagehand',
        tags: ['browser', 'automation', 'ai'], priority: 10 },

      { id: 'playwright-skill', company: 'testmu-ai', category: 'browser', tier: 'free',
        name: 'Playwright E2E', url: 'https://github.com/LambdaTest/agent-skills/tree/main/playwright-skill',
        description: 'Playwright E2E тесты в TS, JS, Python, Java, C#',
        tags: ['browser', 'testing', 'playwright'], priority: 9 },

      { id: 'webapp-testing', company: 'anthropics', category: 'browser', tier: 'free',
        name: 'Web App Testing', url: 'https://officialskills.sh/anthropics/skills/webapp-testing',
        description: 'Тестирование веб-приложений через Playwright',
        tags: ['browser', 'testing', 'playwright'], priority: 8 },

      { id: 'cypress-skill', company: 'testmu-ai', category: 'browser', tier: 'free',
        name: 'Cypress E2E', url: 'https://github.com/LambdaTest/agent-skills/tree/main/cypress-skill',
        description: 'Cypress E2E и компонентные тесты в JS/TS',
        tags: ['browser', 'testing', 'cypress'], priority: 8 },

      { id: 'brave-images-search', company: 'brave', category: 'browser', tier: 'free',
        name: 'Brave Image Search', url: 'https://officialskills.sh/brave/skills/images-search',
        description: 'Поиск изображений через Brave Search API',
        tags: ['search', 'images', 'brave'], priority: 7 },

      // 🗄️ Databases
      { id: 'mongodb-schema-design', company: 'mongodb', category: 'database', tier: 'free',
        name: 'MongoDB Schema Design', url: 'https://officialskills.sh/mongodb/skills/mongodb-schema-design',
        description: 'Дизайн схем документов с валидацией и индексами',
        tags: ['database', 'mongodb', 'schema'], priority: 9 },

      { id: 'mongodb-aggregations', company: 'mongodb', category: 'database', tier: 'free',
        name: 'MongoDB Aggregations', url: 'https://officialskills.sh/mongodb/skills/mongodb-aggregations',
        description: 'MongoDB агрегации от $match до $facet',
        tags: ['database', 'mongodb', 'queries'], priority: 9 },

      { id: 'redis-development', company: 'redis', category: 'database', tier: 'free',
        name: 'Redis Development', url: 'https://officialskills.sh/redis/skills/redis-development',
        description: 'Redis: структуры данных, векторный поиск, кэширование',
        tags: ['database', 'redis', 'caching', 'vector'], priority: 9 },

      { id: 'firebase-security-rules', company: 'firebase', category: 'database', tier: 'free',
        name: 'Firebase Security Rules', url: 'https://officialskills.sh/firebase/skills/firebase-security-rules',
        description: 'Firebase Security Rules для Firestore и Storage',
        tags: ['database', 'firebase', 'security'], priority: 8 },

      // 🧪 Testing
      { id: 'api-skill', company: 'testmu-ai', category: 'testing', tier: 'free',
        name: 'API Testing Suite', url: 'https://github.com/LambdaTest/agent-skills/tree/main/api-skill',
        description: 'Дизайн, мокинг, документирование, тесты REST/GraphQL/gRPC API',
        tags: ['testing', 'api', 'rest', 'graphql'], priority: 10 },

      { id: 'jest-skill', company: 'testmu-ai', category: 'testing', tier: 'free',
        name: 'Jest Testing', url: 'https://github.com/LambdaTest/agent-skills/tree/main/jest-skill',
        description: 'Jest unit/integration тесты с моками и снепшотами',
        tags: ['testing', 'jest', 'javascript'], priority: 9 },

      { id: 'junit-5-skill', company: 'testmu-ai', category: 'testing', tier: 'free',
        name: 'JUnit 5 Testing', url: 'https://github.com/LambdaTest/agent-skills/tree/main/junit-5-skill',
        description: 'JUnit 5 тесты с Mockito в Java',
        tags: ['testing', 'junit', 'java', 'mockito'], priority: 9 },

      { id: 'cicd-pipeline-skill', company: 'testmu-ai', category: 'cicd', tier: 'free',
        name: 'CI/CD Pipelines', url: 'https://github.com/LambdaTest/agent-skills/tree/main/cicd-pipeline-skill',
        description: 'CI/CD пайплайны: GitHub Actions, Jenkins, GitLab CI, Azure DevOps',
        tags: ['cicd', 'pipeline', 'automation'], priority: 9 },

      // 📊 Observability
      { id: 'sentry-feature-setup', company: 'getsentry', category: 'observability', tier: 'free',
        name: 'Sentry AI Monitoring', url: 'https://officialskills.sh/getsentry/skills/sentry-feature-setup',
        description: 'Sentry: AI мониторинг, OTel пайплайны, алерты',
        tags: ['monitoring', 'sentry', 'otel'], priority: 10 },

      { id: 'sentry-performance', company: 'getsentry', category: 'observability', tier: 'free',
        name: 'Sentry Performance', url: 'https://officialskills.sh/getsentry/skills/performance',
        description: 'Sentry Performance: трассировка, боттлнеки, метрики',
        tags: ['monitoring', 'sentry', 'performance'], priority: 9 },

      { id: 'sentry-session-replay', company: 'getsentry', category: 'observability', tier: 'free',
        name: 'Sentry Session Replay', url: 'https://officialskills.sh/getsentry/skills/session-replay',
        description: 'Sentry Session Replay: воспроизведение сессий пользователей',
        tags: ['monitoring', 'sentry', 'replay'], priority: 8 },

      // 🎬 Media & Documents
      { id: 'anthropics-docx', company: 'anthropics', category: 'media', tier: 'free',
        name: 'Word Documents', url: 'https://officialskills.sh/anthropics/skills/docx',
        description: 'Создание, редактирование и анализ Word документов',
        tags: ['documents', 'word', 'office'], priority: 8 },

      { id: 'anthropics-pptx', company: 'anthropics', category: 'media', tier: 'free',
        name: 'PowerPoint', url: 'https://officialskills.sh/anthropics/skills/pptx',
        description: 'Создание, редактирование и анализ PowerPoint презентаций',
        tags: ['documents', 'powerpoint', 'presentation'], priority: 8 },

      { id: 'anthropics-pdf', company: 'anthropics', category: 'media', tier: 'free',
        name: 'PDF Processing', url: 'https://officialskills.sh/anthropics/skills/pdf',
        description: 'Извлечение текста, создание PDF, работа с формами',
        tags: ['documents', 'pdf', 'extraction'], priority: 9 },

      { id: 'slack-gif-creator', company: 'anthropics', category: 'media', tier: 'free',
        name: 'GIF Creator', url: 'https://officialskills.sh/anthropics/skills/slack-gif-creator',
        description: 'Создание анимированных GIF для Slack',
        tags: ['media', 'gif', 'animation'], priority: 7 },

      // 🔄 CI/CD
      { id: 'ship', company: 'garrytan', category: 'cicd', tier: 'free',
        name: 'Release Engineer', url: 'https://officialskills.sh/garrytan/skills/ship',
        description: 'Release Engineer: синк main, тесты, аудит, пуш, PR',
        tags: ['cicd', 'release', 'git'], priority: 9 },

      { id: 'cloudflare', company: 'cloudflare', category: 'cicd', tier: 'free',
        name: 'Cloudflare Platform', url: 'https://officialskills.sh/cloudflare/skills/cloudflare',
        description: 'Cloudflare: Workers, D1, R2, AI Gateway, Durable Objects',
        tags: ['cloud', 'serverless', 'cdn'], priority: 8 },

      // 🌟 Community
      { id: 'recursive-decomposition', company: 'massimodeluisa', category: 'community', tier: 'free',
        name: 'Recursive Decomposition', url: 'https://github.com/massimodeluisa/recursive-decomposition-skill',
        description: 'Обработка длинного контекста (100+ файлов, 50k+ токенов)',
        tags: ['context', 'large-files', 'rlm'], priority: 10 },

      { id: 'tweetclaw', company: 'Xquik-dev', category: 'community', tier: 'free',
        name: 'TweetClaw', url: 'https://github.com/Xquik-dev/tweetclaw',
        description: 'Постинг твитов, реплаи, DM, поиск, мониторинг, MCP',
        tags: ['twitter', 'social', 'mcp'], priority: 8 },

      { id: 'figma-mcp', company: 'openai', category: 'community', tier: 'free',
        name: 'Figma MCP', url: 'https://officialskills.sh/openai/skills/figma',
        description: 'Figma MCP: дизайн-контекст в production код',
        tags: ['design', 'figma', 'mcp'], priority: 8 },

      { id: 'flux-fal', company: 'fal-ai-community', category: 'community', tier: 'free',
        name: 'Flux Image Gen', url: 'https://github.com/fal-ai-community/flux',
        description: 'Генерация изображений через fal.ai (Flux, LoRA)',
        tags: ['image', 'generation', 'ai'], priority: 7 },
    ];

    console.log(`[SkillRegistry] Registered ${this.skills.length} skills`);
    return this.skills;
  },

  // ── Load a skill from URL ─────────────────────────────────
  async _loadSkill(url) {
    try {
      const resp = await fetch(url);
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const text = await resp.text();
      return { url, content: text, loaded: true };
    } catch (err) {
      console.warn(`[SkillRegistry] Failed to load ${url}:`, err.message);
      return { url, error: err.message, loaded: false };
    }
  },

  // ── Activate a skill ──────────────────────────────────────
  async activate(skillId) {
    const skill = this.skills.find(s => s.id === skillId);
    if (!skill) return { error: `Skill "${skillId}" not found` };

    if (this._loaded[skillId]) return this._loaded[skillId];

    const result = await this._loadSkill(skill.url);
    this._loaded[skillId] = { skill, ...result };
    return this._loaded[skillId];
  },

  // ── Get skills by category ────────────────────────────────
  getByCategory(category) {
    return this.skills.filter(s => s.category === category)
      .sort((a, b) => (b.priority || 0) - (a.priority || 0));
  },

  // ── Search skills ─────────────────────────────────────────
  search(query) {
    const q = query.toLowerCase();
    return this.skills.filter(s =>
      s.name.toLowerCase().includes(q) ||
      s.description.toLowerCase().includes(q) ||
      s.tags.some(t => t.includes(q))
    ).sort((a, b) => (b.priority || 0) - (a.priority || 0));
  },

  // ── Get categories ────────────────────────────────────────
  getCategories() {
    const cats = new Set(this.skills.map(s => s.category));
    return this._categoryOrder.filter(c => cats.has(c));
  },

  // ── Get skill by ID ───────────────────────────────────────
  get(id) {
    return this.skills.find(s => s.id === id);
  },

  // ── Get stats ─────────────────────────────────────────────
  getStats() {
    const byCategory = {};
    this.getCategories().forEach(cat => {
      byCategory[cat] = this.getByCategory(cat).length;
    });
    return {
      total: this.skills.length,
      loaded: Object.keys(this._loaded).length,
      categories: this.getCategories().length,
      byCategory,
    };
  },

  // ── Export for plugin system ──────────────────────────────
  getPluginDefinitions() {
    return this.skills.map(s => ({
      id: s.id,
      name: s.name,
      description: s.description,
      version: '1.0.0',
      company: s.company,
      tier: s.tier,
      url: s.url,
      hooks: ['chat:commands', 'sidebar:items'],
    }));
  },
};

// Auto-register
SkillRegistry.registerAll();

// Export globally
window.SkillRegistry = SkillRegistry;
console.log('[SkillRegistry] Ready —', SkillRegistry.skills.length, 'skills available');
