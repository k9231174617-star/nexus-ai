import os
import logging
from datetime import datetime
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup, BotCommand
from telegram.ext import Application, CommandHandler, MessageHandler, CallbackQueryHandler, filters, ContextTypes

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
ALLOWED_USERS = [int(x) for x in os.getenv("ALLOWED_USERS", "").split(",") if x.strip()]
WEB_DASHBOARD_URL = os.getenv("WEB_DASHBOARD_URL", "https://k9231174617-star.github.io/nexus-ai/")

user_sessions = {}

HELP_TEXT = """🤖 **Nexus AI Bot**

Команды:
/start — Главное меню
/help — Помощь
/ask <вопрос> — Задать вопрос AI
/translate <текст> — Перевести текст
/code <язык> — Генерация кода
/summary <текст> — Краткое изображение
/stats — Статистика использования
/settings — Настройки
/dashboard — Открыть веб-дашборд
"""

MAIN_KEYBOARD = InlineKeyboardMarkup([
    [InlineKeyboardButton("🤖 Задать вопрос", callback_data="ask"),
     InlineKeyboardButton("💻 Сгенерировать код", callback_data="code")],
    [InlineKeyboardButton("🌐 Переводчик", callback_data="translate"),
     InlineKeyboardButton("📝 Резюме", callback_data="summary")],
    [InlineKeyboardButton("📊 Статистика", callback_data="stats"),
     InlineKeyboardButton("⚙️ Настройки", callback_data="settings")],
    [InlineKeyboardButton("🌍 Веб-дашборд", url=WEB_DASHBOARD_URL)],
])


def is_allowed(user_id: int) -> bool:
    if not ALLOWED_USERS:
        return True
    return user_id in ALLOWED_USERS


def update_stats(user_id: int, action: str):
    if user_id not in user_sessions:
        user_sessions[user_id] = {"messages": 0, "actions": {}}
    user_sessions[user_id]["messages"] += 1
    user_sessions[user_id]["actions"][action] = user_sessions[user_id]["actions"].get(action, 0) + 1


