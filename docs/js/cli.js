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

// Quick commands
document.querySelectorAll('.quick-cmd').forEach(btn => {
  btn.addEventListener('click', () => {
    cliInput.value = btn.dataset.cmd;
    cliInput.focus();
  });
});

// ── Key handler ────────────────────────────────────────────
function handleCLIKey(e) {
  if (e.key === 'Enter') {
    execCommand();
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    navigateHistory(-1);
  } else if (e.key === 'ArrowDown') {
    e.preventDefault();
    navigateHistory(1);
  } else if (e.key === 'Tab') {
    e.preventDefault();
    autocomplete();
  } else if (e.key === 'c' && e.ctrlKey) {
    cliInput.value = '';
    printPromptLine('^C');
  }
}

function navigateHistory(dir) {
  const newIdx = CLIState.historyIndex - dir;
  if (newIdx < 0 || newIdx >= CLIState.history.length) {
    if (newIdx < 0) { CLIState.historyIndex = -1; cliInput.value = ''; }
    return;
  }
  CLIState.historyIndex = newIdx;
  cliInput.value = CLIState.history[CLIState.history.length - 1 - newIdx];
}

function autocomplete() {
  const val = cliInput.value;
  const parts = val.split(' ');
  const last = parts[parts.length - 1];
  const fs = CLIState.filesystem[CLIState.currentPath] || [];
  const matches = fs.filter(f => f.startsWith(last));
  if (matches.length === 1) {
    parts[parts.length - 1] = matches[0];
    cliInput.value = parts.join(' ');
  } else if (matches.length > 1) {
    printLine(matches.join('  '), 'output');
  }
}

// ── Execute ────────────────────────────────────────────────
function execCommand() {
  const raw = cliInput.value.trim();
  if (!raw) return;

  cliInput.value = '';
  CLIState.history.push(raw);
  CLIState.historyIndex = -1;

  printPromptLine(raw);
  processCommand(raw);
  updatePromptDisplay();
}

function processCommand(raw) {
  const parts = raw.split(/\s+/);
  const cmd   = parts[0];
  const args  = parts.slice(1);

  switch (cmd) {
    case 'help':    cmdHelp();            break;
    case 'ls':      cmdLs(args);          break;
    case 'pwd':     cmdPwd();             break;
    case 'cd':      cmdCd(args);          break;
    case 'cat':     cmdCat(args);         break;
    case 'echo':    cmdEcho(args);        break;
    case 'env':     cmdEnv();             break;
    case 'ps':      cmdPs(args);          break;
    case 'df':      cmdDf(args);          break;
    case 'uname':   cmdUname(args);       break;
    case 'whoami':  cmdWhoami();          break;
    case 'date':    printLine(new Date().toString(), 'output'); break;
    case 'uptime':  cmdUptime();          break;
    case 'clear':   clearCLI.click();     break;
    case 'su':      cmdSu(args);          break;
    case 'exit':    cmdExit();            break;
    case 'mkdir':   cmdMkdir(args);       break;
    case 'touch':   cmdTouch(args);       break;
    case 'rm':      cmdRm(args);          break;
    case 'cp':      cmdCp(args);          break;
    case 'mv':      cmdMv(args);          break;
    case 'grep':    cmdGrep(args);        break;
    case 'find':    cmdFind(args);        break;
    case 'chmod':   cmdChmod(args);       break;
    case 'ping':    cmdPing(args);        break;
    case 'netstat': cmdNetstat();         break;
    case 'id':      cmdId();              break;
    case 'top':     cmdTop();             break;
    case 'history': cmdHistory();         break;
    case 'export':  cmdExport(args);      break;
    case 'sh':
    case 'bash':    printLine(`${cmd}: spawning shell...`, 'output'); break;
    case '':        break;
    default:
      // Check if it's a script
      if (cmd.endsWith('.sh') || cmd.endsWith('.py')) {
        cmdRunScript(cmd);
      } else {
        printLine(`${cmd}: command not found`, 'error');
        printLine(`Type 'help' for available commands`, 'output');
      }
  }
}

