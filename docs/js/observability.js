/* ============================================================
   NEXUS AI — Observability UI
   Trace timeline, metrics dashboard, bottleneck alerts
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const ObservabilityState = {
  traces: [],
  metrics: [],
  alerts: [],
  timeRange: '1h', // '1h' | '6h' | '24h' | '7d'
  selectedTrace: null
};

// ── DOM ────────────────────────────────────────────────────
const traceTimelineView   = document.getElementById('traceTimelineView');
const metricsDashboard    = document.getElementById('metricsDashboard');
const bottleneckAlertView = document.getElementById('bottleneckAlertView');
const obsTimeRange        = document.getElementById('obsTimeRange');
const obsRefreshBtn       = document.getElementById('obsRefreshBtn');
const obsExportBtn        = document.getElementById('obsExportBtn');

// ── Demo Data ──────────────────────────────────────────────
function generateDemoTraces() {
  const traces = [];
  const operations = ['llm_request', 'db_query', 'file_read', 'api_call', 'embed', 'vector_search', 'cache_lookup'];

  for (let i = 0; i < 15; i++) {
    const op = operations[Math.floor(Math.random() * operations.length)];
    const duration = Math.floor(Math.random() * 800 + 50);
    const hasError = Math.random() < 0.15;

    traces.push({
      id: `trace-${Date.now() - i * 60000}`,
      operation: op,
      duration: duration,
      status: hasError ? 'error' : 'success',
      timestamp: Date.now() - i * 60000,
      spans: generateSpans(op, duration, hasError)
    });
  }

  return traces;
}

function generateSpans(operation, totalDuration, hasError) {
  const spans = [];
  const count = Math.floor(Math.random() * 4) + 2;
  let remaining = totalDuration;

  for (let i = 0; i < count; i++) {
    const dur = i === count - 1 ? remaining : Math.floor(Math.random() * remaining * 0.6);
    remaining -= dur;

    spans.push({
      id: `span-${Math.random().toString(36).slice(2, 8)}`,
      operation: i === 0 ? operation : `${operation}_sub_${i}`,
      duration: dur,
      startOffset: spans.reduce((s, sp) => s + sp.duration, 0),
      status: hasError && i === count - 1 ? 'error' : 'success',
      tags: { service: 'nexus-core', thread: 'main' }
    });
  }

  return spans;
}

function generateMetrics() {
  return [
    { name: 'llm_latency_p50', value: 245, unit: 'ms', trend: 'up', change: 12 },
    { name: 'llm_latency_p99', value: 1200, unit: 'ms', trend: 'down', change: -8 },
    { name: 'cache_hit_rate', value: 0.78, unit: '%', trend: 'up', change: 5 },
    { name: 'token_throughput', value: 450, unit: 'tok/s', trend: 'stable', change: 0 },
    { name: 'error_rate', value: 0.03, unit: '%', trend: 'down', change: -2 },
    { name: 'active_sessions', value: 12, unit: '', trend: 'up', change: 3 },
    { name: 'memory_usage', value: 68, unit: '%', trend: 'stable', change: 1 },
    { name: 'cpu_usage', value: 34, unit: '%', trend: 'up', change: 5 }
  ];
}

function generateAlerts() {
  return [
    { id: 'alert-1', severity: 'critical', message: 'LLM latency p99 > 2s', operation: 'llm_request', timestamp: Date.now() - 300000, resolved: false },
    { id: 'alert-2', severity: 'warning', message: 'Cache hit rate < 70%', operation: 'cache_lookup', timestamp: Date.now() - 900000, resolved: false },
    { id: 'alert-3', severity: 'info', message: 'Token throughput spike detected', operation: 'embed', timestamp: Date.now() - 1800000, resolved: true }
  ];
}

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initObservability();
  initObsControls();
});

function initObservability() {
  ObservabilityState.traces = generateDemoTraces();
  ObservabilityState.metrics = generateMetrics();
  ObservabilityState.alerts = generateAlerts();

  renderTraceTimeline();
  renderMetrics();
  renderAlerts();
}

function initObsControls() {
  obsTimeRange?.addEventListener('change', e => {
    ObservabilityState.timeRange = e.target.value;
    showToast(`Range: ${e.target.value}`);
    refreshData();
  });

  obsRefreshBtn?.addEventListener('click', () => {
    showToast('Refreshing...');
    refreshData();
  });

  obsExportBtn?.addEventListener('click', exportMetrics);
}

function refreshData() {
  ObservabilityState.traces = generateDemoTraces();
  ObservabilityState.metrics = generateMetrics();
  ObservabilityState.alerts = generateAlerts();

  renderTraceTimeline();
  renderMetrics();
  renderAlerts();
}

// ── Trace Timeline ─────────────────────────────────────────
function renderTraceTimeline() {
  if (!traceTimelineView) return;

  traceTimelineView.innerHTML = '';

  if (ObservabilityState.traces.length === 0) {
    traceTimelineView.innerHTML = `
      <div class="obs-empty">
        <p>No traces available</p>
      </div>
    `;
    return;
  }

  // Timeline header
  const header = document.createElement('div');
  header.className = 'trace-timeline-header';
  header.innerHTML = `
    <span style="color:#FF0A2F;font-weight:600">Trace Timeline</span>
    <span style="color:#666;font-size:12px">${ObservabilityState.traces.length} traces</span>
  `;
  traceTimelineView.appendChild(header);

  // Find max duration for scaling
  const maxDuration = Math.max(...ObservabilityState.traces.map(t => t.duration), 1);

  ObservabilityState.traces.forEach(trace => {
    const div = document.createElement('div');
    div.className = `trace-row ${trace.status}`;
    div.dataset.traceId = trace.id;

    const time = new Date(trace.timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });

    const widthPct = (trace.duration / maxDuration) * 100;

    div.innerHTML = `
      <div class="trace-info">
        <span class="trace-time">${time}</span>
        <span class="trace-op">${escHtml(trace.operation)}</span>
        <span class="trace-dur ${trace.duration > 500 ? 'slow' : ''}">${trace.duration}ms</span>
      </div>
      <div class="trace-bar-container">
        <div class="trace-bar" style="width:${widthPct}%;background:${trace.status === 'error' ? '#FF4466' : trace.duration > 500 ? '#FFAA44' : '#44AA66'}">
          ${trace.spans.map(span => {
            const spanWidth = (span.duration / trace.duration) * 100;
            const spanOffset = (span.startOffset / trace.duration) * 100;
            return `
              <div class="trace-span-segment"
                   style="left:${spanOffset}%;width:${spanWidth}%;background:${span.status === 'error' ? '#FF4466' : 'rgba(255,255,255,0.2)'}"
                   title="${escHtml(span.operation)}: ${span.duration}ms">
              </div>
            `;
          }).join('')}
        </div>
      </div>
      <div class="trace-status">
        ${trace.status === 'error'
          ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="#FF4466"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>'
          : '<svg width="14" height="14" viewBox="0 0 24 24" fill="#44AA66"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>'
        }
      </div>
    `;

    div.addEventListener('click', () => selectTrace(trace.id));
    traceTimelineView.appendChild(div);
  });
}

function selectTrace(traceId) {
  ObservabilityState.selectedTrace = ObservabilityState.selectedTrace === traceId ? null : traceId;

  document.querySelectorAll('.trace-row').forEach(row => {
    row.classList.toggle('selected', row.dataset.traceId === traceId);
  });

  const trace = ObservabilityState.traces.find(t => t.id === traceId);
  if (!trace) return;

  // Show span detail
  const detailPanel = traceTimelineView.querySelector('.trace-detail') || document.createElement('div');
  detailPanel.className = 'trace-detail';
  detailPanel.innerHTML = `
    <div class="trace-detail-header">
      <span style="color:#FF0A2F">${escHtml(trace.operation)}</span>
      <span style="color:#666">${trace.id}</span>
    </div>
    <div class="span-list">
      ${trace.spans.map(span => `
        <div class="span-row">
          <div class="span-name">${escHtml(span.operation)}</div>
          <div class="span-bar-container">
            <div class="span-bar" style="width:100%;background:${span.status === 'error' ? '#FF4466' : '#77CCFF'}">
              <span class="span-dur">${span.duration}ms</span>
            </div>
          </div>
          <div class="span-tags">
            ${Object.entries(span.tags).map(([k, v]) => `<span class="span-tag">${escHtml(k)}=${escHtml(v)}</span>`).join('')}
          </div>
        </div>
      `).join('')}
    </div>
  `;

  const existing = traceTimelineView.querySelector('.trace-detail');
  if (existing) existing.remove();
  traceTimelineView.appendChild(detailPanel);
}

// ── Metrics Dashboard ──────────────────────────────────────
function renderMetrics() {
  if (!metricsDashboard) return;

  metricsDashboard.innerHTML = '';

  ObservabilityState.metrics.forEach(metric => {
    const card = document.createElement('div');
    card.className = 'metric-card';

    const trendColor = metric.trend === 'up' ? '#44AA66' : metric.trend === 'down' ? '#FF4466' : '#FFAA44';
    const trendIcon = metric.trend === 'up' ? '↑' : metric.trend === 'down' ? '↓' : '→';

    let displayValue = metric.value;
    if (metric.unit === '%' && metric.value < 1) displayValue = (metric.value * 100).toFixed(1);
    else if (metric.value > 1000) displayValue = (metric.value / 1000).toFixed(1) + 'k';

    card.innerHTML = `
      <div class="metric-header">
        <span class="metric-name">${escHtml(metric.name)}</span>
        <span class="metric-trend" style="color:${trendColor}">${trendIcon} ${metric.change > 0 ? '+' : ''}${metric.change}%</span>
      </div>
      <div class="metric-value" style="color:#CCCCDD;font-size:24px;font-weight:700">${displayValue}<span style="font-size:12px;color:#666;margin-left:4px">${metric.unit}</span></div>
      <div class="metric-sparkline">
        ${generateSparkline()}
      </div>
    `;

    metricsDashboard.appendChild(card);
  });
}

function generateSparkline() {
  // Generate random sparkline SVG
  const points = Array.from({ length: 20 }, () => Math.random() * 30 + 10);
  const max = Math.max(...points);
  const min = Math.min(...points);
  const range = max - min || 1;

  const coords = points.map((p, i) => {
    const x = (i / (points.length - 1)) * 100;
    const y = 40 - ((p - min) / range) * 30;
    return `${x},${y}`;
  }).join(' ');

  return `
    <svg width="100%" height="40" viewBox="0 0 100 40" preserveAspectRatio="none">
      <polyline points="${coords}" fill="none" stroke="rgba(255,10,47,0.4)" stroke-width="1.5"/>
      <polygon points="0,40 ${coords} 100,40" fill="rgba(255,10,47,0.05)"/>
    </svg>
  `;
}

// ── Bottleneck Alerts ──────────────────────────────────────
function renderAlerts() {
  if (!bottleneckAlertView) return;

  bottleneckAlertView.innerHTML = '';

  if (ObservabilityState.alerts.length === 0) {
    bottleneckAlertView.innerHTML = `
      <div class="obs-empty">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" style="opacity:0.3">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="#44AA66"/>
        </svg>
        <p>No active alerts</p>
      </div>
    `;
    return;
  }

  const severityColors = {
    critical: '#FF4466',
    warning: '#FFAA44',
    info: '#77CCFF'
  };

  const severityIcons = {
    critical: '🔴',
    warning: '🟡',
    info: '🔵'
  };

  bottleneckAlertView.innerHTML = ObservabilityState.alerts.map(alert => {
    const time = new Date(alert.timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit'
    });

    return `
      <div class="alert-card ${alert.severity} ${alert.resolved ? 'resolved' : ''}">
        <div class="alert-icon" style="color:${severityColors[alert.severity]}">${severityIcons[alert.severity]}</div>
        <div class="alert-content">
          <div class="alert-message">${escHtml(alert.message)}</div>
          <div class="alert-meta">
            <span>${escHtml(alert.operation)}</span>
            <span>${time}</span>
            ${alert.resolved ? '<span style="color:#44AA66">Resolved</span>' : '<span style="color:#FF4466">Active</span>'}
          </div>
        </div>
        ${!alert.resolved ? `<button class="alert-resolve" onclick="resolveAlert('${alert.id}')">Resolve</button>` : ''}
      </div>
    `;
  }).join('');
}

window.resolveAlert = function(alertId) {
  const alert = ObservabilityState.alerts.find(a => a.id === alertId);
  if (alert) {
    alert.resolved = true;
    renderAlerts();
    showToast('Alert resolved');
  }
};

// ── Export ─────────────────────────────────────────────────
function exportMetrics() {
  const data = {
    exported: new Date().toISOString(),
    metrics: ObservabilityState.metrics,
    traces: ObservabilityState.traces.map(t => ({
      id: t.id,
      operation: t.operation,
      duration: t.duration,
      status: t.status
    }))
  };

  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: 'application/json'
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `nexus-observability-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
  showToast('Metrics exported');
}

// ── Helpers ────────────────────────────────────────────────
function escHtml(s) {
  return s.replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
}
