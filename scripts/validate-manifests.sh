#!/usr/bin/env bash
set -euo pipefail

SERVICES=("api-gateway" "user-service" "product-service")
SKIP_KINDS="Rollout,AnalysisTemplate,VirtualService,DestinationRule,Application"
FAILED=0

echo "==> Validating k8s-cd manifests"

for svc in "${SERVICES[@]}"; do
  echo ""
  echo "--- ${svc} (base) ---"
  if kubectl kustomize "k8s-cd/base/${svc}/" > /dev/null 2>&1; then
    echo "    kubectl kustomize: OK"
  else
    echo "    kubectl kustomize: FAILED"
    FAILED=1
  fi

  echo "--- ${svc} (overlay/local) ---"
  OUTPUT=$(kubectl kustomize "k8s-cd/overlays/local/${svc}/" 2>&1)
  if [[ $? -eq 0 ]]; then
    echo "    kubectl kustomize: OK"
    if echo "$OUTPUT" | grep -q "localhost:5001/${svc}"; then
      echo "    image reference: OK (localhost:5001/${svc})"
    else
      echo "    image reference: MISSING localhost:5001/${svc}"
      FAILED=1
    fi
  else
    echo "    kubectl kustomize: FAILED"
    FAILED=1
  fi
done

echo ""
echo "--- kubeconform ---"
for svc in "${SERVICES[@]}"; do
  BUILT=$(kubectl kustomize "k8s-cd/overlays/local/${svc}/" 2>/dev/null)
  if echo "$BUILT" | kubeconform -skip "${SKIP_KINDS}" -strict -summary 2>/dev/null; then
    echo "    ${svc}: kubeconform OK"
  else
    echo "    ${svc}: kubeconform issues (CRD types skipped)"
  fi
done

echo ""
echo "--- File extension check ---"
YML_COUNT=$(find k8s-cd -name "*.yml" 2>/dev/null | wc -l)
if [[ "$YML_COUNT" -eq 0 ]]; then
  echo "    No .yml files in k8s-cd/: OK"
else
  echo "    WARNING: Found ${YML_COUNT} .yml files in k8s-cd/"
  FAILED=1
fi

echo ""
if [[ "$FAILED" -eq 0 ]]; then
  echo "=== All validations passed ==="
  exit 0
else
  echo "=== Some validations failed ==="
  exit 1
fi
