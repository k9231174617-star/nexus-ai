(function() {
    const pm = window.NexusPlugins;
    if (!pm) return;

    let state = {
        agentActive: false,
        strategies: [
            { id: 's1', name: 'Momentum Scalper', type: 'SCALPING', assets: 'BTC, ETH, SOL', active: true, sharpe: 1.42, winRate: 62 },
            { id: 's2', name: 'Swing Trader Pro', type: 'SWING_TRADING', assets: 'BTC, ETH', active: true, sharpe: 1.88, winRate: 68 },
        ],
        positions: [
            { asset: 'BTC', dir: 'LONG', entry: '64,200', current: '65,850', pnl: '+2.57%', status: 'ACTIVE' },
            { asset: 'ETH', dir: 'LONG', entry: '3,480', current: '3,512', pnl: '+0.92%', status: 'ACTIVE' },
        ],
        memeCoins: [],
        logs: [],
    };

    pm.on('sidebar:items', () => [{
        id: 'trading-dash',
        icon: '📈',
        label: 'Trading Agent',
        section: 'tools',
        priority: 2,
        onClick: () => renderDashboard(),
    }]);

    pm.on('chat:commands', () => [
        { command: '/trade', description: 'Trade: start|stop|status', handler: (args) => {
            const cmd = args.join(' ');
            if (cmd === 'start') { state.agentActive = true; addLog('▶ Trading started'); }
            else if (cmd === 'stop') { state.agentActive = false; addLog('⏹ Trading stopped'); }
            else { renderDashboard(); }
        }},
        { command: '/meme', description: 'Scan meme coins', handler: () => {
            addLog('🔍 Scanning meme coins...');
            setTimeout(() => addLog('✅ Found 4 meme coins (PEPE, BONK, WIF, MOG)'), 800);
        }},
    ]);

    function renderDashboard() {
        const main = getMainPanel();
        if (!main) return;
        const totalPnl = state.positions.reduce((s, p) => s + parseFloat(p.pnl.replace('%','').replace('+','')), 0);
        main.innerHTML = `
            <div style="padding:20px;max-width:1200px;margin:0 auto">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px">
                    <div>
                        <h2 style="color:var(--red-core);margin:0">📈 Crypto Trading Agent</h2>
                        <p style="color:var(--text-muted);margin:4px 0 0;font-size:13px">AI-optimized trading · ${state.agentActive ? '🟢 Active' : '⚪ Idle'}</p>
                    </div>
                    <button onclick="toggleTrading()" style="background:${state.agentActive ? '#ef4444' : '#44dd88'};color:white;border:none;padding:8px 24px;border-radius:8px;cursor:pointer;font-weight:600">${state.agentActive ? '⏹ Stop' : '▶ Start'}</button>
                </div>
                <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:20px">
                    ${[{l:'Total P&L', v:(totalPnl>0?'+':'')+totalPnl.toFixed(2)+'%', c:totalPnl>=0?'#44dd88':'#ef4444'},{l:'Positions',v:state.positions.length.toString(),c:'#6366f1'},{l:'Strategies',v:state.strategies.filter(s=>s.active).length+'/'+state.strategies.length,c:'#888'},{l:'Best Sharpe',v:Math.max(...state.strategies.map(s=>s.sharpe)).toFixed(2),c:'#f59e0b'}].map(s => `
                        <div style="background:var(--bg-card);border-radius:10px;padding:14px;border:1px solid var(--border-subtle)">
                            <div style="color:var(--text-muted);font-size:11px">${s.l}</div>
                            <div style="color:${s.c};font-size:22px;font-weight:700;margin-top:4px">${s.v}</div>
                        </div>
                    `).join('')}
                </div>
                <div style="display:grid;grid-template-columns:2fr 1fr;gap:16px">
                    <div style="background:var(--bg-card);border-radius:12px;padding:16px;border:1px solid var(--border-subtle)">
                        <h3 style="color:var(--text-primary);margin:0 0 12px;font-size:14px">📊 Active Positions</h3>
                        ${state.positions.length === 0 ? '<div style="color:var(--text-muted);padding:20px;text-align:center">No active positions</div>' :
                        state.positions.map(p => `
                            <div style="display:flex;align-items:center;gap:12px;padding:10px;background:var(--bg-input);border-radius:8px;margin-bottom:6px;border-left:3px solid ${p.pnl.startsWith('+') ? '#44dd88' : '#ef4444'}">
                                <strong style="color:var(--text-primary);width:40px">${p.asset}</strong>
                                <span style="font-size:11px;background:rgba(16,185,129,0.15);color:#10b981;padding:2px 8px;border-radius:4px">${p.dir}</span>
                                <span style="flex:1;font-size:12px;color:var(--text-muted)">$${p.entry} → $${p.current}</span>
                                <span style="color:${p.pnl.startsWith('+') ? '#44dd88' : '#ef4444'};font-weight:600">${p.pnl}</span>
                            </div>
                        `).join('')}
                    </div>
                    <div style="background:var(--bg-card);border-radius:12px;padding:16px;border:1px solid var(--border-subtle)">
                        <h3 style="color:var(--text-primary);margin:0 0 12px;font-size:14px">🧬 Strategies</h3>
                        ${state.strategies.map(s => `<div style="padding:10px;background:var(--bg-input);border-radius:8px;margin-bottom:6px">
                            <div style="display:flex;justify-content:space-between;align-items:center">
                                <strong style="color:var(--text-primary);font-size:13px">${s.name}</strong>
                                <span style="font-size:10px;background:${s.active?'rgba(68,221,136,0.15)':'rgba(255,255,255,0.1)'};color:${s.active?'#44dd88':'#888'};padding:2px 8px;border-radius:10px">${s.active?'ACTIVE':'PAUSED'}</span>
                            </div>
                            <div style="font-size:11px;color:var(--text-muted);margin-top:4px">${s.type} · ${s.assets} · Sharpe ${s.sharpe} · Win ${s.winRate}%</div>
                        </div>`).join('')}
                        <button onclick="scanMemeCoins()" style="width:100%;margin-top:8px;background:var(--bg-input);color:var(--text-primary);border:1px dashed var(--border-mid);padding:8px;border-radius:8px;cursor:pointer;font-size:12px">🔍 Scan Meme Coins</button>
                    </div>
                </div>
            </div>
        `;
    }

    window.toggleTrading = function() {
        state.agentActive = !state.agentActive;
        addLog(state.agentActive ? '▶ Trading agent started' : '⏹ Trading agent stopped');
        renderDashboard();
    };

    window.scanMemeCoins = function() {
        addLog('🔍 Scanning meme coins...');
        setTimeout(() => addLog('✅ Found 4 potential meme coins'), 800);
    };

    function addLog(msg) {
        state.logs.push(msg);
        const chat = document.getElementById('chatMessages');
        if (chat) {
            const div = document.createElement('div');
            div.className = 'message assistant-msg';
            div.innerHTML = `<div class="msg-content"><div class="msg-text" style="font-size:13px">${msg}</div></div>`;
            chat.appendChild(div);
            chat.scrollTop = chat.scrollHeight;
        }
    }

    function getMainPanel() {
        return document.querySelector('.tab-panel.active') || document.querySelector('.app-content');
    }
    console.log('[Trading Plugin] Loaded');
})();
