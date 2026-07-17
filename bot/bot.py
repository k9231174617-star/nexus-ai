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

# Free tier models (no API key needed for some), paid models also listed
LLM_MODELS = {
    "openai/gpt-4o-mini": "GPT-4o Mini",
    "openai/gpt-4o": "GPT-4o",
    "anthropic/claude-3.5-sonnet": "Claude 3.5 Sonnet",
    "anthropic/claude-3-haiku": "Claude 3 Haiku",
    "google/gemini-2.0-flash-001": "Gemini 2.0 Flash",
    "meta-llama/llama-3.2-3b-instruct": "Llama 3.2 3B",
    "mistral/mistral-7b-instruct": "Mistral 7B",
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
    except asyncio.TimeoutError:
        logger.error("OpenRouter API timeout")
        return "⏳ Превышено время ожидания ответа от LLM. Попробуйте ещё раз."
    except Exception as e:
        logger.error(f"LLM query error: {e}")
        return generate_fallback_response(prompt)


def generate_fallback_response(prompt: str) -> str:
    """Generate fallback response when no API key."""
    lower = prompt.lower()
    if "help" in lower or "помощ" in lower:
        return "Я Nexus AI. Для полных возможностей нужен API ключ OpenRouter. В настройках дашборда можно добавить ключ."
    if "code" in lower or "код" in lower or "python" in lower:
        return '```python\nprint("Hello, Nexus AI!")\n```'
    return "Я работаю в демо-режиме. Добавьте OPENROUTER_API_KEY в переменные окружения Railway для полных возможностей."


# ── Command Handlers ──

async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    await update.message.reply_text(HELP_TEXT, parse_mode="Markdown", reply_markup=MAIN_KEYBOARD)


async def cmd_help(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    await update.message.reply_text(HELP_TEXT, parse_mode="Markdown")


async def cmd_ask(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    user_id = update.effective_user.id
    update_stats(user_id, "ask")
    
    # If args provided, use them as question
    if context.args:
        text = " ".join(context.args)
    else:
        # Set waiting state
        user_sessions[user_id] = user_sessions.get(user_id, {})
        user_sessions[user_id]['waiting_for'] = 'ask'
        await update.message.reply_text("💬 Напишите ваш вопрос, и я отвечу:")
        return
    
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


async def cmd_translate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    user_id = update.effective_user.id
    update_stats(user_id, "translate")
    
    if context.args:
        text = " ".join(context.args)
    else:
        user_sessions[user_id] = user_sessions.get(user_id, {})
        user_sessions[user_id]['waiting_for'] = 'translate'
        await update.message.reply_text("🌐 Напишите текст для перевода:")
        return
    
    thinking_msg = await update.message.reply_text("🤔 Перевожу...")
    reply = await query_llm(f"Translate to English: {text}", system_prompt="You are a professional translator. Translate the user's text to English. If it's already in English, translate to Russian.")
    
    try:
        await thinking_msg.delete()
    except:
        pass
    
    await update.message.reply_text(
        reply,
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🔄 Перевести ещё", callback_data="translate"),
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
        desc = " ".join(context.args[1:]) if len(context.args) > 1 else ""
    else:
        user_sessions[user_id] = user_sessions.get(user_id, {})
        user_sessions[user_id]['waiting_for'] = 'code'
        await update.message.reply_text("💻 Формат: /code <язык> <описание>\nНапример: /code python функция сортировки")
        return
    
    thinking_msg = await update.message.reply_text("🤔 Генерирую код...")
    
    prompt = f"Write {lang} code for: {desc}. Provide complete working implementation."
    system_prompt = "You are NEXUS Code Agent — specialized in Android development, Kotlin, Java, and APK analysis. Provide complete working implementations. Format code in markdown code blocks with language tags."
    reply = await query_llm(prompt, system_prompt=system_prompt)
    
    try:
        await thinking_msg.delete()
    except:
        pass
    
    await update.message.reply_text(
        reply,
        parse_mode='Markdown',
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🔄 Ещё код", callback_data="code"),
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
        text = " ".join(context.args)
    else:
        user_sessions[user_id] = user_sessions.get(user_id, {})
        user_sessions[user_id]['waiting_for'] = 'summary'
        await update.message.reply_text("📝 Напишите текст для краткого содержания:")
        return
    
    thinking_msg = await update.message.reply_text("🤔 Делаю краткое содержание...")
    reply = await query_llm(f"Summarize this text briefly in the same language:\n\n{text}", system_prompt="You are an expert summarizer. Provide concise summaries.")
    
    try:
        await thinking_msg.delete()
    except:
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
    user_id = update.effective_user.id
    session = user_sessions.get(user_id, {})
    msg = session.get('messages', 0)
    actions = session.get('actions', {})
    model = session.get('model', LLM_MODEL)
    
    stats_text = f"""📊 **Статистика**
    
Сообщений: {msg}
Модель: {LLM_MODELS.get(model, model)}
Действия:
"""
    for k, v in actions.items():
        stats_text += f"  • {k}: {v}\n"
    
    await update.message.reply_text(stats_text, parse_mode="Markdown")


async def cmd_settings(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    await update.message.reply_text(
        "⚙️ Настройки доступны в веб-дашборде:",
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("🌍 Открыть настройки", url=f"{WEB_DASHBOARD_URL}?tab=settings"),
        ]])
    )


async def cmd_model(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    user_id = update.effective_user.id
    session = user_sessions.get(user_id, {})
    current = session.get('model', LLM_MODEL)
    
    buttons = []
    for model_id, model_name in LLM_MODELS.items():
        prefix = "✅ " if model_id == current else ""
        buttons.append([InlineKeyboardButton(f"{prefix}{model_name}", callback_data=f"model_{model_id}")])
    
    buttons.append([InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL)])
    
    await update.message.reply_text(
        f"🤖 Текущая модель: {LLM_MODELS.get(current, current)}\nВыберите новую:",
        reply_markup=InlineKeyboardMarkup(buttons)
    )


async def cmd_dashboard(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("⛔ Доступ запрещён.")
        return
    await update.message.reply_text(
        "🌍 Веб-дашборд:",
        reply_markup=InlineKeyboardMarkup([[
            InlineKeyboardButton("Открыть", url=WEB_DASHBOARD_URL),
            InlineKeyboardButton("Mini App", web_app=WebAppInfo(url=WEB_DASHBOARD_URL)),
        ]])
    )


async def handle_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    
    user_id = query.from_user.id
    if not is_allowed(user_id):
        await query.edit_message_text("⛔ Доступ запрещён.")
        return
    
    data = query.data
    
    if data.startswith("model_"):
        model_id = data[6:]
        user_sessions[user_id] = user_sessions.get(user_id, {})
        user_sessions[user_id]['model'] = model_id
        # Update global model for this session
        global LLM_MODEL
        LLM_MODEL = model_id
        await query.edit_message_text(
            f"✅ Модель изменена на: {LLM_MODELS.get(model_id, model_id)}",
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("🌍 Дашборд", url=WEB_DASHBOARD_URL),
            ]])
        )
        return
    
    # Handle other callbacks
    if data == "ask":
        user_sessions[user_id] = user_sessions.get(user_id, {})
        user_sessions[user_id]['waiting_for'] = 'ask'
        await query.edit_message_text("💬 Напишите ваш вопрос:")
    elif data == "translate":
        user_sessions[user_id] = user_sessions.get(user_id, {})
        user_sessions[user_id]['waiting_for'] = 'translate'
        await query.edit_message_text("🌐 Напишите текст для перевода:")
    elif data == "code":
        await query.edit_message_text("💻 Формат: /code <язык> <описание>\nНапример: /code python функция сортировки")
    elif data == "summary":
        user_sessions[user_id] = user_sessions.get(user_id, {})
        user_sessions[user_id]['waiting_for'] = 'summary'
        await query.edit_message_text("📝 Напишите текст для краткого содержания:")
    elif data == "stats":
        session = user_sessions.get(user_id, {})
        msg = session.get('messages', 0)
        actions = session.get('actions', {})
        stats = f"📊 Статистика:\nСообщений: {msg}\n"
        for k, v in actions.items():
            stats += f"  • {k}: {v}\n"
        await query.edit_message_text(stats)
    elif data == "settings":
        await query.edit_message_text(
            "⚙️ Настройки в веб-дашборде:",
            reply_markup=InlineKeyboardMarkup([[
                InlineKeyboardButton("Открыть", url=f"{WEB_DASHBOARD_URL}?tab=settings"),
            ]])
        )


# ── Main Message Handler ──
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
            # For code, user would use /code command
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

    # Regular message - treat as AI question
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
    app.run_polling(drop_pending_updates=True)


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())
