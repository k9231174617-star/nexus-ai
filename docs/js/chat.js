/* ============================================================
   NEXUS AI — Chat Engine
   Supports: Main Agent, Code Agent, Universal Agent
   ============================================================ */

'use strict';

// ── Config ─────────────────────────────────────────────────
const CHAT_CONFIG = {
  maxHistory: 20,
  stream: true,
};

// Conversation histories per agent
const histories = { main: [], code: [], universal: [] };

// ── System Prompts ─────────────────────────────────────────
const SYSTEM_PROMPTS = {
  main: `You are NEXUS AI — a powerful mobile AI agent running on Android. 
You help users with: code analysis, shell commands, file operations, APK manipulation, media processing.
Be concise and technical. Use markdown for code blocks. Answer in the language the user writes in.`,

  code: `You are NEXUS Code Agent — specialized in Android development, Kotlin, Java, and APK analysis.
When asked about code, provide complete working implementations.
Format code in markdown code blocks with language tags.`,

  universal: `You are NEXUS Universal Agent — specialized in media processing, document analysis, and creative tasks.
Help users analyze images, create videos, edit documents, and process media files.`
};

// ── Main Chat ──────────────────────────────────────────────
var sendBtn, chatInput, chatMessages;

function initMainChat() {
  sendBtn = document.getElementById('sendBtn');
  chatInput = document.getElementById('chatInput');
  chatMessages = document.getElementById('chatMessages');
  
  console.log('[Chat] initMainChat: sendBtn=' + (sendBtn ? 'ok' : 'null') + ' chatInput=' + (chatInput ? 'ok' : 'null') + ' chatMessages=' + (chatMessages ? 'ok' : 'null'));
  
  if (!sendBtn || !chatInput || !chatMessages) {
    console.warn('[Chat] elements not found, retrying...');
    setTimeout(initMainChat, 500);
    return;
  }
  
  sendBtn.addEventListener('click', function() { sendMainChat(); });
  chatInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMainChat();
    }
  });
  console.log('[Chat] Main chat initialized');
}

// Initialize after DOM ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initMainChat);
} else {
  initMainChat();
}

