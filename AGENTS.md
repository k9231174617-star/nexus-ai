# Nexus AI — Agent Skills

This file tells the Codex CLI agent what skills are available in this project.
Skills come from the [awesome-agent-skills](https://github.com/VoltAgent/awesome-agent-skills) catalog.

## Available Skills

### 🔌 MCP & Tool Integration
Use these when connecting external APIs, building MCP servers, or integrating tools:
- **mcp-builder** (`anthropics/mcp-builder`) — Create MCP servers to integrate external APIs
- **composio** (`composiohq/composio`) — 1000+ app integrations with managed auth
- **agents-sdk** (`cloudflare/agents-sdk`) — Stateful AI agents with scheduling, RPC, MCP
- **openai-docs** (`openai/openai-docs`) — Authoritative OpenAI API documentation

### 🧠 LLM / AI / RAG
Use when working with LLMs, embeddings, vector search, or RAG:
- **gemini-interactions-api** (`google-gemini/gemini-interactions-api`) — Gemini API for text, chat, streaming, images
- **transformers** (`huggingface/transformers`) — 🤗 Transformers for NLP, CV, audio
- **diffusion** (`huggingface/diffusion`) — 🧨 Diffusers for image generation
- **qdrant-skills** (`qdrant/skills`) — Vector search Qdrant: scaling, optimization, monitoring
- **rag** (`NVIDIA/rag`) — Retrieval-Augmented Generation pipeline
- **hub** (`huggingface/hub`) — Hugging Face Hub: models, datasets, Spaces
- **copilot-sdk** (`microsoft/copilot-sdk`) — GitHub Copilot SDK apps
- **mcp-builder** (`microsoft/mcp-builder`) — MCP server creation for LLM tools

### 🔒 Security & Code Audit
Use for vulnerability scanning, code review, auth, and crypto:
- **static-analysis** (`trailofbits/static-analysis`) — Static security analysis
- **supply-chain** (`trailofbits/supply-chain`) — Supply chain and dependency analysis
- **crypto-review** (`trailofbits/crypto-review`) — Cryptographic audit
- **fuzzing** (`trailofbits/fuzzing`) — Fuzz testing for vulnerabilities
- **taint** (`trailofbits/taint`) — Data-flow tracking for vulnerability discovery
- **create-auth** (`better-auth/create-auth`) — Authentication setup

### 📱 Android & Mobile
Use for Android development, APK analysis, mobile testing:
- **espresso-skill** (`testmu-ai/espresso-skill`) — Espresso UI tests for Android (Kotlin/Java)
- **appium-skill** (`testmu-ai/appium-skill`) — Appium mobile automation (Android/iOS)
- **react-native-best-practices** (`callstackincubator/react-native-best-practices`) — React Native performance
- **expo-sdk** (`expo/expo-sdk`) — Expo SDK: camera, location, notifications
- **expo-router** (`expo/router`) — Expo Router file-based navigation
- **expo-notifications** (`expo/notifications`) — Push notifications with Expo

### 🌐 Browser Automation
Use for web scraping, Playwright, browser testing:
- **webapp-testing** (`anthropics/webapp-testing`) — Web app testing with Playwright
- **stagehand** (`browserbase/stagehand`) — AI-driven browser automation framework
- **playwright-skill** (`testmu-ai/playwright-skill`) — Playwright E2E tests
- **cypress-skill** (`testmu-ai/cypress-skill`) — Cypress E2E and component tests
- **puppeteer-skill** (`testmu-ai/puppeteer-skill`) — Puppeteer browser automation
- **brave-images-search** (`brave/images-search`) — Image search via Brave API
- **brave-local** (`brave/local`) — Local AI search with Brave

### 🗄️ Databases & Storage
Use for data modeling, queries, caching, and storage:
- **mongodb-schema-design** (`mongodb/mongodb-schema-design`) — Document schema design with validation
- **mongodb-aggregations** (`mongodb/mongodb-aggregations`) — MongoDB aggregation pipeline
- **redis-development** (`redis/redis-development`) — Redis: data structures, vector search, caching
- **firebase-data-connect** (`firebase/firebase-data-connect-basics`) — Firebase Data Connect + Cloud SQL
- **firebase-security-rules** (`firebase/firebase-security-rules`) — Security Rules for Firestore/Storage
- **chdb-sql** (`clickhouse/chdb-sql`) — ClickHouse SQL engine for Python

### 🧪 Testing
Use for writing and generating tests:
- **api-skill** (`testmu-ai/api-skill`) — API design, mocking, testing (REST/GraphQL/gRPC)
- **jest-skill** (`testmu-ai/jest-skill`) — Jest unit/integration tests with mocks
- **junit-5-skill** (`testmu-ai/junit-5-skill`) — JUnit 5 tests with Mockito
- **cicd-pipeline-skill** (`testmu-ai/cicd-pipeline-skill`) — CI/CD pipelines (GitHub Actions, Jenkins)
- **espresso-skill** (`testmu-ai/espresso-skill`) — Espresso UI tests (Android)

### 📊 Observability & Monitoring
Use for tracing, metrics, logging, and performance:
- **sentry-feature-setup** (`getsentry/sentry-feature-setup`) — Sentry: AI monitoring, OTel, alerts
- **sentry-performance** (`getsentry/performance`) — Sentry Performance tracing
- **sentry-releases** (`getsentry/releases`) — Sentry Releases: error tracking by version
- **sentry-session-replay** (`getsentry/session-replay`) — Session Replay for debugging
- **dd-docs** (`datadog-labs/dd-docs`) — Datadog LLM-optimized docs

### 🎬 Media & Documents
Use for document processing, image generation, video:
- **docx** (`anthropics/docx`) — Create, edit, analyze Word documents
- **pptx** (`anthropics/pptx`) — Create, edit, analyze PowerPoint presentations
- **xlsx** (`anthropics/xlsx`) — Create, edit, analyze Excel spreadsheets
- **pdf** (`anthropics/pdf`) — Extract text, create PDFs, handle forms
- **slack-gif-creator** (`anthropics/slack-gif-creator`) — Create animated GIFs for Slack
- **video-search-and-summarization** (`NVIDIA/video-search-and-summarization`) — Video analytics and search

### 🔄 CI/CD & DevOps
Use for build pipelines, deployment, infrastructure:
- **cicd-pipeline-skill** (`testmu-ai/cicd-pipeline-skill`) — CI/CD pipeline generation
- **ship** (`garrytan/ship`) — Release engineering: sync, test, audit, push, PR
- **terraform-test** (`hashicorp/terraform-test`) — Terraform testing framework
- **netlify-functions** (`netlify/netlify-functions`) — Serverless API endpoints
- **cloudflare** (`cloudflare/cloudflare`) — Cloudflare platform: Workers, D1, R2, AI

### 🌟 Community & Productivity
- **recursive-decomposition-skill** (`massimodeluisa/recursive-decomposition-skill`) — Handle 100+ files / 50k+ token context
- **tweetclaw** (`Xquik-dev/tweetclaw`) — Twitter/X: posts, replies, DMs, search
- **x-twitter-scraper** (`Xquik-dev/x-twitter-scraper`) — Tweet search, profiles, media, MCP
- **figma** (`openai/figma`) — Figma MCP for design-to-code
- **flux** (`fal-ai-community/flux`) — Image generation via fal.ai (Flux, LoRA)
- **notion-research-documentation** (`openai/notion-research-documentation`) — Notion research to structured briefs

## How to Use

When the user asks to do something, check if there's a relevant skill above. Skills are fetched from `https://officialskills.sh/{company}/skills/{name}` — load the SKILL.md from that URL and follow its instructions.

Example: `https://officialskills.sh/anthropics/skills/mcp-builder`

## Project Context

- **Package:** `com.nexus.agent`
- **Android app** with web dashboard (`docs/`)
- **Kotlin + Hilt DI + Room DB + JNI (llama.cpp)**
- **Plugin system** in `docs/js/plugins/` with `PluginManager`
- **MCP Client** in `core/mcp/MCPClient.kt`
- **Agent Coordinator** in `core/agents/AgentCoordinator.kt`
