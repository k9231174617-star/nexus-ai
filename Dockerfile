FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY bot/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy bot files
COPY bot/bot.py .
COPY bot/.env.example .env 2>/dev/null || true

EXPOSE 8080

CMD ["python", "bot.py"]
