/* ============================================================
   NEXUS AI — CLI Terminal Engine
   Simulates Android shell with root support
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const CLIState = {
  history: [],
  historyIndex: -1,
  currentPath: '~',
  isRoot: false,
  env: {
    USER: 'nexus',
    HOME: '/data/data/com.nexus.agent',
    PATH: '/system/bin:/system/xbin:/sbin',
    SHELL: '/system/bin/sh',
    TERM: 'xterm-256color'
  },
  filesystem: {
    '~': ['Documents/', 'Downloads/', 'scripts/', '.config/', 'main.py', 'notes.md'],
    '~/Documents': ['project/', 'report.pdf', 'data.json'],
    '~/Downloads': ['app.apk', 'image.png', 'archive.zip'],
    '~/scripts': ['deploy.sh', 'backup.py', 'monitor.sh'],
    '/': ['system/', 'data/', 'sdcard/', 'proc/', 'dev/'],
    '/system': ['bin/', 'lib/', 'app/', 'framework/'],
    '/data': ['data/', 'local/', 'media/'],
    '/proc': ['cpuinfo', 'meminfo', 'version', 'uptime'],
  }
};

// ── Helpers for cross-module access ────────────────────────
function getAppState() { return window.AppState || { activeTab: 'cli', sessionStart: Date.now() }; }
function toast(msg) { window.showToast?.(msg) || console.log(msg); }

// ── DOM ────────────────────────────────────────────────────
const terminal    = document.getElementById('terminal');
const cliInput    = document.getElementById('cliInput');
const cliSendBtn  = document.getElementById('cliSendBtn');
const termPrompt  = document.getElementById('termPrompt');
const cliBadge    = document.getElementById('cliBadge');
const cliPath     = document.getElementById('cliPath');
const clearCLI    = document.getElementById('clearCLI');
const rootToggle  = document.getElementById('rootToggle');

// ── Init ───────────────────────────────────────────────────
cliSendBtn?.addEventListener('click', execCommand);
cliInput?.addEventListener('keydown', handleCLIKey);
clearCLI?.addEventListener('click', () => {
  terminal.querySelectorAll('.terminal-line').forEach(l => l.remove());
  printLine('Terminal cleared.', 'success');
});
rootToggle?.addEventListener('click', toggleRoot);

// ── Core Functions ─────────────────────────────────────────
function printLine(text, type = 'info') {
  if (!terminal) return;
  const line = document.createElement('div');
  line.className = `terminal-line ${type}`;
  line.textContent = text;
  terminal.appendChild(line);
  terminal.scrollTop = terminal.scrollHeight;
}

function handleCLIKey(e) {
  if (e.key === 'Enter') {
    e.preventDefault();
    execCommand();
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    navigateHistory('up');
  } else if (e.key === 'ArrowDown') {
    e.preventDefault();
    navigateHistory('down');
  }
}

function navigateHistory(dir) {
  if (CLIState.history.length === 0) return;
  if (dir === 'up') {
    if (CLIState.historyIndex < CLIState.history.length - 1) {
      CLIState.historyIndex++;
      cliInput.value = CLIState.history[CLIState.historyIndex];
    }
  } else {
    if (CLIState.historyIndex > 0) {
      CLIState.historyIndex--;
      cliInput.value = CLIState.history[CLIState.historyIndex];
    } else {
      CLIState.historyIndex = -1;
      cliInput.value = '';
    }
  }
}

function execCommand() {
  const cmd = cliInput?.value?.trim();
  if (!cmd) return;

  printLine(`${getPrompt()}${cmd}`, 'prompt');
  CLIState.history.unshift(cmd);
  CLIState.historyIndex = -1;
  cliInput.value = '';

  const parts = cmd.split(' ');
  const base = parts[0].toLowerCase();

  try {
    switch (base) {
      case 'help':
        printHelp(); break;
      case 'ls': case 'dir':
        listDir(parts[1]); break;
      case 'cd':
        changeDir(parts[1]); break;
      case 'pwd':
        printLine(CLIState.currentPath); break;
      case 'whoami':
        printLine(CLIState.isRoot ? 'root' : CLIState.env.USER); break;
      case 'su': case 'sudo':
        toggleRoot(); break;
      case 'ps':
        printProcesses(); break;
      case 'df':
        printDisk(); break;
      case 'top':
        printTop(); break;
      case 'cat':
        printFile(parts[1]); break;
      case 'echo':
        printLine(parts.slice(1).join(' ')); break;
      case 'clear':
        terminal.querySelectorAll('.terminal-line').forEach(l => l.remove()); break;
      case 'env':
        printEnv(); break;
      case 'exit':
        printLine('Exiting...', 'system'); break;
      default:
        printLine(`Unknown command: ${base}. Type 'help' for list.`, 'error');
    }
  } catch (err) {
    printLine(`Error: ${err.message}`, 'error');
  }
}

function getPrompt() {
  return `${CLIState.isRoot ? '#' : '$'} ${CLIState.currentPath} `;
}

function printHelp() {
  const cmds = [
    'help          - Show this help',
    'ls [path]     - List directory',
    'cd [path]     - Change directory',
    'pwd           - Print working directory',
    'whoami        - Show current user',
    'su/sudo       - Toggle root mode',
    'ps            - Show processes',
    'df            - Disk usage',
    'top           - System resources',
    'cat <file>    - Show file content',
    'echo <text>   - Print text',
    'clear         - Clear terminal',
    'env           - Show environment',
    'exit          - Exit terminal'
  ];
  cmds.forEach(c => printLine(c, 'info'));
}

function listDir(path) {
  const target = resolvePath(path || CLIState.currentPath);
  const items = CLIState.filesystem[target] || [];
  if (items.length === 0) { printLine('(empty)', 'info'); return; }
  items.forEach(item => {
    const isDir = item.endsWith('/');
    printLine(`${isDir ? '📁' : '📄'} ${item}`, isDir ? 'success' : 'info');
  });
}

function resolvePath(p) {
  if (!p) return CLIState.currentPath;
  if (p.startsWith('/')) return p;
  if (p === '..') {
    const parts = CLIState.currentPath.split('/').filter(Boolean);
    parts.pop();
    return '/' + parts.join('/') || '/';
  }
  return CLIState.currentPath + '/' + p;
}

function changeDir(path) {
  const target = resolvePath(path);
  if (CLIState.filesystem[target] !== undefined) {
    CLIState.currentPath = target === '/' ? '/' : target;
    if (cliPath) cliPath.textContent = target;
  } else {
    printLine(`No such directory: ${path}`, 'error');
  }
}

function toggleRoot() {
  CLIState.isRoot = !CLIState.isRoot;
  if (rootToggle) rootToggle.checked = CLIState.isRoot;
  if (cliBadge) {
    cliBadge.textContent = CLIState.isRoot ? 'ROOT' : 'USER';
    cliBadge.style.color = CLIState.isRoot ? '#FF4466' : '#44dd88';
  }
  if (termPrompt) termPrompt.textContent = CLIState.isRoot ? '#' : '$';
  toast(CLIState.isRoot ? 'Root mode activated' : 'Root mode deactivated');
}

function printProcesses() {
  const procs = [
    'PID  USER     CMD',
    '1    root     init',
    '123  nexus    com.nexus.agent',
    '456  root     /system/bin/sh',
    '789  nexus    node index.js',
    '101  root     sshd'
  ];
  procs.forEach(p => printLine(p, 'info'));
}

function printDisk() {
  const fs = [
    'Filesystem     Size  Used Avail Use%',
    '/dev/root      4.0G  2.1G  1.9G  53%',
    '/data          8.0G  3.2G  4.8G  40%',
    '/sdcard       64.0G 12.4G 51.6G  19%'
  ];
  fs.forEach(f => printLine(f, 'info'));
}

function printTop() {
  const state = getAppState();
  const uptime = Math.floor((Date.now() - state.sessionStart) / 1000);
  const lines = [
    `Uptime: ${uptime}s`,
    'CPU:  [████████░░] 42%',
    'RAM:  [██████░░░░] 58% (2.1/3.6 GB)',
    'GPU:  [███░░░░░░░] 12%',
    'Temp: 41°C'
  ];
  lines.forEach(l => printLine(l, 'info'));
}

function printFile(name) {
  if (!name) { printLine('Usage: cat <filename>', 'error'); return; }
  const path = resolvePath(name);
  printLine(`Content of ${path}:`, 'info');
  printLine('[File content would be shown here]', 'info');
}

function printEnv() {
  Object.entries(CLIState.env).forEach(([k,v]) => printLine(`${k}=${v}`, 'info'));
}

// Expose for other modules
window.CLIState = CLIState;
window.execCommand = execCommand;
