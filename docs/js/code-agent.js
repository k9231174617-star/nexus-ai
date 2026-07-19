/* ============================================================
   NEXUS AI — Code Agent Engine (jcode-inspired)
   Multi-panel workspace: Editor, Terminal, Memory, Diff,
   Review, Skills, Mermaid, Swarm, Browser
   ============================================================ */
'use strict';

const CodeAgent = {
  state: {
    files: {},
    openTabs: [],
    activeFile: null,
    activePanel: 'editor',
    activeBottomTab: 'terminal',
    bottomCollapsed: false,
    terminalHistory: [],
    terminalHistoryIdx: -1,
    memoryEntries: [],
    diffOld: '',
    diffNew: '',
    sessions: [],
    activeSession: 0,
    reviewResults: null,
  },

  // ── File content database ──────────────────────────────
  fileContents: {
    'MainActivity.kt': `package com.nexus.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NexusApp()
            }
        }
    }
}`,
    'ChatViewModel.kt': `package com.nexus.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val userMessage = Message(text, isUser = true)
            _messages.value = _messages.value + userMessage

            val response = callLLMAPI(text)
            val aiMessage = Message(response, isUser = false)
            _messages.value = _messages.value + aiMessage
        }
    }
}`,
    'activity_main.xml': `<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/navHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>`,
    'build.gradle.kts': `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.nexus.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nexus.agent"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"
    }
}`,
    'settings.gradle.kts': `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NexusAI"
include(":app")`
  },

  // ── Init ───────────────────────────────────────────────
  init() {
    this.loadFileContents();
    this.initToolbar();
    this.initFileTree();
    this.initEditor();
    this.initTerminal();
    this.initChat();
    this.initQuickActions();
    this.initBottomTabs();
    this.initOverlay();
    this.initMemory();
    this.initSwarm();
    console.log('[CodeAgent] Initialized');
  },

  loadFileContents() {
    Object.entries(this.fileContents).forEach(([name, content]) => {
      this.state.files[name] = content;
    });
  },

  // ── Toolbar ────────────────────────────────────────────
  initToolbar() {
    document.querySelectorAll('.code-tool-btn[data-panel]').forEach(btn => {
      btn.addEventListener('click', () => {
        const panel = btn.dataset.panel;
        this.switchToolPanel(panel);
      });
    });

    document.querySelectorAll('.code-tool-btn[data-action]').forEach(btn => {
      btn.addEventListener('click', () => {
        const action = btn.dataset.action;
        if (action === 'codeReviewAll') this.runFullReview();
      });
    });
  },

  switchToolPanel(panel) {
    document.querySelectorAll('.code-tool-btn[data-panel]').forEach(b => b.classList.remove('active'));
    const btn = document.querySelector(`.code-tool-btn[data-panel="${panel}"]`);
    if (btn) btn.classList.add('active');

    this.state.activePanel = panel;

    switch (panel) {
      case 'editor':
        this.showPanel('editor');
        break;
      case 'terminal':
        this.showPanel('editor');
        this.state.bottomCollapsed = false;
        document.getElementById('codeBottomPanel')?.classList.remove('collapsed');
        this.switchBottomTab('terminal');
        break;
      case 'memory':
        this.showPanel('memory');
        break;
      case 'diff':
        this.showDiffViewer();
        break;
      case 'review':
        this.showPanel('editor');
        this.switchBottomTab('review-output');
        break;
      case 'skills':
        this.showSkillHub();
        break;
      case 'mermaid':
        this.showMermaidDiagram();
        break;
      case 'swarm':
        this.showSwarmPanel();
        break;
    }
  },

  showPanel(panel) {
    const left = document.getElementById('codeLeftPanel');
    const ftSection = document.getElementById('fileTreeSection');
    const memSection = document.getElementById('memorySection');

    if (panel === 'memory') {
      left.style.display = 'flex';
      ftSection.style.display = 'none';
      memSection.classList.remove('hidden');
    } else {
      left.style.display = 'flex';
      ftSection.style.display = 'flex';
      memSection.classList.add('hidden');
    }
  },

  // ── File Tree ──────────────────────────────────────────
  initFileTree() {
    document.querySelectorAll('.tree-item.file').forEach(item => {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        const path = item.dataset.path;
        const name = path.split('/').pop();
        this.openFile(name, path);
        document.querySelectorAll('.tree-item').forEach(i => i.classList.remove('active'));
        item.classList.add('active');
      });
    });

    document.querySelectorAll('.tree-item.folder').forEach(item => {
      item.addEventListener('click', () => {
        item.classList.toggle('open');
      });
    });

    document.getElementById('newFileBtn')?.addEventListener('click', () => {
      const name = prompt('Имя нового файла:');
      if (!name) return;
      this.fileContents[name] = `// ${name}\n`;
      this.state.files[name] = `// ${name}\n`;
      this.openFile(name, name);
    });
  },

  openFile(name, path) {
    if (!this.state.openTabs.includes(name)) {
      this.state.openTabs.push(name);
      this.renderTabs();
    }
    this.state.activeFile = name;
    this.renderEditorContent();
    this.updateStatusBar();
  },

  renderTabs() {
    const container = document.getElementById('editorTabs');
    if (!container) return;
    container.innerHTML = '';
    this.state.openTabs.forEach(name => {
      const tab = document.createElement('div');
      tab.className = 'editor-tab' + (name === this.state.activeFile ? ' active' : '');
      tab.dataset.file = name;
      tab.innerHTML = `${name} <span class="tab-close">×</span>`;
      tab.addEventListener('click', (e) => {
        if (e.target.classList.contains('tab-close')) {
          this.closeTab(name);
        } else {
          this.openFile(name, name);
        }
      });
      container.appendChild(tab);
    });
    const addBtn = document.createElement('button');
    addBtn.className = 'editor-tab new-tab';
    addBtn.textContent = '+';
    addBtn.addEventListener('click', () => {
      const name = prompt('Имя файла:');
      if (name) { this.fileContents[name] = ''; this.openFile(name, name); }
    });
    container.appendChild(addBtn);
  },

  closeTab(name) {
    this.state.openTabs = this.state.openTabs.filter(t => t !== name);
    if (this.state.activeFile === name) {
      this.state.activeFile = this.state.openTabs[this.state.openTabs.length - 1] || null;
    }
    this.renderTabs();
    this.renderEditorContent();
  },

  renderEditorContent() {
    const editor = document.getElementById('codeEditor');
    if (!editor) return;
    const name = this.state.activeFile;
    if (!name || !this.state.files[name]) {
      editor.innerHTML = '<span class="cm">// Выберите файл в дереве</span>';
      return;
    }
    editor.textContent = this.state.files[name];
    this.highlightSyntax(editor);
  },

  highlightSyntax(el) {
    let text = el.textContent;
    // Basic Kotlin syntax highlighting
    text = text
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/(\/\/.*)/gm, '<span class="cm">$1</span>')
      .replace(/\b(package|import|class|fun|override|val|var|if|else|when|return|object|interface|data|sealed|abstract|private|public|internal|protected|suspend|init|super|this|null)\b/g, '<span class="kw">$1</span>')
      .replace(/"([^"]*)"/g, '<span class="str">"$1"</span>')
      .replace(/\b(\d+\.?\d*)\b/g, '<span class="num">$1</span>')
      .replace(/@(\w+)/g, '<span class="ann">@$1</span>');
    el.innerHTML = text;
  },

  initEditor() {
    const editor = document.getElementById('codeEditor');
    if (!editor) return;

    editor.addEventListener('input', () => {
      if (this.state.activeFile) {
        this.state.files[this.state.activeFile] = editor.textContent;
      }
      this.updateStatusBar();
    });

    editor.addEventListener('keydown', (e) => {
      if (e.key === 'Tab') {
        e.preventDefault();
        document.execCommand('insertText', false, '    ');
      }
    });

    this.renderEditorContent();
  },

  updateStatusBar() {
    const name = this.state.activeFile;
    const content = name ? (this.state.files[name] || '') : '';
    const lines = content.split('\n').length;
    const tokens = Math.ceil(content.length / 4);

    const langEl = document.getElementById('editorLang');
    const linesEl = document.getElementById('editorLines');
    const tokensEl = document.getElementById('editorTokens');

    if (langEl) {
      const ext = name ? name.split('.').pop() : '';
      const langMap = { kt: 'Kotlin', java: 'Java', xml: 'XML', kts: 'Kotlin Script', py: 'Python', js: 'JavaScript', ts: 'TypeScript' };
      langEl.textContent = langMap[ext] || ext.toUpperCase();
    }
    if (linesEl) linesEl.textContent = `Lines: ${lines}`;
    if (tokensEl) tokensEl.textContent = `~${tokens} tokens`;
  },

  // ── Terminal ───────────────────────────────────────────
  initTerminal() {
    const input = document.getElementById('codeTerminalInput');
    const sendBtn = document.getElementById('codeTerminalSend');
    if (input) {
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') this.execTerminalCommand();
        if (e.key === 'ArrowUp') this.navigateTerminalHistory('up');
        if (e.key === 'ArrowDown') this.navigateTerminalHistory('down');
      });
    }
    sendBtn?.addEventListener('click', () => this.execTerminalCommand());
  },

  execTerminalCommand() {
    const input = document.getElementById('codeTerminalInput');
    const terminal = document.getElementById('codeTerminal');
    if (!input || !terminal) return;

    const cmd = input.value.trim();
    if (!cmd) return;
    input.value = '';

    this.state.terminalHistory.unshift(cmd);
    this.state.terminalHistoryIdx = -1;

    this.printTermLine(`$ ${cmd}`, 'prompt');

    const output = this.simulateCommand(cmd);
    if (output) this.printTermLine(output, 'output');

    terminal.scrollTop = terminal.scrollHeight;
  },

  simulateCommand(cmd) {
    const parts = cmd.split(' ');
    const base = parts[0].toLowerCase();
    const commands = {
      'help': 'Available: ls, cat, grep, pwd, whoami, git, gradle, clear, echo',
      'pwd': '/data/data/com.nexus.agent',
      'whoami': 'nexus',
      'ls': 'src/  res/  gradle/  build.gradle.kts  settings.gradle.kts',
      'clear': () => { document.getElementById('codeTerminal').querySelectorAll('.terminal-line').forEach(l => l.remove()); return ''; },
      'git status': 'On branch main\nYour branch is up to date.\n\nChanges:\n  modified:   src/main.kt',
      'git log --oneline -5': 'a3f2c1d feat: add chat\nb8e4a2c fix: memory leak\nc1d3f5e init: project setup',
      'gradle build': 'BUILD SUCCESSFUL in 12s\n12 tasks executed',
      'gradle test': 'BUILD SUCCESSFUL\nAll 24 tests passed',
    };

    if (base === 'echo') return parts.slice(1).join(' ');
    if (base === 'cat' && parts[1]) {
      const name = parts[1];
      return this.state.files[name] || `cat: ${name}: No such file`;
    }
    if (base === 'grep') {
      const query = parts[1] || '';
      let results = '';
      Object.entries(this.state.files).forEach(([name, content]) => {
        content.split('\n').forEach((line, i) => {
          if (line.toLowerCase().includes(query.toLowerCase())) {
            results += `${name}:${i + 1}: ${line.trim()}\n`;
          }
        });
      });
      return results || `grep: no matches for "${query}"`;
    }

    const handler = commands[cmd] || commands[base];
    if (typeof handler === 'function') return handler();
    if (handler) return handler;
    return `bash: ${base}: command not found`;
  },

  printTermLine(text, type) {
    const terminal = document.getElementById('codeTerminal');
    if (!terminal) return;
    const line = document.createElement('div');
    line.className = 'terminal-line';
    if (type === 'prompt') {
      line.innerHTML = `<span class="t-prompt">$ </span><span class="t-text">${this.escapeHtml(text)}</span>`;
    } else {
      const cls = text.includes('Error') || text.includes('error') || text.includes('not found') ? 't-error' :
                  text.includes('SUCCESS') || text.includes('passed') ? 't-success' : 't-output';
      line.innerHTML = `<span class="${cls}">${this.escapeHtml(text)}</span>`;
    }
    terminal.appendChild(line);
  },

  navigateTerminalHistory(dir) {
    const input = document.getElementById('codeTerminalInput');
    if (!input) return;
    const hist = this.state.terminalHistory;
    if (dir === 'up' && this.state.terminalHistoryIdx < hist.length - 1) {
      this.state.terminalHistoryIdx++;
      input.value = hist[this.state.terminalHistoryIdx];
    } else if (dir === 'down') {
      this.state.terminalHistoryIdx = Math.max(-1, this.state.terminalHistoryIdx - 1);
      input.value = this.state.terminalHistoryIdx >= 0 ? hist[this.state.terminalHistoryIdx] : '';
    }
  },

  // ── AI Chat ────────────────────────────────────────────
  initChat() {
    const input = document.getElementById('codeInput');
    const sendBtn = document.getElementById('codeSendBtn');
    input?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this.sendCodeChat(); }
    });
    sendBtn?.addEventListener('click', () => this.sendCodeChat());
  },

  async sendCodeChat() {
    const input = document.getElementById('codeInput');
    const container = document.getElementById('codeChatMessages');
    if (!input || !container) return;
    const text = input.value.trim();
    if (!text) return;
    input.value = '';

    this.appendCodeMsg(container, 'user', this.escapeHtml(text));

    const fileContext = this.state.activeFile ?
      `Current file: ${this.state.activeFile}\n\`\`\`\n${(this.state.files[this.state.activeFile] || '').slice(0, 2000)}\n\`\`\`` : '';

    const settings = (typeof loadSettings === 'function') ? loadSettings() : {};
    const apiKey = settings.apiKey || '';

    if (!apiKey || apiKey.length < 10) {
      this.appendCodeMsg(container, 'assistant', '⚠️ API ключ не настроен. Перейдите в Settings для настройки.');
      return;
    }

    const typingEl = this.showCodeTyping(container);
    const t0 = Date.now();

    try {
      const systemPrompt = 'You are NEXUS Code Agent — specialized in Kotlin, Android, and code analysis. Be concise. Use markdown for code blocks. Answer in the language the user writes in.';
      const messages = [
        { role: 'system', content: systemPrompt + '\n\n' + fileContext },
        { role: 'user', content: text }
      ];

      const res = await fetch(settings.endpoint || 'https://openrouter.ai/api/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + apiKey,
          'HTTP-Referer': window.location.origin,
          'X-Title': 'Nexus Code Agent',
        },
        body: JSON.stringify({
          model: settings.codeModel || 'deepseek/deepseek-coder',
          messages: messages,
          temperature: 0.7,
          max_tokens: 2000,
        })
      });

      if (!res.ok) throw new Error(`API ${res.status}`);
      const data = await res.json();
      const reply = data.choices?.[0]?.message?.content || 'No response';

      if (typingEl) typingEl.remove();
      this.appendCodeMsg(container, 'assistant', this.markdownToHtml(reply));

      // Add to memory
      this.addMemoryEntry('code', text.slice(0, 100));
      this.addMemoryEntry('response', reply.slice(0, 100));

    } catch (err) {
      if (typingEl) typingEl.remove();
      this.appendCodeMsg(container, 'assistant', `<span style="color:#FF4466">⚠️ ${err.message}</span>`);
    }
  },

  appendCodeMsg(container, role, html) {
    const div = document.createElement('div');
    div.className = `message ${role === 'user' ? 'user-msg' : 'assistant-msg'} mini`;
    if (role === 'user') {
      div.innerHTML = `<div class="msg-content"><div class="msg-text user">${html}</div></div>`;
    } else {
      div.innerHTML = `<div class="msg-avatar-mini"><svg width="24" height="24" viewBox="0 0 28 28" fill="none"><circle cx="14" cy="14" r="13" fill="#1A0A0F" stroke="#FF0A2F" stroke-width="1"/><circle cx="14" cy="14" r="5" fill="#FF0A2F" opacity="0.8"/><path d="M14 6 L16 10 L14 9 L12 10 Z" fill="#FF0A2F"/></svg></div><div class="msg-content"><div class="msg-text">${html}</div></div>`;
    }
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
  },

  showCodeTyping(container) {
    const div = document.createElement('div');
    div.className = 'message assistant-msg mini typing-msg';
    div.innerHTML = '<div class="msg-avatar-mini"><svg width="24" height="24" viewBox="0 0 28 28" fill="none"><circle cx="14" cy="14" r="13" fill="#1A0A0F" stroke="#FF0A2F" stroke-width="1"/><circle cx="14" cy="14" r="5" fill="#FF0A2F" opacity="0.5"/></svg></div><div class="msg-content"><div class="typing-indicator"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div>';
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
    return div;
  },

  // ── Quick Actions ──────────────────────────────────────
  initQuickActions() {
    document.querySelectorAll('.quick-action').forEach(btn => {
      btn.addEventListener('click', () => {
        const prompt = btn.dataset.prompt;
        const input = document.getElementById('codeInput');
        if (input) { input.value = prompt; this.sendCodeChat(); }
      });
    });
  },

  // ── Bottom Tabs ────────────────────────────────────────
  initBottomTabs() {
    document.querySelectorAll('.bottom-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        this.switchBottomTab(tab.dataset.btab);
      });
    });
    document.getElementById('toggleBottomPanel')?.addEventListener('click', () => {
      const panel = document.getElementById('codeBottomPanel');
      if (panel) {
        this.state.bottomCollapsed = !this.state.bottomCollapsed;
        panel.classList.toggle('collapsed');
        document.getElementById('toggleBottomPanel').textContent = this.state.bottomCollapsed ? '▴' : '▾';
      }
    });
  },

  switchBottomTab(tab) {
    document.querySelectorAll('.bottom-tab').forEach(t => t.classList.remove('active'));
    document.querySelector(`.bottom-tab[data-btab="${tab}"]`)?.classList.add('active');
    document.querySelectorAll('.bottom-pane').forEach(p => p.classList.remove('active'));
    document.getElementById(`pane-${tab}`)?.classList.add('active');
    this.state.activeBottomTab = tab;
  },

  // ── Diff Viewer ────────────────────────────────────────
  showDiffViewer() {
    this.showPanel('editor');
    const overlay = document.getElementById('codeOverlay');
    const title = document.getElementById('overlayTitle');
    const content = document.getElementById('overlayContent');
    if (!overlay || !content) return;

    title.textContent = '📊 Diff Viewer';
    const name = this.state.activeFile || 'MainActivity.kt';
    const original = this.state.files[name] || '';
    const modified = original + '\n// Modified by Code Agent';

    content.innerHTML = `
      <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
        <span style="color:var(--red-core);font-weight:600;font-size:13px">Diff: ${name}</span>
        <span style="font-size:11px;color:var(--text-muted)">Old vs New</span>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
        <div>
          <div style="font-size:11px;color:var(--text-muted);margin-bottom:6px;font-weight:600">ORIGINAL</div>
          <pre style="background:#1a0a0f;border:1px solid var(--border-subtle);border-radius:8px;padding:12px;font-size:12px;font-family:var(--font-mono);overflow:auto;max-height:400px;color:#AAAACC">${this.escapeHtml(original)}</pre>
        </div>
        <div>
          <div style="font-size:11px;color:var(--text-muted);margin-bottom:6px;font-weight:600">MODIFIED</div>
          <pre style="background:#0a1a0f;border:1px solid rgba(68,221,136,0.3);border-radius:8px;padding:12px;font-size:12px;font-family:var(--font-mono);overflow:auto;max-height:400px;color:#AAAACC">${this.escapeHtml(modified)}</pre>
        </div>
      </div>`;
    overlay.classList.remove('hidden');
  },

  // ── Code Review ────────────────────────────────────────
  async runFullReview() {
    const container = document.getElementById('codeChatMessages');
    const reviewPane = document.getElementById('pane-review-output');
    if (!container) return;

    this.switchBottomTab('review-output');
    this.appendCodeMsg(container, 'user', '🔍 Запускаю полный code review всех файлов проекта...');

    const settings = (typeof loadSettings === 'function') ? loadSettings() : {};
    const apiKey = settings.apiKey || '';

    if (!apiKey || apiKey.length < 10) {
      this.appendCodeMsg(container, 'assistant', '⚠️ API ключ не настроен.');
      return;
    }

    const typingEl = this.showCodeTyping(container);

    try {
      let allCode = '';
      Object.entries(this.state.files).forEach(([name, content]) => {
        allCode += `\n\n=== ${name} ===\n${content}`;
      });

      const res = await fetch(settings.endpoint || 'https://openrouter.ai/api/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + apiKey,
          'HTTP-Referer': window.location.origin,
          'X-Title': 'Nexus Code Review',
        },
        body: JSON.stringify({
          model: settings.codeModel || 'deepseek/deepseek-coder',
          messages: [
            { role: 'system', content: 'You are a senior code reviewer. Analyze the code for: bugs, security issues, performance problems, code smells, and best practices violations. Be specific with line numbers. Answer in Russian.' },
            { role: 'user', content: `Проведи полный code review:\n${allCode}` }
          ],
          temperature: 0.3,
          max_tokens: 3000,
        })
      });

      const data = await res.json();
      const review = data.choices?.[0]?.message?.content || 'No review generated';

      if (typingEl) typingEl.remove();
      this.appendCodeMsg(container, 'assistant', this.markdownToHtml(review));

      if (reviewPane) {
        reviewPane.innerHTML = `<div style="padding:12px;font-size:12px;line-height:1.6">${this.markdownToHtml(review)}</div>`;
      }

      this.addMemoryEntry('review', review.slice(0, 200));
    } catch (err) {
      if (typingEl) typingEl.remove();
      this.appendCodeMsg(container, 'assistant', `<span style="color:#FF4466">⚠️ Review error: ${err.message}</span>`);
    }
  },

  // ── Skill Hub ──────────────────────────────────────────
  showSkillHub() {
    this.showPanel('editor');
    const overlay = document.getElementById('codeOverlay');
    const title = document.getElementById('overlayTitle');
    const content = document.getElementById('overlayContent');
    if (!overlay || !content) return;

    title.textContent = '🧰 Skill Hub — AI Agent Skills';
    const skills = [
      { cat: 'MCP & Tools', items: ['MCP Builder', 'Composio', 'Cloudflare Agents SDK', 'OpenAI Docs'] },
      { cat: 'LLM / AI / RAG', items: ['Gemini API', 'HF Transformers', 'HF Diffusers', 'Qdrant Vector'] },
      { cat: 'Security', items: ['Static Analysis', 'Supply Chain Audit'] },
      { cat: 'Android', items: ['Espresso Testing', 'Appium Automation'] },
      { cat: 'Browser', items: ['Stagehand', 'Playwright', 'WebApp Testing'] },
      { cat: 'Database', items: ['MongoDB Schema', 'Redis Dev'] },
      { cat: 'Testing', items: ['API Testing', 'Jest'] },
      { cat: 'Observability', items: ['Sentry Features', 'Sentry Performance'] },
      { cat: 'CI/CD', items: ['Ship', 'CI/CD Pipeline'] },
      { cat: 'Media', items: ['PDF Generation', 'DOCX Export'] },
    ];

    content.innerHTML = `
      <div style="margin-bottom:16px">
        <p style="color:var(--text-muted);font-size:12px;margin-bottom:12px">
          Все скиллы доступны агенту автоматически. При необходимости агент сам подключит нужный скилл.
        </p>
      </div>
      <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:12px">
        ${skills.map(s => `
          <div style="background:var(--bg-card);border:1px solid var(--border-subtle);border-radius:10px;padding:14px">
            <div style="font-size:13px;font-weight:600;color:var(--text-primary);margin-bottom:8px">${s.cat}</div>
            <div style="display:flex;flex-wrap:wrap;gap:4px">
              ${s.items.map(i => `<span style="background:var(--red-faint);color:var(--red-core);padding:2px 8px;border-radius:10px;font-size:10px">${i}</span>`).join('')}
            </div>
          </div>
        `).join('')}
      </div>`;
    overlay.classList.remove('hidden');
  },

  // ── Mermaid Diagrams ───────────────────────────────────
  showMermaidDiagram() {
    this.showPanel('editor');
    const overlay = document.getElementById('codeOverlay');
    const title = document.getElementById('overlayTitle');
    const content = document.getElementById('overlayContent');
    if (!overlay || !content) return;

    title.textContent = '📊 Mermaid Diagram';
    const mermaidCode = `classDiagram
    class MainActivity {
        +onCreate()
        +setContent()
    }
    class ChatViewModel {
        -_messages: StateFlow
        +sendMessage(text)
        +callLLMAPI()
    }
    class Message {
        +text: String
        +isUser: Boolean
    }
    class NexusApp {
        +MaterialTheme()
        +ChatScreen()
    }
    MainActivity --> NexusApp
    NexusApp --> ChatViewModel
    ChatViewModel --> Message`;

    content.innerHTML = `
      <div style="margin-bottom:12px">
        <p style="color:var(--text-muted);font-size:12px">Архитектурная диаграмма проекта (Mermaid)</p>
      </div>
      <div style="background:#0a0a14;border:1px solid var(--border-subtle);border-radius:10px;padding:16px;overflow:auto">
        <pre style="color:#AAAACC;font-family:var(--font-mono);font-size:12px;white-space:pre-wrap">${this.escapeHtml(mermaidCode)}</pre>
      </div>
      <div style="margin-top:12px;padding:12px;background:var(--bg-card);border-radius:8px;border:1px solid var(--border-subtle)">
        <div style="font-size:12px;color:var(--text-muted);margin-bottom:8px;font-weight:600">Как использовать:</div>
        <div style="font-size:11px;color:var(--text-secondary);line-height:1.6">
          • Скопируйте код выше в <a href="https://mermaid.live" target="_blank" style="color:var(--red-core)">mermaid.live</a> для визуализации<br/>
          • Или спросите агента: "Сгенерируй mermaid-диаграмму"<br/>
          • Агент автоматически создаст диаграмму по текущему коду
        </div>
      </div>`;
    overlay.classList.remove('hidden');
  },

  // ── Swarm / Multi-session ──────────────────────────────
  initSwarm() {
    this.state.sessions = [
      { id: 0, name: 'Main Session', status: 'active', task: 'Code analysis', started: Date.now() },
    ];
  },

  showSwarmPanel() {
    this.showPanel('editor');
    const overlay = document.getElementById('codeOverlay');
    const title = document.getElementById('overlayTitle');
    const content = document.getElementById('overlayContent');
    if (!overlay || !content) return;

    title.textContent = '🐝 Swarm — Multi-Session Manager';
    content.innerHTML = `
      <div style="margin-bottom:16px">
        <p style="color:var(--text-muted);font-size:12px;margin-bottom:12px">
          Управляйте несколькими AI-сессиями параллельно. Каждая сессия работает независимо.
        </p>
        <button class="btn-outline-red" onclick="CodeAgent.addSession()" style="font-size:12px;padding:6px 14px">+ Новая сессия</button>
      </div>
      <div id="swarmSessions" style="display:grid;gap:8px">
        ${this.state.sessions.map(s => `
          <div style="background:var(--bg-card);border:1px solid ${s.status === 'active' ? 'rgba(68,221,136,0.4)' : 'var(--border-subtle)'};border-radius:10px;padding:12px;display:flex;align-items:center;gap:12px">
            <div style="width:8px;height:8px;border-radius:50%;background:${s.status === 'active' ? '#44dd88' : '#666'}"></div>
            <div style="flex:1">
              <div style="font-size:13px;font-weight:600;color:var(--text-primary)">${s.name}</div>
              <div style="font-size:11px;color:var(--text-muted)">${s.task} · ${Math.floor((Date.now() - s.started) / 1000)}s ago</div>
            </div>
            <span style="font-size:10px;padding:2px 8px;border-radius:8px;background:${s.status === 'active' ? 'rgba(68,221,136,0.15)' : 'rgba(100,100,100,0.15)'};color:${s.status === 'active' ? '#44dd88' : '#888'}">${s.status.toUpperCase()}</span>
          </div>
        `).join('')}
      </div>`;
    overlay.classList.remove('hidden');
  },

  addSession() {
    const id = this.state.sessions.length;
    this.state.sessions.push({
      id, name: `Session ${id + 1}`, status: 'active', task: 'Waiting for task', started: Date.now()
    });
    this.showSwarmPanel();
  },

  // ── Memory ─────────────────────────────────────────────
  initMemory() {},

  addMemoryEntry(type, text) {
    const container = document.getElementById('memoryEntries');
    if (!container) return;

    const empty = container.querySelector('.mem-empty');
    if (empty) empty.remove();

    const now = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const icons = { code: '📝', response: '🤖', review: '🔍', user: '💬' };

    const div = document.createElement('div');
    div.className = 'mem-entry';
    div.innerHTML = `<span class="mem-type">${icons[type] || '•'}</span><span class="mem-time">${now}</span><span class="mem-text">${text}</span>`;
    container.appendChild(div);
  },

  // ── Overlay ────────────────────────────────────────────
  initOverlay() {
    document.getElementById('closeOverlay')?.addEventListener('click', () => {
      document.getElementById('codeOverlay')?.classList.add('hidden');
    });
  },

  // ── Helpers ────────────────────────────────────────────
  escapeHtml(s) {
    if (typeof s !== 'string') return String(s || '');
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  },

  markdownToHtml(text) {
    if (typeof text !== 'string') return String(text || '');
    return text
      .replace(/```(\w+)?\n([\s\S]*?)```/g, (_, lang, code) =>
        `<pre style="background:#05050D;border:1px solid rgba(255,10,47,0.2);border-radius:8px;padding:10px 12px;overflow-x:auto;margin:6px 0;font-family:var(--font-mono);font-size:12px;line-height:1.5;color:#AAAACC"><code>${this.escapeHtml(code.trim())}</code></pre>`)
      .replace(/`([^`]+)`/g, '<code style="background:#1A0A0F;border:1px solid rgba(255,10,47,0.2);border-radius:4px;padding:1px 5px;font-family:var(--font-mono);font-size:0.88em;color:#FF8899">$1</code>')
      .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      .replace(/\*([^*]+)\*/g, '<em>$1</em>')
      .replace(/^### (.+)$/gm, '<h4 style="color:#FF3355;font-size:13px;margin:8px 0 4px;font-weight:700">$1</h4>')
      .replace(/^## (.+)$/gm, '<h3 style="color:#FF3355;font-size:14px;margin:10px 0 5px;font-weight:700">$1</h3>')
      .replace(/^- (.+)$/gm, '<div style="padding-left:12px;margin:2px 0">• $1</div>')
      .replace(/\n\n/g, '<br><br>')
      .replace(/\n/g, '<br>');
  },
};

// Auto-init
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => CodeAgent.init());
} else {
  CodeAgent.init();
}

window.CodeAgent = CodeAgent;