// ── Commands ───────────────────────────────────────────────
function cmdHelp() {
  const lines = [
    '<span style="color:#FF0A2F;font-weight:700">NEXUS CLI v2.0 — Available Commands</span>',
    '',
    '<span style="color:#FF4466">Navigation:</span>   ls, pwd, cd, find',
    '<span style="color:#FF4466">Files:</span>        cat, mkdir, touch, rm, cp, mv, chmod',
    '<span style="color:#FF4466">System:</span>       ps, df, top, uname, uptime, id, env',
    '<span style="color:#FF4466">Network:</span>      ping, netstat',
    '<span style="color:#FF4466">Search:</span>       grep, find',
    '<span style="color:#FF4466">Shell:</span>        sh, bash, echo, export, history',
    '<span style="color:#FF4466">Root:</span>         su [-], exit',
    '',
    '<span style="color:#888888">Tip: Use Tab for autocomplete, ↑↓ for history</span>'
  ];
  lines.forEach(l => printLine(l, 'raw'));
}

function cmdLs(args) {
  const path = args[0] || CLIState.currentPath;
  const fs = CLIState.filesystem[path] || CLIState.filesystem['~'] || [];
  const showAll = args.includes('-a') || args.includes('-la');
  const showLong = args.includes('-l') || args.includes('-la');

  const files = showAll ? ['.', '..', ...fs] : fs;

  if (showLong) {
    printLine('total ' + (files.length * 4), 'output');
    files.forEach(f => {
      const isDir = f.endsWith('/');
      const perms = isDir
        ? (CLIState.isRoot ? 'drwxr-xr-x' : 'drwxr-x---')
        : (CLIState.isRoot ? '-rwxrwxrwx' : '-rw-r--r--');
      const size  = isDir ? '4096' : String(Math.floor(Math.random() * 9000 + 100));
      const date  = 'Jul 14 12:3' + Math.floor(Math.random()*9);
      const name  = isDir
        ? `<span style="color:#77CCFF">${f}</span>`
        : `<span style="color:#AAAACC">${f}</span>`;
      printLine(`${perms}  1 nexus nexus ${size.padStart(6)} ${date} ${name}`, 'raw');
    });
  } else {
    const colored = files.map(f =>
      f.endsWith('/')
        ? `<span style="color:#77CCFF">${f}</span>`
        : `<span style="color:#AAAACC">${f}</span>`
    );
    printLine(colored.join('  '), 'raw');
  }
}

function cmdPwd() {
  const full = CLIState.currentPath === '~'
    ? CLIState.env.HOME
    : CLIState.currentPath;
  printLine(full, 'output');
}

function cmdCd(args) {
  const target = args[0] || '~';
  if (target === '..') {
    const parts = CLIState.currentPath.split('/');
    parts.pop();
    CLIState.currentPath = parts.join('/') || '~';
  } else if (target === '~' || target === '-') {
    CLIState.currentPath = '~';
  } else if (target.startsWith('/')) {
    CLIState.currentPath = target;
  } else {
    CLIState.currentPath = CLIState.currentPath === '~'
      ? `~/${target}`
      : `${CLIState.currentPath}/${target}`;
  }
  const el = document.getElementById('cliPath');
  if (el) el.textContent = CLIState.currentPath;
}

function cmdCat(args) {
  if (!args[0]) { printLine('cat: missing operand', 'error'); return; }
  const file = args[0];
  const contents = {
    'main.py': `#!/usr/bin/env python3\n# NEXUS AI Agent — main entry\nimport asyncio\nfrom agent import NexusAgent\n\nasync def main():\n    agent = NexusAgent()\n    await agent.start()\n\nif __name__ == '__main__':\n    asyncio.run(main())`,
    'notes.md': `# NEXUS Notes\n\n## TODO\n- [ ] Integrate new LLM endpoint\n- [ ] Add root detection bypass\n- [ ] Optimize memory usage\n\n## Notes\nSession started ${new Date().toLocaleDateString()}`,
    '.config': `[nexus]\nmodel=deepseek-coder-v2\nmax_tokens=4096\nstream=true\nroot_mode=false`,
    '/proc/version': `Linux version 5.15.78-android13 (android-build@build) (gcc version 12.1.0) #1 SMP PREEMPT`,
    '/proc/uptime': `${Math.floor(Math.random()*86400)}.${Math.floor(Math.random()*99)} ${Math.floor(Math.random()*172800)}.${Math.floor(Math.random()*99)}`,
    '/proc/cpuinfo': `processor   : 0\nmodel name  : ARM Cortex-A78\nBogoMIPS    : 38.40\nFeatures    : fp asimd evtstrm aes pmull sha1 sha2 crc32\nCPU variant : 0x1\nCPU part    : 0xd41`,
    '/proc/meminfo': `MemTotal:        7889920 kB\nMemFree:         1234567 kB\nMemAvailable:    3456789 kB\nBuffers:          102400 kB\nCached:          2048000 kB`,
  };

  const key = Object.keys(contents).find(k => file.endsWith(k));
  if (key) {
    contents[key].split('\n').forEach(l => printLine(l, 'output'));
  } else {
    printLine(`cat: ${file}: No such file or directory`, 'error');
  }
}