async function sendMainChat() {
  const text = chatInput?.value.trim();
  if (!text) return;

  chatInput.value = '';
  chatInput.style.height = 'auto';

  appendMessage(chatMessages, 'user', escapeHtml(text));
  addMemoryEntry?.('user', text);

  // Prepend injected context
  let prompt = text;
  if (AppState.injectedContext) {
    prompt = `[Context: ${AppState.injectedContext}]\n\n${text}`;
    AppState.injectedContext = null;
  }

  const typingEl = showTyping(chatMessages);
  const t0 = Date.now();

  try {
    histories.main.push({ role: 'user', content: prompt });
    if (histories.main.length > CHAT_CONFIG.maxHistory * 2) {
      histories.main = histories.main.slice(-CHAT_CONFIG.maxHistory);
    }

    const reply = await callAPI(histories.main, 'main');
    histories.main.push({ role: 'assistant', content: reply });

    const latency = Date.now() - t0;
    updateLatency(latency);
    removeTyping(typingEl);
    appendMessage(chatMessages, 'assistant', markdownToHtml(reply));
    addMemoryEntry?.('agent', reply.slice(0, 80));
    updateTokenCount(estimateTokens(text + reply));

    } catch (err) {
    if (typingEl) removeTyping(typingEl);
    console.error('[Chat] sendMainChat error:', err);
    var errMsg = err.message || 'Unknown error';
    
    // User-friendly error messages
    if (errMsg.includes('401') || errMsg.includes('Unauthorized')) {
      errMsg = '\u274c \u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 API \u043a\u043b\u044e\u0447. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 (Settings \u2192 API Key)';
    } else if (errMsg.includes('429')) {
      errMsg = '\u23f3 \u041f\u0440\u0435\u0432\u044b\u0448\u0435\u043d \u043b\u0438\u043c\u0438\u0442 \u0437\u0430\u043f\u0440\u043e\u0441\u043e\u0432. \u041f\u043e\u0434\u043e\u0436\u0434\u0438\u0442\u0435 \u043c\u0438\u043d\u0443\u0442\u0443.';
    } else if (errMsg.includes('402') || errMsg.includes('credits')) {
      errMsg = '\ud83d\udcb3 \u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043a\u0440\u0435\u0434\u0438\u0442\u043e\u0432 \u043d\u0430 \u0430\u043a\u043a\u0430\u0443\u043d\u0442\u0435 OpenRouter.';
    } else if (errMsg.includes('fetch') || errMsg.includes('network') || errMsg.includes('Failed')) {
      errMsg = '\ud83c\udf10 \u041e\u0448\u0438\u0431\u043a\u0430 \u0441\u0435\u0442\u0438. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435 \u043a \u0438\u043d\u0442\u0435\u0440\u043d\u0435\u0442\u0443.';
    }
    
    if (chatMessages) {
      window.appendMessage(chatMessages, 'assistant',
        '<div style="color:#FF4466;padding:4px 0">\u26a0\ufe0f ' + errMsg + '</div>' +
        '<div style="color:#666;font-size:12px;margin-top:6px">' +
        '\u0411\u0435\u0437 API \u043a\u043b\u044e\u0447\u0430 \u0440\u0430\u0431\u043e\u0442\u0430\u0435\u0442 \u0434\u0435\u043c\u043e-\u0440\u0435\u0436\u0438\u043c. ' +
        '<a href="#" onclick="switchTab(\'settings\');return false" style="color:#FF0A2F">\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u2192</a>' +
        '</div>'
      );
    }
  }
</span>`);
  }
}

// ── Code Chat ──────────────────────────────────────────────
const codeSendBtn = document.getElementById('codeSendBtn');
const codeInput   = document.getElementById('codeInput');
const miniChat    = document.getElementById('miniChat');

codeSendBtn?.addEventListener('click', () => sendCodeChat());
codeInput?.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendCodeChat();
  }
});

async function sendCodeChat() {
  const text = codeInput?.value.trim();
  if (!text) return;

  codeInput.value = '';
  codeInput.style.height = 'auto';

  // Get current editor content as context
  const editorContent = document.getElementById('codeEditor')?.innerText || '';
  const prompt = `Current file:\n\`\`\`kotlin\n${editorContent.slice(0, 2000)}\n\`\`\`\n\nQuestion: ${text}`;

  const userDiv = document.createElement('div');
  userDiv.className = 'message user-msg mini';
  userDiv.innerHTML = `<div class="msg-content"><div class="msg-text">${escapeHtml(text)}</div></div>`;
  miniChat?.appendChild(userDiv);

  const typingEl = showTyping(miniChat);

  try {
    histories.code.push({ role: 'user', content: prompt });

    const reply = await callAPI(histories.code, 'code');
    histories.code.push({ role: 'assistant', content: reply });

    removeTyping(typingEl);

    const replyDiv = document.createElement('div');
    replyDiv.className = 'message assistant-msg mini';
    replyDiv.innerHTML = `<div class="msg-content"><div class="msg-text">${markdownToHtml(reply)}</div></div>`;
    miniChat?.appendChild(replyDiv);
    miniChat.scrollTop = miniChat.scrollHeight;

  } catch (err) {
    removeTyping(typingEl);
    const errDiv = document.createElement('div');
    errDiv.className = 'message assistant-msg mini';
    errDiv.innerHTML = `<div class="msg-content"><div class="msg-text" style="color:#FF4466">Error: ${escapeHtml(err.message)}</div></div>`;
    miniChat?.appendChild(errDiv);
  }
}

// ── Universal Chat ─────────────────────────────────────────
const uniSendBtn   = document.getElementById('uniSendBtn');
const uniInput     = document.getElementById('uniInput');
const uniChatMsgs  = document.getElementById('uniChatMessages');

uniSendBtn?.addEventListener('click', () => sendUniChat());
uniInput?.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendUniChat();
  }
});

async function sendUniChat() {
  const text = uniInput?.value.trim();
  if (!text) return;

  uniInput.value = '';
  appendMessage(uniChatMsgs, 'user', escapeHtml(text));

  const typingEl = showTyping(uniChatMsgs);

  try {
    histories.universal.push({ role: 'user', content: text });

    const reply = await callAPI(histories.universal, 'universal');
    histories.universal.push({ role: 'assistant', content: reply });

    removeTyping(typingEl);
    appendMessage(uniChatMsgs, 'assistant', markdownToHtml(reply));

  } catch (err) {
    removeTyping(typingEl);
    appendMessage(uniChatMsgs, 'assistant', `<span style="color:#FF4466">Error: ${escapeHtml(err.message)}</span>`);
  }
}

