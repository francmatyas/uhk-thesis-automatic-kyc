FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libgles2 \
    libegl1 \
    libglib2.0-0 \
    tesseract-ocr \
    tesseract-ocr-osd \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

CMD ["sh", "-c", "if [ ! -f /app/data/aml.db ] && [ -f /app/data/peps.csv ] && [ -f /app/data/sanctions.csv ]; then echo 'Building AML DB...'; python -m src.aml.build_db; fi; exec python -m src.main"]
