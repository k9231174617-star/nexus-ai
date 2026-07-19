/**
 * Nexus AI — Skill Hub Plugin
 * Integrates 50+ skills from awesome-agent-skills into the Nexus AI dashboard.
 * Skills are accessible via sidebar, chat commands, and auto-routing.
 */
(function() {
    const pm = window.NexusPlugins;
    const sr = window.SkillRegistry;
    if (!pm || !sr) { console.warn('[SkillHub] PluginManager or SkillRegistry not found'); return; }

    // ── Sidebar Items ──────────────────────────────────────────
    pm.on('sidebar:items', () => [
        {
            id: 'skill-hub',
            icon: '🧰',
            label: 'Skill Hub',
            section: 'tools',
            priority: 1,
            onClick: () => renderSkillHub(),
        },
    ]);

    // ── Chat Commands ──────────────────────────────────────────
    pm.on('chat:commands', () => [
        {
            command: '/skills',
            description: 'Список всех доступных AI скиллов. Использование: /skills [категория|поиск]',
            handler: (args) => handleSkillsCommand(args.join(' ')),
        },
        {
            command: '/skill',
            description: 'Активировать скилл. Использование: /skill <id> [аргументы]',
            handler: (args) => handleActivateSkill(args),
        },
        {
            command: '/mcp',
            description: 'MCP команда. Использование: /mcp <server> <tool> <args>',
            handler: (args) => handleMCPCommand(args),
        },
    ]);

    // ── Init ────────────────────────────────────────────────────
    pm.on('app:init', () => {
        console.log('[SkillHub] Initialized with', sr.skills.length, 'skills');
    });

    pm.on('app:render', () => {
        updateStatusBar();
    });

    // ── Handlers ───────────────────────────────────────────────
    function handleSkillsCommand(query) {
        const main = getMainPanel();
        if (!main) return;

        if (!query) {
            renderSkillHub();
            return;
        }

        // Search
        const results = sr.search(query);
        const catInfo = sr.categoryNames[query];
        if (catInfo) {
            renderCategoryView(query);
        } else if (results.length > 0) {
            renderSearchResults(results, query);
        } else {
            appendToChat('assistant', `Скилл "${query}" не найден. Используйте /skills чтобы увидеть все категории.`);
        }
    }

    function handleActivateSkill(args) {
        if (!args || args.length === 0) {
            appendToChat('assistant', 'Укажите ID скилла: /skill <id>. Используйте /skills для списка.');
            return;
        }
        const skillId = args[0];
        const skill = sr.get(skillId);
        if (!skill) {
            appendToChat('assistant', `Скилл "${skillId}" не найден.`);
            return;
        }
        appendToChat('assistant', `🔧 Активирую скилл: **${skill.name}** (${skill.company})\n> ${skill.description}\n> Источник: ${skill.url}`);
        sr.activate(skillId).then(result => {
            if (result.loaded) {
                appendToChat('assistant', `✅ Скилл **${skill.name}** загружен (${(result.content.length / 1024).toFixed(1)} KB)`);
            } else {
                appendToChat('assistant', `⚠️ Не удалось загрузить скилл: ${result.error}`);
            }
        });
    }

    function handleMCPCommand(args) {
        appendToChat('assistant', `🔌 MCP: подключение к ${args[0] || 'серверу'}...\n> Убедитесь, что MCP сервер запущен. Добавить сервер можно в разделе MCP Servers.`);
    }

    // ── Render Functions ───────────────────────────────────────
    function renderSkillHub() {
        const main = getMainPanel();
        if (!main) return;

        const stats = sr.getStats();
        const categories = sr.getCategories();

        const catCards = categories.map(cat => {
            const info = sr.categoryNames[cat] || { icon: '📦', name: cat, color: '#666' };
            const skills = sr.getByCategory(cat);
            return `
                <div class="skill-category-card" onclick="renderCategoryView('${cat}')" 
                     style="background:var(--bg-card);border-radius:12px;padding:16px;border:1px solid var(--border-subtle);
                            cursor:pointer;transition:all 0.2s;hover:border-color:${info.color}">
                    <div style="display:flex;align-items:center;gap:10px;margin-bottom:8px">
                        <span style="font-size:24px">${info.icon}</span>
                        <div>
                            <div style="color:var(--text-primary);font-weight:600;font-size:14px">${info.name}</div>
                            <div style="color:var(--text-muted);font-size:11px">${skills.length} скиллов</div>
                        </div>
                    </div>
                    <div style="color:var(--text-secondary);font-size:12px;line-height:1.4">
                        ${skills.slice(0, 3).map(s => `🔹 ${s.name}`).join('<br>')}
                        ${skills.length > 3 ? `<br><span style="color:${info.color}">+ ещё ${skills.length - 3}</span>` : ''}
                    </div>
                </div>
            `;
        }).join('');

        main.innerHTML = `
            <div style="padding:20px;max-width:1200px;margin:0 auto">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
                    <div>
                        <h2 style="color:var(--red-core);margin:0">🧰 Skill Hub</h2>
                        <p style="color:var(--text-muted);margin:4px 0 0;font-size:13px">
                            ${stats.total} скиллов из awesome-agent-skills · ${stats.categories} категорий
                        </p>
                    </div>
                    <div style="display:flex;gap:10px;align-items:center">
                        <input id="skillSearchInput" placeholder="🔍 Поиск скилла..." 
                               style="padding:8px 14px;background:var(--bg-input);color:var(--text-primary);
                                      border:1px solid var(--border-subtle);border-radius:8px;width:220px;font-size:13px"
                               oninput="searchSkills(this.value)">
                        <span style="color:var(--text-muted);font-size:12px;background:var(--bg-card);padding:4px 12px;border-radius:20px;border:1px solid var(--border-subtle)">
                            📥 ${stats.loaded} загружено
                        </span>
                    </div>
                </div>

                <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:12px" id="skillCategoriesGrid">
                    ${catCards}
                </div>

                <div style="margin-top:24px;background:var(--bg-card);border-radius:12px;padding:16px;border:1px solid var(--border-subtle)">
                    <div style="color:var(--text-primary);font-weight:600;font-size:13px;margin-bottom:8px">💡 Быстрые команды</div>
                    <div style="display:flex;gap:8px;flex-wrap:wrap">
                        <code style="background:var(--bg-input);padding:4px 10px;border-radius:6px;font-size:12px">/skills</code>
                        <code style="background:var(--bg-input);padding:4px 10px;border-radius:6px;font-size:12px">/skill mcp-builder</code>
                        <code style="background:var(--bg-input);padding:4px 10px;border-radius:6px;font-size:12px">/skills security</code>
                        <code style="background:var(--bg-input);padding:4px 10px;border-radius:6px;font-size:12px">/skills testing</code>
                        <code style="background:var(--bg-input);padding:4px 10px;border-radius:6px;font-size:12px">/mcp stagehand</code>
                    </div>
                </div>
            </div>
        `;
    }

    function renderCategoryView(category) {
        const main = getMainPanel();
        if (!main) return;

        const skills = sr.getByCategory(category);
        const info = sr.categoryNames[category] || { icon: '📦', name: category, color: '#666' };

        main.innerHTML = `
            <div style="padding:20px;max-width:900px;margin:0 auto">
                <div style="display:flex;align-items:center;gap:10px;margin-bottom:16px">
                    <button onclick="renderSkillHub()" style="background:none;border:1px solid var(--border-subtle);color:var(--text-secondary);padding:6px 12px;border-radius:8px;cursor:pointer">← Назад</button>
                    <span style="font-size:28px">${info.icon}</span>
                    <h2 style="color:var(--text-primary);margin:0">${info.name}</h2>
                    <span style="color:var(--text-muted);font-size:13px">${skills.length} скиллов</span>
                </div>

                <div style="display:flex;flex-direction:column;gap:8px">
                    ${skills.map(s => `
                        <div class="skill-item" data-id="${s.id}" 
                             style="background:var(--bg-card);border-radius:10px;padding:14px 16px;
                                    border:1px solid var(--border-subtle);cursor:pointer;
                                    transition:all 0.15s;display:flex;align-items:center;gap:12px"
                             onmouseover="this.style.borderColor='${info.color}'" 
                             onmouseout="this.style.borderColor='var(--border-subtle)'"
                             onclick="activateSkill('${s.id}')">
                            <div style="width:32px;height:32px;border-radius:8px;background:${info.color}20;display:flex;align-items:center;justify-content:center;font-size:16px;flex-shrink:0">
                                ${info.icon}
                            </div>
                            <div style="flex:1;min-width:0">
                                <div style="color:var(--text-primary);font-weight:500;font-size:13px">${s.name}</div>
                                <div style="color:var(--text-muted);font-size:12px">${s.description}</div>
                                <div style="display:flex;gap:6px;margin-top:4px;flex-wrap:wrap">
                                    <span style="font-size:11px;color:${info.color}">${s.company}</span>
                                    ${s.tags.slice(0, 3).map(t => 
                                        `<span style="font-size:10px;background:var(--bg-input);padding:2px 8px;border-radius:10px;color:var(--text-muted)">${t}</span>`
                                    ).join('')}
                                    <span style="font-size:10px;background:${s.tier === 'free' ? 'rgba(68,221,136,0.15)' : 'rgba(255,10,47,0.1)'};padding:2px 8px;border-radius:10px;color:${s.tier === 'free' ? '#44dd88' : '#FF0A2F'}">
                                        ${s.tier === 'free' ? 'Free' : 'Paid'}
                                    </span>
                                </div>
                            </div>
                            <button class="skill-activate-btn" onclick="event.stopPropagation();activateSkill('${s.id}')"
                                    style="background:var(--red-core);color:white;border:none;padding:6px 16px;border-radius:8px;cursor:pointer;font-size:12px;font-weight:500;flex-shrink:0">
                                🔧 Apply
                            </button>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;
    }

    function renderSearchResults(results, query) {
        const main = getMainPanel();
        if (!main) return;

        main.innerHTML = `
            <div style="padding:20px">
                <div style="display:flex;align-items:center;gap:10px;margin-bottom:16px">
                    <button onclick="renderSkillHub()" style="background:none;border:1px solid var(--border-subtle);color:var(--text-secondary);padding:6px 12px;border-radius:8px;cursor:pointer">← Назад</button>
                    <h2 style="color:var(--text-primary);margin:0">🔍 Результаты: "${query}"</h2>
                    <span style="color:var(--text-muted);font-size:13px">${results.length} найдено</span>
                </div>
                ${results.map(s => `<div style="padding:8px 12px;background:var(--bg-card);border-radius:8px;margin-bottom:4px;cursor:pointer" onclick="activateSkill('${s.id}')">
                    <strong style="color:var(--text-primary)">${s.name}</strong>
                    <span style="color:var(--text-muted);font-size:12px;margin-left:8px">${s.description}</span>
                </div>`).join('')}
            </div>
        `;
    }

    function updateStatusBar() {
        const badge = document.getElementById('skillCount');
        if (!badge) {
            const statusBar = document.querySelector('.agent-status');
            if (statusBar) {
                const el = document.createElement('span');
                el.id = 'skillCount';
                el.style.cssText = 'margin-left:8px;font-size:11px;color:var(--text-muted)';
                el.textContent = `${sr.skills.length} skills`;
                statusBar.appendChild(el);
            }
        }
    }

    function appendToChat(role, content) {
        const chat = document.getElementById('chatMessages');
        if (!chat) return;
        const div = document.createElement('div');
        div.className = `message ${role}-msg`;
        div.innerHTML = `<div class="msg-content"><div class="msg-text">${content}</div></div>`;
        chat.appendChild(div);
        chat.scrollTop = chat.scrollHeight;
    }

    function getMainPanel() {
        return document.querySelector('.tab-panel.active') || document.querySelector('main > *:last-child') || document.querySelector('.app-content');
    }
})();

