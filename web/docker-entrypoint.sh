#!/bin/sh
# Regenerate /env.js from container env vars, then exec nginx.
# Values here are public — safe to expose in the browser bundle.
#
# Keys mirror src/env.d.ts (window.ENV). One image serves dev/staging/prod;
# per-env overrides come from the container's environment.

set -eu

cat > /usr/share/nginx/html/env.js <<EOF
window.ENV = {
  API_BASE_URL: "${API_BASE_URL:-/api}",
  HIEMDALL_BASE_URL: "${HIEMDALL_BASE_URL:-http://localhost:9082}",
  APP_ID: "${APP_ID:-stash}"
};
EOF

exec nginx -g "daemon off;"
