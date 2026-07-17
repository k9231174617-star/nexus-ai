/* ============================================================
   NEXUS AI — Task Planner UI
   Task graph visualization, execution log, workflow control
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const PlannerState = {
  tasks: [],
  activePlan: null,
  executionLog: [],
  isRunning: false,
  viewMode: 'graph' // 'graph' | 'list'
};

// ── DOM ────────────────────────────────────────────────────
const plannerInput     = document.getElementById('plannerInput');
const plannerCreateBtn = document.getElementById('plannerCreateBtn');
const taskGraphView    = document.getElementById('taskGraphView');
const taskListView     = document.getElementById('taskListView');
const executionLogView = document.getElementById('executionLogView');
const plannerRunBtn    = document.getElementById('plannerRunBtn');
const plannerStopBtn   = document.getElementById('plannerStopBtn');
const plannerClearBtn  = document.getElementById('plannerClearBtn');
const viewToggleGraph  = document.getElementById('viewToggleGraph');
const viewToggleList   = document.getElementById('viewToggleList');
const planProgress     = document.getElementById('planProgress');
const planStatusBadge  = document.getElementById('planStatusBadge');

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initPlanner();
  initViewToggle();
  initPlannerControls();
});

function initPlanner() {
  // Demo plan
  PlannerState.tasks = [
    { id: 't1', name: 'Analyze APK', status: 'completed', deps: [], progress: 100, type: 'analysis' },
    { id: 't2', name: 'Decompile classes', status: 'completed', deps: ['t1'], progress: 100, type: 'decompile' },
    { id: 't3', name: 'Patch smali', status: 'running', deps: ['t2'], progress: 45, type: 'patch' },
    { id: 't4', name: 'Recompile APK', status: 'pending', deps: ['t3'], progress: 0, type: 'compile' },
    { id: 't5', name: 'Sign & verify', status: 'pending', deps: ['t4'], progress: 0, type: 'sign' }
  ];
  PlannerState.activePlan = 'apk-patch';

  renderGraph();
  renderTaskList();
  updateProgress();
  addLogEntry('system', 'Planner initialized. Demo plan loaded.');
}

function initViewToggle() {
  viewToggleGraph?.addEventListener('click', () => setViewMode('graph'));
  viewToggleList?.addEventListener('click', () => setViewMode('list'));
}

function setViewMode(mode) {
  PlannerState.viewMode = mode;
  viewToggleGraph?.classList.toggle('active', mode === 'graph');
  viewToggleList?.classList.toggle('active', mode === 'list');

  if (taskGraphView) taskGraphView.style.display = mode === 'graph' ? 'block' : 'none';
  if (taskListView) taskListView.style.display = mode === 'list' ? 'block' : 'none';
}

function initPlannerControls() {
  plannerCreateBtn?.addEventListener('click', createPlanFromInput);
  plannerInput?.addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      createPlanFromInput();
    }
  });

  plannerRunBtn?.addEventListener('click', runPlan);
  plannerStopBtn?.addEventListener('click', stopPlan);
  plannerClearBtn?.addEventListener('click', clearPlan);
}

// ── Plan Creation ──────────────────────────────────────────
function createPlanFromInput() {
  const goal = plannerInput?.value.trim();
  if (!goal) {
    showToast('Enter a goal to plan');
    return;
  }

  plannerInput.value = '';
  addLogEntry('user', `Goal: ${goal}`);

  // Simulate plan generation
  showToast('Generating plan...');
  setTimeout(() => {
    const newTasks = decomposeGoal(goal);
    PlannerState.tasks = newTasks;
    PlannerState.activePlan = 'plan-' + Date.now();
    PlannerState.executionLog = [];

    renderGraph();
    renderTaskList();
    updateProgress();
    addLogEntry('agent', `Plan created with ${newTasks.length} tasks`);
    showToast(`Plan created: ${newTasks.length} tasks`);
  }, 800);
}

function decomposeGoal(goal) {
  const lower = goal.toLowerCase();
  if (lower.includes('apk') || lower.includes('app')) {
    return [
      { id: 'a1', name: 'Analyze manifest', status: 'pending', deps: [], progress: 0, type: 'analysis' },
      { id: 'a2', name: 'Extract resources', status: 'pending', deps: ['a1'], progress: 0, type: 'extract' },
      { id: 'a3', name: 'Modify smali', status: 'pending', deps: ['a2'], progress: 0, type: 'patch' },
      { id: 'a4', name: 'Rebuild APK', status: 'pending', deps: ['a3'], progress: 0, type: 'compile' },
      { id: 'a5', name: 'Align & sign', status: 'pending', deps: ['a4'], progress: 0, type: 'sign' }
    ];
  }
  if (lower.includes('code') || lower.includes('kotlin')) {
    return [
      { id: 'c1', name: 'Parse source', status: 'pending', deps: [], progress: 0, type: 'analysis' },
      { id: 'c2', name: 'Type inference', status: 'pending', deps: ['c1'], progress: 0, type: 'analysis' },
      { id: 'c3', name: 'Generate tests', status: 'pending', deps: ['c2'], progress: 0, type: 'generate' },
      { id: 'c4', name: 'Optimize imports', status: 'pending', deps: ['c3'], progress: 0, type: 'optimize' }
    ];
  }
  return [
    { id: 'g1', name: 'Analyze request', status: 'pending', deps: [], progress: 0, type: 'analysis' },
    { id: 'g2', name: 'Research context', status: 'pending', deps: ['g1'], progress: 0, type: 'research' },
    { id: 'g3', name: 'Execute action', status: 'pending', deps: ['g2'], progress: 0, type: 'execute' },
    { id: 'g4', name: 'Verify result', status: 'pending', deps: ['g3'], progress: 0, type: 'verify' }
  ];
}

// ── Graph View ─────────────────────────────────────────────
function renderGraph() {
  if (!taskGraphView) return;

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('width', '100%');
  svg.setAttribute('height', '400');
  svg.style.background = '#0A0A0F';

  const nodeRadius = 28;
  const centerX = taskGraphView.clientWidth / 2 || 400;
  const levelHeight = 80;
  const startY = 40;

  // Calculate positions
  const levels = topologicalLevels(PlannerState.tasks);
  const positions = {};

  levels.forEach((level, li) => {
    const count = level.length;
    const spacing = Math.min(120, (taskGraphView.clientWidth - 100) / Math.max(count - 1, 1));
    const offsetX = centerX - ((count - 1) * spacing) / 2;

    level.forEach((taskId, ti) => {
      positions[taskId] = {
        x: offsetX + ti * spacing,
        y: startY + li * levelHeight
      };
    });
  });

  // Draw edges
  PlannerState.tasks.forEach(task => {
    task.deps.forEach(depId => {
      const from = positions[depId];
      const to = positions[task.id];
      if (!from || !to) return;

      const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
      line.setAttribute('x1', from.x);
      line.setAttribute('y1', from.y + nodeRadius);
      line.setAttribute('x2', to.x);
      line.setAttribute('y2', to.y - nodeRadius);

      const isActive = task.status === 'running' || task.status === 'completed';
      line.setAttribute('stroke', isActive ? '#FF0A2F' : '#2A2A3A');
      line.setAttribute('stroke-width', isActive ? '2' : '1');
      line.setAttribute('stroke-dasharray', task.status === 'running' ? '4,4' : 'none');

      if (task.status === 'running') {
        const animate = document.createElementNS('http://www.w3.org/2000/svg', 'animate');
        animate.setAttribute('attributeName', 'stroke-dashoffset');
        animate.setAttribute('from', '0');
        animate.setAttribute('to', '8');
        animate.setAttribute('dur', '1s');
        animate.setAttribute('repeatCount', 'indefinite');
        line.appendChild(animate);
      }

      svg.appendChild(line);
    });
  });

  // Draw nodes
  PlannerState.tasks.forEach(task => {
    const pos = positions[task.id];
    if (!pos) return;

    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    g.style.cursor = 'pointer';

    // Circle
    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    circle.setAttribute('cx', pos.x);
    circle.setAttribute('cy', pos.y);
    circle.setAttribute('r', nodeRadius);

    const statusColors = {
      pending:    '#1A1A2E',
      running:    '#FF0A2F',
      completed:  '#44AA66',
      failed:     '#FF4466',
      cancelled:  '#666666'
    };

    circle.setAttribute('fill', statusColors[task.status] || '#1A1A2E');
    circle.setAttribute('stroke', task.status === 'running' ? '#FF0A2F' : '#2A2A3A');
    circle.setAttribute('stroke-width', '2');

    if (task.status === 'running') {
      const pulse = document.createElementNS('http://www.w3.org/2000/svg', 'animate');
      pulse.setAttribute('attributeName', 'r');
      pulse.setAttribute('values', `${nodeRadius};${nodeRadius + 4};${nodeRadius}`);
      pulse.setAttribute('dur', '1.5s');
      pulse.setAttribute('repeatCount', 'indefinite');
      circle.appendChild(pulse);
    }

    // Label
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    text.setAttribute('x', pos.x);
    text.setAttribute('y', pos.y + 5);
    text.setAttribute('text-anchor', 'middle');
    text.setAttribute('fill', '#CCCCDD');
    text.setAttribute('font-size', '10');
    text.setAttribute('font-family', 'var(--font-mono)');
    text.textContent = task.id;

    // Name below
    const nameText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    nameText.setAttribute('x', pos.x);
    nameText.setAttribute('y', pos.y + nodeRadius + 18);
    nameText.setAttribute('text-anchor', 'middle');
    nameText.setAttribute('fill', '#8888AA');
    nameText.setAttribute('font-size', '11');
    nameText.textContent = task.name;

    // Progress arc
    if (task.progress > 0) {
      const circumference = 2 * Math.PI * (nodeRadius + 6);
      const arc = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      arc.setAttribute('cx', pos.x);
      arc.setAttribute('cy', pos.y);
      arc.setAttribute('r', nodeRadius + 6);
      arc.setAttribute('fill', 'none');
      arc.setAttribute('stroke', 'rgba(255, 10, 47, 0.4)');
      arc.setAttribute('stroke-width', '3');
      arc.setAttribute('stroke-dasharray', `${circumference * task.progress / 100} ${circumference}`);
      arc.setAttribute('transform', `rotate(-90 ${pos.x} ${pos.y})`);
      g.appendChild(arc);
    }

    g.appendChild(circle);
    g.appendChild(text);
    g.appendChild(nameText);

    g.addEventListener('click', () => selectTask(task.id));
    g.addEventListener('mouseenter', () => {
      circle.setAttribute('stroke', '#FF0A2F');
      circle.setAttribute('stroke-width', '3');
    });
    g.addEventListener('mouseleave', () => {
      circle.setAttribute('stroke', task.status === 'running' ? '#FF0A2F' : '#2A2A3A');
      circle.setAttribute('stroke-width', '2');
    });

    svg.appendChild(g);
  });

  taskGraphView.innerHTML = '';
  taskGraphView.appendChild(svg);
}

function topologicalLevels(tasks) {
  const visited = new Set();
  const levels = [];
  const inDegree = {};

  tasks.forEach(t => { inDegree[t.id] = 0; });
  tasks.forEach(t => {
    t.deps.forEach(d => {
      if (inDegree[d] !== undefined) inDegree[t.id]++;
    });
  });

  while (visited.size < tasks.length) {
    const level = tasks.filter(t => !visited.has(t.id) && t.deps.every(d => visited.has(d)));
    if (level.length === 0) break;
    level.forEach(t => visited.add(t.id));
    levels.push(level.map(t => t.id));
  }

  return levels;
}

// ── List View ──────────────────────────────────────────────
function renderTaskList() {
  if (!taskListView) return;

  taskListView.innerHTML = '';

  PlannerState.tasks.forEach(task => {
    const card = document.createElement('div');
    card.className = `task-card ${task.status}`;
    card.dataset.taskId = task.id;

    const statusIcons = {
      pending:   '○',
      running:   '◐',
      completed: '●',
      failed:    '✕',
      cancelled: '⊘'
    };

    const statusColors = {
      pending:   '#666666',
      running:   '#FF0A2F',
      completed: '#44AA66',
      failed:    '#FF4466',
      cancelled: '#888888'
    };

    card.innerHTML = `
      <div class="task-card-header">
        <span class="task-status-icon" style="color:${statusColors[task.status]}">${statusIcons[task.status]}</span>
        <span class="task-name">${escHtml(task.name)}</span>
        <span class="task-type-badge">${task.type}</span>
      </div>
      <div class="task-card-progress">
        <div class="task-progress-bar">
          <div class="task-progress-fill" style="width:${task.progress}%;background:${statusColors[task.status]}"></div>
        </div>
        <span class="task-progress-text">${task.progress}%</span>
      </div>
      <div class="task-card-meta">
        <span>ID: ${task.id}</span>
        <span>${task.deps.length ? 'Deps: ' + task.deps.join(', ') : 'No deps'}</span>
      </div>
      <div class="task-card-actions">
        <button class="task-btn-run" onclick="runTask('${task.id}')" ${task.status !== 'pending' ? 'disabled' : ''}>Run</button>
        <button class="task-btn-skip" onclick="skipTask('${task.id}')" ${task.status === 'completed' ? 'disabled' : ''}>Skip</button>
      </div>
    `;

    card.addEventListener('click', e => {
      if (!e.target.closest('button')) selectTask(task.id);
    });

    taskListView.appendChild(card);
  });
}

window.runTask = function(taskId) {
  const task = PlannerState.tasks.find(t => t.id === taskId);
  if (!task || task.status !== 'pending') return;

  task.status = 'running';
  task.progress = 0;
  renderGraph();
  renderTaskList();
  addLogEntry('system', `Started task: ${task.name}`);

  // Simulate execution
  let progress = 0;
  const interval = setInterval(() => {
    progress += Math.random() * 15 + 5;
    if (progress >= 100) {
      progress = 100;
      task.status = 'completed';
      clearInterval(interval);
      addLogEntry('system', `Completed: ${task.name}`);
      checkAutoAdvance();
    }
    task.progress = Math.floor(progress);
    renderGraph();
    renderTaskList();
    updateProgress();
  }, 400);
};

window.skipTask = function(taskId) {
  const task = PlannerState.tasks.find(t => t.id === taskId);
  if (!task) return;

  task.status = 'cancelled';
  task.progress = 0;
  renderGraph();
  renderTaskList();
  addLogEntry('system', `Skipped: ${task.name}`);
  updateProgress();
};

function selectTask(taskId) {
  document.querySelectorAll('.task-card').forEach(c => c.classList.remove('selected'));
  const card = document.querySelector(`.task-card[data-task-id="${taskId}"]`);
  if (card) card.classList.add('selected');

  const task = PlannerState.tasks.find(t => t.id === taskId);
  if (!task) return;

  // Show in log
  addLogEntry('system', `Selected: ${task.name} (${task.status}, ${task.progress}%)`);
}

// ── Execution ──────────────────────────────────────────────
function runPlan() {
  if (PlannerState.isRunning) return;
  if (PlannerState.tasks.length === 0) {
    showToast('Create a plan first');
    return;
  }

  PlannerState.isRunning = true;
  planStatusBadge && (planStatusBadge.textContent = 'RUNNING');
  planStatusBadge && (planStatusBadge.style.color = '#FF0A2F');

  addLogEntry('system', 'Plan execution started');

  // Auto-run pending tasks in order
  runNextPending();
}

function runNextPending() {
  const next = PlannerState.tasks.find(t => t.status === 'pending' && t.deps.every(d => {
    const dep = PlannerState.tasks.find(x => x.id === d);
    return dep && dep.status === 'completed';
  }));

  if (!next) {
    PlannerState.isRunning = false;
    planStatusBadge && (planStatusBadge.textContent = 'DONE');
    planStatusBadge && (planStatusBadge.style.color = '#44AA66');
    addLogEntry('system', 'Plan execution completed');
    showToast('Plan completed');
    return;
  }

  runTask(next.id);
}

function checkAutoAdvance() {
  if (!PlannerState.isRunning) return;
  setTimeout(runNextPending, 200);
}

function stopPlan() {
  PlannerState.isRunning = false;
  planStatusBadge && (planStatusBadge.textContent = 'STOPPED');
  planStatusBadge && (planStatusBadge.style.color = '#FF4466');
  addLogEntry('system', 'Plan execution stopped');
  showToast('Execution stopped');
}

function clearPlan() {
  if (!confirm('Clear current plan?')) return;
  PlannerState.tasks = [];
  PlannerState.activePlan = null;
  PlannerState.isRunning = false;
  PlannerState.executionLog = [];
  planStatusBadge && (planStatusBadge.textContent = 'IDLE');
  planStatusBadge && (planStatusBadge.style.color = '#888888');
  renderGraph();
  renderTaskList();
  renderLog();
  updateProgress();
  showToast('Plan cleared');
}

// ── Progress ───────────────────────────────────────────────
function updateProgress() {
  if (!planProgress) return;

  const total = PlannerState.tasks.length;
  if (total === 0) {
    planProgress.style.width = '0%';
    return;
  }

  const completed = PlannerState.tasks.filter(t => t.status === 'completed').length;
  const pct = Math.floor((completed / total) * 100);
  planProgress.style.width = pct + '%';
}

// ── Execution Log ──────────────────────────────────────────
function addLogEntry(source, message) {
  const entry = {
    timestamp: Date.now(),
    source,
    message
  };
  PlannerState.executionLog.push(entry);
  if (PlannerState.executionLog.length > 200) {
    PlannerState.executionLog = PlannerState.executionLog.slice(-200);
  }
  renderLog();
}

function renderLog() {
  if (!executionLogView) return;

  executionLogView.innerHTML = '';

  const sourceColors = {
    system: '#FFAA44',
    user:   '#8888AA',
    agent:  '#FF0A2F',
    error:  '#FF4466'
  };

  PlannerState.executionLog.forEach(entry => {
    const div = document.createElement('div');
    div.className = 'log-entry';

    const time = new Date(entry.timestamp).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });

    div.innerHTML = `
      <span class="log-time" style="color:#444466">${time}</span>
      <span class="log-source" style="color:${sourceColors[entry.source] || '#CCCCDD'}">[${entry.source.toUpperCase()}]</span>
      <span class="log-message">${escHtml(entry.message)}</span>
    `;

    executionLogView.appendChild(div);
  });

  executionLogView.scrollTop = executionLogView.scrollHeight;
}

// ── Helpers ────────────────────────────────────────────────
function escHtml(s) {
  return s.replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
}
