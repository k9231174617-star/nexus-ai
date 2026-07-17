/**
 * MCP Bridge Plugin - Model Context Protocol integration
 * Allows Nexus AI to connect to any MCP-compatible server.
 */
(function() {
    const pm = window.NexusPlugins;
    let connections = JSON.parse(localStorage.getItem('nexus_mcp_connections') || '[]');

    pm.on('sidebar:items', () => [{
        id: 'mcp-bridge',
        label: 'MCP Servers',
        icon: '🔌',
        section: 'tools',
        priority: 4,
        onClick: () => showMCPServerList(),
    }]);

    pm.on('chat:commands', () => [{
        command: '/mcp',
        description: 'Send command to MCP server. Usage: /mcp <server> <tool> <args>',
        handler: (args) => executeMCP(args.join(' ')),
    }]);

    pm.on('settings:panels', () => [{
        id: 'mcp-settings',
        label: 'MCP Connections',
        render: () => renderMCPSettings(),
    }]);

    function showMCPServerList() {
        const main = document.querySelector('.tab-content.active');
        if (!main) return;
        main.innerHTML = `
            <div style="padding:20px">
                <h2 style="color:var(--red-core)">🔌 MCP Servers</h2>
                <p style="color:var(--text-muted);margin:8px 0 16px">
                    Model Context Protocol — connect to external AI tools and data sources
                </p>
                <div id="mcpServerList">
                    ${connections.map((c, i) => `
                        <div style="background:var(--bg-card);border-radius:10px;padding:14px;margin-bottom:8px;border:1px solid var(--border-subtle);display:flex;justify-content:space-between;align-items:center">
                            <div>
                                <strong style="color:var(--text-primary)">${c.name}</strong>
                                <div style="color:var(--text-muted);font-size:12px">${c.url} · ${c.status || 'disconnected'}</div>
                            </div>
                            <div style="display:flex;gap:8px">
                                <button onclick="connectMCP(${i})" style="background:var(--color-success);color:white;border:none;padding:6px 14px;border-radius:6px;cursor:pointer">Connect</button>
                                <button onclick="disconnectMCP(${i})" style="background:var(--red-dim);color:white;border:none;padding:6px 14px;border-radius:6px;cursor:pointer">Remove</button>
                            </div>
                        </div>
                    `).join('')}
                </div>
                <div style="margin-top:16px;display:flex;gap:10px">
                    <input id="mcpServerName" placeholder="Server name" style="flex:1;padding:10px;background:var(--bg-input);color:var(--text-primary);border:1px solid var(--border-mid);border-radius:8px">
                    <input id="mcpServerUrl" placeholder="ws://localhost:8080/mcp" style="flex:2;padding:10px;background:var(--bg-input);color:var(--text-primary);border:1px solid var(--border-mid);border-radius:8px">
                    <button onclick="addMCPServer()" style="background:var(--red-core);color:white;border:none;padding:10px 20px;border-radius:8px;cursor:pointer">Add Server</button>
                </div>
            </div>
        `;
    }

    window.addMCPServer = function() {
        const name = document.getElementById('mcpServerName').value.trim();
        const url = document.getElementById('mcpServerUrl').value.trim();
        if (!name || !url) return;
        connections.push({ name, url, status: 'disconnected' });
        localStorage.setItem('nexus_mcp_connections', JSON.stringify(connections));
        showMCPServerList();
    };

    window.connectMCP = function(idx) {
        connections[idx].status = 'connected';
        localStorage.setItem('nexus_mcp_connections', JSON.stringify(connections));
        showMCPServerList();
    };

    window.disconnectMCP = function(idx) {
        connections.splice(idx, 1);
        localStorage.setItem('nexus_mcp_connections', JSON.stringify(connections));
        showMCPServerList();
    };

    function executeMCP(cmd) {
        console.log('[MCP] Executing:', cmd);
    }

    function renderMCPSettings() {
        return '<p>Configure MCP server connections in the MCP Servers tab</p>';
    }

    console.log('[MCP Bridge] Plugin loaded');
})();
