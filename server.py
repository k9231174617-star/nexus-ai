"""
Nexus AI — Combined Web Dashboard + Telegram Bot Server
Serves the dashboard UI and runs the Telegram bot concurrently.
"""
import os
import sys
import json
import logging
import threading
from http.server import HTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlparse
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

PORT = int(os.getenv("PORT", "8080"))
DASHBOARD_DIR = os.path.join(os.path.dirname(__file__), "docs")


class DashboardHandler(SimpleHTTPRequestHandler):
    """Serve dashboard static files + API config endpoint."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DASHBOARD_DIR, **kwargs)

    def do_GET(self):
        parsed = urlparse(self.path)
        
        # Health check endpoint
        if parsed.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({
                'status': 'ok',
                'version': '2.0',
                'uptime': os.path.getmtime(__file__) if os.path.exists(__file__) else 0
            }).encode())
            return
            
        if parsed.path == '/api/config':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
            self.end_headers()
            config = {
                'apiKey': os.getenv('OPENROUTER_API_KEY', ''),
                'endpoint': 'https://openrouter.ai/api/v1/chat/completions',
                'mainModel': os.getenv('LLM_MODEL', 'openai/gpt-4o-mini'),
                'codeModel': 'deepseek/deepseek-coder',
                'uniModel': os.getenv('LLM_MODEL', 'openai/gpt-4o-mini'),
                'dashboardUrl': os.getenv('WEB_DASHBOARD_URL', ''),
            }
            self.wfile.write(json.dumps(config).encode())
            return
            
        super().do_GET()

    def do_POST(self):
        """Handle POST requests (chat API proxy)."""
        parsed = urlparse(self.path)
        
        if parsed.path == '/api/chat':
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length) if content_length > 0 else b'{}'
            
            try:
                data = json.loads(body)
                api_key = os.getenv('OPENROUTER_API_KEY', '')
                model = data.get('model', 'openai/gpt-4o-mini')
                messages = data.get('messages', [])
                
                import urllib.request
                
                headers = {
                    'Content-Type': 'application/json',
                    'Authorization': f'Bearer {api_key}',
                    'HTTP-Referer': self.headers.get('Origin', 'https://nexus-ai.app'),
                    'X-Title': 'Nexus AI Dashboard',
                }
                
                req_body = json.dumps({
                    'model': model,
                    'messages': messages,
                    'temperature': 0.7,
                    'max_tokens': 1500,
                }).encode()
                
                req = urllib.request.Request(
                    'https://openrouter.ai/api/v1/chat/completions',
                    data=req_body,
                    headers=headers,
                    method='POST'
                )
                
                with urllib.request.urlopen(req, timeout=60) as resp:
                    result = json.loads(resp.read())
                
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps(result).encode())
                
            except Exception as e:
                logger.error(f"[API] Chat proxy error: {e}")
                self.send_response(500)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
            return
            
        self.send_response(404)
        self.end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.end_headers()

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def log_message(self, format, *args):
        logger.info(f"[DASHBOARD] {args[0]} {args[1]} {args[2]}")


def run_dashboard():
    """Start the dashboard HTTP server."""
    os.makedirs(DASHBOARD_DIR, exist_ok=True)
    server = HTTPServer(("0.0.0.0", PORT), DashboardHandler)
    logger.info(f"Dashboard running on http://0.0.0.0:{PORT}")
    logger.info(f"Dashboard directory: {DASHBOARD_DIR}")
    server.serve_forever()


def run_bot():
    """Start the Telegram bot in a background thread with its own asyncio event loop."""
    bot_path = os.path.join(os.path.dirname(__file__), "bot", "bot.py")
    if not os.path.exists(bot_path):
        logger.warning(f"Bot file not found: {bot_path}")
        return

    bot_dir = os.path.dirname(bot_path)
    if bot_dir not in sys.path:
        sys.path.insert(0, bot_dir)

    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location("nexus_bot", bot_path)
        bot_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(bot_module)

        # Each thread needs its own event loop (multiprocessing caused asyncio conflicts)
        import asyncio
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        bot_module.main()
    except Exception as e:
        logger.error(f"Bot thread error: {e}", exc_info=True)


if __name__ == "__main__":
    logger.info("Starting Nexus AI Server...")
    
    # Check if docs directory exists
    if not os.path.isdir(DASHBOARD_DIR):
        logger.warning(f"Dashboard directory not found: {DASHBOARD_DIR}")
        os.makedirs(DASHBOARD_DIR, exist_ok=True)
    
    # Start bot in background thread (threading avoids asyncio event loop conflicts)
    bot_thread = threading.Thread(target=run_bot, name="NexusTelegramBot", daemon=True)
    bot_thread.start()
    logger.info("Telegram bot thread started")

    # Run dashboard HTTP server in main thread
    try:
        run_dashboard()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
