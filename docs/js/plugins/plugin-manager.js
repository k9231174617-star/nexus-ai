/**
 * Nexus AI Plugin System
 * Plugin API for extending the dashboard with new tabs, commands, and tools.
 * Inspired by Ruflo's plugin architecture.
 */

class PluginManager {
    constructor() {
        this.plugins = new Map();
        this.hooks = {
            'sidebar:items': [],
            'topbar:actions': [],
            'chat:commands': [],
            'app:init': [],
            'app:render': [],
            'settings:panels': [],
        };
        this._loaded = false;
    }

    /** Register a plugin */
    async register(manifest) {
        const { id, name, version, description, main } = manifest;
        
        if (this.plugins.has(id)) {
            console.warn(`[PluginManager] Plugin "${id}" already registered`);
            return false;
        }

        const plugin = {
            id,
            name,
            version: version || '1.0.0',
            description: description || '',
            api: null,
            hooks: {},
            enabled: true,
        };

        this.plugins.set(id, plugin);
        console.log(`[PluginManager] Registered: ${name} v${plugin.version}`);
        return true;
    }

    /** Load a plugin from URL */
    async loadFromUrl(url) {
        try {
            const resp = await fetch(url);
            const manifest = await resp.json();
            await this.register(manifest);
            
            if (manifest.main) {
                const baseUrl = url.substring(0, url.lastIndexOf('/') + 1);
                const script = document.createElement('script');
                script.src = baseUrl + manifest.main;
                script.onload = () => this._initPlugin(manifest.id);
                document.body.appendChild(script);
            }
            
            return true;
        } catch (err) {
            console.error(`[PluginManager] Failed to load plugin from ${url}:`, err);
            return false;
        }
    }

    /** Scan and load all plugins from /plugins/ directory */
    async loadAll() {
        try {
            const resp = await fetch('plugins/manifest.json');
            const pluginList = await resp.json();
            
            for (const pluginUrl of pluginList.plugins) {
                await this.loadFromUrl(pluginUrl);
            }
            
            this._loaded = true;
            this._runHook('app:init');
            this._runHook('app:render');
            console.log(`[PluginManager] Loaded ${this.plugins.size} plugins`);
        } catch (err) {
            console.warn('[PluginManager] No plugin manifest found, skipping');
        }
    }

    /** Add a hook handler */
    on(hook, handler) {
        if (this.hooks[hook]) {
            this.hooks[hook].push(handler);
        } else {
            this.hooks[hook] = [handler];
        }
    }

    /** Run all handlers for a hook */
    _runHook(hook, ...args) {
        const handlers = this.hooks[hook] || [];
        return handlers.flatMap(fn => {
            try { return fn(...args) || []; }
            catch (e) { console.error(`[PluginManager] Hook ${hook} error:`, e); return []; }
        });
    }

    /** Get plugin by ID */
    get(id) { return this.plugins.get(id); }

    /** List all plugins */
    list() { return Array.from(this.plugins.values()); }

    /** Enable/disable plugin */
    setEnabled(id, enabled) {
        const plugin = this.plugins.get(id);
        if (plugin) plugin.enabled = enabled;
    }

    /** Get sidebar items from plugins */
    getSidebarItems() {
        return this._runHook('sidebar:items');
    }

    /** Get topbar actions from plugins */
    getTopbarActions() {
        return this._runHook('topbar:actions');
    }

    /** Get chat commands from plugins */
    getChatCommands() {
        return this._runHook('chat:commands');
    }
}

// Global singleton
window.NexusPlugins = window.NexusPlugins || new PluginManager();
