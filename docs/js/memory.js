/* ============================================================
   NEXUS AI — Session Memory UI
   Timeline, context injection, memory stats
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const MemoryState = {
  entries: [],
  filter: 'all',
  searchQuery: '',
  selectedEntry: null
};

// ── DOM ────────────────────────────────────────────────────
const memTimeline    = document.getElementById('memoryTimeline');
const memSearch      = document.getElementById('memSearch');
const memFilterAll   = document.getElementById('memFilterAll');
const memFilterCtx   = document.getElementById('memFilterCtx');
const memFilterUser  = document.getElementById('memFilterUser');
const memFilterAgent = document.getElementById('memFilterAgent');
const memEntries     = document.getElementById('memEntries');
const memClearBtn    = document.getElementById('memClearBtn');
const memExportBtn   = document.getElementById('memExportBtn');
const ctxInjectInput = document.getElementById('ctxInjectInput');
const injectCtxBtn   = document.getElementById('injectCtxBtn');

// ── Icons ──────────────────────────────────────────────────
const MEM_ICONS = {
  context: '💡',
  user:    '👤',
  agent:   '🤖',
  file:    '📄',
  cmd:     '⚡',
  system:  '🔧'
};

const MEM_COLORS = {
  context: '#FFAA44',
  user:    '#8888AA',
  agent:   '#FF0A2F',
  file:    '#44CC88',
  cmd:     '#FF4466',
  system:  '#AAAAAA'
};

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initMemory();
  initMemoryFilters();
  initMemorySearch();
  initContextInjection();
});

function initMemory() {
  // Load from localStorage if saveHistory enabled
  const settings = loadSettings();
  if (settings.saveHistory) {
    try {
      const saved = JSON.parse(localStorage.getItem('nexus_memory') || '[]');
      MemoryState.entries = saved;
      renderTimeline();
    } catch {}
  }

  // Initial empty state
  if (MemoryState.entries.length === 0) {
    showEmptyState();
  }

  updateEntryCount();
}

function initMemoryFilters() {
  const filters = [
    { el: memFilterAll,   type: 'all' },
    { el: memFilterCtx,   type: 'context' },
    { el: memFilterUser,  type: 'user' },
    { el: memFilterAgent, type: 'agent' }
  ];

  filters.forEach(f => {
    f.el?.addEventListener('click', () => {
      filters.forEach(x => x.el.classList.remove('active'));
      f.el.classList.add('active');
      MemoryState.filter = f.type;
      renderTimeline();
    });
  });
}

function initMemorySearch() {
  memSearch?.addEventListener('input', debounce(() => {
    MemoryState.searchQuery = memSearch.value.trim().toLowerCase();
    renderTimeline();
  }, 300));
}

function initContextInjection() {
  injectCtxBtn?.addEventListener('click', () => {
    const val = ctxInjectInput?.value.trim();
    if (!val) {
      showToast('Введите контекст для инъекции');
      return;
    }

    AppState.injectedContext = val;
    addMemoryEntry('context', val);
    ctxInjectInput.value = '';
    showToast('Контекст инжектирован ✓');

    // Visual feedback
    injectCtxBtn.style.background = 'rgba(255, 10, 47, 0.3)';
    setTimeout(() => {
      injectCtxBtn.style.background = '';
    }, 500);
  });

  ctxInjectInput?.addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      injectCtxBtn?.click();
    }
  });
}

// ── Timeline Rendering ─────────────────────────────────────
function renderTimeline() {
  if (!memTimeline) return;

  // Clear
  memTimeline.innerHTML = '';

  // Filter
  let filtered = MemoryState.entries;
  if (MemoryState.filter !== 'all') {
    filtered = filtered.filter(e => e.type === MemoryState.filter);
  }
  if (MemoryState.searchQuery) {
    filtered = filtered.filter(e =>
      e.text.toLowerCase().includes(MemoryState.searchQuery)
    );
  }

  // Empty state
  if (filtered.length === 0) {
    showEmptyState();
    updateEntryCount();
    return;
  }

  // Render entries
  filtered.forEach((entry, idx) => {
    const div = document.createElement('div');
    div.className = 'mem-entry';
    div.dataset.index = idx;
    div.dataset.id = entry.id;

    const isSelected = MemoryState.selectedEntry === entry.id;
    if (isSelected) div.classList.add('selected');

    const time = new Date(entry.timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit'
    });
    const date = new Date(entry.timestamp).toLocaleDateString([], {
      day: '2-digit',
      month: 'short'
    });

    div.innerHTML = `
      <div class="mem-entry-accent" style="background:${MEM_COLORS[entry.type] || '#FF0A2F'}"></div>
      <div class="mem-entry-icon">${MEM_ICONS[entry.type] || '•'}</div>
      <div class="mem-entry-content">
        <div class="mem-entry-header">
          <span class="mem-entry-type" style="color:${MEM_COLORS[entry.type] || '#FF0A2F'}">${entry.type.toUpperCase()}</span>
          <span class="mem-entry-time">${date} ${time}</span>
        </div>
        <div class="mem-entry-text">${escHtml(entry.text.slice(0, 200))}${entry.text.length > 200 ? '…' : ''}</div>
        ${entry.tokens ? `<div class="mem-entry-meta">${entry.tokens} tokens</div>` : ''}
      </div>
      <button class="mem-entry-delete" title="Delete" onclick="deleteMemoryEntry('${entry.id}')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
          <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" fill="#FF4466"/>
        </svg>
      </button>
    `;

    div.addEventListener('click', () => selectMemoryEntry(entry.id));
    memTimeline.appendChild(div);
  });

  updateEntryCount();
}

function showEmptyState() {
  if (!memTimeline) return;
  memTimeline.innerHTML = `
    <div class="mem-empty">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" style="opacity:0.3">
        <path d="M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7c-1.1 0-2 .9-2 2v2H3v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2zm-4 6H7V7h10v10z" fill="#FF0A2F"/>
      </svg>
      <p>No memory entries yet</p>
      <span style="color:#666;font-size:12px">Chat messages and context will appear here</span>
    </div>
  `;
}

function selectMemoryEntry(id) {
  MemoryState.selectedEntry = MemoryState.selectedEntry === id ? null : id;
  renderTimeline();

  const entry = MemoryState.entries.find(e => e.id === id);
  if (!entry) return;

  // Show in detail panel if exists
  const detailPanel = document.getElementById('memoryDetailPanel');
  if (detailPanel) {
    detailPanel.style.display = 'block';
    detailPanel.innerHTML = `
      <div class="mem-detail-header">
        <span class="mem-detail-type" style="color:${MEM_COLORS[entry.type]}">${entry.type.toUpperCase()}</span>
        <span class="mem-detail-time">${new Date(entry.timestamp).toLocaleString()}</span>
      </div>
      <div class="mem-detail-text">${escHtml(entry.text)}</div>
      ${entry.tokens ? `<div class="mem-detail-meta">Tokens: ${entry.tokens}</div>` : ''}
    `;
  }
}

// ── Entry Management ───────────────────────────────────────
window.deleteMemoryEntry = function(id) {
  MemoryState.entries = MemoryState.entries.filter(e => e.id !== id);
  if (MemoryState.selectedEntry === id) {
    MemoryState.selectedEntry = null;
    const detailPanel = document.getElementById('memoryDetailPanel');
    if (detailPanel) detailPanel.style.display = 'none';
  }
  saveMemory();
  renderTimeline();
  showToast('Entry deleted');
};

memClearBtn?.addEventListener('click', () => {
  if (!confirm('Clear all memory entries?')) return;
  MemoryState.entries = [];
  MemoryState.selectedEntry = null;
  saveMemory();
  renderTimeline();
  showToast('Memory cleared');
});

memExportBtn?.addEventListener('click', () => {
  if (MemoryState.entries.length === 0) {
    showToast('Nothing to export');
    return;
  }

  const exportData = {
    exported: new Date().toISOString(),
    entries: MemoryState.entries
  };

  const blob = new Blob([JSON.stringify(exportData, null, 2)], {
    type: 'application/json'
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `nexus-memory-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
  showToast('Memory exported');
});

// ── Public API (called from app.js / chat.js) ──────────────
window.addMemoryEntry = function(type, text, tokens = null) {
  const entry = {
    id: 'mem-' + Date.now() + '-' + Math.random().toString(36).slice(2, 6),
    type,
    text,
    tokens,
    timestamp: Date.now()
  };

  MemoryState.entries.unshift(entry);
  if (MemoryState.entries.length > 500) {
    MemoryState.entries = MemoryState.entries.slice(0, 500);
  }

  saveMemory();
  renderTimeline();
};

function saveMemory() {
  const settings = loadSettings();
  if (settings.saveHistory) {
    localStorage.setItem('nexus_memory', JSON.stringify(MemoryState.entries));
  }
}

function updateEntryCount() {
  if (memEntries) {
    memEntries.textContent = MemoryState.entries.length;
  }
}

// ── Context Injection Panel ────────────────────────────────
window.injectContext = function(text) {
  AppState.injectedContext = text;
  addMemoryEntry('context', text);
};

// ── Helpers ────────────────────────────────────────────────
function debounce(fn, ms) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

function escHtml(s) {
  return s.replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
}
