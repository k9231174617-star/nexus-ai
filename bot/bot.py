import os
import logging
import json
import re
import aiohttp
import asyncio
from datetime import datetime, timedelta
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup, BotCommand, WebAppInfo
from telegram.ext import Application, CommandHandler, MessageHandler, CallbackQueryHandler, filters, ContextTypes
from dotenv import load_dotenv

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)
load_dotenv()

BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
ALLOWED_USERS = [int(x) for x in os.getenv("ALLOWED_USERS", "").split(",") if x.strip()]
WEB_DASHBOARD_URL = os.getenv("WEB_DASHBOARD_URL", "https://k9231174617-star.github.io/nexus-ai/")

user_sessions = {}

# LLM Configuration via OpenRouter (free tier available)
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY", "")
LLM_MODEL = os.getenv("LLM_MODEL", "openai/gpt-4o-mini")
DASHBOARD_GITHUB = os.getenv("DASHBOARD_GITHUB", "https://k9231174617-star.github.io/nexus-ai/")

# Free tier models (OpenRouter — no API key needed for some), paid models also listed
LLM_MODELS = {
    "openai/gpt-4o-mini": "GPT-4o Mini",
    "openai/gpt-4o": "GPT-4o",
    "anthropic/claude-3.5-sonnet": "Claude 3.5 Sonnet",
    "anthropic/claude-3-haiku": "Claude 3 Haiku",
    "google/gemini-2.0-flash-001": "Gemini 2.0 Flash",
    "meta-llama/llama-3.2-3b-instruct": "Llama 3.2 3B",
    "mistralai/mistral-7b-instruct": "Mistral 7B Instruct",
    "qwen/qwen-2.5-7b-instruct": "Qwen 2.5 7B",
    "deepseek/deepseek-chat": "DeepSeek Chat",
}

if not OPENROUTER_API_KEY:
    logger.warning("OPENROUTER_API_KEY not set! Using fallback responses.")

HELP_TEXT = f"""🤖 **Nexus AI — Telegram Bot**

Подключён к OpenRouter LLM: *{LLM_MODELS.get(LLM_MODEL, LLM_MODEL)}*

Команды:
/start — Главное меню
/help — Помощь
/ask <вопрос> — Задать вопрос AI
/translate <текст> — Перевести текст
/code <язык> <описание> — Генерация кода
/summary <текст> — Краткое содержание
/stats — Статистика использования
/settings — Настройки
/model — Выбрать модель LLM
/dashboard — Открыть веб-дашборд

🔗 Дашборд: {WEB_DASHBOARD_URL}
📱 Mini App: {WEB_DASHBOARD_URL}
"""

MAIN_KEYBOARD = InlineKeyboardMarkup([
    [InlineKeyboardButton("🤖 Задать вопрос", callback_data="ask"),
     InlineKeyboardButton("💻 Код", callback_data="code")],
    [InlineKeyboardButton("🌐 Перевод", callback_data="translate"),
     InlineKeyboardButton("📝 Резюме", callback_data="summary"),
     InlineKeyboardButton("📊 Статистика", callback_data="stats")],
    [InlineKeyboardButton("⚙️ Настройки", callback_data="settings"),
     InlineKeyboardButton("🤖 Модель", callback_data="model")],
    [InlineKeyboardButton("🌍 Веб-дашборд", url=WEB_DASHBOARD_URL),
     InlineKeyboardButton("📱 Mini App", web_app=WebAppInfo(url=WEB_DASHBOARD_URL))],
])


def is_allowed(user_id: int) -> bool:
    if not ALLOWED_USERS:
        return True
    return user_id in ALLOWED_USERS


def update_stats(user_id: int, action: str):
    if user_id not in user_sessions:
        user_sessions[user_id] = {"messages": 0, "actions": {}, "model": LLM_MODEL}
    user_sessions[user_id]["messages"] += 1
    user_sessions[user_id]["actions"][action] = user_sessions[user_id]["actions"].get(action, 0) + 1


