#!/usr/bin/env bash
# Flips which search-service color (blue/green) receives live traffic
# through the nginx proxy, with zero downtime and no gateway changes.
#
# Usage: ./scripts/switch-search-color.sh <blue|green> [--force]
set -euo pipefail

COLOR="${1:-}"
FORCE="${2:-}"
PROXY_CONTAINER="document-search-search-service-proxy"

if [[ "$COLOR" != "blue" && "$COLOR" != "green" ]]; then
  echo "Usage: $0 <blue|green> [--force]" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTIVE_CONF="${SCRIPT_DIR}/../nginx/search-service/conf.d/active.conf"
HEALTH_PORT="$([[ "$COLOR" == "blue" ]] && echo 8091 || echo 8092)"

if [[ "$FORCE" != "--force" ]]; then
  echo "Checking health of search-service-${COLOR} on localhost:${HEALTH_PORT} ..."
  if ! curl -fs "http://localhost:${HEALTH_PORT}/actuator/health" >/dev/null; then
    echo "search-service-${COLOR} is not healthy; refusing to switch. Use --force to override." >&2
    exit 1
  fi
fi

echo "server search-service-${COLOR}:8083 max_fails=3 fail_timeout=10s;" > "${ACTIVE_CONF}"

docker exec "${PROXY_CONTAINER}" nginx -t
docker exec "${PROXY_CONTAINER}" nginx -s reload

echo "Switched search-service traffic to: ${COLOR}"