// ── API Call ───────────────────────────────────────────────
async function callAPI(messages, agentType) {
  const settings = loadSettings();
  const apiKey = settings.apiKey || '';
  const endpoint = settings.endpoint || 'https://openrouter.ai/api/v1/chat/completions';
  const useCustom = apiKey.length > 10;

  if (useCustom) {
    try {
      return await callCustomAPI(messages, agentType, settings);
    } catch (e) {
      console.error('[NexusChat] API error, trying fallback:', e.message);
    }
  }
  // Always try OpenRouter as fallback
  try {
    const model = settings.mainModel || getDefaultModelForAgent(agentType);
    const res = await fetch('https://openrouter.ai/api/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': apiKey.length > 10 ? 'Bearer ' + apiKey : undefined,
        'HTTP-Referer': window.location.origin,
        'X-Title': 'Nexus AI Dashboard',
      },
      body: JSON.stringify({
        model,
        messages: [
          { role: 'system', content: SYSTEM_PROMPTS[agentType] },
          ...messages.slice(-16)
        ],
        temperature: 0.7,
        max_tokens: 1500,
      })
    });
    if (res.ok) {
      const data = await res.json();
      if (data.choices?.[0]?.message?.content) {
        return data.choices[0].message.content;
      }
    }
  } catch (e) { console.warn('[NexusChat] OpenRouter fallback failed:', e.message); }
  return callDemoAPI(messages, agentType);
}

// ── Model Selector ─────────────────────────────────────────
let modelSelectorInitialized = false;

function initModelSelector() {
  if (modelSelectorInitialized) return;
  modelSelectorInitialized = true;

  const btn = document.getElementById('modelSelectorBtn');
  const dd = document.getElementById('modelDropdown');
  const list = document.getElementById('modelDropdownList');
  const autoBtn = document.getElementById('modelAutoBtn');
  const searchInput = document.getElementById('modelSearchInput');

  if (!btn || !dd || !list) return;

  // Toggle dropdown
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    dd.classList.toggle('open');
    if (dd.classList.contains('open')) {
      renderModelList(list, searchInput?.value || '');
      searchInput?.focus();
    }
  });

  // Close on outside click
  document.addEventListener('click', (e) => {
    const wrap = document.getElementById('modelSelectorWrap');
    if (wrap && !wrap.contains(e.target)) {
      dd?.classList.remove('open');
    }
  });

  // Auto button
  autoBtn?.addEventListener('click', () => {
    AppState.chatOverrideModel = null;
    updateModelSelectorLabel();
    dd.classList.remove('open');
  });

  // Search
  searchInput?.addEventListener('input', () => {
    renderModelList(list, searchInput.value);
  });

  // Listen for tab switches to update auto-label
  document.addEventListener('tabChanged', () => {
    if (!AppState.chatOverrideModel) updateModelSelectorLabel();
  });

  // Initial render
  updateModelSelectorLabel();
}