function cmdEcho(args) {
  let text = args.join(' ');
  // Handle env vars
  text = text.replace(/\$(\w+)/g, (_, v) => CLIState.env[v] || '');
  printLine(text, 'output');
}

function cmdEnv() {
  Object.entries(CLIState.env).forEach(([k, v]) => printLine(`${k}=${v}`, 'output'));
}

function cmdPs(args) {
  const showAll = args.includes('aux') || args.includes('-aux');
  const procs = [
    { pid: 1,    user: 'root',  cpu: '0.0', mem: '0.1', cmd: '/init' },
    { pid: 234,  user: 'root',  cpu: '0.1', mem: '0.3', cmd: 'zygote64' },
    { pid: 891,  user: 'system',cpu: '1.2', mem: '1.8', cmd: 'system_server' },
    { pid: 1203, user: 'nexus', cpu: '2.4', mem: '4.2', cmd: 'com.nexus.agent' },
    { pid: 1890, user: 'nexus', cpu: '0.0', mem: '0.2', cmd: 'sh' },
    { pid: 2341, user: 'media', cpu: '0.3', mem: '0.9', cmd: 'mediaserver' },
    { pid: 3102, user: 'wifi',  cpu: '0.1', mem: '0.4', cmd: 'wpa_supplicant' },
    { pid: 4521, user: 'nexus', cpu: '0.0', mem: '0.1', cmd: 'ps aux' },
  ];
  if (showAll) {
    printLine('USER       PID  %CPU %MEM COMMAND', 'output');
    procs.forEach(p => {
      printLine(`${p.user.padEnd(10)} ${String(p.pid).padEnd(5)} ${p.cpu.padEnd(5)} ${p.mem.padEnd(5)} ${p.cmd}`, 'output');
    });
  } else {
    printLine('  PID COMMAND', 'output');
    procs.forEach(p => printLine(`${String(p.pid).padStart(5)} ${p.cmd}`, 'output'));
  }
}

function cmdDf(args) {
  const human = args.includes('-h');
  printLine('Filesystem            Size   Used  Avail  Use%  Mounted on', 'output');
  const rows = [
    ['/dev/block/dm-0',  '6.1G', '4.2G', '1.9G', '70%', '/system'],
    ['/dev/block/dm-5',  '64G',  '38G',  '26G',  '59%', '/data'],
    ['tmpfs',            '3.8G', '156M', '3.6G', '4%',  '/dev'],
    ['/dev/block/sdf1',  '512G', '180G', '332G', '35%', '/sdcard'],
  ];
  rows.forEach(r => {
    printLine(`${r[0].padEnd(20)} ${r[1].padEnd(7)} ${r[2].padEnd(7)} ${r[3].padEnd(7)} ${r[4].padEnd(5)} ${r[5]}`, 'output');
  });
}

function cmdUname(args) {
  const full = args.includes('-a');
  if (full) {
    printLine('Linux nexus-android 5.15.78-android13 #1 SMP PREEMPT aarch64 GNU/Linux', 'output');
  } else {
    printLine('Linux', 'output');
  }
}

function cmdWhoami() {
  printLine(CLIState.isRoot ? 'root' : 'nexus', 'output');
}

function cmdId() {
  if (CLIState.isRoot) {
    printLine('uid=0(root) gid=0(root) groups=0(root)', 'output');
  } else {
    printLine('uid=10234(nexus) gid=10234(nexus) groups=10234(nexus),3003(inet),9997(everybody)', 'output');
  }
}

function cmdUptime() {
  const secs = Math.floor((Date.now() - AppState.sessionStart) / 1000);
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  printLine(` ${new Date().toLocaleTimeString()}  up ${h}:${String(m).padStart(2,'0')},  1 user,  load average: 0.42, 0.38, 0.35`, 'output');
}

