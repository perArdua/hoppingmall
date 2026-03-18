#!/usr/bin/env bash
set -euo pipefail

# 전제조건 체크
check_prerequisites() {
  echo "🔍 Checking prerequisites..."
  local tools=("kind" "kubectl" "docker" "istioctl" "argocd" "kustomize")
  for tool in "${tools[@]}"; do
    if ! command -v "$tool" &> /dev/null; then
      echo "❌ Error: $tool is not installed." >&2
      exit 1
    fi
    echo "✅ $tool found."
  done
}

# Kind 클러스터 + 레지스트리
setup_kind_cluster() {
  echo "🚀 Setting up Kind cluster and local registry..."
  
  # 로컬 레지스트리 시작 (localhost:5001)
  local reg_name='kind-registry'
  local reg_port='5001'
  if [ "$(docker inspect -f '{{.State.Running}}' "${reg_name}" 2>/dev/null || true)" != 'true' ]; then
    echo "📦 Starting local registry '${reg_name}' on port ${reg_port}..."
    docker run -d --restart=always -p "127.0.0.1:${reg_port}:5000" --name "${reg_name}" registry:2
  else
    echo "✅ Local registry '${reg_name}' already running."
  fi

  # Kind 클러스터 생성 (이미 존재하면 스킵)
  if kind get clusters | grep -q "hoppingmall"; then
    echo "✅ Kind cluster 'hoppingmall' already exists, skipping..."
  else
    echo "🏗️ Creating Kind cluster 'hoppingmall'..."
    kind create cluster --name hoppingmall --config k8s-cd/kind-config.yaml
  fi

  # Kind 노드를 레지스트리 네트워크에 연결
  echo "🔗 Connecting Kind nodes to registry network..."
  docker network connect "kind" "${reg_name}" 2>/dev/null || true
}

# 네임스페이스 생성
create_namespaces() {
  echo "📂 Creating namespaces..."
  local namespaces=("hoppingmall" "argocd" "argo-rollouts")
  for ns in "${namespaces[@]}"; do
    if ! kubectl get namespace "$ns" &> /dev/null; then
      kubectl create namespace "$ns"
      echo "✅ Namespace '$ns' created."
    else
      echo "✅ Namespace '$ns' already exists."
    fi
  done
}

# 설치 placeholder
install_tools() {
  echo "🛠️ Installing Istio, ArgoCD, and Argo Rollouts (Placeholders)..."
  # TODO: install-istio.sh 호출
  # bash scripts/install-istio.sh
  
  # TODO: install-argocd.sh 호출
  # bash scripts/install-argocd.sh
  
  # TODO: install-argo-rollouts.sh 호출
  # bash scripts/install-argo-rollouts.sh
}

main() {
  check_prerequisites
  setup_kind_cluster
  create_namespaces
  install_tools
  echo "✅ 셋업 완료!"
}

main "$@"
