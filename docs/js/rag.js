/* ============================================================
   NEXUS AI — RAG System UI
   Document upload, chunk preview, vector search, source attribution
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const RAGState = {
  documents: [],
  chunks: [],
  isIndexing: false,
  lastQuery: '',
  searchResults: []
};

// ── DOM ────────────────────────────────────────────────────
const docUploadPanel    = document.getElementById('documentUploadPanel');
const chunkPreviewView  = document.getElementById('chunkPreviewView');
const searchQueryView   = document.getElementById('searchQueryView');
const sourceAttributionView = document.getElementById('sourceAttributionView');
const ragUploadBtn      = document.getElementById('ragUploadBtn');
const ragFileInput      = document.getElementById('ragFileInput');
const ragSearchBtn      = document.getElementById('ragSearchBtn');
const ragSearchInput    = document.getElementById('ragSearchInput');
const ragIndexBtn       = document.getElementById('ragIndexBtn');
const ragClearBtn       = document.getElementById('ragClearBtn');
const docCountBadge     = document.getElementById('docCountBadge');
const chunkCountBadge   = document.getElementById('chunkCountBadge');

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initRAG();
  initUpload();
  initSearch();
});

function initRAG() {
  // Demo documents
  RAGState.documents = [
    { id: 'doc-1', name: 'nexus-readme.md', size: 2450, chunks: 12, status: 'indexed' },
    { id: 'doc-2', name: 'api-reference.pdf', size: 128000, chunks: 45, status: 'indexed' }
  ];
  RAGState.chunks = generateDemoChunks();

  updateStats();
  renderDocuments();
  renderChunks();
}

function initUpload() {
  ragUploadBtn?.addEventListener('click', () => ragFileInput?.click());

  ragFileInput?.addEventListener('change', e => {
    const file = e.target.files[0];
    if (!file) return;
    handleDocumentUpload(file);
  });

  // Drag & drop
  docUploadPanel?.addEventListener('dragover', e => {
    e.preventDefault();
    docUploadPanel.classList.add('drag-over');
  });
  docUploadPanel?.addEventListener('dragleave', () => {
    docUploadPanel.classList.remove('drag-over');
  });
  docUploadPanel?.addEventListener('drop', e => {
    e.preventDefault();
    docUploadPanel.classList.remove('drag-over');
    const file = e.dataTransfer.files[0];
    if (file) handleDocumentUpload(file);
  });
}

function initSearch() {
  ragSearchBtn?.addEventListener('click', performRAGSearch);
  ragSearchInput?.addEventListener('keydown', e => {
    if (e.key === 'Enter') performRAGSearch();
  });
  ragIndexBtn?.addEventListener('click', indexDocuments);
  ragClearBtn?.addEventListener('click', clearRAG);
}

// ── Document Upload ────────────────────────────────────────
function handleDocumentUpload(file) {
  showToast(`Uploading ${file.name}...`);

  const doc = {
    id: 'doc-' + Date.now(),
    name: file.name,
    size: file.size,
    chunks: 0,
    status: 'uploading'
  };

  RAGState.documents.push(doc);
  renderDocuments();
  updateStats();

  // Simulate upload + chunking
  setTimeout(() => {
    doc.status = 'processing';
    renderDocuments();

    setTimeout(() => {
      doc.chunks = Math.floor(file.size / 800) + 1;
      doc.status = 'indexed';
      RAGState.chunks.push(...generateChunksForDoc(doc));
      renderDocuments();
      renderChunks();
      updateStats();
      showToast(`${file.name} indexed (${doc.chunks} chunks)`);
    }, 1500);
  }, 800);
}

function generateChunksForDoc(doc) {
  const count = doc.chunks || 5;
  return Array.from({ length: count }, (_, i) => ({
    id: `${doc.id}-chunk-${i}`,
    docId: doc.id,
    docName: doc.name,
    text: `Sample chunk ${i + 1} from ${doc.name}. This represents a vectorized segment of the document content that can be retrieved during semantic search operations.`,
    tokens: Math.floor(Math.random() * 100 + 50),
    embedding: Array.from({ length: 384 }, () => Math.random() - 0.5)
  }));
}

function generateDemoChunks() {
  const chunks = [];
  RAGState.documents.forEach(doc => {
    chunks.push(...generateChunksForDoc(doc));
  });
  return chunks;
}

// ── Rendering ──────────────────────────────────────────────
function renderDocuments() {
  if (!docUploadPanel) return;

  const list = docUploadPanel.querySelector('.doc-list') || docUploadPanel;
  const existingList = list.querySelector('.rag-doc-list');
  if (existingList) existingList.remove();

  const container = document.createElement('div');
  container.className = 'rag-doc-list';

  if (RAGState.documents.length === 0) {
    container.innerHTML = `
      <div class="rag-empty">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" style="opacity:0.3">
          <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z" fill="#FF0A2F"/>
        </svg>
        <p>Drop documents here</p>
        <span style="color:#666;font-size:12px">PDF, MD, TXT supported</span>
      </div>
    `;
  } else {
    container.innerHTML = RAGState.documents.map(doc => {
      const sizeStr = doc.size > 1024
        ? `${(doc.size / 1024).toFixed(1)} KB`
        : `${doc.size} B`;

      const statusColors = {
        uploading: '#FFAA44',
        processing: '#77CCFF',
        indexed: '#44AA66',
        error: '#FF4466'
      };

      return `
        <div class="rag-doc-item" data-doc-id="${doc.id}">
          <div class="rag-doc-icon">
            ${doc.name.endsWith('.pdf') ? '📕' : doc.name.endsWith('.md') ? '📝' : '📄'}
          </div>
          <div class="rag-doc-info">
            <div class="rag-doc-name">${escHtml(doc.name)}</div>
            <div class="rag-doc-meta">${sizeStr} · ${doc.chunks} chunks</div>
          </div>
          <div class="rag-doc-status" style="color:${statusColors[doc.status] || '#888'}">
            ${doc.status === 'uploading' ? '↑' : doc.status === 'processing' ? '◐' : '●'} ${doc.status}
          </div>
                    <button class="rag-doc-delete" onclick="deleteDocument('${doc.id}')" title="Remove">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" fill="#FF4466"/>
            </svg>
          </button>
        </div>
      `;
    }).join('');
  }

  list.appendChild(container);
}

function renderChunks() {
  if (!chunkPreviewView) return;

  chunkPreviewView.innerHTML = '';

  if (RAGState.chunks.length === 0) {
    chunkPreviewView.innerHTML = `
      <div class="rag-empty">
        <p>No chunks available</p>
      </div>
    `;
    return;
  }

  RAGState.chunks.slice(0, 20).forEach(chunk => {
    const div = document.createElement('div');
    div.className = 'chunk-card';
    div.dataset.chunkId = chunk.id;

    div.innerHTML = `
      <div class="chunk-header">
        <span class="chunk-id">${chunk.id}</span>
        <span class="chunk-tokens">${chunk.tokens} tokens</span>
      </div>
      <div class="chunk-text">${escHtml(chunk.text.slice(0, 120))}${chunk.text.length > 120 ? '…' : ''}</div>
      <div class="chunk-meta">
        <span class="chunk-doc">${escHtml(chunk.docName)}</span>
        <span class="chunk-sim" style="color:#444466">—</span>
      </div>
    `;

    chunkPreviewView.appendChild(div);
  });
}

// ── Search ─────────────────────────────────────────────────
function performRAGSearch() {
  const query = ragSearchInput?.value.trim();
  if (!query) {
    showToast('Enter search query');
    return;
  }

  RAGState.lastQuery = query;
  showToast(`Searching: "${query.slice(0, 40)}..."`);

  if (sourceAttributionView) {
    sourceAttributionView.innerHTML = `
      <div class="search-loading">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" style="animation:spin 1s linear infinite">
          <circle cx="12" cy="12" r="10" stroke="#FF0A2F" stroke-width="2" fill="none" stroke-dasharray="30 10"/>
        </svg>
        <span>Retrieving relevant chunks...</span>
      </div>
    `;
  }

  setTimeout(() => {
    // Simulate semantic search
    const results = simulateVectorSearch(query);

    RAGState.searchResults = results;
    renderSearchResults(results);
    highlightRelevantChunks(results);
    updateChunkSimilarities(results);

    showToast(`Found ${results.length} relevant chunks`);
  }, 800);
}

function simulateVectorSearch(query) {
  // Simulate semantic similarity scoring
  return RAGState.chunks
    .map(chunk => ({
      ...chunk,
      score: Math.random() * 0.5 + 0.4 // 0.4-0.9 simulated score
    }))
    .sort((a, b) => b.score - a.score)
    .slice(0, 5);
}

function renderSearchResults(results) {
  if (!sourceAttributionView) return;

  if (results.length === 0) {
    sourceAttributionView.innerHTML = `
      <div class="rag-empty">
        <p>No relevant chunks found</p>
      </div>
    `;
    return;
  }

  sourceAttributionView.innerHTML = `
    <div class="search-header">
      <span style="color:#FF0A2F">Query: "${escHtml(RAGState.lastQuery)}"</span>
      <span style="color:#666;font-size:12px">${results.length} results</span>
    </div>
    ${results.map((r, i) => `
      <div class="source-card" data-chunk-id="${r.id}">
        <div class="source-rank">#${i + 1}</div>
        <div class="source-content">
          <div class="source-score">
            <div class="score-bar">
              <div class="score-fill" style="width:${r.score * 100}%;background:${scoreColor(r.score)}"></div>
            </div>
            <span class="score-value">${(r.score * 100).toFixed(1)}%</span>
          </div>
          <div class="source-text">${escHtml(r.text.slice(0, 150))}${r.text.length > 150 ? '…' : ''}</div>
          <div class="source-meta">
            <span>${escHtml(r.docName)}</span>
            <span>${r.tokens} tokens</span>
          </div>
        </div>
      </div>
    `).join('')}
  `;

  // Click to highlight chunk
  sourceAttributionView.querySelectorAll('.source-card').forEach(card => {
    card.addEventListener('click', () => {
      const chunkId = card.dataset.chunkId;
      scrollToChunk(chunkId);
    });
  });
}

function scoreColor(score) {
  if (score > 0.8) return '#44AA66';
  if (score > 0.6) return '#FFAA44';
  return '#FF4466';
}

function highlightRelevantChunks(results) {
  const resultIds = new Set(results.map(r => r.id));
  document.querySelectorAll('.chunk-card').forEach(card => {
    if (resultIds.has(card.dataset.chunkId)) {
      card.classList.add('relevant');
      card.style.borderColor = 'rgba(255, 10, 47, 0.4)';
    } else {
      card.classList.remove('relevant');
      card.style.borderColor = '';
    }
  });
}

function updateChunkSimilarities(results) {
  results.forEach(r => {
    const card = document.querySelector(`.chunk-card[data-chunk-id="${r.id}"]`);
    if (card) {
      const simEl = card.querySelector('.chunk-sim');
      if (simEl) {
        simEl.textContent = `${(r.score * 100).toFixed(1)}%`;
        simEl.style.color = scoreColor(r.score);
      }
    }
  });
}

function scrollToChunk(chunkId) {
  const chunk = document.querySelector(`.chunk-card[data-chunk-id="${chunkId}"]`);
  if (chunk) {
    chunk.scrollIntoView({ behavior: 'smooth', block: 'center' });
    chunk.style.animation = 'pulse 1s ease';
    setTimeout(() => chunk.style.animation = '', 1000);
  }
}

// ── Document Management ────────────────────────────────────
window.deleteDocument = function(docId) {
  RAGState.documents = RAGState.documents.filter(d => d.id !== docId);
  RAGState.chunks = RAGState.chunks.filter(c => c.docId !== docId);
  renderDocuments();
  renderChunks();
  updateStats();
  showToast('Document removed');
};

function indexDocuments() {
  if (RAGState.documents.length === 0) {
    showToast('No documents to index');
    return;
  }

  RAGState.isIndexing = true;
  showToast('Re-indexing all documents...');

  setTimeout(() => {
    RAGState.isIndexing = false;
    showToast('Re-index complete');
  }, 2000);
}

function clearRAG() {
  if (!confirm('Clear all documents and chunks?')) return;
  RAGState.documents = [];
  RAGState.chunks = [];
  RAGState.searchResults = [];
  renderDocuments();
  renderChunks();
  if (sourceAttributionView) sourceAttributionView.innerHTML = '';
  updateStats();
  showToast('RAG cleared');
}

function updateStats() {
  if (docCountBadge) docCountBadge.textContent = RAGState.documents.length;
  if (chunkCountBadge) chunkCountBadge.textContent = RAGState.chunks.length;
}

// ── Helpers ────────────────────────────────────────────────
function escHtml(s) {
  return s.replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
}
