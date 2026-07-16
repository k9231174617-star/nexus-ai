/* ============================================================
   NEXUS AI — CI/CD Integration UI
   Pipeline visualization, build triggers, deploy manager
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const CICDState = {
  pipelines: [],
  builds: [],
  deployments: [],
  activePipeline: null,
  isRunning: false
};

// ── DOM ────────────────────────────────────────────────────
const pipelineParser    = document.getElementById('pipelineParser');
const buildTrigger      = document.getElementById('buildTrigger');
const deployManager     = document.getElementById('deployManager');
const cicdCreateBtn     = document.getElementById('cicdCreateBtn');
const cicdRunBtn        = document.getElementById('cicdRunBtn');
const cicdStopBtn       = document.getElementById('cicdStopBtn');
const cicdImportBtn     = document.getElementById('cicdImportBtn');
const cicdExportBtn     = document.getElementById('cicdExportBtn');
const pipelineYamlInput = document.getElementById('pipelineYamlInput');

// ── Demo Data ──────────────────────────────────────────────
const DEMO_PIPELINES = [
  {
    id: 'pipe-1',
    name: 'Android Release',
    stages: [
      { name: 'build', status: 'completed', duration: 124000, logs: ['> ./gradlew assembleRelease', 'BUILD SUCCESSFUL'] },
      { name: 'test', status: 'completed', duration: 45000, logs: ['> ./gradlew test', '42 tests passed'] },
      { name: 'sign', status: 'running', duration: 0, logs: ['> Signing APK...'] },
      { name: 'deploy', status: 'pending', duration: 0, logs: [] }
    ],
    config: `
stages:
  - build
  - test
  - sign
  - deploy

build:
  script: ./gradlew assembleRelease

test:
  script: ./gradlew test

sign:
  script: ./scripts/sign-apk.sh

deploy:
  script: ./scripts/deploy-playstore.sh
    `
  },
  {
    id: 'pipe-2',
    name: 'Nightly Tests',
    stages: [
      { name: 'lint', status: 'completed', duration: 12000, logs: ['> ktlint check', 'No issues found'] },
      { name: 'unit', status: 'failed', duration: 89000, logs: ['> ./gradlew testDebug', 'FAILED: TokenCounterTest'] },
      { name: 'integration', status: 'skipped', duration: 0, logs: [] }
    ],
    config: `
stages:
  - lint
  - unit
  - integration
    `
  }
];

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initCICD();
  initCICDControls();
});

function initCICD() {
  CICDState.pipelines = DEMO_PIPELINES.map(p => ({ ...p }));
  CICDState.activePipeline = CICDState.pipelines[0];

  renderPipelineList();
  renderPipelineView();
  renderBuildHistory();
  renderDeployments();
}

function initCICDControls() {
  cicdCreateBtn?.addEventListener('click', createNewPipeline);
  cicdRunBtn?.addEventListener('click', runPipeline);
  cicdStopBtn?.addEventListener('click', stopPipeline);
  cicdImportBtn?.addEventListener('click', importPipeline);
  cicdExportBtn?.addEventListener('click', exportPipeline);
}

// ── Pipeline List ──────────────────────────────────────────
function renderPipelineList() {
  const list = document.getElementById('pipelineList');
  if (!list) return;

  list.innerHTML = CICDState.pipelines.map(pipe => {
    const status = getPipelineStatus(pipe);
    const statusColors = {
      success: '#44AA66',
      running: '#FF0A2F',
      failed: '#FF4466',
      pending: '#666666'
    };

    const isActive = CICDState.activePipeline?.id === pipe.id;

    return `
      <div class="pipeline-list-item ${isActive ? 'active' : ''}" onclick="selectPipeline('${pipe.id}')">
        <div class="pipeline-status-dot" style="background:${statusColors[status] || '#666'}"></div>
        <div class="pipeline-info">
          <div class="pipeline-name">${escHtml(pipe.name)}</div>
          <div class="pipeline-meta">${pipe.stages.length} stages · ${status}</div>
        </div>
        <div class="pipeline-actions">
          <button onclick="event.stopPropagation();runPipelineById('${pipe.id}')" title="Run">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="#44AA66"><path d="M8 5v14l11-7z"/></svg>
          </button>
        </div>
      </div>
    `;
  }).join('');
}

window.selectPipeline = function(pipeId) {
  CICDState.activePipeline = CICDState.pipelines.find(p => p.id === pipeId);
  renderPipelineList();
  renderPipelineView();
  renderBuildHistory();
};

function getPipelineStatus(pipeline) {
  if (pipeline.stages.some(s => s.status === 'running')) return 'running';
  if (pipeline.stages.some(s => s.status === 'failed')) return 'failed';
  if (pipeline.stages.every(s => s.status === 'completed')) return 'success';
  return 'pending';
}

// ── Pipeline View ──────────────────────────────────────────
function renderPipelineView() {
  const view = document.getElementById('pipelineView');
  if (!view || !CICDState.activePipeline) return;

  const pipe = CICDState.activePipeline;

  view.innerHTML = `
    <div class="pipeline-header">
      <div class="pipeline-title">
        <span style="color:#FF0A2F;font-size:18px;font-weight:700">${escHtml(pipe.name)}</span>
        <span class="pipeline-id" style="color:#666;font-size:11px">${pipe.id}</span>
      </div>
      <div class="pipeline-actions-bar">
        <button class="cicd-btn-run" onclick="runPipeline()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="#0A0A0F"><path d="M8 5v14l11-7z"/></svg>
          Run
        </button>
        <button class="cicd-btn-stop" onclick="stopPipeline()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="#0A0A0F"><path d="M6 6h12v12H6z"/></svg>
          Stop
        </button>
      </div>
    </div>
    <div class="pipeline-stages">
      ${pipe.stages.map((stage, i) => renderStage(stage, i, pipe.stages.length)).join('')}
    </div>
    <div class="pipeline-yaml">
      <div class="yaml-header">
        <span style="color:#8888AA;font-size:12px">pipeline.yml</span>
        <button onclick="copyYaml()" title="Copy">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="#8888AA"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
        </button>
      </div>
      <pre class="yaml-content">${escHtml(pipe.config.trim())}</pre>
    </div>
  `;
}

function renderStage(stage, index, total) {
  const statusConfig = {
    completed: { color: '#44AA66', icon: '●', label: 'Done' },
    running:   { color: '#FF0A2F', icon: '◐', label: 'Running' },
    failed:    { color: '#FF4466', icon: '✕', label: 'Failed' },
    skipped:   { color: '#666666', icon: '⊘', label: 'Skipped' },
    pending:   { color: '#2A2A3A', icon: '○', label: 'Pending' }
  };

  const cfg = statusConfig[stage.status] || statusConfig.pending;
  const duration = stage.duration > 0
    ? stage.duration > 60000
      ? `${(stage.duration / 60000).toFixed(1)}m`
      : `${(stage.duration / 1000).toFixed(1)}s`
    : '—';

  return `
    <div class="stage-card ${stage.status}">
      <div class="stage-connector ${index < total - 1 ? 'has-next' : ''}"></div>
      <div class="stage-indicator" style="border-color:${cfg.color};color:${cfg.color}">
        <span class="stage-icon">${cfg.icon}</span>
      </div>
      <div class="stage-info">
        <div class="stage-name">${escHtml(stage.name)}</div>
        <div class="stage-meta">
          <span style="color:${cfg.color}">${cfg.label}</span>
          <span style="color:#666">· ${duration}</span>
        </div>
      </div>
      <div class="stage-logs">
        ${stage.logs.length ? `
          <div class="stage-log-preview">
            ${stage.logs.slice(-2).map(l => `<div>${escHtml(l)}</div>`).join('')}
          </div>
        ` : '<span style="color:#444466;font-size:11px">No logs</span>'}
      </div>
    </div>
  `;
}

// ── Build Execution ────────────────────────────────────────
window.runPipeline = function() {
  if (!CICDState.activePipeline) {
    showToast('Select a pipeline first');
    return;
  }

  if (CICDState.isRunning) {
    showToast('Already running');
    return;
  }

  CICDState.isRunning = true;
  const pipe = CICDState.activePipeline;

  // Reset stages
  pipe.stages.forEach(s => {
    s.status = 'pending';
    s.duration = 0;
    s.logs = [];
  });

  renderPipelineView();
  showToast(`Starting: ${pipe.name}`);

  runStageRecursive(pipe, 0);
};

function runStageRecursive(pipe, stageIndex) {
  if (stageIndex >= pipe.stages.length || !CICDState.isRunning) {
    CICDState.isRunning = false;
    const allCompleted = pipe.stages.every(s => s.status === 'completed');
    showToast(allCompleted ? 'Pipeline completed ✓' : 'Pipeline finished');
    renderPipelineView();
    renderBuildHistory();
    return;
  }

  const stage = pipe.stages[stageIndex];
  stage.status = 'running';
  stage.logs = [`> Starting ${stage.name}...`];
  renderPipelineView();

  const startTime = Date.now();

  // Simulate stage execution
  const duration = Math.random() * 3000 + 1000;
  const shouldFail = Math.random() < 0.1 && stageIndex > 0;

  setTimeout(() => {
    if (!CICDState.isRunning) return;

    stage.duration = Date.now() - startTime;

    if (shouldFail) {
      stage.status = 'failed';
      stage.logs.push(`> ERROR: ${stage.name} failed`);
      CICDState.isRunning = false;
      showToast(`Failed at ${stage.name}`);
    } else {
      stage.status = 'completed';
      stage.logs.push(`> ${stage.name} completed successfully`);
      stage.logs.push(`> Duration: ${(stage.duration / 1000).toFixed(2)}s`);
    }

    renderPipelineView();

    if (stage.status === 'completed') {
      setTimeout(() => runStageRecursive(pipe, stageIndex + 1), 300);
    } else {
      renderBuildHistory();
    }
  }, duration);
};

window.runPipelineById = function(pipeId) {
  selectPipeline(pipeId);
  setTimeout(runPipeline, 100);
};

window.stopPipeline = function() {
  if (!CICDState.isRunning) {
    showToast('Nothing to stop');
    return;
  }
  CICDState.isRunning = false;
  showToast('Pipeline stopped');
  renderPipelineView();
};

// ── Build History ──────────────────────────────────────────
function renderBuildHistory() {
  const list = document.getElementById('buildHistory');
  if (!list) return;

  // Add current run to history
  if (CICDState.activePipeline) {
    const lastBuild = CICDState.builds[0];
    const status = getPipelineStatus(CICDState.activePipeline);

    if (!lastBuild || lastBuild.pipelineId !== CICDState.activePipeline.id || status !== lastBuild.status) {
      CICDState.builds.unshift({
        id: `build-${Date.now()}`,
        pipelineId: CICDState.activePipeline.id,
        pipelineName: CICDState.activePipeline.name,
        status,
        timestamp: Date.now(),
        duration: CICDState.activePipeline.stages.reduce((s, st) => s + st.duration, 0)
      });

      if (CICDState.builds.length > 20) CICDState.builds = CICDState.builds.slice(0, 20);
    }
  }

  if (CICDState.builds.length === 0) {
    list.innerHTML = `
      <div class="cicd-empty">
        <p>No builds yet</p>
      </div>
    `;
    return;
  }

  list.innerHTML = CICDState.builds.map(build => {
    const statusColors = {
      success: '#44AA66',
      running: '#FF0A2F',
      failed: '#FF4466',
      pending: '#666666'
    };

    const time = new Date(build.timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit'
    });

    const dur = build.duration > 0
      ? build.duration > 60000
        ? `${(build.duration / 60000).toFixed(1)}m`
        : `${(build.duration / 1000).toFixed(1)}s`
      : '—';

    return `
      <div class="build-row ${build.status}">
        <div class="build-status" style="color:${statusColors[build.status] || '#666'}">
          ${build.status === 'success' ? '●' : build.status === 'running' ? '◐' : build.status === 'failed' ? '✕' : '○'}
        </div>
        <div class="build-info">
          <div class="build-name">${escHtml(build.pipelineName)}</div>
          <div class="build-meta">#${build.id.slice(-4)} · ${time} · ${dur}</div>
        </div>
        <div class="build-actions">
          <button onclick="viewBuildLogs('${build.id}')" title="Logs">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="#8888AA"><path d="M3 3h18v2H3V3zm0 4h14v2H3V7zm0 4h18v2H3v-2zm0 4h14v2H3v-2zm0 4h18v2H3v-2z"/></svg>
          </button>
        </div>
      </div>
    `;
  }).join('');
}

window.viewBuildLogs = function(buildId) {
  const build = CICDState.builds.find(b => b.id === buildId);
  if (!build) return;

  const pipe = CICDState.pipelines.find(p => p.id === build.pipelineId);
  const logs = pipe ? pipe.stages.flatMap(s => s.logs) : ['No logs available'];

  const modal = document.createElement('div');
  modal.className = 'cicd-modal';
  modal.innerHTML = `
    <div class="cicd-modal-backdrop" onclick="this.parentElement.remove()"></div>
    <div class="cicd-modal-content">
      <div class="cicd-modal-header">
        <span style="color:#FF0A2F;font-weight:600">Build Logs</span>
        <span style="color:#666;font-size:12px">${buildId}</span>
        <button onclick="this.closest('.cicd-modal').remove()" style="margin-left:auto">×</button>
      </div>
      <div class="cicd-modal-body">
        <pre class="build-logs">${escHtml(logs.join('\n'))}</pre>
      </div>
    </div>
  `;

  document.body.appendChild(modal);
};

// ── Deployments ────────────────────────────────────────────
function renderDeployments() {
  const list = document.getElementById('deployList');
  if (!list) return;

  CICDState.deployments = [
    { id: 'dep-1', env: 'staging', version: '2.1.0-rc3', status: 'active', timestamp: Date.now() - 3600000 },
    { id: 'dep-2', env: 'production', version: '2.0.8', status: 'active', timestamp: Date.now() - 86400000 * 3 },
    { id: 'dep-3', env: 'staging', version: '2.1.0-rc2', status: 'rolled_back', timestamp: Date.now() - 86400000 * 2 }
  ];

  list.innerHTML = CICDState.deployments.map(dep => {
    const statusColors = {
      active: '#44AA66',
      rolled_back: '#FF4466',
      pending: '#FFAA44'
    };

    const time = new Date(dep.timestamp).toLocaleDateString([], {
      day: '2-digit',
      month: 'short'
    });

    return `
      <div class="deploy-row">
        <div class="deploy-env">
          <span class="deploy-env-badge" style="background:${statusColors[dep.status] || '#666'}20;color:${statusColors[dep.status] || '#666'}">
            ${dep.env}
          </span>
        </div>
        <div class="deploy-info">
          <div class="deploy-version">${escHtml(dep.version)}</div>
          <div class="deploy-meta">${time} · ${dep.status}</div>
        </div>
        <div class="deploy-actions">
          ${dep.status === 'active' && dep.env === 'staging' ? `
            <button class="deploy-promote" onclick="promoteDeploy('${dep.id}')">Promote</button>
          ` : ''}
          ${dep.status === 'active' ? `
            <button class="deploy-rollback" onclick="rollbackDeploy('${dep.id}')">Rollback</button>
          ` : ''}
        </div>
      </div>
    `;
  }).join('');
}

window.promoteDeploy = function(depId) {
  showToast(`Promoting ${depId} to production...`);
  setTimeout(() => {
    CICDState.deployments.unshift({
      id: 'dep-' + Date.now(),
      env: 'production',
      version: CICDState.deployments.find(d => d.id === depId)?.version || 'unknown',
      status: 'active',
      timestamp: Date.now()
    });
    renderDeployments();
    showToast('Promoted to production');
  }, 1500);
};

window.rollbackDeploy = function(depId) {
  if (!confirm('Rollback this deployment?')) return;
  const dep = CICDState.deployments.find(d => d.id === depId);
  if (dep) {
    dep.status = 'rolled_back';
    renderDeployments();
    showToast('Rollback initiated');
  }
};

// ── Pipeline Creation ──────────────────────────────────────
function createNewPipeline() {
  const name = prompt('Pipeline name:');
  if (!name) return;

  const newPipe = {
    id: 'pipe-' + Date.now(),
    name,
    stages: [
      { name: 'build', status: 'pending', duration: 0, logs: [] },
      { name: 'test', status: 'pending', duration: 0, logs: [] }
    ],
    config: `stages:\n  - build\n  - test\n\nbuild:\n  script: echo "Building..."\n\ntest:\n  script: echo "Testing..."`
  };

  CICDState.pipelines.push(newPipe);
  selectPipeline(newPipe.id);
  showToast(`Created: ${name}`);
}

// ── Import / Export ────────────────────────────────────────
function importPipeline() {
  const yaml = pipelineYamlInput?.value.trim();
  if (!yaml) {
    showToast('Paste YAML configuration');
    return;
  }

  try {
    const parsed = parseYamlPipeline(yaml);
    const newPipe = {
      id: 'pipe-' + Date.now(),
      name: parsed.name || 'Imported Pipeline',
      stages: parsed.stages.map(s => ({
        name: s,
        status: 'pending',
        duration: 0,
        logs: []
      })),
      config: yaml
    };

    CICDState.pipelines.push(newPipe);
    selectPipeline(newPipe.id);
    showToast('Pipeline imported');
  } catch (e) {
    showToast('Invalid YAML: ' + e.message);
  }
}

function parseYamlPipeline(yaml) {
  // Simple YAML parser for demo
  const lines = yaml.split('\n');
  const stages = [];
  let name = 'Imported Pipeline';

  lines.forEach(line => {
    const trimmed = line.trim();
    if (trimmed.startsWith('name:')) {
      name = trimmed.split(':')[1].trim();
    }
    if (trimmed.startsWith('- ') && !trimmed.includes(':')) {
      stages.push(trimmed.slice(2).trim());
    }
  });

  if (stages.length === 0) throw new Error('No stages found');

  return { name, stages };
}

function exportPipeline() {
  if (!CICDState.activePipeline) {
    showToast('Select a pipeline to export');
    return;
  }

  const blob = new Blob([CICDState.activePipeline.config], {
    type: 'text/yaml'
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${CICDState.activePipeline.name.toLowerCase().replace(/\s+/g, '-')}.yml`;
  a.click();
  URL.revokeObjectURL(url);
  showToast('Pipeline exported');
}

window.copyYaml = function() {
  const yaml = CICDState.activePipeline?.config;
  if (!yaml) return;

  navigator.clipboard.writeText(yaml).then(() => {
    showToast('YAML copied');
  }).catch(() => {
    const ta = document.createElement('textarea');
    ta.value = yaml;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    showToast('YAML copied');
  });
};

// ── Helpers ────────────────────────────────────────────────
function escHtml(s) {
  return s.replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
}