async def query_llm(prompt: str, system_prompt: str = "Ты полезный AI ассистент Nexus AI.") -> str:
    """Query OpenRouter API for real LLM response."""
    if not OPENROUTER_API_KEY:
        return generate_fallback_response(prompt)
    
    try:
        async with aiohttp.ClientSession() as session:
            async with session.post(
                "https://openrouter.ai/api/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {OPENROUTER_API_KEY}",
                    "Content-Type": "application/json",
                    "HTTP-Referer": "https://github.com/k9231174617-star/nexus-ai",
                    "X-Title": "Nexus AI Bot",
                },
                json={
                    "model": LLM_MODEL,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": prompt}
                    ],
                    "temperature": 0.7,
                    "max_tokens": 1500,
                },
                timeout=aiohttp.ClientTimeout(total=60)
            ) as resp:
                if resp.status != 200:
                    err = await resp.text()
                    logger.error(f"OpenRouter API error {resp.status}: {err}")
                    return generate_fallback_response(prompt)
                data = await resp.json()
                return data["choices"][0]["message"]["content"]
    except Exception as e:
        logger.error(f"LLM query error: {e}")
        return generate_fallback_response(prompt)


def generate_fallback_response(prompt: str) -> str:
    """Generate a helpful response when no API key is configured."""
    return (
        "🤖 **Nexus AI (Demo Mode)**\n\n"
        "I'm running without an OpenRouter API key. To enable real AI responses:\n\n"
        "1. Get a free API key at https://openrouter.ai\n"
        "2. Add it to Railway environment variables: `OPENROUTER_API_KEY`\n"
        "3. Restart the service\n\n"
        f"*Your message: {prompt[:100]}{'...' if len(prompt) > 100 else ''}*"
    )


# ── Command Handlers ────────────────────────────────────────

async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    update_stats(update.effective_user.id, "start")
    await update.message.reply_text(HELP_TEXT, parse_mode='Markdown', reply_markup=MAIN_KEYBOARD)


async def cmd_help(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    update_stats(update.effective_user.id, "help")
    await update.message.reply_text(HELP_TEXT, parse_mode='Markdown')


async def cmd_ask(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    
    user_id = update.effective_user.id
    update_stats(user_id, "ask")
    
    if context.args:
        prompt = ' '.join(context.args)
    else:
        user_sessions[user_id]['waiting_for'] = 'ask'
        await update.message.reply_text("🤔 Напишите ваш вопрос...")
        return

    thinking_msg = await update.message.reply_text("🤔 Думаю...")
    system_prompt = "You are NEXUS AI — a powerful AI assistant. Answer concisely and helpfully. Use the language the user writes in."
    reply = await query_llm(prompt, system_prompt=system_prompt)
    
    try:
        await thinking_msg.delete()
    except Exception:
        pass
    
    await update.message.reply_text(
        reply,
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🔄 Ещё вопрос", callback_data="ask"),
            InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL),
        ]])
    )


async def cmd_translate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    
    user_id = update.effective_user.id
    update_stats(user_id, "translate")
    
    if context.args:
        prompt = ' '.join(context.args)
    else:
        user_sessions[user_id]['waiting_for'] = 'translate'
        await update.message.reply_text("🌐 Напишите текст для перевода...")
        return

    thinking_msg = await update.message.reply_text("🤔 Перевожу...")
    system_prompt = "You are a professional translator. Translate the user's text to English. If it's already in English, translate to Russian."
    reply = await query_llm(prompt, system_prompt=system_prompt)
    
    try:
        await thinking_msg.delete()
    except Exception:
        pass
    
    await update.message.reply_text(
        reply,
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🔄 Ещё", callback_data="translate"),
            InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL),
        ]])
    )


async def cmd_code(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    
    user_id = update.effective_user.id
    update_stats(user_id, "code")
    
    if context.args:
        lang = context.args[0]
        desc = ' '.join(context.args[1:])
        if not desc:
            user_sessions[user_id]['waiting_for'] = 'code'
            await update.message.reply_text("💻 Опишите что нужно запрограммировать...")
            return
    else:
        user_sessions[user_id]['waiting_for'] = 'code'
        await update.message.reply_text("💻 Напишите: `/code <язык> <описание>` или просто описание задачи")
        return

    thinking_msg = await update.message.reply_text("🤔 Генерирую код...")
    system_prompt = "You are NEXUS Code Agent — specialized in Android development, Kotlin, Java, and APK analysis. Provide complete working implementations. Format code in markdown code blocks with language tags."
    reply = await query_llm(f"Write {lang} code for: {desc}", system_prompt=system_prompt)
    
    try:
        await thinking_msg.delete()
    except Exception:
        pass
    
    await update.message.reply_text(
        reply,
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🔄 Ещё", callback_data="code"),
            InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL),
        ]])
    )