// ── Global Functions (for onclick handlers) ──────────────────
window.renderSkillHub = () => {
    const event = new CustomEvent('skill-hub:open');
    document.dispatchEvent(event);
};

window.renderCategoryView = (cat) => {
    const sr = window.SkillRegistry;
    const main = document.querySelector('.tab-panel.active');
    if (!main || !sr) return;

    const skills = sr.getByCategory(cat);
    const info = sr.categoryNames[cat] || { icon: '📦', name: cat, color: '#666' };

    main.innerHTML = `
        <div style="padding:20px;max-width:900px;margin:0 auto">
            <div style="display:flex;align-items:center;gap:10px;margin-bottom:16px">
                <button onclick="document.querySelector('[data-tab=\\'tools\\']')?.click() || window.renderSkillHub()" 
                        style="background:none;border:1px solid var(--border-subtle);color:var(--text-secondary);padding:6px 12px;border-radius:8px;cursor:pointer">← Назад</button>
                <span style="font-size:28px">${info.icon}</span>
                <h2 style="color:var(--text-primary);margin:0">${info.name}</h2>
                <span style="color:var(--text-muted);font-size:13px">${skills.length} скиллов</span>
            </div>
            <div style="display:flex;flex-direction:column;gap:8px">
                ${skills.map(s => `
                    <div class="skill-item" style="background:var(--bg-card);border-radius:10px;padding:14px 16px;border:1px solid var(--border-subtle);display:flex;align-items:center;gap:12px">
                        <div style="flex:1;min-width:0">
                            <div style="color:var(--text-primary);font-weight:500;font-size:13px">${s.name}</div>
                            <div style="color:var(--text-muted);font-size:12px">${s.description}</div>
                            <div style="display:flex;gap:6px;margin-top:4px;flex-wrap:wrap">
                                <span style="font-size:11px;color:${info.color}">${s.company}</span>
                            </div>
                        </div>
                        <button onclick="window.activateSkill('${s.id}')" style="background:var(--red-core);color:white;border:none;padding:6px 16px;border-radius:8px;cursor:pointer;font-size:12px">🔧 Apply</button>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
};

