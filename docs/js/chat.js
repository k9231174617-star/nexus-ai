/* ============================================================
   NEXUS AI — Chat Engine
   Supports: Main Agent, Code Agent, Universal Agent
   ============================================================ */

'use strict';

// ── Config ─────────────────────────────────────────────────
var CHAT_CONFIG = {
  maxHistory: 20,
  stream: true,
};

// Conversation histories per agent
var histories = { main: [], code: [], universal: [] };

// ── System Prompts ─────────────────────────────────────────
var SYSTEM_PROMPTS = {
  main: 'You are NEXUS AI — a powerful mobile AI agent running on Android. \nYou help users with: code analysis, shell commands, file operations, APK manipulation, media processing.\nBe concise and technical. Use markdown for code blocks. Answer in the language the user writes in.',

  code: 'You are NEXUS Code Agent — specialized in Android development, Kotlin, Java, and APK analysis.\nWhen asked about code, provide complete working implementations.\nFormat code in markdown code blocks with language tags.',

  universal: 'You are NEXUS Universal Agent — specialized in media processing, document analysis, and creative tasks.\nHelp users analyze images, create videos, edit documents, and process media files.'
};

// ── Module-level variables (assigned in init) ──────────────
var sendBtn = null, chatInput = null, chatMessages = null;
var codeSendBtn = null, codeInput = null, miniChat = null;
var uniSendBtn = null, uniInput = null, uniChatMsgs = null;

// ── Main Chat Init ─────────────────────────────────────────
function initMainChat() {
  sendBtn = document.getElementById('sendBtn');
  chatInput = document.getElementById('chatInput');
  chatMessages = document.getElementById('chatMessages');
  
  console.log('[Chat] initMainChat: sendBtn=' + (sendBtn ? 'ok' : 'null') + ' chatInput=' + (chatInput ? 'ok' : 'null') + ' chatMessages=' + (chatMessages ? 'ok' : 'null'));
  
  if (!sendBtn || !chatInput || !chatMessages) {
    console.warn('[Chat] elements not found, retrying in 500ms...');
    setTimeout(initMainChat, 500);
    return;
  }
  
  // Remove old listeners by cloning
  var newBtn = sendBtn.cloneNode(true);
  sendBtn.parentNode.replaceChild(newBtn, sendBtn);
  sendBtn = newBtn;
  
  sendBtn.addEventListener('click', function() { sendMainChat(); });
  chatInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMainChat();
    }
  });
  console.log('[Chat] Main chat initialized');
}

// ── Code Chat Init ─────────────────────────────────────────
function initCodeChat() {
  codeSendBtn = document.getElementById('codeSendBtn');
  codeInput = document.getElementById('codeInput');
  miniChat = document.getElementById('miniChat');
  
  console.log('[Chat] initCodeChat: sendBtn=' + (codeSendBtn ? 'ok' : 'null') + ' input=' + (codeInput ? 'ok' : 'null'));
  
  if (!codeSendBtn || !codeInput) {
    console.warn('[Chat] code chat elements not found, retrying...');
    setTimeout(initCodeChat, 500);
    return;
  }
  
  var newBtn = codeSendBtn.cloneNode(true);
  codeSendBtn.parentNode.replaceChild(newBtn, codeSendBtn);
  codeSendBtn = newBtn;
  
  codeSendBtn.addEventListener('click', function() { sendCodeChat(); });
  codeInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendCodeChat();
    }
  });
  console.log('[Chat] Code chat initialized');
}

// ── Universal Chat Init ────────────────────────────────────
function initUniChat() {
  uniSendBtn = document.getElementById('uniSendBtn');
  uniInput = document.getElementById('uniInput');
  uniChatMsgs = document.getElementById('uniChatMessages');
  
  console.log('[Chat] initUniChat: sendBtn=' + (uniSendBtn ? 'ok' : 'null') + ' input=' + (uniInput ? 'ok' : 'null'));
  
  if (!uniSendBtn || !uniInput) {
    console.warn('[Chat] universal chat elements not found, retrying...');
    setTimeout(initUniChat, 500);
    return;
  }
  
  var newBtn = uniSendBtn.cloneNode(true);
  uniSendBtn.parentNode.replaceChild(newBtn, uniSendBtn);
  uniSendBtn = newBtn;
  
  uniSendBtn.addEventListener('click', function() { sendUniChat(); });
  uniInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendUniChat();
    }
  });
  console.log('[Chat] Universal chat initialized');
}

