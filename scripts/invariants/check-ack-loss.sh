#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || "$1" == "-h" || "$1" == "--help" ]]; then
  echo "usage: $0 <k6 console output file>"
  echo "INV-8 ack 쓰기 손실: k6가 2xx(OLC ORDER_OK)로 받은 주문 중 order_db.orders에 없는 주문 수"
  echo "env: MYSQL_HOST MYSQL_PORT MYSQL_USER MYSQL_PASSWORD"
  exit 0
fi

K6_OUTPUT="$1"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"

run_mysql() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" --protocol=TCP -N -B "$@" | tr -d '\r'
}

EXECUTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
GIT_HEAD="$(git -C "$(dirname "$0")" rev-parse HEAD 2>/dev/null || echo unknown)"
TARGET="$MYSQL_USER@$MYSQL_HOST:$MYSQL_PORT"

acked_ids="$(grep -o 'OLC ORDER_OK order_id=[0-9]*' "$K6_OUTPUT" | sed 's/.*order_id=//' | tr -d '\r' | sort -nu || true)"
acked_count="$(printf '%s' "$acked_ids" | grep -c . || true)"

missing_ids=""
if [[ "$acked_count" -gt 0 ]]; then
  id_list="$(printf '%s' "$acked_ids" | paste -sd, -)"
  found_ids="$(run_mysql -e "SELECT id FROM order_db.orders WHERE id IN ($id_list)")"
  missing_ids="$(awk 'NR == FNR { found[$1]; next } !($1 in found)' <(printf '%s\n' "$found_ids") <(printf '%s\n' "$acked_ids") | grep . || true)"
fi
missing_count="$(printf '%s' "$missing_ids" | grep -c . || true)"
db_orders="$(run_mysql -e "SELECT COUNT(*) FROM order_db.orders WHERE deleted_at IS NULL")"
missing_json="$(printf '%s' "$missing_ids" | paste -sd, -)"

echo "{\"executed_at\":\"$EXECUTED_AT\",\"git_head\":\"$GIT_HEAD\",\"db_target\":\"$TARGET\",\"k6_output\":\"$K6_OUTPUT\",\"counts\":{\"INV-8\":$missing_count},\"acked_orders\":$acked_count,\"db_orders\":$db_orders,\"missing_order_ids\":[${missing_json}]}"
echo ""
echo "| INV | 의미 | 건수 |"
echo "|---|---|---|"
echo "| INV-8 | k6가 2xx로 받은 주문 중 DB에 없는 주문 (acked=$acked_count, db_orders=$db_orders) | $missing_count |"
