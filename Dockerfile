FROM python:3.12-slim

WORKDIR /app

# Install dependencies
COPY bot/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy all project files
COPY server.py .
COPY bot/ ./bot/
COPY docs/ ./docs/

EXPOSE 8080

CMD ["python", "server.py"]
