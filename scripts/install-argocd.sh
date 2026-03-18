#!/usr/bin/env bash
set -euo pipefail

ARGOCD_VERSION="stable"

install_argocd() {
  if kubectl get namespace argocd &>/dev/null && kubectl get deployment argocd-server -n argocd &>/dev/null; then
    echo "✅ ArgoCD already installed, skipping..."
    return
  fi

  echo "🔧 Installing ArgoCD..."
  kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -n argocd -f "https://raw.githubusercontent.com/argoproj/argo-cd/${ARGOCD_VERSION}/manifests/install.yaml"

  echo "⏳ Waiting for ArgoCD to be ready..."
  kubectl wait --for=condition=available --timeout=300s deployment/argocd-server -n argocd
}

patch_insecure() {
  echo "🔓 Patching ArgoCD server to insecure mode..."
  kubectl patch configmap argocd-cmd-params-cm -n argocd \
    --type merge \
    -p '{"data":{"server.insecure":"true"}}' 2>/dev/null || true

  kubectl rollout restart deployment/argocd-server -n argocd
  kubectl rollout status deployment/argocd-server -n argocd
}

print_info() {
  echo ""
  echo "✅ ArgoCD 설치 완료!"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "📌 ArgoCD UI 접속:"
  echo "   kubectl port-forward svc/argocd-server -n argocd 8443:443"
  echo "   → https://localhost:8443"
  echo ""
  echo "🔑 초기 admin 비밀번호:"
  echo "   kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d && echo"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

install_argocd
patch_insecure
print_info
