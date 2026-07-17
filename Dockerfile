FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY bot/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy bot files
COPY bot/bot.py .

# Copy env example (optional, won't fail if missing)
COPY bot/.env.example .env.example

EXPOSE 8080

CMD ["python", "bot.py"]
