#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="hoppingmall"
REGISTRY_NAME="kind-registry"
REGISTRY_PORT="5001"

total_ram_kb=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo "0")
total_ram_gb=$((total_ram_kb / 1024 / 1024))
if [[ "$total_ram_gb" -lt 8 && "$total_ram_gb" -gt 0 ]]; then
  echo "WARNING: System RAM is ${total_ram_gb}GB. Minimum 8GB recommended."
  read -p "Continue? (y/N) " -n 1 -r
  echo
  [[ $REPLY =~ ^[Yy]$ ]] || exit 1
fi

if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  echo "==> Creating Kind cluster: ${CLUSTER_NAME}"

  if ! docker inspect "${REGISTRY_NAME}" &>/dev/null; then
    docker run -d --restart=always -p "${REGISTRY_PORT}:5000" --network bridge --name "${REGISTRY_NAME}" registry:2
  fi

  cat <<EOF | kind create cluster --name "${CLUSTER_NAME}" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
containerdConfigPatches:
  - |-
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:${REGISTRY_PORT}"]
      endpoint = ["http://${REGISTRY_NAME}:5000"]
EOF

  if [ "$(docker inspect -f='{{json .NetworkSettings.Networks.kind}}' "${REGISTRY_NAME}")" = 'null' ]; then
    docker network connect "kind" "${REGISTRY_NAME}"
  fi
else
  echo "==> Kind cluster '${CLUSTER_NAME}' already exists"
fi

echo "==> Installing Istio"
if ! kubectl get namespace istio-system &>/dev/null; then
  istioctl install --set profile=demo -y
else
  echo "    Istio already installed"
fi

echo "==> Creating hoppingmall namespace"
kubectl apply -f k8s/namespace.yml
kubectl label namespace hoppingmall istio-injection=enabled --overwrite

echo "==> Disabling Istio sidecar for infra pods"
for manifest in k8s/infra/*.yml; do
  kubectl apply -f "$manifest"
done
for deploy in mysql kafka zookeeper redis zipkin prometheus loki grafana; do
  kubectl patch statefulset "$deploy" -n hoppingmall --type=merge -p '{"spec":{"template":{"metadata":{"annotations":{"sidecar.istio.io/inject":"false"}}}}}' 2>/dev/null || true
  kubectl patch deployment "$deploy" -n hoppingmall --type=merge -p '{"spec":{"template":{"metadata":{"annotations":{"sidecar.istio.io/inject":"false"}}}}}' 2>/dev/null || true
done
kubectl patch daemonset promtail -n hoppingmall --type=merge -p '{"spec":{"template":{"metadata":{"annotations":{"sidecar.istio.io/inject":"false"}}}}}' 2>/dev/null || true

echo "==> Installing Argo Rollouts"
if ! kubectl get namespace argo-rollouts &>/dev/null; then
  kubectl create namespace argo-rollouts
  kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
else
  echo "    Argo Rollouts already installed"
fi

echo "==> Installing ArgoCD"
if ! kubectl get namespace argocd &>/dev/null; then
  kubectl create namespace argocd
  kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
else
  echo "    ArgoCD already installed"
fi

echo "==> Applying base config"
kubectl apply -f k8s/configmap.yml -f k8s/secret.yml

echo ""
echo "=== Setup Complete ==="
echo "ArgoCD admin password: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
echo "ArgoCD port-forward:  kubectl port-forward svc/argocd-server -n argocd 8443:443"
echo "Argo Rollouts dashboard: kubectl argo rollouts dashboard -n hoppingmall"
