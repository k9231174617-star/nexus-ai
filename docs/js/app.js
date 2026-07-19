/* ============================================================
   NEXUS AI — App Core: Navigation, Sidebar, Tabs
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const AppState = {
  activeTab: 'main',
  sessionStart: Date.now(),
  messageCount: 0,
  totalTokens: 0,
  apiLatency: null,
  injectedContext: null,
  models: {
    main: 'dolphin-2.6-mistral',
    code: 'deepseek-coder-v2',
    universal: 'nous-hermes-2-mixtral'
  },
  // Override model selected manually in chat bar (null = auto)
  chatOverrideModel: null,
};

// ── Model Database ─────────────────────────────────────────
// Free models (OpenRouter): no API key needed
// Paid models: require Custom API key in Settings
const MODEL_DATABASE = [
  // Free models (OpenRouter — no API key needed)
  { id: 'openai/gpt-4o-mini',                name: 'GPT-4o Mini',            tier: 'free',  type: 'general',  provider: 'OpenRouter' },
  { id: 'deepseek/deepseek-chat',            name: 'DeepSeek Chat',          tier: 'free',  type: 'general',  provider: 'OpenRouter' },
  { id: 'deepseek/deepseek-coder',           name: 'DeepSeek Coder',         tier: 'free',  type: 'code',     provider: 'OpenRouter' },
  { id: 'meta-llama/llama-3.2-3b-instruct',  name: 'Llama 3.2 3B',          tier: 'free',  type: 'general',  provider: 'OpenRouter' },
  { id: 'mistralai/mistral-7b-instruct',     name: 'Mistral 7B Instruct',    tier: 'free',  type: 'general',  provider: 'OpenRouter' },
  { id: 'qwen/qwen-2.5-7b-instruct',        name: 'Qwen 2.5 7B',           tier: 'free',  type: 'general',  provider: 'OpenRouter' },

  // Paid models (via OpenRouter API key)
  { id: 'openai/gpt-4o',                     name: 'GPT-4o',                 tier: 'paid',  type: 'general',  provider: 'OpenAI' },
  { id: 'openai/gpt-5.6',                    name: 'GPT 5.6',                tier: 'paid',  type: 'general',  provider: 'OpenAI' },
  { id: 'anthropic/claude-3.5-sonnet',        name: 'Claude 3.5 Sonnet',      tier: 'paid',  type: 'general',  provider: 'Anthropic' },
  { id: 'anthropic/claude-3-haiku',           name: 'Claude 3 Haiku',         tier: 'paid',  type: 'general',  provider: 'Anthropic' },
  { id: 'anthropic/claude-3-opus',            name: 'Claude 3 Opus',          tier: 'paid',  type: 'general',  provider: 'Anthropic' },
  { id: 'google/gemini-2.0-flash-001',       name: 'Gemini 2.0 Flash',       tier: 'paid',  type: 'general',  provider: 'Google' },
  { id: 'google/gemini-pro',                 name: 'Gemini Pro',             tier: 'paid',  type: 'general',  provider: 'Google' },
  { id: 'moonshotai/moonshot-v1-128k',       name: 'Moonshot v1',            tier: 'paid',  type: 'general',  provider: 'Moonshot' },
  { id: 'cohere/command-r-plus',             name: 'Command R+',             tier: 'paid',  type: 'general',  provider: 'Cohere' },
];

// Auto-select best model for an agent type
function getDefaultModelForAgent(agentType) {
  const settings = JSON.parse(localStorage.getItem('nexus_settings') || '{}');

  // User has per-agent model set in settings
  const savedKey = agentType === 'main' ? 'mainModel' : agentType === 'code' ? 'codeModel' : 'uniModel';
  if (settings[savedKey] && settings[savedKey] !== '__auto__') return settings[savedKey];

  // Auto-select best OpenRouter model by agent type
  if (agentType === 'code') return 'deepseek/deepseek-coder';
  if (agentType === 'universal') return 'openai/gpt-4o-mini';
  return 'openai/gpt-4o-mini';
}

// Get current agent type from active tab
function getCurrentAgentType() {
  return AppState.activeTab === 'code' ? 'code' : 
         AppState.activeTab === 'universal' ? 'universal' : 'main';
}

// Get model display info
function getModelInfo(modelId) {
  return MODEL_DATABASE.find(m => m.id === modelId) || 
         { id: modelId, name: modelId, tier: 'paid', type: 'general', provider: 'Custom' };
}

// Get models by tier
function getModelsByTier(tier) {
  return MODEL_DATABASE.filter(m => m.tier === tier);
}

// Get all model IDs for select options
function getAllModelOptions() {
  return MODEL_DATABASE.map(m => ({ value: m.id, label: `${m.name} (${m.provider}, ${m.tier === 'free' ? 'Free' : 'Paid'})` }));
}

// Tab metadata for topbar title
const TAB_META = {
  main:      { title: 'Main Agent',     iconPath: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z' },
  code:      { title: 'Code Agent',      iconPath: 'M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z' },
  universal: { title: 'Universal Agent', iconPath: 'M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z' },
  cli:       { title: 'CLI Terminal',    iconPath: 'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8h16v10zm-9-1h2v-2h-2v2zm-4 0h2v-2H7v2zm8 0h2v-2h-2v2zM5 10l1.5 1.5L5 13l1 1 2.5-2.5L6 9l-1 1z' },
  files:     { title: 'File Manager',    iconPath: 'M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z' },
  memory:    { title: 'Session Memory',  iconPath: 'M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7c-1.1 0-2 .9-2 2v2H3v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2zm-4 6H7V7h10v10z' },
  settings:  { title: 'Settings',        iconPath: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.07.63-.07.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z' }
};

// ── DOM refs ───────────────────────────────────────────────
const $ = id => document.getElementById(id);
const $q = sel => document.querySelector(sel);
const $qa = sel => document.querySelectorAll(sel);

// ── Attach Button ────────────────────────────────────────
function initAttachButton() {
  var attachBtn = document.getElementById('attachBtn');
  var fileInput = document.getElementById('fileInput');
  if (!attachBtn || !fileInput) return;

  attachBtn.addEventListener('click', function() {
    fileInput.click();
  });

  fileInput.addEventListener('change', function() {
    var files = fileInput.files;
    if (!files || files.length === 0) return;
    var file = files[0];

    // Show attachment preview
    var preview = document.getElementById('attachmentPreview');
    var nameEl = document.getElementById('attachName');
    var removeBtn = document.getElementById('removeAttach');
    if (preview) preview.style.display = 'flex';
    if (nameEl) nameEl.textContent = file.name;

    if (removeBtn) {
      removeBtn.onclick = function() {
        preview.style.display = 'none';
        fileInput.value = '';
      };
    }

    // If image, show thumbnail
    if (file.type.startsWith('image/')) {
      var reader = new FileReader();
      reader.onload = function(e) {
        var imgEl = document.getElementById('attachThumb');
        if (imgEl) {
          imgEl.src = e.target.result;
          imgEl.style.display = 'block';
        }
      };
      reader.readAsDataURL(file);
    }

    console.log('[Nexus] File attached:', file.name, file.type, file.size);
  });
}

// ── Init Session (placeholder) ─────────────────────────────
function initSession() {
  console.log('[Nexus] initSession (no-op)');
}

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  console.log('[Nexus] init started');
  document.body.classList.add('app-loaded');
  
  try { initNav(); } catch(e) { console.error('[Nexus] initNav:', e); }
  try { initSidebar(); } catch(e) { console.error('[Nexus] initSidebar:', e); }
  try { initSession(); } catch(e) { console.error('[Nexus] initSession:', e); }
  try { initContextBar(); } catch(e) { console.error('[Nexus] initContextBar:', e); }
  try { populateFiles(); } catch(e) { console.error('[Nexus] populateFiles:', e); }
  try { startSessionTimer(); } catch(e) { console.error('[Nexus] startSessionTimer:', e); }
  try { initAttachButton(); } catch(e) { console.error('[Nexus] initAttachButton:', e); }
  
  console.log('[Nexus] init complete');
  
  // Debug: check sidebar state
  var sidebar = document.getElementById('sidebar');
  var menuToggle = document.getElementById('menuToggle') || document.querySelector('.topbar-menu');
  console.log('[Nexus] sidebar:', sidebar ? 'found' : 'missing', 'menuToggle:', menuToggle ? 'found' : 'missing');
  if (sidebar) {
    console.log('[Nexus] sidebar display:', window.getComputedStyle(sidebar).display);
    console.log('[Nexus] sidebar transform:', window.getComputedStyle(sidebar).transform);
  }
});

// ── Tab Navigation ─────────────────────────────────────────
function initNav() {
  $qa('.nav-item').forEach(item => {
    item.addEventListener('click', e => {
      e.preventDefault();
      const tab = item.dataset.tab;
      if (tab) switchTab(tab);
    });
  });
}

function switchTab(tab) {
  // Hide all panels
  $qa('.tab-panel').forEach(p => {
    p.classList.remove('active');
    p.style.display = 'none';
  });
  $qa('.nav-item').forEach(n => n.classList.remove('active'));

  // Show target
  const panel = $(`tab-${tab}`);
  const navItem = $q(`[data-tab="${tab}"]`);

  if (panel) {
    panel.classList.add('active');
    panel.style.display = 'flex';
  }
  if (navItem) navItem.classList.add('active');

  AppState.activeTab = tab;

  // Update topbar
  const meta = TAB_META[tab];
  if (meta) {
    const titleEl = $('topbarTitle');
    if (titleEl) {
      titleEl.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" class="topbar-icon">
          <path d="${meta.iconPath}" fill="#FF0A2F"/>
        </svg>
        ${meta.title}
      `;
    }
  }

  // Close sidebar on mobile
  if (window.innerWidth <= 768) closeSidebar();
  
  // Dispatch tab change event for model selector
  document.dispatchEvent(new CustomEvent('tabChanged', { detail: { tab } }));
}

// ── Sidebar ────────────────────────────────────────────────
function initSidebar() {
  var sidebar  = document.getElementById('sidebar');
  var overlay  = document.getElementById('overlay');
  var menuBtn  = document.getElementById('menuToggle');
  var closeBtn = document.getElementById('sidebarClose');

  if (!sidebar) {
    console.error('[Nexus] sidebar element not found!');
    return;
  }

  var isDesktop = function() { return window.innerWidth > 768; };

  function openSidebar() {
    sidebar.classList.add('open');
    if (overlay && !isDesktop()) {
      overlay.style.display = 'block';
      // Double rAF ensures display:block is painted before opacity transition
      requestAnimationFrame(function() {
        requestAnimationFrame(function() {
          overlay.style.opacity = '1';
        });
      });
    }
  }

  function closeSidebar() {
    sidebar.classList.remove('open');
    if (overlay) {
      overlay.style.opacity = '0';
      setTimeout(function() {
        if (!sidebar.classList.contains('open')) {
          overlay.style.display = 'none';
        }
      }, 280);
    }
  }

  // Desktop: always open on start
  if (isDesktop()) {
    sidebar.classList.add('open');
    if (overlay) overlay.style.display = 'none';
  }

  // Menu toggle: on desktop toggles sidebar, on mobile opens
  if (menuBtn) {
    menuBtn.addEventListener('click', function() {
      if (sidebar.classList.contains('open') && !isDesktop()) {
        closeSidebar();
      } else if (!sidebar.classList.contains('open')) {
        openSidebar();
      } else if (isDesktop()) {
        // On desktop, toggle sidebar visibility
        sidebar.classList.remove('open');
      }
    });
  }

  if (closeBtn) closeBtn.addEventListener('click', closeSidebar);
  if (overlay)  overlay.addEventListener('click', closeSidebar);

  document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && !isDesktop()) closeSidebar();
  });

  // On resize: keep sidebar open on desktop, close on mobile
  window.addEventListener('resize', function() {
    if (isDesktop()) {
      sidebar.classList.add('open');
      if (overlay) { overlay.style.opacity = '0'; overlay.style.display = 'none'; }
    }
  });

  window.openSidebar  = openSidebar;
  window.closeSidebar = closeSidebar;

  console.log('[Nexus] sidebar initialized, desktop:', isDesktop());
}

// ── Session Timer ──────────────────────────────────────────
function startSessionTimer() {
  setInterval(() => {
    const elapsed = Math.floor((Date.now() - AppState.sessionStart) / 1000);
    const m = String(Math.floor(elapsed / 60)).padStart(2, '0');
    const s = String(elapsed % 60).padStart(2, '0');
    const el = $('sessionTime');
    if (el) el.textContent = `${m}:${s}`;
  }, 1000);
}

// ── Context Bar ────────────────────────────────────────────
function initContextBar() {
  $qa('.ctx-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      $qa('.ctx-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    });
  });
}

// ── Token counter update ───────────────────────────────────
function updateTokenCount(n) {
  AppState.totalTokens += n;
  const el = $('tokenCount');
  if (el) el.textContent = AppState.totalTokens;
}

// ── Toast ──────────────────────────────────────────────────
window.showToast = function(msg, duration) {
  if (duration === undefined) duration = 2200;
  var t = document.getElementById('toast');
  if (!t) return;
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(function() { t.classList.remove('show'); }, duration);
};

window.clearAttachment = function() {
  var preview = document.getElementById('attachmentPreview');
  var fileInput = document.getElementById('fileInput');
  if (preview) preview.style.display = 'none';
  if (fileInput) fileInput.value = '';
};

// ── Files ──────────────────────────────────────────────────
function populateFiles() {
  const grid = $('filesGrid');
  if (!grid) return;

  const items = [
    { name: 'Documents', icon: '📁', size: '—', type: 'folder' },
    { name: 'Downloads', icon: '📂', size: '—', type: 'folder' },
    { name: 'main.py',   icon: '🐍', size: '4.2 KB', type: 'file' },
    { name: 'app.apk',   icon: '📦', size: '12.4 MB', type: 'file' },
    { name: 'data.json', icon: '📄', size: '1.1 KB', type: 'file' },
    { name: 'image.png', icon: '🖼️', size: '380 KB', type: 'file' },
    { name: 'notes.md',  icon: '📝', size: '2.8 KB', type: 'file' },
    { name: 'backup.zip',icon: '🗜️', size: '45 MB', type: 'file' },
  ];

  grid.innerHTML = items.map(f => `
    <div class="file-item" data-name="${f.name}" onclick="selectFile(this, '${f.name}')">
      <span class="file-icon">${f.icon}</span>
      <span class="file-name" style="font-size:12px;color:var(--text-primary)">${f.name}</span>
      <span class="file-size" style="font-size:10px;color:var(--text-muted)">${f.size}</span>
    </div>
  `).join('');
}

window.selectFile = function(el, name) {
  $qa('.file-item').forEach(i => i.classList.remove('selected'));
  el.classList.add('selected');

  const panel = $('filePreviewPanel');
  if (panel) {
    panel.style.display = 'flex';
    const fnEl = $('previewFileName');
    const fcEl = $('previewContent');
    if (fnEl) fnEl.textContent = name;
    if (fcEl) fcEl.textContent = 'File: ' + name + '\n\nClick "Анализировать AI" to analyze with NEXUS AI agent.';
  }
};

// ── Media Drop Zone ────────────────────────────────────────
const dropZone = $('mediaDropZone');
if (dropZone) {
  dropZone.addEventListener('dragover', e => {
    e.preventDefault();
    dropZone.classList.add('drag-over');
  });
  dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));
  dropZone.addEventListener('drop', e => {
    e.preventDefault();
    dropZone.classList.remove('drag-over');
    const file = e.dataTransfer.files[0];
    if (file) handleMediaFile(file);
  });
  $('mediaUploadBtn')?.addEventListener('click', () => $('mediaFileInput').click());
  $('mediaFileInput')?.addEventListener('change', e => {
    const file = e.target.files[0];
    if (file) handleMediaFile(file);
  });
}

function handleMediaFile(file) {
  showToast(`Loaded: ${file.name}`);
  const msgs = $('uniChatMessages');
  if (msgs) {
    appendMessage(msgs, 'assistant', `Файл <strong>${file.name}</strong> (${(file.size/1024).toFixed(1)} KB) загружен. Что с ним сделать?`);
  }
}

// ── Media tool buttons ─────────────────────────────────────
$qa('.media-tool-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    $qa('.media-tool-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
  });
});

// ── APK Workspace ──────────────────────────────────────────
$('openAPKBtn')?.addEventListener('click', () => {
  showToast('APK Workspace: загрузите .apk файл');
});

// ── Helper: append message ─────────────────────────────────
window.appendMessage = function(container, role, html, name) {
  const isUser = role === 'user';
  const div = document.createElement('div');
  div.className = `message ${isUser ? 'user-msg' : 'assistant-msg'}`;

  const now = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  if (isUser) {
    div.innerHTML = `
      <div class="user-avatar-dot">Y</div>
      <div class="msg-content">
        <div class="msg-header">
          <span class="msg-name" style="color:#8888AA">YOU</span>
          <span class="msg-time">${now}</span>
        </div>
        <div class="msg-text">${html}</div>
      </div>
    `;
  } else {
    div.innerHTML = `
      <div class="msg-avatar">
        <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
          <circle cx="14" cy="14" r="13" fill="#1A0A0F" stroke="#FF0A2F" stroke-width="1"/>
          <circle cx="14" cy="14" r="5" fill="#FF0A2F" opacity="0.8"/>
          <path d="M14 6 L16 10 L14 9 L12 10 Z" fill="#FF0A2F"/>
        </svg>
      </div>
      <div class="msg-content">
        <div class="msg-header">
          <span class="msg-name">${name || 'NEXUS'}</span>
          <span class="msg-time">${now}</span>
        </div>
        <div class="msg-text">${html}</div>
      </div>
    `;
  }

  container.appendChild(div);
  container.scrollTop = container.scrollHeight;

  AppState.messageCount++;
  const mc = $('msgCount');
  if (mc) mc.textContent = AppState.messageCount;
};

// ── Memory Entry ───────────────────────────────────────────
function addMemoryEntry(type, text) {
  const tl = $('memoryTimeline');
  if (!tl) return;

  // Remove empty state
  const empty = tl.querySelector('.mem-empty');
  if (empty) empty.remove();

  const now = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const icons = {
    context: '💡',
    user: '👤',
    agent: '🤖',
    file: '📄',
    cmd: '⚡'
  };

  const div = document.createElement('div');
  div.className = 'mem-entry';
  div.innerHTML = `
    <div class="mem-entry-icon">${icons[type] || '•'}</div>
    <div class="mem-entry-text">${text.slice(0, 120)}${text.length > 120 ? '…' : ''}</div>
    <div class="mem-entry-time">${now}</div>
  `;
  tl.appendChild(div);

  // Update counter
  const entries = tl.querySelectorAll('.mem-entry').length;
  const el = $('memEntries');
  if (el) el.textContent = entries;
}

window.addMemoryEntry = addMemoryEntry;
window.updateTokenCount = updateTokenCount;
window.AppState = AppState;
window.MODEL_DATABASE = MODEL_DATABASE;
window.getDefaultModelForAgent = getDefaultModelForAgent;
window.getCurrentAgentType = getCurrentAgentType;
window.getModelInfo = getModelInfo;

// ─── Plugin System Integration ───────────────────────────────

function initPluginSystem() {
    const pm = window.NexusPlugins;
    if (!pm) return;

    pm.on('app:render', () => {
        renderPluginSidebarItems();
    });
}

function renderPluginSidebarItems() {
    const pm = window.NexusPlugins;
    if (!pm) return;

    const items = pm.getSidebarItems();
    if (items.length === 0) return;

    // Find the tools section in sidebar
    const nav = document.querySelector('.sidebar-nav');
    if (!nav) return;

    const toolsSection = nav.querySelector('[data-section="tools"]') || nav.querySelector('*:nth-child(3)');

    items.forEach(item => {
        const el = document.createElement('a');
        el.className = 'nav-item plugin-item';
        el.dataset.plugin = item.id;
        el.innerHTML = `<span class="nav-icon">${item.icon || '🧩'}</span>${item.label}`;
        el.onclick = (e) => {
            e.preventDefault();
            if (item.onClick) item.onClick();
            // Close sidebar on mobile
            const sidebar = document.getElementById('sidebar');
            if (sidebar && window.innerWidth <= 768) {
                sidebar.classList.remove('open');
            }
        };
        if (toolsSection) {
            toolsSection.parentNode.insertBefore(el, toolsSection.nextSibling);
        } else {
            nav.appendChild(el);
        }
    });
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initPluginSystem);
} else {
    initPluginSystem();
}
