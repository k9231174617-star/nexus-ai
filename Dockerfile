FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY bot/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy bot files
COPY bot/bot.py .
COPY bot/.env.example .env 2>/dev/null || true

# Expose port (Railway needs this for health checks)
EXPOSE 8080

# Run the bot
CMD ["python", "bot.py"]
