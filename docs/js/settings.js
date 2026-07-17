/* ============================================================
   NEXUS AI — Files & Settings
   ============================================================ */

'use strict';

// ── Settings ───────────────────────────────────────────────
const SETTINGS_KEY = 'nexus_settings';

function loadSettings() {
  try { return JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}'); }
  catch { return {}; }
}

function saveSettings(data) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(data));
}

// ── Init Settings UI ───────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initSettings();
  initRanges();
  initCodeDrawer();
  initVoice();
});

function initSettings() {
  const s = loadSettings();

  // Populate model select dropdowns from MODEL_DATABASE
  populateModelSelects();

  // Populate other selects
  const fields = {
    customEndpoint:  'endpoint',
    apiKeyInput:     'apiKey',
  };

  Object.entries(fields).forEach(([id, key]) => {
    const el = document.getElementById(id);
    if (el && s[key]) el.value = s[key];
  });

  // Restore saved model selections
  ['mainModelSelect', 'codeModelSelect', 'uniModelSelect'].forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;
    const key = id === 'mainModelSelect' ? 'mainModel' : id === 'codeModelSelect' ? 'codeModel' : 'uniModel';
    if (s[key]) el.value = s[key];
  });

  // Toggles
  const toggleFields = {
    streamToggle:      'stream',
    autoCliToggle:     'autoCli',
    rootModeToggle:    'rootMode',
    saveHistoryToggle: 'saveHistory',
  };

  Object.entries(toggleFields).forEach(([id, key]) => {
    const el = document.getElementById(id);
    if (el && s[key] !== undefined) el.checked = s[key];
  });

  // Update model labels in sidebar
  updateModelLabels(s);
}

function populateModelSelects() {
  ['mainModelSelect', 'codeModelSelect', 'uniModelSelect'].forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;

    // Clear existing options
    el.innerHTML = '';

    // Add auto option
    const autoOpt = document.createElement('option');
    autoOpt.value = '__auto__';
    autoOpt.textContent = '⟲ Auto (по умолчанию для агента)';
    el.appendChild(autoOpt);

    // Group by tier
    const freeModels = MODEL_DATABASE.filter(m => m.tier === 'free');
    const paidModels = MODEL_DATABASE.filter(m => m.tier === 'paid');

    // Free models group
    const freeGroup = document.createElement('optgroup');
    freeGroup.label = 'Бесплатные (OpenRouter)';
    freeModels.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.id;
      opt.textContent = `${m.name} — ${m.provider}`;
      freeGroup.appendChild(opt);
    });
    el.appendChild(freeGroup);

    // Paid models group
    const paidGroup = document.createElement('optgroup');
    paidGroup.label = 'Платные (API Key)';
    paidModels.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.id;
      opt.textContent = `${m.name} — ${m.provider}`;
      paidGroup.appendChild(opt);
    });
    el.appendChild(paidGroup);
  });
}

function updateModelLabels(s) {
  const mainLabel = document.getElementById('mainModelLabel');
  const codeLabel = document.getElementById('codeModelLabel');
  const uniLabel  = document.getElementById('uniModelLabel');
  const modelBadge = document.getElementById('activeModelBadge');

  if (mainLabel && s.mainModel) mainLabel.textContent = s.mainModel.split('-').slice(0,2).join('-');
  if (codeLabel && s.codeModel) codeLabel.textContent = s.codeModel.split('-').slice(0,2).join('-');
  if (uniLabel  && s.uniModel)  uniLabel.textContent  = s.uniModel.split('-').slice(0,2).join('-');
  if (modelBadge && s.mainModel) modelBadge.textContent = s.mainModel.split('-').slice(0,2).join('-').toUpperCase();
}

// Save button
document.getElementById('saveApiSettings')?.addEventListener('click', () => {
  const s = loadSettings();

  const updates = {
    mainModel: document.getElementById('mainModelSelect')?.value,
    codeModel: document.getElementById('codeModelSelect')?.value,
    uniModel:  document.getElementById('uniModelSelect')?.value,
    endpoint:  document.getElementById('customEndpoint')?.value,
    apiKey:    document.getElementById('apiKeyInput')?.value,
    stream:    document.getElementById('streamToggle')?.checked,
    autoCli:   document.getElementById('autoCliToggle')?.checked,
    rootMode:  document.getElementById('rootModeToggle')?.checked,
    saveHistory: document.getElementById('saveHistoryToggle')?.checked,
  };

  // If model is set to auto, clear the saved preference
  if (updates.mainModel === '__auto__') delete updates.mainModel;
  if (updates.codeModel === '__auto__') delete updates.codeModel;
  if (updates.uniModel === '__auto__') delete updates.uniModel;

  Object.assign(s, updates);
  saveSettings(s);
  updateModelLabels(s);
  showToast('Settings saved ✓');
});

// Toggle API key visibility
document.getElementById('toggleApiKey')?.addEventListener('click', () => {
  const input = document.getElementById('apiKeyInput');
  if (!input) return;
  input.type = input.type === 'password' ? 'text' : 'password';
});

// Clear data
document.getElementById('clearDataBtn')?.addEventListener('click', () => {
  if (!confirm('Clear ALL data? This cannot be undone.')) return;
  localStorage.clear();
  showToast('All data cleared');
  setTimeout(() => location.reload(), 1000);
});