function renderModelList(container, searchQuery) {
  const settings = JSON.parse(localStorage.getItem('nexus_settings') || '{}');
  const hasApiKey = settings.apiKey && settings.apiKey.length > 10;

  let models = [...MODEL_DATABASE];

  // Filter by search
  if (searchQuery) {
    const q = searchQuery.toLowerCase();
    models = models.filter(m => 
      m.name.toLowerCase().includes(q) || 
      m.id.toLowerCase().includes(q) || 
      m.provider.toLowerCase().includes(q)
    );
  }

  // Group by tier
  const freeModels = models.filter(m => m.tier === 'free');
  const paidModels = models.filter(m => m.tier === 'paid');

  let html = '';

  // Current selection indicator
  const currentModel = AppState.chatOverrideModel;
  const currentInfo = currentModel ? getModelInfo(currentModel) : null;

  if (currentInfo) {
    html += `<div class="model-dropdown-current">
      <span class="model-dd-dot" style="background:${currentInfo.tier === 'free' ? '#44dd88' : '#FF0A2F'}"></span>
      <span><strong>${currentInfo.name}</strong></span>
      <span style="color:var(--text-muted);font-size:11px;margin-left:auto">${currentInfo.provider}</span>
    </div>`;
  }

  // Free models section
  if (freeModels.length > 0) {
    html += `<div class="model-dd-section-label">Бесплатные модели</div>`;
    freeModels.forEach(m => {
      const active = currentModel === m.id ? ' active' : '';
      html += `<div class="model-dd-item${active}" data-model="${m.id}">
        <span class="model-dd-dot free-dot"></span>
        <span class="model-dd-name">${m.name}</span>
        <span class="model-dd-provider">${m.provider}</span>
      </div>`;
    });
  }

  // Paid models section
  if (paidModels.length > 0) {
    html += `<div class="model-dd-section-label${!hasApiKey ? ' dimmed' : ''}">${hasApiKey ? 'Платные модели' : 'Платные модели (нужен API Key)'}</div>`;
    paidModels.forEach(m => {
      const active = currentModel === m.id ? ' active' : '';
      const disabled = !hasApiKey ? ' disabled' : '';
      html += `<div class="model-dd-item${active}${disabled}" data-model="${m.id}">
        <span class="model-dd-dot paid-dot"></span>
        <span class="model-dd-name">${m.name}</span>
        <span class="model-dd-provider">${m.provider}</span>
      </div>`;
    });
  }

  if (!html) {
    html = '<div class="model-dd-empty">Модели не найдены</div>';
  }

  container.innerHTML = html;

  // Click handlers
  container.querySelectorAll('.model-dd-item:not(.disabled)').forEach(el => {
    el.addEventListener('click', () => {
      const modelId = el.dataset.model;
      AppState.chatOverrideModel = modelId;
      updateModelSelectorLabel();
      document.getElementById('modelDropdown')?.classList.remove('open');
    });
  });
}

function updateModelSelectorLabel() {
  const label = document.getElementById('modelSelectorLabel');
  const dot = document.getElementById('modelSelectorDot');
  const badge = document.getElementById('activeModelBadge');

  if (!label) return;

  const agentType = getCurrentAgentType();
  const modelId = AppState.chatOverrideModel || getDefaultModelForAgent(agentType);
  const info = getModelInfo(modelId);

  if (AppState.chatOverrideModel) {
    label.textContent = info.name.split(' ').slice(0, 2).join(' ');
    label.style.color = 'var(--red-core)';
    dot.style.background = info.tier === 'free' ? '#44dd88' : '#FF0A2F';
  } else {
    label.textContent = 'Auto';
    label.style.color = 'var(--text-muted)';
    dot.style.background = 'var(--text-muted)';
  }

  if (badge) badge.textContent = info.name.split(' ').slice(0, 2).join(' ').toUpperCase();
}

// Initialize after DOM
document.addEventListener('DOMContentLoaded', () => {
  initModelSelector();
});

