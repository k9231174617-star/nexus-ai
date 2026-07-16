/* ============================================================
   NEXUS AI — Browser Agent UI
   Web navigation, content extraction, action recording
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const BrowserState = {
  currentUrl: '',
  history: [],
  canGoBack: false,
  canGoForward: false,
  isLoading: false,
  recordedActions: [],
  isRecording: false
};

// ── DOM ────────────────────────────────────────────────────
const addressBar       = document.getElementById('addressBar');
const webContentView   = document.getElementById('webContentView');
const browserBackBtn   = document.getElementById('browserBackBtn');
const browserFwdBtn    = document.getElementById('browserFwdBtn');
const browserReloadBtn = document.getElementById('browserReloadBtn');
const browserGoBtn     = document.getElementById('browserGoBtn');
const searchResultPanel = document.getElementById('searchResultPanel');
const actionRecorderView = document.getElementById('actionRecorderView');
const recordToggleBtn  = document.getElementById('recordToggleBtn');
const clearActionsBtn  = document.getElementById('clearActionsBtn');
const exportActionsBtn = document.getElementById('exportActionsBtn');

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initBrowser();
  initBrowserControls();
  initActionRecorder();
});

function initBrowser() {
  navigateTo('https://example.com');
}

function initBrowserControls() {
  addressBar?.addEventListener('keydown', e => {
    if (e.key === 'Enter') navigateTo(addressBar.value);
  });

  browserGoBtn?.addEventListener('click', () => navigateTo(addressBar.value));
  browserBackBtn?.addEventListener('click', goBack);
  browserFwdBtn?.addEventListener('click', goForward);
  browserReloadBtn?.addEventListener('click', () => navigateTo(BrowserState.currentUrl));
}

function initActionRecorder() {
  recordToggleBtn?.addEventListener('click', toggleRecording);
  clearActionsBtn?.addEventListener('click', clearRecordedActions);
  exportActionsBtn?.addEventListener('click', exportActions);
}

// ── Navigation ─────────────────────────────────────────────
function navigateTo(url) {
  if (!url) return;

  // Normalize URL
  if (!url.startsWith('http')) {
    if (url.includes('.') && !url.includes(' ')) {
      url = 'https://' + url;
    } else {
      // Search query
      performSearch(url);
      return;
    }
  }

  BrowserState.isLoading = true;
  updateLoadingState(true);
  addToHistory(url);

  // Simulate loading
  if (webContentView) {
    webContentView.innerHTML = `
      <div class="browser-loading">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" style="animation:spin 1s linear infinite">
          <circle cx="12" cy="12" r="10" stroke="#FF0A2F" stroke-width="2" fill="none" stroke-dasharray="30 10"/>
        </svg>
        <span>Loading ${escHtml(url)}...</span>
      </div>
    `;
  }

  setTimeout(() => {
    BrowserState.isLoading = false;
    updateLoadingState(false);
    renderPage(url);
    if (addressBar) addressBar.value = url;
    BrowserState.currentUrl = url;

    if (BrowserState.isRecording) {
      recordAction('navigate', { url });
    }
  }, 800 + Math.random() * 1000);
}

function renderPage(url) {
  if (!webContentView) return;

  const domain = new URL(url).hostname;

  // Simulated page content
  const pages = {
    'example.com': {
      title: 'Example Domain',
      content: `
        <h1>Example Domain</h1>
        <p>This domain is for use in illustrative examples in documents.</p>
        <p>You may use this domain in literature without prior coordination.</p>
        <a href="https://www.iana.org/domains/example">More information...</a>
      `
    },
    'github.com': {
      title: 'GitHub',
      content: `
        <h1>GitHub</h1>
        <p>Where the world builds software.</p>
        <ul>
          <li>Repositories</li>
          <li>Pull Requests</li>
          <li>Issues</li>
          <li>Actions</li>
        </ul>
      `
    },
    'kotlinlang.org': {
      title: 'Kotlin',
      content: `
        <h1>Kotlin Programming Language</h1>
        <p>Concise. Multiplatform. Fun.</p>
        <pre>fun main() {
    println("Hello, World!")
}</pre>
      `
    }
  };

  const page = pages[domain] || {
    title: domain,
    content: `<h1>${domain}</h1><p>Page content would appear here.</p><p>This is a simulated browser view.</p>`
  };

  webContentView.innerHTML = `
    <div class="browser-page">
      <h1 style="color:#FF0A2F;font-size:18px;margin-bottom:12px">${escHtml(page.title)}</h1>
      <div class="browser-page-content">${page.content}</div>
    </div>
  `;

  // Make links clickable
  webContentView.querySelectorAll('a').forEach(a => {
    a.addEventListener('click', e => {
      e.preventDefault();
      const href = a.getAttribute('href');
      if (href) navigateTo(href);
    });
  });
}

function goBack() {
  if (BrowserState.history.length < 2) {
    showToast('No history');
    return;
  }
  const prev = BrowserState.history[BrowserState.history.length - 2];
  BrowserState.history.pop(); // Remove current
  navigateTo(prev);
}

function goForward() {
  showToast('Forward navigation (demo)');
}

function performSearch(query) {
  if (!searchResultPanel) return;

  searchResultPanel.style.display = 'block';
  searchResultPanel.innerHTML = `
    <div class="search-loading">Searching for "${escHtml(query)}"...</div>
  `;

  setTimeout(() => {
    const results = [
      { title: `${query} - Wikipedia`, url: 'https://en.wikipedia.org', snippet: 'Wikipedia article about ' + query },
      { title: `${query} documentation`, url: 'https://docs.example.com', snippet: 'Official documentation and API reference' },
      { title: `Learn ${query} in 5 minutes`, url: 'https://tutorial.example.com', snippet: 'Quick start guide and examples' }
    ];

    searchResultPanel.innerHTML = `
      <div class="search-header">
        <span style="color:#FF0A2F">Results for "${escHtml(query)}"</span>
        <span style="color:#666;font-size:12px">${results.length} found</span>
      </div>
      ${results.map(r => `
        <div class="search-result" onclick="navigateTo('${r.url}')">
          <div class="search-result-title">${escHtml(r.title)}</div>
          <div class="search-result-url">${escHtml(r.url)}</div>
          <div class="search-result-snippet">${escHtml(r.snippet)}</div>
        </div>
      `).join('')}
    `;
  }, 600);
}

function addToHistory(url) {
  if (BrowserState.history.length > 50) {
    BrowserState.history = BrowserState.history.slice(-50);
  }
  BrowserState.history.push(url);
  BrowserState.canGoBack = BrowserState.history.length > 1;
  updateNavButtons();
}

function updateNavButtons() {
  if (browserBackBtn) {
    browserBackBtn.style.opacity = BrowserState.canGoBack ? '1' : '0.3';
    browserBackBtn.disabled = !BrowserState.canGoBack;
  }
  if (browserFwdBtn) {
    browserFwdBtn.style.opacity = BrowserState.canGoForward ? '1' : '0.3';
  }
}

function updateLoadingState(loading) {
  if (browserReloadBtn) {
    browserReloadBtn.innerHTML = loading
      ? `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" style="animation:spin 1s linear infinite"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" stroke="#FF0A2F" stroke-width="2" fill="none"/></svg>`
      : `<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z" fill="#FF0A2F"/></svg>`;
  }
}

// ── Content Extraction ─────────────────────────────────────
window.extractContent = function() {
  if (!webContentView) return;

  const text = webContentView.innerText || '';
  const clean = text.replace(/\s+/g, ' ').trim();

  const div = document.createElement('div');
  div.className = 'extracted-content';
  div.innerHTML = `
    <div class="extract-header">
      <span style="color:#FF0A2F">Extracted Content</span>
      <button onclick="this.parentElement.parentElement.remove()">×</button>
    </div>
    <pre style="max-height:200px;overflow:auto">${escHtml(clean.slice(0, 2000))}${clean.length > 2000 ? '…' : ''}</pre>
  `;

  webContentView.appendChild(div);

  if (BrowserState.isRecording) {
    recordAction('extract', { length: clean.length });
  }

  showToast(`Extracted ${clean.length} chars`);
};

window.takeScreenshot = function() {
  showToast('Screenshot captured (simulated)');
  if (BrowserState.isRecording) {
    recordAction('screenshot', { url: BrowserState.currentUrl });
  }
};

// ── Action Recorder ────────────────────────────────────────
function toggleRecording() {
  BrowserState.isRecording = !BrowserState.isRecording;

  if (recordToggleBtn) {
    recordToggleBtn.classList.toggle('active', BrowserState.isRecording);
    recordToggleBtn.innerHTML = BrowserState.isRecording
      ? `<svg width="14" height="14" viewBox="0 0 24 24" fill="#FF0A2F"><circle cx="12" cy="12" r="6"/></svg> Recording`
      : `<svg width="14" height="14" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="6" stroke="#FF0A2F" stroke-width="2"/></svg> Record`;
  }

  showToast(BrowserState.isRecording ? 'Recording started' : 'Recording stopped');
}

function recordAction(type, data) {
  BrowserState.recordedActions.push({
    type,
    data,
    timestamp: Date.now(),
    url: BrowserState.currentUrl
  });
  renderRecordedActions();
}

function renderRecordedActions() {
  if (!actionRecorderView) return;

  if (BrowserState.recordedActions.length === 0) {
    actionRecorderView.innerHTML = `
      <div class="recorder-empty">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" style="opacity:0.3">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z" fill="#FF0A2F"/>
        </svg>
        <p>No recorded actions</p>
      </div>
    `;
    return;
  }

  actionRecorderView.innerHTML = BrowserState.recordedActions.map((action, i) => {
    const time = new Date(action.timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });

    const icons = {
      navigate: '🔗',
      extract:  '📄',
      screenshot: '📸',
      click:    '🖱️',
      scroll:   '📜'
    };

    return `
      <div class="action-entry">
        <span class="action-num">#${i + 1}</span>
        <span class="action-icon">${icons[action.type] || '•'}</span>
        <span class="action-type">${action.type}</span>
        <span class="action-time">${time}</span>
        <span class="action-url">${escHtml(action.url.slice(0, 40))}…</span>
      </div>
    `;
  }).join('');
}

function clearRecordedActions() {
  BrowserState.recordedActions = [];
  renderRecordedActions();
  showToast('Actions cleared');
}

function exportActions() {
  if (BrowserState.recordedActions.length === 0) {
    showToast('Nothing to export');
    return;
  }

  const data = {
    exported: new Date().toISOString(),
    actions: BrowserState.recordedActions
  };

  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: 'application/json'
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `nexus-browser-actions-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
  showToast('Actions exported');
}

// ── Helpers ────────────────────────────────────────────────
function escHtml(s) {
  return s.replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
}
