#!/usr/bin/env bash
set -euo pipefail

REGISTRY="localhost:5001"
TAG=$(git rev-parse --short HEAD)
SERVICES=("api-gateway" "user-service" "product-service")

if [[ $# -gt 0 ]]; then
  SERVICES=("$@")
fi

echo "==> Building with tag: ${TAG}"

FAILED=()
for svc in "${SERVICES[@]}"; do
  echo "==> Building ${svc}..."
  if docker build -t "${REGISTRY}/${svc}:${TAG}" "./${svc}/"; then
    docker push "${REGISTRY}/${svc}:${TAG}"
    echo "    ${svc}:${TAG} pushed"
  else
    echo "    FAILED: ${svc}"
    FAILED+=("$svc")
  fi
done

echo ""
if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo "=== Build failures: ${FAILED[*]} ==="
  exit 1
else
  echo "=== All builds successful (tag: ${TAG}) ==="
fi
