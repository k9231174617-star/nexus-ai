/* ============================================================
   NEXUS AI — Graph Memory UI
   Entity graph visualization, relation editor, Cypher query panel
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const GraphState = {
  entities: [],
  relations: [],
  selectedNode: null,
  viewMode: 'graph', // 'graph' | 'table'
  query: ''
};

// ── DOM ────────────────────────────────────────────────────
const entityGraphView = document.getElementById('entityGraphView');
const relationEditor  = document.getElementById('relationEditor');
const graphQueryPanel = document.getElementById('graphQueryPanel');
const graphAddEntityBtn  = document.getElementById('graphAddEntityBtn');
const graphAddRelationBtn = document.getElementById('graphAddRelationBtn');
const graphRunQueryBtn    = document.getElementById('graphRunQueryBtn');
const graphQueryInput     = document.getElementById('graphQueryInput');
const graphStatsPanel     = document.getElementById('graphStatsPanel');
const graphViewToggle     = document.getElementById('graphViewToggle');

// ── Demo Data ──────────────────────────────────────────────
const DEMO_ENTITIES = [
  { id: 'ent-1', type: 'Person', label: 'Alice', properties: { age: '30', role: 'Developer' }, x: 200, y: 150 },
  { id: 'ent-2', type: 'Person', label: 'Bob', properties: { age: '28', role: 'Designer' }, x: 400, y: 100 },
  { id: 'ent-3', type: 'Project', label: 'Nexus AI', properties: { status: 'Active', lang: 'Kotlin' }, x: 300, y: 280 },
  { id: 'ent-4', type: 'Technology', label: 'Neo4j', properties: { category: 'Database', type: 'Graph' }, x: 500, y: 250 },
  { id: 'ent-5', type: 'Organization', label: 'Moonshot', properties: { location: 'Beijing' }, x: 150, y: 300 }
];

const DEMO_RELATIONS = [
  { id: 'rel-1', from: 'ent-1', to: 'ent-3', type: 'WORKS_ON', properties: { since: '2024' } },
  { id: 'rel-2', from: 'ent-2', to: 'ent-3', type: 'WORKS_ON', properties: { since: '2024' } },
  { id: 'rel-3', from: 'ent-1', to: 'ent-2', type: 'KNOWS', properties: { since: '2022' } },
  { id: 'rel-4', from: 'ent-3', to: 'ent-4', type: 'USES', properties: { version: '5.x' } },
  { id: 'rel-5', from: 'ent-5', to: 'ent-3', type: 'OWNS', properties: {} }
];

// ── Type Colors ────────────────────────────────────────────
const TYPE_COLORS = {
  Person: '#FF0A2F',
  Project: '#44AA66',
  Technology: '#77CCFF',
  Organization: '#FFAA44',
  Location: '#AA66FF',
  Event: '#FF66AA'
};

const RELATION_COLORS = {
  WORKS_ON: '#44AA66',
  KNOWS: '#77CCFF',
  USES: '#FFAA44',
  OWNS: '#FF0A2F',
  LOCATED_IN: '#AA66FF',
  PART_OF: '#FF66AA'
};

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initGraph();
  initGraphControls();
  initQueryPanel();
});

function initGraph() {
  GraphState.entities = [...DEMO_ENTITIES];
  GraphState.relations = [...DEMO_RELATIONS];
  renderGraph();
  renderRelationEditor();
  updateStats();
}

function initGraphControls() {
  graphAddEntityBtn?.addEventListener('click', showAddEntityModal);
  graphAddRelationBtn?.addEventListener('click', showAddRelationModal);
  graphViewToggle?.addEventListener('click', toggleGraphView);
}

function initQueryPanel() {
  graphRunQueryBtn?.addEventListener('click', runGraphQuery);
  graphQueryInput?.addEventListener('keydown', e => {
    if (e.key === 'Enter' && e.ctrlKey) runGraphQuery();
  });

  // Preset queries
  document.querySelectorAll('.query-preset').forEach(btn => {
    btn.addEventListener('click', () => {
      if (graphQueryInput) graphQueryInput.value = btn.dataset.query;
    });
  });
}

// ── Graph Rendering (SVG) ──────────────────────────────────
function renderGraph() {
  if (!entityGraphView) return;

  const width = entityGraphView.clientWidth || 600;
  const height = 400;

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('width', '100%');
  svg.setAttribute('height', height);
  svg.style.background = '#0A0A0F';

  // Grid
  const grid = document.createElementNS('http://www.w3.org/2000/svg', 'pattern');
  grid.setAttribute('id', 'grid');
  grid.setAttribute('width', '40');
  grid.setAttribute('height', '40');
  grid.setAttribute('patternUnits', 'userSpaceOnUse');
  const gridPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  gridPath.setAttribute('d', 'M 40 0 L 0 0 0 40');
  gridPath.setAttribute('fill', 'none');
  gridPath.setAttribute('stroke', 'rgba(255,10,47,0.05)');
  gridPath.setAttribute('stroke-width', '1');
  grid.appendChild(gridPath);

  const gridRect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
  gridRect.setAttribute('width', '100%');
  gridRect.setAttribute('height', '100%');
  gridRect.setAttribute('fill', 'url(#grid)');
  svg.appendChild(grid);

  // Draw edges
  GraphState.relations.forEach(rel => {
    const from = GraphState.entities.find(e => e.id === rel.from);
    const to = GraphState.entities.find(e => e.id === rel.to);
    if (!from || !to) return;

    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');

    // Curved line
    const mx = (from.x + to.x) / 2;
    const my = (from.y + to.y) / 2 - 30;
    const d = `M ${from.x} ${from.y} Q ${mx} ${my} ${to.x} ${to.y}`;

    path.setAttribute('d', d);
    path.setAttribute('fill', 'none');
    path.setAttribute('stroke', RELATION_COLORS[rel.type] || '#2A2A3A');
    path.setAttribute('stroke-width', '2');
    path.setAttribute('stroke-opacity', '0.6');

    // Arrow marker
    const markerId = `arrow-${rel.id}`;
    const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
    marker.setAttribute('id', markerId);
    marker.setAttribute('markerWidth', '10');
    marker.setAttribute('markerHeight', '10');
    marker.setAttribute('refX', '20');
    marker.setAttribute('refY', '3');
    marker.setAttribute('orient', 'auto');
    marker.setAttribute('markerUnits', 'strokeWidth');
    const arrow = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    arrow.setAttribute('d', 'M0,0 L0,6 L9,3 z');
    arrow.setAttribute('fill', RELATION_COLORS[rel.type] || '#2A2A3A');
    marker.appendChild(arrow);
    svg.appendChild(marker);

    path.setAttribute('marker-end', `url(#${markerId})`);

    // Relation label
    const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    label.setAttribute('x', mx);
    label.setAttribute('y', my);
    label.setAttribute('text-anchor', 'middle');
    label.setAttribute('fill', RELATION_COLORS[rel.type] || '#8888AA');
    label.setAttribute('font-size', '9');
    label.setAttribute('font-family', 'var(--font-mono)');
    label.textContent = rel.type;

    svg.appendChild(path);
    svg.appendChild(label);
  });

  // Draw nodes
  GraphState.entities.forEach(entity => {
    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    g.style.cursor = 'pointer';
    g.dataset.entityId = entity.id;

    const isSelected = GraphState.selectedNode === entity.id;
    const color = TYPE_COLORS[entity.type] || '#FF0A2F';

    // Glow for selected
    if (isSelected) {
      const glow = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      glow.setAttribute('cx', entity.x);
      glow.setAttribute('cy', entity.y);
      glow.setAttribute('r', 28);
      glow.setAttribute('fill', color);
      glow.setAttribute('opacity', '0.15');
      const anim = document.createElementNS('http://www.w3.org/2000/svg', 'animate');
      anim.setAttribute('attributeName', 'r');
      anim.setAttribute('values', '28;32;28');
      anim.setAttribute('dur', '1.5s');
      anim.setAttribute('repeatCount', 'indefinite');
      glow.appendChild(anim);
      svg.appendChild(glow);
    }

    // Node circle
    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    circle.setAttribute('cx', entity.x);
    circle.setAttribute('cy', entity.y);
    circle.setAttribute('r', 20);
    circle.setAttribute('fill', '#1A0A0F');
    circle.setAttribute('stroke', color);
    circle.setAttribute('stroke-width', isSelected ? '3' : '2');

    // Type icon (first letter)
    const icon = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    icon.setAttribute('x', entity.x);
    icon.setAttribute('y', entity.y + 5);
    icon.setAttribute('text-anchor', 'middle');
    icon.setAttribute('fill', color);
    icon.setAttribute('font-size', '14');
    icon.setAttribute('font-weight', '700');
    icon.textContent = entity.type[0];

    // Label
    const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    label.setAttribute('x', entity.x);
    label.setAttribute('y', entity.y + 36);
    label.setAttribute('text-anchor', 'middle');
    label.setAttribute('fill', '#CCCCDD');
    label.setAttribute('font-size', '11');
    label.textContent = entity.label;

    // Type label
    const typeLabel = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    typeLabel.setAttribute('x', entity.x);
    typeLabel.setAttribute('y', entity.y + 50);
    typeLabel.setAttribute('text-anchor', 'middle');
    typeLabel.setAttribute('fill', '#666677');
    typeLabel.setAttribute('font-size', '9');
    typeLabel.textContent = entity.type;

    g.appendChild(circle);
    g.appendChild(icon);
    g.appendChild(label);
    g.appendChild(typeLabel);

    g.addEventListener('click', () => selectNode(entity.id));
    g.addEventListener('mouseenter', () => {
      circle.setAttribute('stroke-width', '3');
      circle.setAttribute('fill', 'rgba(255,10,47,0.1)');
    });
    g.addEventListener('mouseleave', () => {
      circle.setAttribute('stroke-width', isSelected ? '3' : '2');
      circle.setAttribute('fill', '#1A0A0F');
    });

    // Make draggable
    makeDraggable(g, entity);

    svg.appendChild(g);
  });

  entityGraphView.innerHTML = '';
  entityGraphView.appendChild(svg);
}

function makeDraggable(element, entity) {
  let dragging = false;
  let startX, startY;

  element.addEventListener('mousedown', e => {
    dragging = true;
    startX = e.clientX - entity.x;
    startY = e.clientY - entity.y;
    element.style.cursor = 'grabbing';
  });

  document.addEventListener('mousemove', e => {
    if (!dragging) return;
    entity.x = e.clientX - startX;
    entity.y = e.clientY - startY;
    renderGraph();
  });

  document.addEventListener('mouseup', () => {
    dragging = false;
    element.style.cursor = 'pointer';
  });
}

function selectNode(entityId) {
  GraphState.selectedNode = GraphState.selectedNode === entityId ? null : entityId;
  renderGraph();
  renderRelationEditor();

  const entity = GraphState.entities.find(e => e.id === entityId);
  if (!entity) return;

  // Show details in query panel
  if (graphQueryPanel) {
    const detail = graphQueryPanel.querySelector('.entity-detail') || document.createElement('div');
    detail.className = 'entity-detail';
    detail.innerHTML = `
      <div class="entity-detail-header">
        <span style="color:${TYPE_COLORS[entity.type] || '#FF0A2F'}">${entity.type}</span>
        <span style="color:#666;font-size:12px">${entity.id}</span>
      </div>
      <div class="entity-detail-name" style="font-size:16px;color:#CCCCDD;margin:8px 0">${escHtml(entity.label)}</div>
      <div class="entity-props">
        ${Object.entries(entity.properties).map(([k, v]) => `
          <div class="prop-row">
            <span class="prop-key">${escHtml(k)}</span>
            <span class="prop-val">${escHtml(v)}</span>
          </div>
        `).join('')}
      </div>
      <div class="entity-relations" style="margin-top:12px">
        <div style="color:#666;font-size:11px;margin-bottom:4px">Relations:</div>
        ${getEntityRelations(entityId).map(r => `
          <div class="rel-row">
            <span style="color:${RELATION_COLORS[r.type] || '#888'}">${r.type}</span>
            <span>→ ${escHtml(getEntityLabel(r.to === entityId ? r.from : r.to))}</span>
          </div>
        `).join('') || '<span style="color:#444466">None</span>'}
      </div>
    `;

    const existing = graphQueryPanel.querySelector('.entity-detail');
    if (existing) existing.remove();
    graphQueryPanel.insertBefore(detail, graphQueryPanel.firstChild);
  }
}

function getEntityRelations(entityId) {
  return GraphState.relations.filter(r => r.from === entityId || r.to === entityId);
}

function getEntityLabel(entityId) {
  const e = GraphState.entities.find(x => x.id === entityId);
  return e ? e.label : entityId;
}

// ── Relation Editor ────────────────────────────────────────
function renderRelationEditor() {
  if (!relationEditor) return;

  relationEditor.innerHTML = `
    <div class="rel-editor-header">
      <span style="color:#FF0A2F;font-weight:600">Relations</span>
      <span style="color:#666;font-size:12px">${GraphState.relations.length} total</span>
    </div>
    <div class="rel-list">
      ${GraphState.relations.map(rel => {
        const from = GraphState.entities.find(e => e.id === rel.from);
        const to = GraphState.entities.find(e => e.id === rel.to);
        const isHighlighted = GraphState.selectedNode && (rel.from === GraphState.selectedNode || rel.to === GraphState.selectedNode);

        return `
          <div class="rel-item ${isHighlighted ? 'highlighted' : ''}" data-rel-id="${rel.id}">
            <div class="rel-nodes">
              <span class="rel-entity" style="color:${from ? TYPE_COLORS[from.type] : '#888'}">${from ? from.label : rel.from}</span>
              <span class="rel-arrow" style="color:${RELATION_COLORS[rel.type] || '#888'}">—${rel.type}→</span>
              <span class="rel-entity" style="color:${to ? TYPE_COLORS[to.type] : '#888'}">${to ? to.label : rel.to}</span>
            </div>
            ${Object.keys(rel.properties).length ? `
              <div class="rel-props">
                ${Object.entries(rel.properties).map(([k, v]) => `${escHtml(k)}: ${escHtml(v)}`).join(', ')}
              </div>
            ` : ''}
            <button class="rel-delete" onclick="deleteRelation('${rel.id}')" title="Delete">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="#FF4466">
                <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
              </svg>
            </button>
          </div>
        `;
      }).join('')}
    </div>
  `;
}

window.deleteRelation = function(relId) {
  GraphState.relations = GraphState.relations.filter(r => r.id !== relId);
  renderGraph();
  renderRelationEditor();
  updateStats();
  showToast('Relation deleted');
};

// ── Modals ─────────────────────────────────────────────────
function showAddEntityModal() {
  const name = prompt('Entity label:');
  if (!name) return;
  const type = prompt('Entity type (Person/Project/Technology/etc):') || 'Unknown';

  const newEntity = {
    id: 'ent-' + Date.now(),
    type,
    label: name,
    properties: {},
    x: 100 + Math.random() * 400,
    y: 100 + Math.random() * 200
  };

  GraphState.entities.push(newEntity);
  renderGraph();
  updateStats();
  showToast(`Added: ${name}`);
}

function showAddRelationModal() {
  if (GraphState.entities.length < 2) {
    showToast('Need at least 2 entities');
    return;
  }

  const fromId = prompt('From entity ID:');
  const toId = prompt('To entity ID:');
  const type = prompt('Relation type:') || 'RELATED_TO';

  if (!fromId || !toId) return;

  const newRel = {
    id: 'rel-' + Date.now(),
    from: fromId,
    to: toId,
    type,
    properties: {}
  };

  GraphState.relations.push(newRel);
  renderGraph();
  renderRelationEditor();
  updateStats();
  showToast('Relation added');
}

// ── Query Panel ────────────────────────────────────────────
function runGraphQuery() {
  const query = graphQueryInput?.value.trim();
  if (!query) {
    showToast('Enter Cypher query');
    return;
  }

  showToast('Executing query...');

  // Simulate query execution
  setTimeout(() => {
    const results = simulateQuery(query);
    displayQueryResults(results);
  }, 600);
}

function simulateQuery(cypher) {
  const lower = cypher.toLowerCase();

  if (lower.includes('match') && lower.includes('person')) {
    return GraphState.entities.filter(e => e.type === 'Person');
  }
  if (lower.includes('match') && lower.includes('project')) {
    return GraphState.entities.filter(e => e.type === 'Project');
  }
  if (lower.includes('count')) {
    return [{ count: GraphState.entities.length }];
  }
  if (lower.includes('knows') || lower.includes('works_on')) {
    const relType = lower.includes('knows') ? 'KNOWS' : 'WORKS_ON';
    return GraphState.relations.filter(r => r.type === relType);
  }

  // Default: return all entities
  return GraphState.entities;
}

function displayQueryResults(results) {
  if (!graphQueryPanel) return;

  const existing = graphQueryPanel.querySelector('.query-results');
  if (existing) existing.remove();

  const resultsDiv = document.createElement('div');
  resultsDiv.className = 'query-results';

  resultsDiv.innerHTML = `
    <div class="query-results-header">
      <span style="color:#44AA66">✓</span>
      <span>${results.length} results</span>
      <span style="color:#666;font-size:11px">${new Date().toLocaleTimeString()}</span>
    </div>
    <div class="query-results-list">
      ${results.map(r => `
        <div class="query-result-item">
          <pre style="font-size:11px;color:#AAAACC;margin:0;white-space:pre-wrap">${escHtml(JSON.stringify(r, null, 2))}</pre>
        </div>
      `).join('')}
    </div>
  `;

  graphQueryPanel.appendChild(resultsDiv);
}

function toggleGraphView() {
  GraphState.viewMode = GraphState.viewMode === 'graph' ? 'table' : 'graph';
  graphViewToggle && (graphViewToggle.textContent = GraphState.viewMode === 'graph' ? 'Table View' : 'Graph View');

  if (entityGraphView) {
    if (GraphState.viewMode === 'table') {
      renderTableView();
    } else {
      renderGraph();
    }
  }
}

function renderTableView() {
  if (!entityGraphView) return;

  entityGraphView.innerHTML = `
    <div class="graph-table-view">
      <table class="graph-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Type</th>
            <th>Label</th>
            <th>Properties</th>
            <th>Relations</th>
          </tr>
        </thead>
        <tbody>
          ${GraphState.entities.map(e => `
            <tr>
              <td><code style="color:#8888AA">${e.id}</code></td>
              <td><span style="color:${TYPE_COLORS[e.type] || '#FF0A2F'}">${e.type}</span></td>
              <td>${escHtml(e.label)}</td>
              <td><code style="font-size:10px">${escHtml(JSON.stringify(e.properties))}</code></td>
              <td>${getEntityRelations(e.id).length}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}

function updateStats() {
  if (!graphStatsPanel) return;

  graphStatsPanel.innerHTML = `
    <div class="graph-stat">
      <span class="stat-value" style="color:#FF0A2F">${GraphState.entities.length}</span>
      <span class="stat-label">Entities</span>
    </div>
    <div class="graph-stat">
      <span class="stat-value" style="color:#77CCFF">${GraphState.relations.length}</span>
      <span class="stat-label">Relations</span>
    </div>
    <div class="graph-stat">
      <span class="stat-value" style="color:#44AA66">${new Set(GraphState.entities.map(e => e.type)).size}</span>
      <span class="stat-label">Types</span>
    </div>
  `;
}

// ── Helpers ────────────────────────────────────────────────
function escHtml(s) {
  return s.replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
}
