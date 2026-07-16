# NEXUS AI — Architecture & Structure v2.0

## Структура проекта

```
nexus-ai-dashboard/
├── index.html                    ← App shell, все tab-панели
│
├── styles/
│   ├── main.css                  ← Базовые стили, палитра, layout
│   ├── components.css            ← Компоненты: Code, CLI, Files, Settings
│   └── animations.css            ← Keyframes, motion design
│
└── js/
    ├── app.js                    ← Ядро: навигация, sidebar, tabs, toast, файлы
    ├── chat.js                   ← Chat Engine: Main / Code / Universal Agent
    ├── cli.js                    ← CLI Terminal: команды, root, history
    ├── files.js                  ← File Manager: breadcrumb, sort
    └── settings.js               ← Settings: API key, model select, sliders, voice
```

---

## Обновлённая архитектура

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                             │
│                                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │ Main Agent  │  │ Code Agent  │  │ Uni Agent   │  │ CLI Term    │    │
│  │  Chat+Stats │  │ Editor+Tree │  │ Media+Chat  │  │  Root+Cmds  │    │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘    │
│  ┌─────────────┐  ┌─────────────┐                                        │
│  │ File Manager│  │  Settings   │                                        │
│  │  Grid+Preview│  │ API+Models  │                                        │
│  └──────┬──────┘  └──────┬──────┘                                        │
│         └──────────────────────────────────────────────────────┐         │
│                       Sidebar Drawer + Topbar                  │         │
└────────────────────────────────────────────────────────────────┼─────────┘
                                                                 │
┌────────────────────────────────────────────────────────────────┼─────────┐
│                          CORE LAYER (JS)                       │         │
│                                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  AppState   │  │  ChatEngine │  │  CLIEngine  │  │  Settings   │    │
│  │  (Global)   │  │  (3 agents) │  │  (30+ cmds) │  │  (Store)    │    │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘    │
│         │                │                │                  │           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │MemoryTracker│  │MarkdownHTML │  │  RootBridge  │  │  VoiceInput │    │
│  │(Timeline)   │  │ (Renderer)  │  │  (SU Sim)   │  │  (WebSpeech)│    │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────┬────────────────────────────────────────┘
                                  │
┌─────────────────────────────────┼────────────────────────────────────────┐
│                         LLM BRIDGE LAYER                                 │
│                                                                          │
│  ┌──────────────────────────┐   ┌──────────────────────────────────┐    │
│  │    FREE MODE (Demo)      │   │       CUSTOM API MODE            │    │
│  │  Pre-generated responses │   │  Any OpenAI-compatible endpoint  │    │
│  │  No API key required     │   │  Supports: OpenAI, Claude,       │    │
│  │  Instant responses       │   │  Gemini, local LLMs (Ollama)     │    │
│  └──────────────────────────┘   └──────────────────────────────────┘    │
│                                                                          │
│  Per-agent model routing:                                                │
│  ┌─────────────────┐  ┌──────────────────┐  ┌─────────────────────┐    │
│  │   Main Agent    │  │   Code Agent     │  │  Universal Agent    │    │
│  │ dolphin-2.6 /   │  │ deepseek-coder / │  │  nous-hermes /      │    │
│  │ GPT-4o / Claude │  │ GPT-4o / Claude  │  │  Gemini Vision      │    │
│  └─────────────────┘  └──────────────────┘  └─────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Новые фичи v2.0 (vs оригинал)

### UI/UX
- **Dark + Neon Red** — тёмный фон `#080810`, неоновый красный `#FF0A2F`
- **SF Pro font** — `-apple-system, "SF Pro Display"` стек
- **SVG иконки** — все иконки нативные SVG, без внешних зависимостей
- **Stats Row** — 4 карточки: Messages, Session Time, Memory, Latency
- **Context Bar** — быстрое переключение режима (Auto/Code/File/Shell/APK)
- **Ambient background** — CSS grid + radial glow эффекты

### Chat Engine
- **3 независимых агента** — каждый со своей историей и system prompt
- **Context Injection** — вброс контекста в следующий запрос из Memory вкладки
- **Markdown renderer** — код блоки, bold, italic, заголовки, списки
- **Typing indicator** — анимированные точки
- **Token counter** — оценка использованных токенов
- **API latency** — отображение времени ответа

### CLI Terminal
- **30+ команд** — ls, ps, df, top, grep, find, ping, netstat и др.
- **Root simulation** — `su`/`exit`, изменение prompt, badge
- **Tab autocomplete** — по файловой системе
- **Command history** — ↑↓ навигация, `history` команда
- **Quick commands** — кнопки быстрого доступа
- **Ctrl+C** — прерывание команды

### Code Agent
- **File Tree** — браузер проекта с папками/файлами
- **Syntax highlighting** — Kotlin keywords, classes, functions
- **Multi-tab editor** — вкладки файлов с закрытием
- **Inline Code AI drawer** — боковой чат с контекстом текущего файла
- **APK Workspace** — кнопка открытия APK инструментов

### Settings
- **Per-agent model select** — отдельная модель для каждого агента
- **Custom endpoint** — любой OpenAI-compatible API
- **Range sliders** — Max Tokens (1K–32K), Temperature (0–1.0)
- **Toggle switches** — Stream, Auto-CLI, Root Mode, Save History
- **API key vault** — хранение в localStorage с show/hide
- **System info** — версия, DB size, cache size

### Voice Input
- **Web Speech API** — распознавание на русском (`ru-RU`)
- **Visual feedback** — иконка подсвечивается при записи

### Memory / Session
- **Memory Timeline** — все события сессии с иконками и временем
- **Context Injection** — ручной вброс контекста
- **Session timer** — счётчик времени сессии в реальном времени

---

## Дизайн-токены

| Token | Value | Использование |
|---|---|---|
| `--bg-deep` | `#080810` | Основной фон |
| `--bg-base` | `#0A0A12` | Контент зоны |
| `--bg-card` | `#111120` | Карточки |
| `--bg-sidebar` | `#09090F` | Sidebar, topbar |
| `--red-core` | `#FF0A2F` | Акцент, активные |
| `--red-bright` | `#FF3355` | Hover состояния |
| `--red-dim` | `#CC0A26` | Кнопки |
| `--red-glow` | `rgba(255,10,47,0.25)` | Box-shadow |
| `--text-primary` | `#F0F0FF` | Основной текст |
| `--text-secondary` | `#8888AA` | Вторичный текст |
| `--text-muted` | `#444466` | Заглушенный |
| `--border-subtle` | `rgba(255,10,47,0.12)` | Тихие границы |
| `--border-mid` | `rgba(255,10,47,0.25)` | Видимые границы |
| `--font-sf` | `-apple-system, "SF Pro Display"` | Основной шрифт |
| `--font-mono` | `"SF Mono", "Fira Code"` | Код, CLI |
