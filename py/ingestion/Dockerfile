# ── py/ingestion/Dockerfile ──────────────────────────────────────────────────
# Standalone image for the STASH ingestion engine.
#
# The `ingestion` package depends only on PyPI packages (no workspace path deps), so it builds
# directly from its own pyproject.toml — no need to copy the whole uv workspace.
#
# Build context is this directory (py/ingestion/):
#   docker build -t stash-ingestion py/ingestion
#
# Runtime config is read from the environment (see README §Configuration / .env.example).
# Mount a log volume and keep LOG_DIR=/logs (port convention 7xxx is irrelevant here — the
# engine is a worker and exposes no port).

FROM python:3.12-slim

# uv for fast, reproducible installs.
RUN pip install --no-cache-dir uv

WORKDIR /app

# Install dependencies first (better layer caching), then the package itself.
COPY pyproject.toml ./
COPY src ./src
RUN uv pip install --system --no-cache .

ENV PYTHONUNBUFFERED=1 \
    LOG_DIR=/logs

# Long-running worker. SIGTERM triggers a clean shutdown (see main._install_signal_handlers).
ENTRYPOINT ["python", "-m", "ingestion.main"]