window.activateSkill = (skillId) => {
    const sr = window.SkillRegistry;
    const skill = sr?.get(skillId);
    if (!skill) return;

    const chat = document.getElementById('chatMessages');
    if (chat) {
        const div = document.createElement('div');
        div.className = 'message assistant-msg';
        div.innerHTML = `<div class="msg-content">
            <div class="msg-header"><span class="msg-name">Skill Hub</span></div>
            <div class="msg-text">
                <strong>🔧 ${skill.name}</strong><br>
                <span style="color:var(--text-muted);font-size:13px">${skill.description}</span><br><br>
                <span style="font-size:12px">📎 <a href="${skill.url}" target="_blank" style="color:var(--red-core)">Открыть skill</a></span>
            </div>
        </div>`;
        chat.appendChild(div);
        chat.scrollTop = chat.scrollHeight;
    }

    sr.activate(skillId).then(result => {
        if (result.loaded) {
            appendNotification(`✅ Skill "${skill.name}" loaded`);
        }
    });
};

window.searchSkills = (query) => {
    const sr = window.SkillRegistry;
    if (!sr || !query) return;
    const results = sr.search(query);
    const grid = document.getElementById('skillCategoriesGrid');
    if (!grid) return;
    if (results.length === 0) {
        grid.innerHTML = '<div style="padding:40px;text-align:center;color:var(--text-muted)">Ничего не найдено</div>';
        return;
    }
    grid.innerHTML = results.map(s => {
        const info = sr.categoryNames[s.category] || { icon: '📦', name: s.category, color: '#666' };
        return `
            <div style="background:var(--bg-card);border-radius:10px;padding:14px;border:1px solid var(--border-subtle);cursor:pointer" onclick="window.activateSkill('${s.id}')">
                <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">
                    <span>${info.icon}</span>
                    <strong style="color:var(--text-primary);font-size:13px">${s.name}</strong>
                </div>
                <div style="color:var(--text-muted);font-size:12px">${s.description}</div>
            </div>
        `;
    }).join('');
};

function appendNotification(msg) {
    const toast = document.getElementById('toast');
    if (toast) {
        toast.textContent = msg;
        toast.style.opacity = '1';
        setTimeout(() => toast.style.opacity = '0', 2500);
    }
} 
