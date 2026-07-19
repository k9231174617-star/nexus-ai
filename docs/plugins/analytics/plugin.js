/**
 * Analytics Plugin — tracks usage patterns and provides insights
 */
(function() {
    const pm = window.NexusPlugins;

    pm.on('sidebar:items', () => [{
        id: 'nexus-analytics',
        label: 'Analytics',
        icon: '📊',
        section: 'tools',
        priority: 3,
        onClick: () => showAnalytics(),
    }]);

    function showAnalytics() {
        const main = document.querySelector('.tab-panel.active');
        if (!main) return;
        main.innerHTML = `
            <div style="padding:20px">
                <h2 style="color:var(--red-core)">📊 Analytics</h2>
                <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px;margin:16px 0">
                    <div style="background:var(--bg-card);border-radius:10px;padding:16px;border:1px solid var(--border-subtle)">
                        <div style="color:var(--text-muted);font-size:12px">Chat Messages</div>
                        <div style="font-size:28px;color:var(--red-core);font-weight:bold" id="analyticsMessages">0</div>
                    </div>
                    <div style="background:var(--bg-card);border-radius:10px;padding:16px;border:1px solid var(--border-subtle)">
                        <div style="color:var(--text-muted);font-size:12px">Tokens Used</div>
                        <div style="font-size:28px;color:var(--color-info);font-weight:bold" id="analyticsTokens">0</div>
                    </div>
                    <div style="background:var(--bg-card);border-radius:10px;padding:16px;border:1px solid var(--border-subtle)">
                        <div style="color:var(--text-muted);font-size:12px">Session Time</div>
                        <div style="font-size:28px;color:var(--color-success);font-weight:bold">0h 0m</div>
                    </div>
                    <div style="background:var(--bg-card);border-radius:10px;padding:16px;border:1px solid var(--border-subtle)">
                        <div style="color:var(--text-muted);font-size:12px">API Calls</div>
                        <div style="font-size:28px;color:var(--color-warning);font-weight:bold">0</div>
                    </div>
                </div>
                <div style="background:var(--bg-card);border-radius:10px;padding:16px;border:1px solid var(--border-subtle)">
                    <h3 style="color:var(--text-primary);margin-bottom:12px">Model Usage</h3>
                    <div style="height:200px;display:flex;align-items:flex-end;gap:8px;padding-top:20px">
                        ${['GPT-4o','Claude 3.5','Gemini','Local'].map((m,i) => `
                            <div style="flex:1;display:flex;flex-direction:column;align-items:center">
                                <div style="width:100%;background:var(--red-core);border-radius:4px 4px 0 0;height:${40 + i * 30}px;opacity:${1 - i * 0.2};transition:height 0.3s"></div>
                                <div style="font-size:11px;color:var(--text-muted);margin-top:6px">${m}</div>
                            </div>
                        `).join('')}
                    </div>
                </div>
            </div>
        `;
    }

    console.log('[Analytics] Plugin loaded');
})();
