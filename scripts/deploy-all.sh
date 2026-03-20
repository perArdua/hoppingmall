#!/usr/bin/env bash
set -euo pipefail

TAG=$(git rev-parse --short HEAD)
SERVICES=("api-gateway" "user-service" "product-service" "order-service" "payment-service" "notification-service" "settlement-service")

echo "==> Updating image tags to ${TAG}"
for svc in "${SERVICES[@]}"; do
  KUSTOMIZATION="k8s-cd/overlays/local/${svc}/kustomization.yaml"
  sed -i "s|newTag:.*|newTag: ${TAG}|" "$KUSTOMIZATION"
  echo "    ${svc}: tag updated to ${TAG}"
done

echo "==> Applying ArgoCD Applications"
kubectl apply -f k8s-cd/argocd/

echo "==> Waiting for sync..."
sleep 5

for svc in "${SERVICES[@]}"; do
  echo ""
  echo "--- ${svc} ---"
  kubectl argo rollouts status "${svc}" -n hoppingmall --timeout 60s 2>/dev/null || echo "  Rollout not yet available"
done

echo ""
echo "=== Deployment initiated ==="
echo "Monitor: kubectl argo rollouts dashboard -n hoppingmall"