// Initialize all chat systems after DOM ready
document.addEventListener('DOMContentLoaded', function() {
  initMainChat();
  initCodeChat();
  initUniChat();
});

// ── Send Main Chat ─────────────────────────────────────────
async function sendMainChat() {
  // Ensure elements are set - try to find them if null
  if (!chatMessages) {
    chatMessages = document.getElementById('chatMessages');
  }
  if (!chatInput) {
    chatInput = document.getElementById('chatInput');
    if (!chatInput) {
      console.error('[Chat] chatInput is null');
      return;
    }
  }
  
  var text = chatInput.value.trim();
  if (!text) return;

  chatInput.value = '';
  chatInput.style.height = 'auto';

  try {
    if (chatMessages) {
      appendMessage(chatMessages, 'user', escapeHtml(text));
    }
    if (window.addMemoryEntry) addMemoryEntry('user', text);

    // Prepend injected context
    var prompt = text;
    if (window.AppState && AppState.injectedContext) {
      prompt = '[Context: ' + AppState.injectedContext + ']\n\n' + text;
      AppState.injectedContext = null;
    }

    // Include attached file info
    var fileInput = document.getElementById('fileInput');
    if (fileInput && fileInput.files && fileInput.files.length > 0) {
      var file = fileInput.files[0];
      prompt = '[User attached file: ' + file.name + ' (' + file.type + ', ' + Math.round(file.size/1024) + 'KB)]\n\n' + prompt;
      fileInput.value = '';
      var preview = document.getElementById('attachmentPreview');
      if (preview) preview.style.display = 'none';
    }

    var typingEl = chatMessages ? showTyping(chatMessages) : null;
    var t0 = Date.now();

    histories.main.push({ role: 'user', content: prompt });
    if (histories.main.length > CHAT_CONFIG.maxHistory * 2) {
      histories.main = histories.main.slice(-CHAT_CONFIG.maxHistory);
    }

    var reply = await callAPI(histories.main, 'main');
    histories.main.push({ role: 'assistant', content: reply });

    var latency = Date.now() - t0;
    updateLatency(latency);
    if (typingEl) removeTyping(typingEl);
    if (chatMessages) {
      appendMessage(chatMessages, 'assistant', markdownToHtml(reply));
    }
    if (window.addMemoryEntry) addMemoryEntry('agent', reply.slice(0, 80));
    if (window.updateTokenCount) updateTokenCount(estimateTokens(text + reply));

  } catch (err) {
    if (typingEl) removeTyping(typingEl);
    console.error('[Chat] sendMainChat error:', err);
    if (chatMessages) {
      var errMsg = (err && err.message) ? err.message : String(err);
      var friendlyMsg = errMsg;
      if (errMsg.includes('401') || errMsg.toLowerCase().includes('unauthorized')) {
        friendlyMsg = '❌ Неверный API ключ. Перейдите в <a href="#" onclick="switchTab(\'settings\');return false" style="color:#FF0A2F">Settings</a> и введите ключ OpenRouter.';
      } else if (errMsg.includes('429')) {
        friendlyMsg = '⏳ Превышен лимит запросов. Подождите минуту и попробуйте снова.';
      } else if (errMsg.includes('402')) {
        friendlyMsg = '💳 Недостаточно кредитов на OpenRouter. Пополните баланс.';
      } else if (errMsg.toLowerCase().includes('fetch') || errMsg.toLowerCase().includes('network') || errMsg.toLowerCase().includes('failed')) {
        friendlyMsg = '🌐 Ошибка сети. Проверьте подключение к интернету.';
      } else if (!window.loadSettings || !window.loadSettings().apiKey) {
        friendlyMsg = 'ℹ️ Демо-режим. Добавьте API ключ в <a href="#" onclick="switchTab(\'settings\');return false" style="color:#FF0A2F">Settings</a> для полного AI ответа.';
      }
      appendMessage(chatMessages, 'assistant', '<span style="color:#FF4466">⚠️ ' + friendlyMsg + '</span>');
    }
  }
}

