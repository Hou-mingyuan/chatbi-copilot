#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env (LLM_PROVIDER=mock, keys optional)."
fi

echo "Starting docker compose (mock LLM + demo MySQL/PostgreSQL)..."
docker compose up -d --build --wait

echo "Running mock demo smoke..."
node scripts/smoke-mock-demo.mjs "${CHATBI_SMOKE_BASE_URL:-http://127.0.0.1:19030}"

cat <<EOF

Mock demo is up.
  Web UI:  http://localhost:${FRONTEND_HOST_PORT:-19031}/
  API:     http://localhost:${BACKEND_HOST_PORT:-19030}/api/health

Try asking: 「各产品类目的销售额占比」
Follow-up: 「只看华东大区」
Stop: docker compose down
EOF
