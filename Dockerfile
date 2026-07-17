FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY nexus-ai/bot/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy all project files
COPY nexus-ai/server.py .
COPY nexus-ai/bot/ ./bot/
COPY nexus-ai/docs/ ./docs/

EXPOSE 8080

CMD ["python", "server.py"]