// ── Send Code Chat ─────────────────────────────────────────
async function sendCodeChat() {
  if (!codeInput) return;
  var text = codeInput.value.trim();
  if (!text) return;

  codeInput.value = '';
  codeInput.style.height = 'auto';

  // Get current editor content as context
  var editorContent = (document.getElementById('codeEditor') || {}).innerText || '';
  var prompt = 'Current file:\n```kotlin\n' + editorContent.slice(0, 2000) + '\n```\n\nQuestion: ' + text;

  if (miniChat) {
    var userDiv = document.createElement('div');
    userDiv.className = 'message user-msg mini';
    userDiv.innerHTML = '<div class="msg-content"><div class="msg-text">' + escapeHtml(text) + '</div></div>';
    miniChat.appendChild(userDiv);
  }

  var typingEl = miniChat ? showTyping(miniChat) : null;

  try {
    histories.code.push({ role: 'user', content: prompt });

    var reply = await callAPI(histories.code, 'code');
    histories.code.push({ role: 'assistant', content: reply });

    if (typingEl) removeTyping(typingEl);

    if (miniChat) {
      var replyDiv = document.createElement('div');
      replyDiv.className = 'message assistant-msg mini';
      replyDiv.innerHTML = '<div class="msg-content"><div class="msg-text">' + markdownToHtml(reply) + '</div></div>';
      miniChat.appendChild(replyDiv);
      miniChat.scrollTop = miniChat.scrollHeight;
    }

  } catch (err) {
    if (typingEl) removeTyping(typingEl);
    console.error('[Chat] sendCodeChat error:', err);
    if (miniChat) {
      var errDiv = document.createElement('div');
      errDiv.className = 'message assistant-msg mini';
      errDiv.innerHTML = '<div class="msg-content"><div class="msg-text" style="color:#FF4466">Error: ' + escapeHtml(err.message || String(err)) + '</div></div>';
      miniChat.appendChild(errDiv);
    }
  }
}

// ── Send Universal Chat ────────────────────────────────────
async function sendUniChat() {
  if (!uniInput) return;
  var text = uniInput.value.trim();
  if (!text) return;

  uniInput.value = '';
  if (uniChatMsgs) appendMessage(uniChatMsgs, 'user', escapeHtml(text));

  var typingEl = uniChatMsgs ? showTyping(uniChatMsgs) : null;

  try {
    histories.universal.push({ role: 'user', content: text });

    var reply = await callAPI(histories.universal, 'universal');
    histories.universal.push({ role: 'assistant', content: reply });

    if (typingEl) removeTyping(typingEl);
    if (uniChatMsgs) appendMessage(uniChatMsgs, 'assistant', markdownToHtml(reply));

  } catch (err) {
    if (typingEl) removeTyping(typingEl);
    console.error('[Chat] sendUniChat error:', err);
    if (uniChatMsgs) {
      appendMessage(uniChatMsgs, 'assistant', '<span style="color:#FF4466">Error: ' + escapeHtml(err.message || String(err)) + '</span>');
    }
  }
}

// ── API Call ───────────────────────────────────────────────
async function callAPI(messages, agentType) {
  var settings = loadSettings();
  var apiKey = settings.apiKey || '';
  var endpoint = settings.endpoint || 'https://openrouter.ai/api/v1/chat/completions';
  var useCustom = apiKey && apiKey.length > 10;

  if (useCustom) {
    try {
      return await callCustomAPI(messages, agentType, settings);
    } catch (e) {
      console.error('[NexusChat] API error, trying fallback:', e.message);
    }
  }
  
  // Try OpenRouter with the key if available
  if (apiKey && apiKey.length > 10) {
    try {
      var model = settings.mainModel || (window.getDefaultModelForAgent ? getDefaultModelForAgent(agentType) : 'openai/gpt-4o-mini');
      var headers = {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + apiKey,
        'HTTP-Referer': window.location.origin,
        'X-Title': 'Nexus AI Dashboard',
      };
      var res = await fetch('https://openrouter.ai/api/v1/chat/completions', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({
          model: model,
          messages: [
            { role: 'system', content: SYSTEM_PROMPTS[agentType] }
          ].concat(messages.slice(-16)),
          temperature: 0.7,
          max_tokens: 1500,
        })
      });
      if (res.ok) {
        var data = await res.json();
        if (data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content) {
          return data.choices[0].message.content;
        }
      } else {
        console.warn('[NexusChat] OpenRouter API returned', res.status);
      }
    } catch (e) { 
      console.warn('[NexusChat] OpenRouter fallback failed:', e.message); 
    }
  }
  
  // No API key configured
  throw new Error('NO_API_KEY');
}