function cmdTop() {
  printLine('top - ' + new Date().toLocaleTimeString() + ' up 2:34,  1 user,  load average: 0.42, 0.38, 0.35', 'output');
  printLine('Tasks:  89 total,   1 running,  88 sleeping,   0 stopped,   0 zombie', 'output');
  printLine('%Cpu(s): 12.3 us,  3.1 sy,  0.0 ni, 83.1 id,  0.0 wa,  0.0 hi,  1.5 si', 'output');
  printLine('MiB Mem :   7705.0 total,   1205.7 free,   4234.2 used,   2265.1 buff/cache', 'output');
  printLine('', 'output');
  printLine('  PID USER      PR  NI  VIRT    RES    SHR   S  %CPU  %MEM  TIME+   COMMAND', 'output');
  const tops = [
    [1203, 'nexus',  20, 0, '512M', '43M', '28M', 'S', 2.4, 4.2, '0:12.34', 'com.nexus.agent'],
    [891,  'system', 20, 0, '1.2G', '89M', '12M', 'S', 1.8, 1.8, '1:45.22', 'system_server'],
    [234,  'root',   20, 0, '128M', '18M', '4M',  'S', 0.3, 0.3, '0:03.12', 'zygote64'],
  ];
  tops.forEach(r => {
    printLine(`${String(r[0]).padStart(5)} ${r[1].padEnd(8)} ${String(r[2]).padStart(3)} ${String(r[3]).padStart(3)}  ${r[4].padEnd(7)} ${r[5].padEnd(6)} ${r[6].padEnd(6)} ${r[7]}  ${String(r[8]).padStart(4)}  ${String(r[9]).padStart(4)}  ${r[10].padEnd(8)} ${r[11]}`, 'output');
  });
}

function cmdGrep(args) {
  if (args.length < 2) { printLine('Usage: grep <pattern> <file>', 'error'); return; }
  const pattern = args[0];
  const file = args[1];
  printLine(`grep: searching '${pattern}' in ${file}...`, 'output');
  setTimeout(() => {
    printLine(`${file}:3:    # Contains: ${pattern}`, 'output');
    printLine(`${file}:7:    def ${pattern}_handler():`, 'output');
  }, 200);
}

function cmdFind(args) {
  const path = args[0] || '.';
  const name = args[args.indexOf('-name') + 1] || '*';
  printLine(`Searching ${path} for ${name}...`, 'output');
  setTimeout(() => {
    const results = [
      `${path}/src/${name.replace('*','')}main.kt`,
      `${path}/res/layout/fragment_main.xml`,
      `${path}/build.gradle.kts`,
    ];
    results.forEach(r => printLine(r, 'output'));
  }, 300);
}

function cmdMkdir(args) {
  if (!args[0]) { printLine('mkdir: missing operand', 'error'); return; }
  printLine(``, 'output'); // silent success like real mkdir
  const fs = CLIState.filesystem[CLIState.currentPath];
  if (fs) fs.push(args[0] + '/');
}

function cmdTouch(args) {
  if (!args[0]) { printLine('touch: missing operand', 'error'); return; }
  const fs = CLIState.filesystem[CLIState.currentPath];
  if (fs && !fs.includes(args[0])) fs.push(args[0]);
}

function cmdRm(args) {
  if (!args[0]) { printLine('rm: missing operand', 'error'); return; }
  const target = args.filter(a => !a.startsWith('-'))[0];
  if (target.includes('*') && !CLIState.isRoot) {
    printLine('rm: cannot remove: Permission denied', 'error');
    return;
  }
  printLine(``, 'output');
}

function cmdCp(args) {
  if (args.length < 2) { printLine('Usage: cp <src> <dst>', 'error'); return; }
  printLine(`'${args[0]}' -> '${args[1]}'`, 'output');
}

function cmdMv(args) {
  if (args.length < 2) { printLine('Usage: mv <src> <dst>', 'error'); return; }
  printLine(`'${args[0]}' renamed to '${args[1]}'`, 'output');
}

function cmdChmod(args) {
  if (args.length < 2) { printLine('Usage: chmod <mode> <file>', 'error'); return; }
  printLine(`mode of '${args[1]}' changed to ${args[0]}`, 'output');
}

function cmdExport(args) {
  if (!args[0]) { cmdEnv(); return; }
  const [key, val] = args[0].split('=');
  if (key && val) {
    CLIState.env[key] = val;
    printLine(`export ${key}=${val}`, 'output');
  }
}