async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    update_stats(update.effective_user.id, "start")
    await update.message.reply_text(
        "👋 Привет! Я Nexus AI Bot.\nВыберите действие:",
        reply_markup=MAIN_KEYBOARD
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
    await update.message.reply_text("🤔 Думаю...")
    response = generate_response(query)
    await update.message.reply_text(response, parse_mode="Markdown")


async def cmd_translate(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    text = " ".join(context.args) if context.args else None
    if not text:
        await update.message.reply_text("Использование: /translate <текст>")
        return
    update_stats(update.effective_user.id, "translate")
    translated = f"🌐 Перевод:\n{text}"
    await update.message.reply_text(translated)


async def cmd_code(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    query = " ".join(context.args) if context.args else "hello world"
    update_stats(update.effective_user.id, "code")
    code = generate_code(query)
    await update.message.reply_text(code, parse_mode="Markdown")


async def cmd_summary(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    text = " ".join(context.args) if context.args else None
    if not text:
        await update.message.reply_text("Использование: /summary <текст для резюмирования>")
        return
    update_stats(update.effective_user.id, "summary")
    await update.message.reply_text(f"📝 Резюме:\n\n{text[:500]}...")


async def cmd_stats(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    uid = update.effective_user.id
    stats = user_sessions.get(uid, {"messages": 0, "actions": {}})
    actions = "\n".join(f"  • {k}: {v}" for k, v in stats.get("actions", {}).items())
    text = f"📊 Статистика:\n\nВсего сообщений: {stats.get('messages', 0)}\nДействия:\n{actions or '  Нет'}"
    await update.message.reply_text(text)


async def cmd_settings(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    kb = InlineKeyboardMarkup([
        [InlineKeyboardButton("🌐 Язык: Русский", callback_data="lang_ru"),
         InlineKeyboardButton("🇬🇧 Язык: English", callback_data="lang_en")],
        [InlineKeyboardButton("🔔 Уведомления: Вкл", callback_data="notif_on"),
         InlineKeyboardButton("🔕 Уведомления: Выкл", callback_data="notif_off")],
        [InlineKeyboardButton("« Назад", callback_data="back_to_main")],
    ])
    await update.message.reply_text("⚙️ Настройки:", reply_markup=kb)


async def cmd_dashboard(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        await update.message.reply_text("❌ Доступ запрещён")
        return
    await update.message.reply_text(
        f"🌍 Веб-дашборд:\n{WEB_DASHBOARD_URL}",
        reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("Открыть дашборд", url=WEB_DASHBOARD_URL)]])
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
        await query.edit_message_text("💻 Генерация кода:\n/code <язык> <описание>\nНапример: /code python сортировка")
    elif data == "translate":
        await query.edit_message_text("🌐 Переводчик:\n/translate <текст>")
    elif data == "summary":
        await query.edit_message_text("📝 Резюмирование:\n/summary <текст>")
    elif data == "stats":
        uid = query.from_user.id
        stats = user_sessions.get(uid, {"messages": 0, "actions": {}})
        text = f"📊 Сообщений: {stats.get('messages', 0)}"
        await query.edit_message_text(text)
    elif data == "settings":
        kb = InlineKeyboardMarkup([
            [InlineKeyboardButton("« Назад", callback_data="back_to_main")],
        ])
        await query.edit_message_text("⚙️ Настройки (скоро)", reply_markup=kb)
    elif data == "back_to_main":
        await query.edit_message_text("Выберите действие:", reply_markup=MAIN_KEYBOARD)


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_allowed(update.effective_user.id):
        return
    update_stats(update.effective_user.id, "message")
    await update.message.reply_text(
        "🤖 Используйте команды или кнопки ниже:",
        reply_markup=MAIN_KEYBOARD
    )


def generate_response(query: str) -> str:
    q = query.lower()
    if any(w in q for w in ["привет", "hello", "hi"]):
        return "👋 Привет! Чем могу помочь?"
    if any(w in q for w in ["код", "code", "программ"]):
        return "💻 Для генерации кода используйте:\n/code <язык> <описание>"
    if any(w in q for w in ["помощь", "help", "что умеешь"]):
        return HELP_TEXT
    return f"🤖 Вы спросили: *{query}*\n\nДля полноценного AI ответа подключите LLM API в настройках."


def generate_code(language: str) -> str:
    examples = {
        "python": '```python\ndef hello():\n    print("Hello from Nexus AI!")\n\nhello()\n```',
        "javascript": '```javascript\nfunction hello() {\n  console.log("Hello from Nexus AI!");\n}\nhello();\n```',
        "java": '```java\npublic class Hello {\n    public static void main(String[] args) {\n        System.out.println("Hello from Nexus AI!");\n    }\n}\n```',
        "rust": '```rust\nfn main() {\n    println!("Hello from Nexus AI!");\n}\n```',
    }
    lang = language.lower().strip()
    for key, code in examples.items():
        if key in lang:
            return f"💻 {key.title()}:\n{code}"
    return f'```python\n# {language}\nprint("Hello from Nexus AI!")\n```'


async def post_init(application: Application):
    commands = [
        BotCommand("start", "Главное меню"),
        BotCommand("help", "Помощь"),
        BotCommand("ask", "Задать вопрос AI"),
        BotCommand("translate", "Перевести текст"),
        BotCommand("code", "Генерация кода"),
        BotCommand("summary", "Краткое изображение"),
        BotCommand("stats", "Статистика"),
        BotCommand("settings", "Настройки"),
        BotCommand("dashboard", "Веб-дашборд"),
    ]
    await application.bot.set_my_commands(commands)
    logger.info("Bot started! Commands registered.")


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
    app.add_handler(CommandHandler("dashboard", cmd_dashboard))
    app.add_handler(CallbackQueryHandler(handle_callback))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))

    logger.info("Starting Nexus AI Telegram Bot...")
    app.run_polling(drop_pending_updates=True)


if __name__ == "__main__":
    main()
