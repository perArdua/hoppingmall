#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:-api-gateway}"
NAMESPACE="hoppingmall"

echo "=== Canary Demo: ${SERVICE} ==="
echo ""

echo "==> Current rollout status"
kubectl argo rollouts get rollout "${SERVICE}" -n "${NAMESPACE}" 2>/dev/null || {
  echo "Rollout '${SERVICE}' not found. Deploy first with: ./scripts/deploy-all.sh"
  exit 1
}

echo ""
echo "==> To trigger a canary update, build a new image and update the tag:"
echo "    ./scripts/build-push.sh ${SERVICE}"
echo "    cd k8s-cd/overlays/local/${SERVICE}"
echo "    kustomize edit set image ${SERVICE}=localhost:5001/${SERVICE}:\$(git rev-parse --short HEAD)"
echo "    git add . && git commit -m 'chore: update ${SERVICE} image' && git push"
echo ""
echo "==> ArgoCD will detect the change and start canary rollout"
echo ""
echo "==> At each pause step, use these commands:"
echo "    Promote:  kubectl argo rollouts promote ${SERVICE} -n ${NAMESPACE}"
echo "    Abort:    kubectl argo rollouts abort ${SERVICE} -n ${NAMESPACE}"
echo "    Status:   kubectl argo rollouts get rollout ${SERVICE} -n ${NAMESPACE} --watch"
echo ""

read -p "Watch rollout status now? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
  kubectl argo rollouts get rollout "${SERVICE}" -n "${NAMESPACE}" --watch
fi
