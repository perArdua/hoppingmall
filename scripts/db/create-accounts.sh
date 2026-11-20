#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || "$1" == "-h" || "$1" == "--help" ]]; then
  echo "usage: $0 <compose 프로젝트 이름> <env 파일>..."
  echo "이미 만들어진 mysql 볼륨에 서비스별 계정과 consistency_monitor 계정을 만든다(다시 돌려도 같은 결과)."
  echo "env 파일에서 읽는 변수: USER_DB_PASSWORD PRODUCT_DB_PASSWORD ORDER_DB_PASSWORD PAYMENT_DB_PASSWORD NOTIFICATION_DB_PASSWORD SETTLEMENT_DB_PASSWORD CONSISTENCY_MONITOR_DB_PASSWORD"
  exit 0
fi

PROJECT="$1"
shift
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
VARS=(USER_DB_PASSWORD PRODUCT_DB_PASSWORD ORDER_DB_PASSWORD PAYMENT_DB_PASSWORD NOTIFICATION_DB_PASSWORD SETTLEMENT_DB_PASSWORD CONSISTENCY_MONITOR_DB_PASSWORD)

for env_file in "$@"; do
  set -a
  # shellcheck disable=SC1090
  . <(tr -d '\r' < "$env_file")
  set +a
done

env_args=()
for name in "${VARS[@]}"; do
  [[ -n "${!name:-}" ]] || { echo "$name 이(가) env 파일에 없습니다" >&2; exit 1; }
  env_args+=(-e "$name")
done

container="$(docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "label=com.docker.compose.service=mysql")"
[[ -n "$container" ]] || { echo "프로젝트 $PROJECT 의 mysql 컨테이너가 떠 있지 않습니다" >&2; exit 1; }

tr -d '\r' < "$ROOT_DIR/docker/init-accounts.sh" | docker exec -i "${env_args[@]}" "$container" bash -s
echo "계정을 만들었습니다: project=$PROJECT"
