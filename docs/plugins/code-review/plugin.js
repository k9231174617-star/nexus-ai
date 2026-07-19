/**
 * Code Review Plugin for Nexus AI
 * Adds automated code review capabilities to the dashboard.
 */
(function() {
    const pm = window.NexusPlugins;

    // Register sidebar item
    pm.on('sidebar:items', () => [{
        id: 'code-review',
        label: 'Code Review',
        icon: '🔍',
        section: 'tools',
        priority: 5,
        onClick: () => showReviewPanel(),
    }]);

    // Register chat commands
    pm.on('chat:commands', () => [{
        command: '/review',
        description: 'Review code in the current editor',
        handler: (args) => reviewCode(args.join(' ')),
    }, {
        command: '/audit',
        description: 'Run full security audit on project',
        handler: () => runAudit(),
    }]);

    function showReviewPanel() {
        const main = document.querySelector('.tab-content.active');
        if (!main) return;
        main.innerHTML = `
            <div class="review-panel" style="padding:20px">
                <h2 style="color:var(--red-core)">🔍 Code Review</h2>
                <div class="review-controls" style="display:flex;gap:10px;margin:16px 0">
                    <select id="reviewScope" style="flex:1;padding:8px;background:var(--bg-card);color:var(--text-primary);border:1px solid var(--border-mid);border-radius:8px">
                        <option value="file">Current File</option>
                        <option value="project">Full Project</option>
                        <option value="selection">Selected Code</option>
                    </select>
                    <button onclick="runReview()" class="btn-primary" style="padding:8px 20px;background:var(--red-core);color:white;border:none;border-radius:8px;cursor:pointer">
                        Review
                    </button>
                </div>
                <div id="reviewResults" style="background:var(--bg-card);border-radius:10px;padding:16px;min-height:200px;border:1px solid var(--border-subtle)">
                    <div style="color:var(--text-muted);text-align:center;padding:40px">
                        Select scope and click Review to start
                    </div>
                </div>
            </div>
        `;
    }

    window.runReview = async function() {
        const scope = document.getElementById('reviewScope').value;
        const resultsDiv = document.getElementById('reviewResults');
        if (!resultsDiv) return;

        resultsDiv.innerHTML = '<div style="text-align:center;padding:40px;color:var(--text-secondary)">Analyzing code...</div>';

        // Simulate AI review
        setTimeout(() => {
            resultsDiv.innerHTML = `
                <div style="display:flex;gap:12px;margin-bottom:16px">
                    <div style="flex:1;background:var(--bg-input);border-radius:8px;padding:12px;text-align:center">
                        <div style="font-size:24px;color:var(--color-success)">3</div>
                        <div style="font-size:12px;color:var(--text-muted)">Issues Found</div>
                    </div>
                    <div style="flex:1;background:var(--bg-input);border-radius:8px;padding:12px;text-align:center">
                        <div style="font-size:24px;color:var(--red-core)">1</div>
                        <div style="font-size:12px;color:var(--text-muted)">Critical</div>
                    </div>
                    <div style="flex:1;background:var(--bg-input);border-radius:8px;padding:12px;text-align:center">
                        <div style="font-size:24px;color:var(--color-warning)">2</div>
                        <div style="font-size:12px;color:var(--text-muted)">Suggestions</div>
                    </div>
                </div>
                <div style="margin-top:12px">
                    <div style="background:rgba(255,10,47,0.1);border-left:3px solid var(--red-core);padding:10px;margin-bottom:8px;border-radius:0 6px 6px 0">
                        <strong style="color:var(--red-core)">SECURITY</strong>
                        <p style="margin:4px 0 0;color:var(--text-secondary);font-size:13px">Hardcoded API key detected in config file</p>
                    </div>
                    <div style="background:rgba(255,170,0,0.1);border-left:3px solid var(--color-warning);padding:10px;margin-bottom:8px;border-radius:0 6px 6px 0">
                        <strong style="color:var(--color-warning)">PERFORMANCE</strong>
                        <p style="margin:4px 0 0;color:var(--text-secondary);font-size:13px">Inefficient loop in data processing</p>
                    </div>
                    <div style="background:rgba(68,221,136,0.1);border-left:3px solid var(--color-success);padding:10px;border-radius:0 6px 6px 0">
                        <strong style="color:var(--color-success)">STYLE</strong>
                        <p style="margin:4px 0 0;color:var(--text-secondary);font-size:13px">Unused import detected</p>
                    </div>
                </div>
            `;
        }, 1500);
    }

    function reviewCode(code) {
        console.log('[CodeReview] Reviewing code:', code.substring(0, 100));
    }

    function runAudit() {
        console.log('[CodeReview] Running full security audit');
    }

    console.log('[CodeReview] Plugin loaded');
})();