// ── Custom API Call ────────────────────────────────────────
async function callCustomAPI(messages, agentType, settings) {
  // Use chat override model if set, otherwise per-agent settings, otherwise auto-detect
  var model;
  if (window.AppState && AppState.chatOverrideModel) {
    model = AppState.chatOverrideModel;
  } else {
    var getModel = window.getDefaultModelForAgent || function(t) { return 'openai/gpt-4o-mini'; };
    var modelMap = {
      main: settings.mainModel || getModel('main'),
      code: settings.codeModel || getModel('code'),
      universal: settings.uniModel || getModel('universal'),
    };
    model = modelMap[agentType] || getModel(agentType);
  }

  var endpoint = settings.endpoint || 'https://openrouter.ai/api/v1/chat/completions';
  var isOpenRouter = endpoint.indexOf('openrouter.ai') !== -1;
  var headers = {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + settings.apiKey,
  };
  if (isOpenRouter) {
    headers['HTTP-Referer'] = window.location.origin;
    headers['X-Title'] = 'Nexus AI Dashboard';
  }

  var res = await fetch(endpoint, {
    method: 'POST',
    headers: headers,
    body: JSON.stringify({
      model: model,
      messages: [
        { role: 'system', content: SYSTEM_PROMPTS[agentType] }
      ].concat(messages.slice(-16)),
      temperature: settings.temperature || 0.7,
      max_tokens: 1500,
      stream: false
    })
  });

  if (!res.ok) {
    var errText = await res.text();
    var errObj;
    try { errObj = JSON.parse(errText); } catch(e) { errObj = {}; }
    throw new Error(errObj.error && errObj.error.message ? errObj.error.message : 'API Error ' + res.status);
  }

  var data = await res.json();
  return (data.choices && data.choices[0] && data.choices[0].message && data.choices[0].message.content) || 'No response';
}


// ── Model Selector ─────────────────────────────────────────
var modelSelectorInitialized = false;

function initModelSelector() {
  if (modelSelectorInitialized) return;
  modelSelectorInitialized = true;

  var btn = document.getElementById('modelSelectorBtn');
  var dd = document.getElementById('modelDropdown');
  var list = document.getElementById('modelDropdownList');
  var autoBtn = document.getElementById('modelAutoBtn');
  var searchInput = document.getElementById('modelSearchInput');

  if (!btn || !dd || !list) {
    console.warn('[Chat] Model selector elements not found, retrying...');
    setTimeout(initModelSelector, 500);
    return;
  }

  // Toggle dropdown
  btn.addEventListener('click', function(e) {
    e.stopPropagation();
    dd.classList.toggle('open');
    if (dd.classList.contains('open')) {
      renderModelList(list, searchInput ? searchInput.value : '');
      if (searchInput) searchInput.focus();
    }
  });

  // Close on outside click
  document.addEventListener('click', function(e) {
    var wrap = document.getElementById('modelSelectorWrap');
    if (wrap && !wrap.contains(e.target)) {
      if (dd) dd.classList.remove('open');
    }
  });

  // Auto button
  if (autoBtn) {
    autoBtn.addEventListener('click', function() {
      if (window.AppState) AppState.chatOverrideModel = null;
      updateModelSelectorLabel();
      dd.classList.remove('open');
    });
  }

  // Search
  if (searchInput) {
    searchInput.addEventListener('input', function() {
      renderModelList(list, searchInput.value);
    });
  }

  // Listen for tab switches to update auto-label
  document.addEventListener('tabChanged', function() {
    if (!window.AppState || !AppState.chatOverrideModel) updateModelSelectorLabel();
  });

  // Initial render
  updateModelSelectorLabel();
  console.log('[Chat] Model selector initialized');
}

