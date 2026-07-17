/* ============================================================
   NEXUS AI — Code Sandbox UI
   Multi-language execution, resource monitoring, output console
   ============================================================ */

'use strict';

// ── State ──────────────────────────────────────────────────
const SandboxState = {
  code: '',
  language: 'python',
  isRunning: false,
  lastResult: null,
  history: []
};

// ── Supported Languages ────────────────────────────────────
const LANGUAGES = {
  python: {
    name: 'Python',
    icon: '🐍',
    ext: 'py',
    template: '# Write your Python code here\nprint("Hello from NEXUS Sandbox")\n\n# Example: Fibonacci\ndef fib(n):\n    return n if n < 2 else fib(n-1) + fib(n-2)\n\nprint(f"fib(10) = {fib(10)}")'
  },
  javascript: {
    name: 'JavaScript',
    icon: '⚡',
    ext: 'js',
    template: '// Write your JavaScript here\nconsole.log("Hello from NEXUS Sandbox");\n\n// Example: Async fetch\nasync function demo() {\n  const data = { status: "ok", latency: 42 };\n  return data;\n}\n\ndemo().then(r => console.log(r));'
  },
  kotlin: {
    name: 'Kotlin',
    icon: '🅺',
    ext: 'kt',
    template: '// Kotlin sandbox (simulated)\nfun main() {\n    val list = listOf(1, 2, 3, 4, 5)\n    val doubled = list.map { it * 2 }\n    println("Doubled: $doubled")\n    \n    // Data class demo\n    data class User(val name: String, val id: Int)\n    println(User("Nexus", 1))\n}'
  },
  bash: {
    name: 'Bash',
    icon: '🐚',
    ext: 'sh',
    template: '#!/bin/bash\n# NEXUS Shell Sandbox\necho "Current user: $(whoami)"\necho "Working dir: $(pwd)"\n\n# Loop demo\nfor i in {1..5}; do\n  echo "Iteration $i"\ndone'
  },
  rust: {
    name: 'Rust',
    icon: '🦀',
    ext: 'rs',
    template: 'fn main() {\n    let msg = "Hello from NEXUS Rust sandbox";\n    println!("{}", msg);\n    \n    // Vector demo\n    let nums = vec![1, 2, 3, 4, 5];\n    let sum: i32 = nums.iter().sum();\n    println!("Sum: {}", sum);\n}'
  }
};

// ── DOM ────────────────────────────────────────────────────
const codeEditorPanel  = document.getElementById('codeEditorPanel');
const outputConsole    = document.getElementById('outputConsole');
const resourceMonitor  = document.getElementById('resourceMonitorView');
const languageSelector = document.getElementById('languageSelector');
const sandboxRunBtn    = document.getElementById('sandboxRunBtn');
const sandboxStopBtn   = document.getElementById('sandboxStopBtn');
const sandboxClearBtn  = document.getElementById('sandboxClearBtn');
const sandboxCopyBtn   = document.getElementById('sandboxCopyBtn');
const cpuBar           = document.getElementById('cpuBar');
const memBar           = document.getElementById('memBar');
const timeVal          = document.getElementById('execTimeVal');

// ── Init ───────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initSandbox();
  initLanguageSelector();
  initSandboxControls();
  startResourceMonitor();
});

function initSandbox() {
  SandboxState.language = 'python';
  loadTemplate('python');

  if (codeEditorPanel) {
    codeEditorPanel.contentEditable = true;
    codeEditorPanel.spellcheck = false;
    codeEditorPanel.addEventListener('input', () => {
      SandboxState.code = codeEditorPanel.innerText;
    });
  }
}

function initLanguageSelector() {
  if (!languageSelector) return;

  languageSelector.innerHTML = Object.entries(LANGUAGES).map(([key, lang]) => `
    <option value="${key}" ${key === 'python' ? 'selected' : ''}>
      ${lang.icon} ${lang.name}
    </option>
  `).join('');

  languageSelector.addEventListener('change', () => {
    const newLang = languageSelector.value;
    if (SandboxState.code && SandboxState.code !== LANGUAGES[SandboxState.language]?.template) {
      if (!confirm('Switching language will reset code. Continue?')) {
        languageSelector.value = SandboxState.language;
        return;
      }
    }
    SandboxState.language = newLang;
    loadTemplate(newLang);
    showToast(`Switched to ${LANGUAGES[newLang].name}`);
  });
}

function loadTemplate(langKey) {
  const lang = LANGUAGES[langKey];
  if (!lang || !codeEditorPanel) return;

  codeEditorPanel.innerHTML = syntaxHighlight(lang.template, langKey);
  SandboxState.code = lang.template;
}