async def cmd_summary(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    
    user_id = update.effective_user.id
    update_stats(user_id, "summary")
    
    if context.args:
        prompt = ' '.join(context.args)
    else:
        user_sessions[user_id]['waiting_for'] = 'summary'
        await update.message.reply_text("📝 Напишите текст для краткого содержания...")
        return

    thinking_msg = await update.message.reply_text("🤔 Суммирую...")
    system_prompt = "You are an expert summarizer. Provide concise summaries."
    reply = await query_llm(f"Summarize this text briefly in the same language:\n\n{prompt}", system_prompt=system_prompt)
    
    try:
        await thinking_msg.delete()
    except Exception:
        pass
    
    await update.message.reply_text(
        reply,
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🔄 Ещё", callback_data="summary"),
            InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL),
        ]])
    )


async def cmd_stats(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    update_stats(update.effective_user.id, "stats")
    session = user_sessions.get(update.effective_user.id, {})
    actions = session.get('actions', {})
    msg = f"📊 **Статистика**\n\nСообщений: {session.get('messages', 0)}\nМодель: {LLM_MODELS.get(session.get('model', LLM_MODEL), session.get('model', LLM_MODEL))}\n\nДействия:\n"
    for action, count in actions.items():
        msg += f"  {action}: {count}\n"
    await update.message.reply_text(msg, parse_mode='Markdown')


async def cmd_settings(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    update_stats(update.effective_user.id, "settings")
    await update.message.reply_text(
        f"⚙️ **Настройки**\n\n"
        f"Текущая модель: {LLM_MODELS.get(LLM_MODEL, LLM_MODEL)}\n"
        f"API ключ: {'✅ Настроен' if OPENROUTER_API_KEY else '❌ Не настроен'}\n\n"
        f"Используйте /model для смены модели.",
        parse_mode='Markdown'
    )


async def cmd_model(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    update_stats(update.effective_user.id, "model")
    
    keyboard = []
    for model_id, name in LLM_MODELS.items():
        tier = "🆓" if model_id in ["openai/gpt-4o-mini", "deepseek/deepseek-chat", "deepseek/deepseek-coder", 
                                   "meta-llama/llama-3.2-3b-instruct", "mistralai/mistral-7b-instruct", 
                                   "qwen/qwen-2.5-7b-instruct"] else "💎"
        keyboard.append([InlineKeyboardButton(f"{tier} {name}", callback_data=f"model_{model_id}")])
    keyboard.append([InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL)])
    
    await update.message.reply_text(
        f"🤖 **Выбор модели LLM**\n\nТекущая: {LLM_MODELS.get(LLM_MODEL, LLM_MODEL)}\n\n"
        f"🆓 — бесплатные через OpenRouter\n💎 — платные (требуют баланс на OpenRouter)",
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup(keyboard)
    )


async def cmd_dashboard(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    update_stats(update.effective_user.id, "dashboard")
    await update.message.reply_text(
        f"🌍 **Веб-дашборд**\n\n"
        f"Откройте: {WEB_DASHBOARD_URL}\n\n"
        f"📱 Mini App доступен прямо в Telegram!",
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🌍 Открыть дашборд", url=WEB_DASHBOARD_URL),
            InlineKeyboardButton("📱 Mini App", web_app=WebAppInfo(url=WEB_DASHBOARD_URL)),
        ]])
    )


# ── Callback Handler ────────────────────────────────────────

async def handle_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    
    if not is_allowed(query.from_user.id):
        await query.edit_message_text("⛔ Доступ запрещён.")
        return
    
    user_id = query.from_user.id
    data = query.data
    
    if data.startswith("model_"):
        model_id = data[6:]
        if model_id in LLM_MODELS:
            user_sessions[user_id]['model'] = model_id
            # Note: This only changes for this user's session
            # To change globally, need env var or persistent config
            await query.edit_message_text(
                f"✅ Модель изменена на: {LLM_MODELS.get(model_id, model_id)}\n\n"
                f"*(Применено для этой сессии. Для глобальной смены измените переменную окружения LLM_MODEL)*",
                parse_mode='Markdown'
            )
        return
    
    # Handle action callbacks
    if data in ['ask', 'translate', 'code', 'summary']:
        user_sessions[user_id]['waiting_for'] = data
        labels = {'ask': 'вопрос', 'translate': 'текст для перевода', 'code': 'описание кода', 'summary': 'текст для резюме'}
        await query.edit_message_text(f"✍️ Напишите {labels.get(data, 'сообщение')}...")
        return
    
    if data == 'stats':
        await cmd_stats(query, context)
        return
    if data == 'settings':
        await cmd_settings(query, context)
        return
    if data == 'model':
        await cmd_model(query, context)
        return