function renderModelList(container, searchQuery) {
  var settings = JSON.parse(localStorage.getItem('nexus_settings') || '{}');
  var hasApiKey = settings.apiKey && settings.apiKey.length > 10;
  var db = window.MODEL_DATABASE || [];
  
  if (db.length === 0) {
    container.innerHTML = '<div class="model-dd-empty">Model database not loaded</div>';
    return;
  }

  var models = db.slice(); // copy

  // Filter by search
  if (searchQuery) {
    var q = searchQuery.toLowerCase();
    models = models.filter(function(m) {
      return (m.name || '').toLowerCase().indexOf(q) !== -1 || 
             (m.id || '').toLowerCase().indexOf(q) !== -1 || 
             (m.provider || '').toLowerCase().indexOf(q) !== -1;
    });
  }

  // Group by tier
  var freeModels = models.filter(function(m) { return m.tier === 'free'; });
  var paidModels = models.filter(function(m) { return m.tier === 'paid'; });

  var html = '';
  var currentModel = window.AppState ? AppState.chatOverrideModel : null;
  var getInfo = window.getModelInfo || function(id) { return { name: id, tier: 'paid', provider: 'Custom' }; };
  var currentInfo = currentModel ? getInfo(currentModel) : null;

  if (currentInfo) {
    html += '<div class="model-dropdown-current">' +
      '<span class="model-dd-dot" style="background:' + (currentInfo.tier === 'free' ? '#44dd88' : '#FF0A2F') + '"></span>' +
      '<span><strong>' + currentInfo.name + '</strong></span>' +
      '<span style="color:var(--text-muted);font-size:11px;margin-left:auto">' + currentInfo.provider + '</span>' +
    '</div>';
  }

  // Free models section
  if (freeModels.length > 0) {
    html += '<div class="model-dd-section-label">Бесплатные модели</div>';
    freeModels.forEach(function(m) {
      var active = currentModel === m.id ? ' active' : '';
      html += '<div class="model-dd-item' + active + '" data-model="' + m.id + '">' +
        '<span class="model-dd-dot free-dot"></span>' +
        '<span class="model-dd-name">' + m.name + '</span>' +
        '<span class="model-dd-provider">' + m.provider + '</span>' +
      '</div>';
    });
  }

  // Paid models section
  if (paidModels.length > 0) {
    html += '<div class="model-dd-section-label' + (!hasApiKey ? ' dimmed' : '') + '">' + 
      (hasApiKey ? 'Платные модели' : 'Платные модели (нужен API Key)') + 
    '</div>';
    paidModels.forEach(function(m) {
      var active = currentModel === m.id ? ' active' : '';
      var disabled = !hasApiKey ? ' disabled' : '';
      html += '<div class="model-dd-item' + active + disabled + '" data-model="' + m.id + '">' +
        '<span class="model-dd-dot paid-dot"></span>' +
        '<span class="model-dd-name">' + m.name + '</span>' +
        '<span class="model-dd-provider">' + m.provider + '</span>' +
      '</div>';
    });
  }

  if (!html) {
    html = '<div class="model-dd-empty">Модели не найдены</div>';
  }

  container.innerHTML = html;

  // Click handlers
  container.querySelectorAll('.model-dd-item:not(.disabled)').forEach(function(el) {
    el.addEventListener('click', function() {
      var modelId = el.dataset.model;
      if (window.AppState) AppState.chatOverrideModel = modelId;
      updateModelSelectorLabel();
      var dd = document.getElementById('modelDropdown');
      if (dd) dd.classList.remove('open');
    });
  });
}

