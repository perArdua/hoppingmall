#!/usr/bin/env bash
set -euo pipefail

install_argo_rollouts() {
  if kubectl get namespace argo-rollouts &>/dev/null && kubectl get deployment argo-rollouts -n argo-rollouts &>/dev/null; then
    echo "✅ Argo Rollouts already installed, skipping..."
    return
  fi
  echo "🔧 Installing Argo Rollouts..."
  kubectl create namespace argo-rollouts --dry-run=client -o yaml | kubectl apply -f -
  kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
  echo "⏳ Waiting for Argo Rollouts to be ready..."
  kubectl wait --for=condition=available --timeout=300s deployment/argo-rollouts -n argo-rollouts
}

print_plugin_info() {
  echo ""
  echo "✅ Argo Rollouts 설치 완료!"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "📌 kubectl plugin 설치 (미설치 시):"
  echo "   Linux/Mac: curl -LO https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-linux-amd64"
  echo "   chmod +x kubectl-argo-rollouts-linux-amd64 && mv kubectl-argo-rollouts-linux-amd64 /usr/local/bin/kubectl-argo-rollouts"
  echo ""
  echo "📌 사용법:"
  echo "   kubectl argo rollouts version"
  echo "   kubectl argo rollouts list rollouts -n hoppingmall"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

install_argo_rollouts
print_plugin_info