function initSandboxControls() {
  sandboxRunBtn?.addEventListener('click', runSandboxCode);
  sandboxStopBtn?.addEventListener('click', stopSandbox);
  sandboxClearBtn?.addEventListener('click', clearSandbox);
  sandboxCopyBtn?.addEventListener('click', copySandboxCode);
}

// ── Code Execution ─────────────────────────────────────────
function runSandboxCode() {
  if (SandboxState.isRunning) {
    showToast('Already running');
    return;
  }

  const code = codeEditorPanel?.innerText || '';
  if (!code.trim()) {
    showToast('Enter code to run');
    return;
  }

  SandboxState.isRunning = true;
  sandboxRunBtn && (sandboxRunBtn.disabled = true);
  sandboxRunBtn && (sandboxRunBtn.innerHTML = `
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" style="animation:spin 1s linear infinite">
      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z" fill="#FF0A2F"/>
    </svg>
    Running...
  `);

  appendOutput('─'.repeat(50), 'separator');
  appendOutput(`[${LANGUAGES[SandboxState.language].name}] Executing...`, 'info');

  const t0 = performance.now();

  // Simulate execution
  setTimeout(() => {
    const result = simulateExecution(code, SandboxState.language);
    const elapsed = Math.round(performance.now() - t0);

    SandboxState.isRunning = false;
    sandboxRunBtn && (sandboxRunBtn.disabled = false);
    sandboxRunBtn && (sandboxRunBtn.innerHTML = `
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
        <path d="M8 5v14l11-7z" fill="#0A0A0F"/>
      </svg>
      Run
    `);

    timeVal && (timeVal.textContent = elapsed + 'ms');

    if (result.success) {
      appendOutput(result.output, 'output');
      appendOutput(`✓ Completed in ${elapsed}ms`, 'success');
      SandboxState.lastResult = result;
      SandboxState.history.push({ code, result, timestamp: Date.now() });
    } else {
      appendOutput(result.error, 'error');
      appendOutput(`✕ Failed after ${elapsed}ms`, 'error');
    }

    appendOutput('─'.repeat(50), 'separator');

  }, Math.random() * 1000 + 500);
}

function simulateExecution(code, lang) {
  // Simulated execution for demo
  const outputs = {
    python: [
      'Hello from NEXUS Sandbox\nfib(10) = 55',
      'Doubled: [2, 4, 6, 8, 10]\nSum: 30',
      'Error: NameError: name \'undefined_var\' is not defined'
    ],
    javascript: [
      'Hello from NEXUS Sandbox\n{ status: "ok", latency: 42 }',
      'TypeError: Cannot read property \'x\' of undefined'
    ],
    kotlin: [
      'Doubled: [2, 4, 6, 8, 10]\nUser(name=Nexus, id=1)',
      'NullPointerException: lateinit property x has not been initialized'
    ],
    bash: [
      'Current user: nexus\nWorking dir: /data/data/com.nexus.agent\nIteration 1\nIteration 2\nIteration 3\nIteration 4\nIteration 5',
      'bash: syntax error near unexpected token `then\''
    ],
    rust: [
      'Hello from NEXUS Rust sandbox\nSum: 15',
      'error[E0425]: cannot find value `x` in this scope'
    ]
  };

  const langOutputs = outputs[lang] || ['Output simulated'];
  const hasError = code.includes('error') || code.includes('Error') || Math.random() < 0.1;

  if (hasError && langOutputs.length > 1) {
    return {
      success: false,
      output: '',
      error: langOutputs[1] || 'Execution failed'
    };
  }

  return {
    success: true,
    output: langOutputs[0],
    error: ''
  };
}

function stopSandbox() {
  if (!SandboxState.isRunning) {
    showToast('Nothing to stop');
    return;
  }
  SandboxState.isRunning = false;
  sandboxRunBtn && (sandboxRunBtn.disabled = false);
  sandboxRunBtn && (sandboxRunBtn.innerHTML = 'Run');
  appendOutput('Execution cancelled by user', 'warning');
  showToast('Execution stopped');
}

function clearSandbox() {
  if (codeEditorPanel) {
    codeEditorPanel.innerHTML = '';
    SandboxState.code = '';
  }
  if (outputConsole) outputConsole.innerHTML = '';
  showToast('Sandbox cleared');
}

function copySandboxCode() {
  const code = codeEditorPanel?.innerText || '';
  navigator.clipboard.writeText(code).then(() => {
    showToast('Code copied to clipboard');
  }).catch(() => {
    // Fallback
    const ta = document.createElement('textarea');
    ta.value = code;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    showToast('Code copied');
  });
}