function updateModelSelectorLabel() {
  var label = document.getElementById('modelSelectorLabel');
  var dot = document.getElementById('modelSelectorDot');
  var badge = document.getElementById('activeModelBadge');
  if (!label) return;

  var getAgentType = window.getCurrentAgentType || function() { return 'main'; };
  var getDefault = window.getDefaultModelForAgent || function() { return 'openai/gpt-4o-mini'; };
  var getInfo = window.getModelInfo || function(id) { return { name: id, tier: 'paid' }; };
  
  var agentType = getAgentType();
  var modelId = (window.AppState && AppState.chatOverrideModel) || getDefault(agentType);
  var info = getInfo(modelId);

  if (window.AppState && AppState.chatOverrideModel) {
    label.textContent = info.name.split(' ').slice(0, 2).join(' ');
    label.style.color = 'var(--red-core)';
    if (dot) dot.style.background = info.tier === 'free' ? '#44dd88' : '#FF0A2F';
  } else {
    label.textContent = 'Auto';
    label.style.color = 'var(--text-muted)';
    if (dot) dot.style.background = 'var(--text-muted)';
  }

  if (badge) badge.textContent = info.name.split(' ').slice(0, 2).join(' ').toUpperCase();
}

// Initialize model selector after DOM
document.addEventListener('DOMContentLoaded', function() {
  initModelSelector();
});

// ── Typing Indicator ───────────────────────────────────────
function showTyping(container) {
  var div = document.createElement('div');
  div.className = 'message assistant-msg typing-msg';
  div.innerHTML = '' +
    '<div class="msg-avatar">' +
      '<svg width="28" height="28" viewBox="0 0 28 28" fill="none">' +
        '<circle cx="14" cy="14" r="13" fill="#1A0A0F" stroke="#FF0A2F" stroke-width="1"/>' +
        '<circle cx="14" cy="14" r="5" fill="#FF0A2F" opacity="0.5"/>' +
      '</svg>' +
    '</div>' +
    '<div class="msg-content">' +
      '<div class="typing-indicator">' +
        '<span class="typing-dot"></span>' +
        '<span class="typing-dot"></span>' +
        '<span class="typing-dot"></span>' +
      '</div>' +
    '</div>';
  if (container) {
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
  }
  return div;
}

function removeTyping(el) {
  if (el && el.parentNode) el.parentNode.removeChild(el);
}

// ── Latency display ────────────────────────────────────────
function updateLatency(ms) {
  var el = document.getElementById('apiLatency');
  if (!el) return;
  el.textContent = ms < 1000 ? ms + 'ms' : (ms/1000).toFixed(1) + 's';
  if (window.AppState) AppState.apiLatency = ms;
}

// ── Helpers ────────────────────────────────────────────────
function escapeHtml(s) {
  if (typeof s !== 'string') return String(s || '');
  return s.replace(/&/g,'&').replace(/</g,'<').replace(/>/g,'>').replace(/"/g,'"');
}

function markdownToHtml(text) {
  if (typeof text !== 'string') return String(text || '');
  return text
    .replace(/```(\w+)?\n([\s\S]*?)```/g, function(_, lang, code) {
      return '<pre style="background:#05050D;border:1px solid rgba(255,10,47,0.2);border-radius:8px;padding:12px 14px;overflow-x:auto;margin:8px 0;font-family:var(--font-mono);font-size:12px;line-height:1.6;color:#AAAACC"><code>' + escapeHtml(code.trim()) + '</code></pre>';
    })
    .replace(/`([^`]+)`/g, '<code style="background:#1A0A0F;border:1px solid rgba(255,10,47,0.2);border-radius:4px;padding:1px 5px;font-family:var(--font-mono);font-size:0.88em;color:#FF8899">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/^### (.+)$/gm, '<h4 style="color:#FF3355;font-size:13px;margin:8px 0 4px;font-weight:700">$1</h4>')
    .replace(/^## (.+)$/gm, '<h3 style="color:#FF3355;font-size:14px;margin:10px 0 5px;font-weight:700">$1</h3>')
    .replace(/^- (.+)$/gm, '<div style="padding-left:12px;margin:2px 0">• $1</div>')
    .replace(/^\d+\. (.+)$/gm, '<div style="padding-left:12px;margin:2px 0">$1</div>')
    .replace(/\n\n/g, '<br><br>')
    .replace(/\n/g, '<br>');
}

function estimateTokens(text) {
  if (typeof text !== 'string') return 0;
  return Math.ceil(text.length / 4);
}

function loadSettings() {
  try {
    return JSON.parse(localStorage.getItem('nexus_settings') || '{}');
  } catch(e) { 
    return {}; 
  }
}