// ── Range sliders ──────────────────────────────────────────
function initRanges() {
  const s = loadSettings();

  const maxTokensRange = document.getElementById('maxTokensRange');
  const maxTokensVal   = document.getElementById('maxTokensVal');
  const tempRange      = document.getElementById('tempRange');
  const tempVal        = document.getElementById('tempVal');

  if (maxTokensRange) {
    if (s.maxTokens) maxTokensRange.value = s.maxTokens;
    maxTokensVal && (maxTokensVal.textContent = maxTokensRange.value);
    maxTokensRange.addEventListener('input', () => {
      maxTokensVal && (maxTokensVal.textContent = maxTokensRange.value);
      const sv = loadSettings();
      sv.maxTokens = parseInt(maxTokensRange.value);
      saveSettings(sv);
    });
  }

  if (tempRange) {
    if (s.temperature) tempRange.value = Math.round(s.temperature * 100);
    tempVal && (tempVal.textContent = (parseInt(tempRange.value) / 100).toFixed(2));
    tempRange.addEventListener('input', () => {
      const val = (parseInt(tempRange.value) / 100).toFixed(2);
      tempVal && (tempVal.textContent = val);
      const sv = loadSettings();
      sv.temperature = parseFloat(val);
      saveSettings(sv);
    });
  }

  // DB and cache size display
  try {
    const dbSizeEl = document.getElementById('dbSize');
    const cacheSizeEl = document.getElementById('cacheSize');
    if (dbSizeEl) {
      const stored = JSON.stringify(localStorage).length;
      dbSizeEl.textContent = stored > 1024
        ? `${(stored/1024).toFixed(1)} KB`
        : `${stored} B`;
    }
    if (cacheSizeEl) cacheSizeEl.textContent = '0 KB';
  } catch {}
}

// ── Code Drawer ────────────────────────────────────────────
function initCodeDrawer() {
  const toggle  = document.getElementById('codeDrawerToggle');
  const content = document.getElementById('codeDrawerContent');
  let open = true;

  toggle?.addEventListener('click', () => {
    open = !open;
    if (content) {
      content.style.display = open ? 'flex' : 'none';
      content.style.flexDirection = 'column';
    }
  });

  // File tree click
  document.querySelectorAll('.tree-item').forEach(item => {
    item.addEventListener('click', () => {
      document.querySelectorAll('.tree-item').forEach(i => i.classList.remove('active'));
      item.classList.add('active');

      const name = item.querySelector('span')?.textContent;
      if (name && !item.classList.contains('folder')) {
        // Update editor tab
        const activeTab = document.querySelector('.editor-tab.active');
        if (activeTab) activeTab.childNodes[0].textContent = name + ' ';

        // Add mini chat message
        const miniChat = document.getElementById('miniChat');
        if (miniChat) {
          const div = document.createElement('div');
          div.className = 'message assistant-msg mini';
          div.innerHTML = `<div class="msg-content"><div class="msg-text">Открыт файл <strong>${name}</strong>. Что нужно изменить?</div></div>`;
          miniChat.appendChild(div);
          miniChat.scrollTop = miniChat.scrollHeight;
        }
      }
    });
  });

  // New file button
  document.getElementById('newFileBtn')?.addEventListener('click', () => {
    const name = prompt('File name:');
    if (!name) return;
    const tree = document.getElementById('fileTree');
    if (tree) {
      const div = document.createElement('div');
      div.className = 'tree-item file';
      div.style.paddingLeft = '24px';
      div.innerHTML = `
        <svg class="tree-icon" viewBox="0 0 16 16"><path d="M2 1.75C2 .784 2.784 0 3.75 0h6.586c.464 0 .909.184 1.237.513l2.914 2.914c.329.328.513.773.513 1.237v9.586A1.75 1.75 0 0113.25 16h-9.5A1.75 1.75 0 012 14.25V1.75z" fill="#2A2A3A" stroke="#444" stroke-width="0.5"/></svg>
        <span>${name}</span>
      `;
      tree.insertBefore(div, tree.lastElementChild);
      showToast(`Created: ${name}`);
    }
  });

  // Editor tabs
  document.querySelector('.editor-tab.new-tab')?.addEventListener('click', () => {
    const tabs = document.getElementById('editorTabs');
    const newTab = document.createElement('div');
    newTab.className = 'editor-tab active';
    newTab.innerHTML = `untitled.kt <span class="tab-close">×</span>`;

    // Remove active from others
    tabs?.querySelectorAll('.editor-tab').forEach(t => t.classList.remove('active'));
    tabs?.insertBefore(newTab, tabs.querySelector('.new-tab'));

    newTab.addEventListener('click', () => {
      tabs?.querySelectorAll('.editor-tab').forEach(t => t.classList.remove('active'));
      newTab.classList.add('active');
    });
  });
}

// ── Voice Input ────────────────────────────────────────────
function initVoice() {
  const voiceBtn = document.getElementById('voiceBtn');
  if (!voiceBtn) return;

  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    voiceBtn.title = 'Voice not supported in this browser';
    voiceBtn.style.opacity = '0.4';
    return;
  }

  const recognition = new SpeechRecognition();
  recognition.lang = 'ru-RU';
  recognition.interimResults = false;

  let listening = false;

  voiceBtn.addEventListener('click', () => {
    if (listening) {
      recognition.stop();
      listening = false;
      voiceBtn.style.color = '';
      return;
    }
    recognition.start();
    listening = true;
    voiceBtn.style.color = 'var(--red-core)';
    showToast('🎙 Говорите...');
  });

  recognition.onresult = e => {
    const text = e.results[0][0].transcript;
    const input = document.getElementById('chatInput');
    if (input) {
      input.value = text;
      input.style.height = 'auto';
      input.style.height = Math.min(input.scrollHeight, 140) + 'px';
    }
    showToast(`Распознано: "${text.slice(0, 40)}..."`);
  };

  recognition.onend = () => {
    listening = false;
    voiceBtn.style.color = '';
  };

  recognition.onerror = e => {
    listening = false;
    voiceBtn.style.color = '';
    showToast('Voice error: ' + e.error);
  };
}
