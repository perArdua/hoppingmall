#!/usr/bin/env bash
set -euo pipefail

install_istio() {
  if kubectl get namespace istio-system &>/dev/null; then
    echo "Istio already installed, skipping..."
    return
  fi

  echo "Installing Istio (demo profile)..."
  istioctl install --set profile=demo -y
  echo "Istio installed."
}

label_namespace() {
  echo "Enabling Istio sidecar injection for hoppingmall namespace..."
  kubectl label namespace hoppingmall istio-injection=enabled --overwrite
}

verify_istio() {
  echo "Verifying Istio installation..."
  istioctl verify-install
}

install_istio
label_namespace
verify_istio
echo "Istio setup completed."
