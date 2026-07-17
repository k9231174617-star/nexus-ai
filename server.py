"""
Nexus AI — Combined Web Dashboard + Telegram Bot Server
Serves the dashboard UI and runs the Telegram bot concurrently.
"""
import os
import sys
import asyncio
import multiprocessing
import logging
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

PORT = int(os.getenv("PORT", "8080"))
DASHBOARD_DIR = os.path.join(os.path.dirname(__file__), "docs")


import json
from http.server import HTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlparse

class DashboardHandler(SimpleHTTPRequestHandler):
    """Serve dashboard static files + API config endpoint."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DASHBOARD_DIR, **kwargs)

    def do_GET(self):
        parsed = urlparse(self.path)
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

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
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
    """Start the Telegram bot in a separate process."""
    bot_path = os.path.join(os.path.dirname(__file__), "bot", "bot.py")
    if not os.path.exists(bot_path):
        print(f"Bot file not found: {bot_path}")
        return
    
    # Change to bot directory for imports
    bot_dir = os.path.dirname(bot_path)
    sys.path.insert(0, bot_dir)
    os.chdir(bot_dir)
    
    # Import and run bot
    import bot as bot_module
    try:
        bot_module.main()
    except Exception as e:
        print(f"Bot error: {e}")


if __name__ == "__main__":
    logger.info("Starting Nexus AI Server...")
    
    # Start dashboard in main thread
    logger.info(f"Dashboard will be at http://0.0.0.0:{PORT}")
    
    # Check if docs directory exists
    if not os.path.isdir(DASHBOARD_DIR):
        logger.warning(f"Dashboard directory not found: {DASHBOARD_DIR}")
        os.makedirs(DASHBOARD_DIR, exist_ok=True)
        # Create a minimal index.html if missing
        index_path = os.path.join(DASHBOARD_DIR, "index.html")
        if not os.path.exists(index_path):
            with open(index_path, "w") as f:
                f.write("<html><body><h1>Nexus AI</h1><p>Dashboard loading...</p></body></html>")
    
    # Start bot in background process
    bot_process = multiprocessing.Process(target=run_bot)
    bot_process.start()
    
    # Run dashboard in main thread
    try:
        run_dashboard()
    finally:
        bot_process.terminate()
