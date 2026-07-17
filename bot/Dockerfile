FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy all bot files
COPY . .

# Expose port if needed
EXPOSE 8080

# Run the bot
CMD ["python", "bot.py"]