# ── Message Handler ────────────────────────────────────────

async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return

    user_id = update.effective_user.id
    text = update.message.text.strip()
    update_stats(user_id, "message")

    # If user is waiting for input for a command
    session = user_sessions.get(user_id, {})
    waiting_for = session.get('waiting_for')
    
    if waiting_for:
        user_sessions[user_id]['waiting_for'] = None
        
        thinking_msg = await update.message.reply_text("🤔 Думаю...")
        
        system_prompt = "You are NEXUS AI — a powerful AI assistant. Answer concisely and helpfully. Use the language the user writes in."
        
        if waiting_for == 'ask':
            reply = await query_llm(text, system_prompt=system_prompt)
        elif waiting_for == 'translate':
            reply = await query_llm(f"Translate to English: {text}", system_prompt="You are a professional translator. Translate the user's text to English. If it's already in English, translate to Russian.")
        elif waiting_for == 'code':
            reply = await query_llm(f"Write Python code for: {text}", system_prompt="You are NEXUS Code Agent — specialized in Android development, Kotlin, Java, and APK analysis. Provide complete working implementations.")
        elif waiting_for == 'summary':
            reply = await query_llm(f"Summarize this text briefly in the same language:\n\n{text}", system_prompt="You are an expert summarizer. Provide concise summaries.")
        else:
            reply = await query_llm(text, system_prompt=system_prompt)
        
        try:
            await thinking_msg.delete()
        except:
            pass
        
        await update.message.reply_text(
            reply,
            parse_mode='Markdown',
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("🔄 Ещё", callback_data=waiting_for),
                InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL),
            ]])
        )
        return

    # Regular message = AI question
    thinking_msg = await update.message.reply_text("🤔 Думаю...")
    
    system_prompt = "You are NEXUS AI — a powerful AI assistant. Answer concisely and helpfully. Use the language the user writes in."
    reply = await query_llm(text, system_prompt=system_prompt)
    
    try:
        await thinking_msg.delete()
    except:
        pass
    
    await update.message.reply_text(
        reply,
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🔄 Ещё вопрос", callback_data="ask"),
            InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL),
        ]])
    )


# ── Post Init ──────────────────────────────────────────────

async def post_init(application: Application):
    commands = [
        BotCommand("start", "Главное меню"),
        BotCommand("help", "Помощь"),
        BotCommand("ask", "Задать вопрос AI"),
        BotCommand("translate", "Перевести текст"),
        BotCommand("code", "Генерация кода"),
        BotCommand("summary", "Краткое содержание"),
        BotCommand("stats", "Статистика"),
        BotCommand("settings", "Настройки"),
        BotCommand("model", "Выбрать модель LLM"),
        BotCommand("dashboard", "Веб-дашборд"),
    ]
    try:
        await application.bot.set_my_commands(commands)
    except Exception as e:
        logger.warning(f"Could not set commands: {e}")
    logger.info(f"Bot started! Model: {LLM_MODELS.get(LLM_MODEL, LLM_MODEL)}")
    if OPENROUTER_API_KEY:
        logger.info("OpenRouter LLM connected!")
    else:
        logger.warning("OpenRouter not configured — using fallback responses")


async def main():
    if not BOT_TOKEN:
        logger.error("TELEGRAM_BOT_TOKEN not set!")
        return

    app = Application.builder().token(BOT_TOKEN).post_init(post_init).build()

    app.add_handler(CommandHandler("start", cmd_start))
    app.add_handler(CommandHandler("help", cmd_help))
    app.add_handler(CommandHandler("ask", cmd_ask))
    app.add_handler(CommandHandler("translate", cmd_translate))
    app.add_handler(CommandHandler("code", cmd_code))
    app.add_handler(CommandHandler("summary", cmd_summary))
    app.add_handler(CommandHandler("stats", cmd_stats))
    app.add_handler(CommandHandler("settings", cmd_settings))
    app.add_handler(CommandHandler("model", cmd_model))
    app.add_handler(CommandHandler("dashboard", cmd_dashboard))
    app.add_handler(CallbackQueryHandler(handle_callback))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))

    logger.info("Starting Nexus AI Telegram Bot...")
    await app.run_polling(drop_pending_updates=True)


def run():
    """Entry point for multiprocessing spawn."""
    asyncio.run(main())


if __name__ == "__main__":
    asyncio.run(main())
