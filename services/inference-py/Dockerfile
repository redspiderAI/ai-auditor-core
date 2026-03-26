FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# Copy project files (copy lockfile first for better cache)
COPY pyproject.toml uv.lock ./
COPY . /app

# Install uv and upgrade pip
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir uv

# Install dependencies into a project virtual environment using uv
RUN uv sync --locked

# Put the project's virtualenv and local uv bin on PATH
ENV PATH="/app/.venv/bin:/root/.local/bin:$PATH"

# Expose gRPC default port
EXPOSE 50051

# Start with uv
CMD ["uv", "run", "main.py"]
