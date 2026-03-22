#!/usr/bin/env bash
set -euo pipefail

echo "=== Observability E2E Verification ==="
echo ""

TEMPO_URL="${TEMPO_URL:-http://localhost:3200}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
LOKI_URL="${LOKI_URL:-http://localhost:3100}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"

check() {
  local name="$1" url="$2"
  if curl -sf "$url" > /dev/null 2>&1; then
    echo "[PASS] $name is reachable ($url)"
    return 0
  else
    echo "[FAIL] $name is NOT reachable ($url)"
    return 1
  fi
}

echo "--- 1. Infrastructure Health ---"
check "Tempo" "$TEMPO_URL/ready"
check "Prometheus" "$PROMETHEUS_URL/-/ready"
check "Loki" "$LOKI_URL/ready"
check "Grafana" "$GRAFANA_URL/api/health"
echo ""

echo "--- 2. Prometheus Custom Metrics ---"
for metric in payment_completed_count_total payment_failed_count_total order_created_count_total outbox_event_published_count_total dlq_message_saved_count_total; do
  result=$(curl -sf "$PROMETHEUS_URL/api/v1/query?query=$metric" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',{}).get('result',[])))" 2>/dev/null || echo "0")
  if [ "$result" != "0" ]; then
    echo "[PASS] $metric has data ($result series)"
  else
    echo "[INFO] $metric has no data yet (service may not have processed events)"
  fi
done
echo ""

echo "--- 3. Tempo Traces ---"
trace_count=$(curl -sf "$TEMPO_URL/api/search?limit=5" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('traces',[])))" 2>/dev/null || echo "0")
if [ "$trace_count" != "0" ]; then
  echo "[PASS] Tempo has $trace_count recent traces"
else
  echo "[INFO] Tempo has no traces yet (send some requests first)"
fi
echo ""

echo "--- 4. Loki Logs ---"
log_count=$(curl -sf "$LOKI_URL/loki/api/v1/query?query={job=%22docker%22}&limit=5" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',{}).get('result',[])))" 2>/dev/null || echo "0")
if [ "$log_count" != "0" ]; then
  echo "[PASS] Loki has log streams ($log_count streams)"
else
  echo "[INFO] Loki has no logs yet"
fi
echo ""

echo "--- 5. Grafana Datasources ---"
for ds in Prometheus Loki Tempo; do
  result=$(curl -sf -u admin:admin "$GRAFANA_URL/api/datasources/name/$ds" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('name',''))" 2>/dev/null || echo "")
  if [ "$result" = "$ds" ]; then
    echo "[PASS] Grafana datasource '$ds' configured"
  else
    echo "[FAIL] Grafana datasource '$ds' not found"
  fi
done
echo ""

echo "--- 6. Grafana Dashboards ---"
dashboard_count=$(curl -sf -u admin:admin "$GRAFANA_URL/api/search?type=dash-db" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "0")
echo "[INFO] Grafana has $dashboard_count dashboards loaded"
echo ""

echo "=== Verification Complete ==="