function cmdPing(args) {
  const host = args[0] || '8.8.8.8';
  printLine(`PING ${host}: 56 data bytes`, 'output');
  let count = 0;
  const iv = setInterval(() => {
    const ms = (Math.random() * 30 + 5).toFixed(3);
    printLine(`64 bytes from ${host}: seq=${count} ttl=64 time=${ms} ms`, 'output');
    count++;
    if (count >= 4) {
      clearInterval(iv);
      printLine(`--- ${host} ping statistics ---`, 'output');
      printLine(`4 packets transmitted, 4 received, 0% packet loss`, 'output');
    }
  }, 800);
}

function cmdNetstat() {
  printLine('Active Internet connections:', 'output');
  printLine('Proto Recv-Q Send-Q Local Address           Foreign Address         State', 'output');
  const conns = [
    ['tcp', '0', '0', '0.0.0.0:22',    '0.0.0.0:*',            'LISTEN'],
    ['tcp', '0', '0', '127.0.0.1:8080','0.0.0.0:*',            'LISTEN'],
    ['tcp', '0', '52', '10.0.2.15:51234', '142.250.82.14:443', 'ESTABLISHED'],
  ];
  conns.forEach(c => printLine(c.map((v,i) => v.padEnd([5,7,7,24,24,11][i]||v.length)).join(' '), 'output'));
}

function cmdHistory() {
  CLIState.history.forEach((cmd, i) => {
    printLine(`${String(i+1).padStart(4)}  ${cmd}`, 'output');
  });
}

function cmdSu(args) {
  if (!CLIState.isRoot) {
    printLine('Checking root access...', 'output');
    setTimeout(() => {
      // Simulate su attempt
      CLIState.isRoot = true;
      CLIState.env.USER = 'root';
      printLine('[NEXUS] Root access granted ✓', 'success');
      updateRootUI(true);
      showToast('Root mode activated');
    }, 600);
  } else {
    printLine('Already running as root', 'output');
  }
}

function cmdExit() {
  if (CLIState.isRoot) {
    CLIState.isRoot = false;
    CLIState.env.USER = 'nexus';
    printLine('Exiting root shell...', 'output');
    updateRootUI(false);
  } else {
    printLine('Use the app navigation to switch tabs', 'output');
  }
}

function cmdRunScript(name) {
  printLine(`Executing ${name}...`, 'output');
  setTimeout(() => {
    printLine('[OK] Script completed successfully', 'success');
  }, 500);
}

// ── Root toggle button ─────────────────────────────────────
function toggleRoot() {
  if (!CLIState.isRoot) {
    printLine('nexus@android:~$ su -', 'output');
    cmdSu([]);
  } else {
    cmdExit();
  }
}

function updateRootUI(isRoot) {
  if (cliBadge) {
    cliBadge.textContent = isRoot ? 'ROOT' : 'USER';
    cliBadge.className = isRoot ? 'cli-badge root' : 'cli-badge';
  }
  updatePromptDisplay();
}

// ── Print helpers ──────────────────────────────────────────
function printPromptLine(cmd) {
  const div = document.createElement('div');
  div.className = 'terminal-line';
  const promptChar = CLIState.isRoot ? '#' : '$';
  const user = CLIState.isRoot ? 'root' : 'nexus';
  div.innerHTML = `<span class="t-prompt">${user}@android:${CLIState.currentPath}${promptChar}</span> <span class="t-text">${escCLI(cmd)}</span>`;
  insertBeforeCursor(div);
}

function printLine(text, type = 'output') {
  const div = document.createElement('div');
  div.className = 'terminal-line';
  if (type === 'raw') {
    div.innerHTML = text;
  } else {
    const cls = { output: 't-output', error: 't-error', success: 't-success' }[type] || 't-output';
    div.innerHTML = `<span class="${cls}">${text}</span>`;
  }
  insertBeforeCursor(div);
}

function insertBeforeCursor(el) {
  const cursorLine = document.querySelector('.terminal-cursor-line');
  if (cursorLine) {
    terminal.insertBefore(el, cursorLine);
  } else {
    terminal.appendChild(el);
  }
  terminal.scrollTop = terminal.scrollHeight;
}

function updatePromptDisplay() {
  const promptChar = CLIState.isRoot ? '#' : '$';
  const user = CLIState.isRoot ? 'root' : 'nexus';
  if (termPrompt) termPrompt.textContent = `${user}@android:${CLIState.currentPath}${promptChar}`;
  if (cliPath) cliPath.textContent = CLIState.currentPath;
}

function escCLI(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