// ── Output Console ─────────────────────────────────────────
function appendOutput(text, type = 'output') {
  if (!outputConsole) return;

  const div = document.createElement('div');
  div.className = `console-line ${type}`;

  const colors = {
    output:    '#AAAACC',
    error:     '#FF4466',
    success:   '#44AA66',
    warning:   '#FFAA44',
    info:      '#77CCFF',
    separator: '#2A2A3A'
  };

  if (type === 'separator') {
    div.innerHTML = `<span style="color:${colors[type]};letter-spacing:2px">${text}</span>`;
  } else {
    // Preserve whitespace and handle newlines
    const lines = text.split('\n');
    div.innerHTML = lines.map(line =>
      `<span style="color:${colors[type] || colors.output}">${escHtml(line) || ' '}</span>`
    ).join('<br>');
  }

  outputConsole.appendChild(div);
  outputConsole.scrollTop = outputConsole.scrollHeight;
}

// ── Resource Monitor ───────────────────────────────────────
function startResourceMonitor() {
  if (!resourceMonitor) return;

  setInterval(() => {
    if (!SandboxState.isRunning) {
      updateResourceBars(0, 0);
      return;
    }

    const cpu = Math.random() * 30 + (SandboxState.isRunning ? 40 : 0);
    const mem = Math.random() * 20 + (SandboxState.isRunning ? 15 : 5);
    updateResourceBars(cpu, mem);

  }, 500);
}

function updateResourceBars(cpu, mem) {
  if (cpuBar) {
    cpuBar.style.width = Math.min(cpu, 100) + '%';
    cpuBar.style.background = cpu > 80 ? '#FF4466' : cpu > 50 ? '#FFAA44' : '#44AA66';
  }
  if (memBar) {
    memBar.style.width = Math.min(mem, 100) + '%';
    memBar.style.background = mem > 80 ? '#FF4466' : mem > 50 ? '#FFAA44' : '#44AA66';
  }
}

// ── Syntax Highlighting ────────────────────────────────────
function syntaxHighlight(code, lang) {
  // Simple regex-based highlighting
  let html = escHtml(code);

  const patterns = {
    python: {
      comment: /(#.*$)/gm,
      string:  /(".*?"|'.*?')/g,
      keyword: /\b(def|class|if|else|elif|for|while|return|import|from|as|try|except|finally|with|lambda|yield|async|await|print)\b/g,
      number:  /\b\d+\b/g,
      func:    /\b([a-zA-Z_]\w*)\s*(?=\()/g
    },
    javascript: {
      comment: /(\/\/.*$|\/\*[\s\S]*?\*\/)/gm,
      string:  /(".*?"|'.*?'|`.*?`)/g,
      keyword: /\b(const|let|var|function|return|if|else|for|while|async|await|try|catch|class|import|export|from|new|this)\b/g,
      number:  /\b\d+\b/g,
      func:    /\b([a-zA-Z_]\w*)\s*(?=\()/g
    },
    kotlin: {
      comment: /(\/\/.*$|\/\*[\s\S]*?\*\/)/gm,
      string:  /(".*?"|'.*?')/g,
      keyword: /\b(fun|val|var|class|data|if|else|when|for|while|return|import|package|try|catch|throw|suspend|inline|companion|object)\b/g,
      number:  /\b\d+\b/g,
      func:    /\b([a-zA-Z_]\w*)\s*(?=\()/g
    },
    bash: {
      comment: /(#.*$)/gm,
      string:  /(".*?"|'.*?')/g,
      keyword: /\b(if|then|else|elif|fi|for|do|done|while|case|esac|function|return|exit|echo|cd|ls|export)\b/g,
      number:  /\b\d+\b/g
    },
    rust: {
      comment: /(\/\/.*$|\/\*[\s\S]*?\*\/)/gm,
      string:  /(".*?"|'.*?')/g,
      keyword: /\b(fn|let|mut|if|else|match|for|while|return|use|mod|struct|enum|impl|trait|pub|unsafe|async|await)\b/g,
      number:  /\b\d+\b/g,
      func:    /\b([a-zA-Z_]\w*)\s*(?=\()/g
    }
  };

  const p = patterns[lang] || patterns.python;

  // Apply highlighting (order matters)
  if (p.comment) html = html.replace(p.comment, '<span style="color:#666677;font-style:italic">$1</span>');
  if (p.string)  html = html.replace(p.string, '<span style="color:#88CC88">$1</span>');
  if (p.keyword) html = html.replace(p.keyword, '<span style="color:#FF7799;font-weight:600">$1</span>');
  if (p.number)  html = html.replace(p.number, '<span style="color:#FFAA44">$1</span>');
  if (p.func)    html = html.replace(p.func, '<span style="color:#77CCFF">$1</span>');

  return html;
}

// ── Helpers ────────────────────────────────────────────────
function escHtml(s) {
  return s.replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
}