async function callCustomAPI(messages, agentType, settings) {
  // Use chat override model if set, otherwise per-agent settings, otherwise auto-detect
  let model;
  if (AppState.chatOverrideModel) {
    model = AppState.chatOverrideModel;
  } else {
    const modelMap = {
      main: settings.mainModel || getDefaultModelForAgent('main'),
      code: settings.codeModel || getDefaultModelForAgent('code'),
      universal: settings.uniModel || getDefaultModelForAgent('universal'),
    };
    model = modelMap[agentType] || getDefaultModelForAgent(agentType);
  }

  const endpoint = settings.endpoint || 'https://openrouter.ai/api/v1/chat/completions';

  const isOpenRouter = endpoint.includes('openrouter.ai');
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${settings.apiKey}`,
  };
  if (isOpenRouter) {
    headers['HTTP-Referer'] = window.location.origin;
    headers['X-Title'] = 'Nexus AI Dashboard';
  }

  const res = await fetch(endpoint, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      model,
      messages: [
        { role: 'system', content: SYSTEM_PROMPTS[agentType] },
        ...messages.slice(-16)
      ],
      temperature: settings.temperature || 0.7,
      max_tokens: 1500,
      stream: false
    })
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.error?.message || `API Error ${res.status}`);
  }

  const data = await res.json();
  return data.choices?.[0]?.message?.content || 'No response';
}

// Demo responses when no API key is set
async function callDemoAPI(messages, agentType) {
  await new Promise(r => setTimeout(r, 600 + Math.random() * 800));

  const last = messages[messages.length - 1]?.content || '';
  const lower = last.toLowerCase();

  const demos = {
    help: '**Available commands:**\n\n`chat` — Natural language conversation\n`code` — Code generation & analysis\n`shell` — Execute terminal commands\n`files` — Browse & manage files\n`apk` — APK decompilation/recompilation\n\n_Connect an API key in Settings for full AI capabilities._',
    code: '```kotlin\nclass NexusAgent(\n    private val llm: LLMBridge,\n    private val cli: CLIExecutor\n) {\n    suspend fun execute(prompt: String): String {\n        val context = buildContext()\n        return llm.generate(prompt, context)\n    }\n}\n```',
    file: '**File Manager** ready.\n\nI can help you:\n- Read and analyze files\n- Search for patterns in code\n- Extract APK resources\n- Convert between formats',
    default: [
      'I\'m NEXUS AI — running in **demo mode**. Add your API key in Settings to unlock full capabilities.',
      'Connect an API key to enable real AI responses. I support OpenAI, Claude, and any OpenAI-compatible endpoint.',
      '**NEXUS** is ready. For full functionality:\n1. Go to Settings\n2. Enter your API key\n3. Select your model\n\nDemo mode uses pre-generated responses.'
    ]
  };

  if (lower.includes('help') || lower.includes('команд')) return demos.help;
  if (lower.includes('код') || lower.includes('code') || lower.includes('kotlin')) return demos.code;
  if (lower.includes('файл') || lower.includes('file')) return demos.file;

  const arr = demos.default;
  return arr[Math.floor(Math.random() * arr.length)];
}

// ── Typing Indicator ───────────────────────────────────────
function showTyping(container) {
  const div = document.createElement('div');
  div.className = 'message assistant-msg typing-msg';
  div.innerHTML = `
    <div class="msg-avatar">
      <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
        <circle cx="14" cy="14" r="13" fill="#1A0A0F" stroke="#FF0A2F" stroke-width="1"/>
        <circle cx="14" cy="14" r="5" fill="#FF0A2F" opacity="0.5"/>
      </svg>
    </div>
    <div class="msg-content">
      <div class="typing-indicator">
        <span class="typing-dot"></span>
        <span class="typing-dot"></span>
        <span class="typing-dot"></span>
      </div>
    </div>
  `;
  container?.appendChild(div);
  container.scrollTop = container.scrollHeight;
  return div;
}

function removeTyping(el) {
  el?.remove();
}

// ── Latency display ────────────────────────────────────────
function updateLatency(ms) {
  const el = document.getElementById('apiLatency');
  if (!el) return;
  el.textContent = ms < 1000 ? `${ms}ms` : `${(ms/1000).toFixed(1)}s`;
  AppState.apiLatency = ms;
}

// ── Helpers ────────────────────────────────────────────────
function escapeHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function markdownToHtml(text) {
  return text
    // Code blocks
    .replace(/```(\w+)?\n([\s\S]*?)```/g, (_, lang, code) =>
      `<pre style="background:#05050D;border:1px solid rgba(255,10,47,0.2);border-radius:8px;padding:12px 14px;overflow-x:auto;margin:8px 0;font-family:var(--font-mono);font-size:12px;line-height:1.6;color:#AAAACC"><code>${escapeHtml(code.trim())}</code></pre>`
    )
    // Inline code
    .replace(/`([^`]+)`/g, '<code style="background:#1A0A0F;border:1px solid rgba(255,10,47,0.2);border-radius:4px;padding:1px 5px;font-family:var(--font-mono);font-size:0.88em;color:#FF8899">$1</code>')
    // Bold
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    // Italic
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    // Headings
    .replace(/^### (.+)$/gm, '<h4 style="color:#FF3355;font-size:13px;margin:8px 0 4px;font-weight:700">$1</h4>')
    .replace(/^## (.+)$/gm, '<h3 style="color:#FF3355;font-size:14px;margin:10px 0 5px;font-weight:700">$1</h3>')
    // Lists
    .replace(/^- (.+)$/gm, '<div style="padding-left:12px;margin:2px 0">• $1</div>')
    .replace(/^\d+\. (.+)$/gm, '<div style="padding-left:12px;margin:2px 0">$1</div>')
    // Line breaks
    .replace(/\n\n/g, '<br><br>')
    .replace(/\n/g, '<br>');
}

function estimateTokens(text) {
  return Math.ceil(text.length / 4);
}

function loadSettings() {
  try {
    return JSON.parse(localStorage.getItem('nexus_settings') || '{}');
  } catch { return {}; }
}
