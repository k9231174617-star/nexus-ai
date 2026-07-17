import os
import logging
import json
import re
import aiohttp
import asyncio
from datetime import datetime, timedelta
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup, BotCommand
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
"""

MAIN_KEYBOARD = InlineKeyboardMarkup([
    [InlineKeyboardButton("🤖 Задать вопрос", callback_data="ask"),
     InlineKeyboardButton("💻 Код", callback_data="code")],
    [InlineKeyboardButton("🌐 Перевод", callback_data="translate"),
     InlineKeyboardButton("📝 Резюме", callback_data="summary"),
     InlineKeyboardButton("📊 Статистика", callback_data="stats")],
    [InlineKeyboardButton("⚙️ Настройки", callback_data="settings"),
     InlineKeyboardButton("🤖 Модель", callback_data="model")],
    [InlineKeyboardButton("🌍 Веб-дашборд", url=WEB_DASHBOARD_URL)],
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
                        {"role": "user", "content": prompt},
                    ],
                    "max_tokens": 2048,
                    "temperature": 0.7,
                },
                timeout=aiohttp.ClientTimeout(total=60),
            ) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    return data["choices"][0]["message"]["content"]
                else:
                    error_text = await resp.text()
                    logger.error(f"OpenRouter error {resp.status}: {error_text[:200]}")
                    return f"⚠️ Ошибка LLM ({resp.status}). Использую запасной ответ.\n\n{generate_fallback_response(prompt)}"
    except asyncio.TimeoutError:
        return "⏱️ Превышено время ожидания LLM. Попробуйте ещё раз."
    except Exception as e:
        logger.error(f"LLM query failed: {e}")
        return f"⚠️ Не удалось подключиться к LLM.\n\n{generate_fallback_response(prompt)}"


async def query_llm_code(prompt: str, language: str = "") -> str:
    """Generate code via LLM."""
    system = "Ты эксперт по программированию. Отвечай ТОЛЬКО кодом без лишних пояснений."
    if language:
        system += f" Язык: {language}."
    return await query_llm(prompt, system)


def generate_fallback_response(prompt: str) -> str:
    """Fallback when LLM is not available."""
    prompt_lower = prompt.lower()
    if any(w in prompt_lower for w in ["привет", "hello", "hi", "здравствуй"]):
        return "👋 Привет! Чем могу помочь?"
    if any(w in prompt_lower for w in ["код", "code", "программ", "напиши"]):
        return "💻 Для генерации кода используйте:\n/code <язык> <описание>"
    if any(w in prompt_lower for w in ["помощь", "help", "что умеешь"]):
        return HELP_TEXT
    if any(w in prompt_lower for w in ["перевод", "translate", "переведи"]):
        return "🌐 Для перевода используйте:\n/translate <текст>"
    return f"🤖 Вы спросили: *{prompt}*\n\nДля AI ответа добавьте OPENROUTER_API_KEY в настройках."


def generate_fallback_code(language: str) -> str:
    """Fallback code generation."""
    examples = {
        "python": '```python\ndef hello():\n    print("Hello from Nexus AI!")\n\nhello()\n```',
        "javascript": '```javascript\nfunction hello() {\n  console.log("Hello from Nexus AI!");\n}\nhello();\n```',
        "java": '```java\npublic class Hello {\n    public static void main(String[] args) {\n        System.out.println("Hello from Nexus AI!");\n    }\n}\n```',
        "rust": '```rust\nfn main() {\n    println!("Hello from Nexus AI!");\n}\n```',
        "kotlin": '```kotlin\nfun main() {\n    println("Hello from Nexus AI!")\n}\n```',
        "go": '```go\npackage main\nimport "fmt"\nfunc main() {\n    fmt.Println("Hello from Nexus AI!")\n}\n```',
    }
    for key, code in examples.items():
        if key in language.lower():
            return f"💻 {key.title()}:\n{code}"
    return f'```\n# {language}\nprint("Hello from Nexus AI!")\n```'


async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    update_stats(update.effective_user.id, "start")
    user = update.effective_user
    await update.message.reply_text(
        f"👋 Привет, {user.first_name or 'пользователь'}!\n\n"
        f"Я Nexus AI — многофункциональный AI ассистент.\n"
        f"Модель: *{LLM_MODELS.get(LLM_MODEL, LLM_MODEL)}*\n\n"
        f"Выберите действие:",
        reply_markup=MAIN_KEYBOARD,
        parse_mode="Markdown",
    )


async def cmd_help(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    await update.message.reply_text(HELP_TEXT, parse_mode="Markdown")


async def cmd_ask(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    query = " ".join(context.args) if context.args else None
    if not query:
        await update.message.reply_text("Использование: /ask <ваш вопрос>\nНапример: /ask Что такое нейросеть?")
        return
    update_stats(update.effective_user.id, "ask")
    sent_msg = await update.message.reply_text("🤔 Думаю...")
    response = await query_llm(query)
    # Split long messages
    if len(response) > 4000:
        chunks = [response[i:i+4000] for i in range(0, len(response), 4000)]
        for chunk in chunks:
            await update.message.reply_text(chunk, parse_mode="Markdown")
        await sent_msg.delete()
    else:
        await sent_msg.edit_text(response, parse_mode="Markdown")


async def cmd_translate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    text = " ".join(context.args) if context.args else None
    if not text:
        await update.message.reply_text("Использование: /translate <текст>")
        return
    update_stats(update.effective_user.id, "translate")
    sent_msg = await update.message.reply_text("🌐 Перевожу...")
    response = await query_llm(
        f"Переведи на русский язык. Ответь ТОЛЬКО переводом, без пояснений:\n\n{text}",
        "Ты переводчик. Отвечай только переводом."
    )
    await sent_msg.edit_text(f"🌐 *Перевод:*\n\n{response}", parse_mode="Markdown")


async def cmd_code(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    query = " ".join(context.args) if context.args else "hello world"
    update_stats(update.effective_user.id, "code")
    
    if not OPENROUTER_API_KEY:
        await update.message.reply_text(generate_fallback_code(query), parse_mode="Markdown")
        return
    
    sent_msg = await update.message.reply_text("💻 Генерирую код...")
    response = await query_llm_code(query)
    if len(response) > 4000:
        await sent_msg.edit_text("💻 Код готов (отправляю частями):")
        chunks = [response[i:i+4000] for i in range(0, len(response), 4000)]
        for chunk in chunks:
            await update.message.reply_text(chunk, parse_mode="Markdown")
    else:
        await sent_msg.edit_text(response, parse_mode="Markdown")


async def cmd_summary(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    text = " ".join(context.args) if context.args else None
    if not text:
        await update.message.reply_text("Использование: /summary <текст для резюмирования>")
        return
    update_stats(update.effective_user.id, "summary")
    sent_msg = await update.message.reply_text("📝 Составляю краткое содержание...")
    response = await query_llm(f"Сделай краткое содержание (3-5 предложений):\n\n{text}")
    if len(response) > 4000:
        await sent_msg.edit_text("📝 *Краткое содержание:*")
        chunks = [response[i:i+4000] for i in range(0, len(response), 4000)]
        for chunk in chunks:
            await update.message.reply_text(chunk, parse_mode="Markdown")
    else:
        await sent_msg.edit_text(f"📝 *Краткое содержание:*\n\n{response}", parse_mode="Markdown")


async def cmd_stats(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    uid = update.effective_user.id
    stats = user_sessions.get(uid, {"messages": 0, "actions": {}})
    actions = "\n".join(f"  • {k}: {v}" for k, v in stats.get("actions", {}).items())
    user_model = stats.get("model", LLM_MODEL)
    text = (
        f"📊 *Статистика*\n\n"
        f"Всего сообщений: {stats.get('messages', 0)}\n"
        f"Модель: {LLM_MODELS.get(user_model, user_model)}\n"
        f"Действия:\n{actions or '  Нет'}"
    )
    await update.message.reply_text(text, parse_mode="Markdown")


async def cmd_settings(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    kb = InlineKeyboardMarkup([
        [InlineKeyboardButton("🌐 Язык: Русский", callback_data="lang_ru"),
         InlineKeyboardButton("🇬🇧 English", callback_data="lang_en")],
        [InlineKeyboardButton("« Назад", callback_data="back_to_main")],
    ])
    await update.message.reply_text("⚙️ *Настройки*\n\nВыберите язык:", reply_markup=kb, parse_mode="Markdown")


async def cmd_model(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    buttons = []
    row = []
    for i, (model_id, model_name) in enumerate(LLM_MODELS.items()):
        prefix = "●" if model_id == LLM_MODEL else "○"
        row.append(InlineKeyboardButton(f"{prefix} {model_name}", callback_data=f"model_{model_id}"))
        if len(row) == 2 or i == len(LLM_MODELS) - 1:
            buttons.append(row)
            row = []
    if row:
        buttons.append(row)
    buttons.append([InlineKeyboardButton("« Назад", callback_data="back_to_main")])
    kb = InlineKeyboardMarkup(buttons)
    await update.message.reply_text(
        f"🤖 *Выбор модели LLM*\n\n"
        f"Текущая: *{LLM_MODELS.get(LLM_MODEL, LLM_MODEL)}*\n\n"
        f"Выберите модель:",
        reply_markup=kb,
        parse_mode="Markdown",
    )


async def cmd_dashboard(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    await update.message.reply_text(
        f"🌍 *Веб-дашборд:*\n{WEB_DASHBOARD_URL}",
        reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("🌍 Открыть дашборд", url=WEB_DASHBOARD_URL)]]),
        parse_mode="Markdown",
    )


async def handle_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    if not is_allowed(query.from_user.id):
        await query.answer("❌ Доступ запрещён", show_alert=True)
        return
    await query.answer()
    data = query.data

    if data == "ask":
        await query.edit_message_text("🤖 Задайте вопрос, отправив:\n/ask <ваш вопрос>")
    elif data == "code":
        await query.edit_message_text("💻 Генерация кода:\n/code <язык> <описание>\n/code python сортировка пузырьком")
    elif data == "translate":
        await query.edit_message_text("🌐 Перевод:\n/translate <текст для перевода>")
    elif data == "summary":
        await query.edit_message_text("📝 Резюмирование:\n/summary <длинный текст>")
    elif data == "stats":
        uid = query.from_user.id
        stats = user_sessions.get(uid, {"messages": 0, "actions": {}, "model": LLM_MODEL})
        actions = "\n".join(f"  • {k}: {v}" for k, v in stats.get("actions", {}).items())
        model_name = LLM_MODELS.get(stats.get("model", LLM_MODEL), LLM_MODEL)
        text = f"📊 *Статистика*\nСообщений: {stats.get('messages', 0)}\nМодель: {model_name}\n{actions or ''}"
        await query.edit_message_text(text, parse_mode="Markdown")
    elif data == "settings":
        kb = InlineKeyboardMarkup([
            [InlineKeyboardButton("🌐 Русский", callback_data="lang_ru"),
             InlineKeyboardButton("🇬🇧 English", callback_data="lang_en")],
            [InlineKeyboardButton("« Назад", callback_data="back_to_main")],
        ])
        await query.edit_message_text("⚙️ *Настройки:*", reply_markup=kb, parse_mode="Markdown")
    elif data == "model":
        await cmd_model(update, context)
    elif data.startswith("model_"):
        model_id = data.replace("model_", "")
        if model_id in LLM_MODELS:
            uid = query.from_user.id
            if uid not in user_sessions:
                user_sessions[uid] = {"messages": 0, "actions": {}, "model": model_id}
            else:
                user_sessions[uid]["model"] = model_id
            await query.edit_message_text(
                f"✅ Модель изменена на *{LLM_MODELS[model_id]}*\n\n"
                f"Новая модель будет использоваться для всех запросов.",
                parse_mode="Markdown",
            )
        else:
            await query.edit_message_text("❌ Модель не найдена")
    elif data == "back_to_main":
        await query.edit_message_text(
            f"Выберите действие:\nМодель: *{LLM_MODELS.get(LLM_MODEL, LLM_MODEL)}*",
            reply_markup=MAIN_KEYBOARD,
            parse_mode="Markdown",
        )
    elif data.startswith("lang_"):
        lang = "🇬🇧 English" if data == "lang_en" else "🌐 Русский"
        await query.edit_message_text(f"✅ Язык изменён на {lang}\n\n(Функционал перевода интерфейса в разработке)", reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("« Назад", callback_data="settings")]]))


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        return
    update_stats(update.effective_user.id, "message")
    await update.message.reply_text(
        "🤖 Используйте команды или кнопки ниже:",
        reply_markup=MAIN_KEYBOARD
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


def main():
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
    main()
