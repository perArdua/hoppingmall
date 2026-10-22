#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="${SQL_FILE:-$SCRIPT_DIR/invariants.sql}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"

PENDING_MAX_MINUTES="${PENDING_MAX_MINUTES:-10}"
PAYING_MAX_MINUTES="${PAYING_MAX_MINUTES:-15}"
ORPHAN_GRACE_MINUTES="${ORPHAN_GRACE_MINUTES:-1}"
DATA_TIME_ZONE="${DATA_TIME_ZONE:-+09:00}"

DETAIL=false
for arg in "$@"; do
  case "$arg" in
    --detail) DETAIL=true ;;
    -h|--help)
      echo "usage: $0 [--detail]"
      echo "env: MYSQL_HOST MYSQL_PORT MYSQL_USER MYSQL_PASSWORD PENDING_MAX_MINUTES PAYING_MAX_MINUTES ORPHAN_GRACE_MINUTES DATA_TIME_ZONE SQL_FILE"
      exit 0
      ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

run_mysql() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" --protocol=TCP --init-command="SET time_zone = '$DATA_TIME_ZONE'" "$@"
}

render() {
  sed -e "s/{{PENDING_MAX_MINUTES}}/$PENDING_MAX_MINUTES/g" \
      -e "s/{{PAYING_MAX_MINUTES}}/$PAYING_MAX_MINUTES/g" \
      -e "s/{{ORPHAN_GRACE_MINUTES}}/$ORPHAN_GRACE_MINUTES/g"
}

json_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

EXECUTED_AT="$(date '+%Y-%m-%dT%H:%M:%S%z')"
GIT_HEAD="$(git -C "$SCRIPT_DIR" rev-parse HEAD 2>/dev/null || echo unknown)"
TARGET="$MYSQL_USER@$MYSQL_HOST:$MYSQL_PORT"

ids=()
meanings=()
queries=()
while IFS=$'\t' read -r id meaning query; do
  ids+=("$id")
  meanings+=("$meaning")
  queries+=("$query")
done < <(render < "$SQL_FILE" | tr -d '\r' | awk '
  /^-- @INV / {
    line = substr($0, 9)
    split(line, parts, " \\| ")
    id = parts[1]; meaning = parts[2]; sql = ""; next
  }
  /^--/ || /^[[:space:]]*$/ { next }
  {
    sql = (sql == "" ? $0 : sql " " $0)
    if ($0 ~ /;[[:space:]]*$/) {
      sub(/;[[:space:]]*$/, "", sql)
      printf "%s\t%s\t%s\n", id, meaning, sql
      sql = ""
    }
  }')

counts=()
errors=()
for i in "${!ids[@]}"; do
  if out="$(run_mysql -N -B -e "SELECT COUNT(*) FROM (${queries[$i]}) v" 2>&1)"; then
    counts+=("$(printf '%s' "$out" | tail -n 1)")
    errors+=("")
  else
    counts+=("null")
    errors+=("$(printf '%s' "$out" | grep -v 'Using a password' | tail -n 1)")
  fi
done

json="{\"executed_at\":\"$EXECUTED_AT\",\"git_head\":\"$GIT_HEAD\",\"db_target\":\"$(json_escape "$TARGET")\""
json+=",\"params\":{\"PENDING_MAX_MINUTES\":$PENDING_MAX_MINUTES,\"PAYING_MAX_MINUTES\":$PAYING_MAX_MINUTES,\"ORPHAN_GRACE_MINUTES\":$ORPHAN_GRACE_MINUTES,\"DATA_TIME_ZONE\":\"$DATA_TIME_ZONE\"}"
json+=",\"counts\":{"
for i in "${!ids[@]}"; do
  [[ $i -gt 0 ]] && json+=","
  json+="\"${ids[$i]}\":${counts[$i]}"
done
json+="}"
has_error=false
for e in "${errors[@]}"; do [[ -n "$e" ]] && has_error=true; done
if $has_error; then
  json+=",\"errors\":{"
  first=true
  for i in "${!ids[@]}"; do
    [[ -z "${errors[$i]}" ]] && continue
    $first || json+=","
    first=false
    json+="\"${ids[$i]}\":\"$(json_escape "${errors[$i]}")\""
  done
  json+="}"
fi
json+="}"
echo "$json"

echo ""
echo "- executed_at: $EXECUTED_AT"
echo "- git_head: $GIT_HEAD"
echo "- db_target: $TARGET"
echo ""
echo "| INV | 의미 | 건수 |"
echo "|---|---|---|"
for i in "${!ids[@]}"; do
  value="${counts[$i]}"
  [[ "$value" == "null" ]] && value="ERROR"
  echo "| ${ids[$i]} | ${meanings[$i]} | $value |"
done

if $DETAIL; then
  for i in "${!ids[@]}"; do
    [[ "${counts[$i]}" == "0" ]] && continue
    echo ""
    echo "### ${ids[$i]} — ${meanings[$i]}"
    if [[ -n "${errors[$i]}" ]]; then
      echo "ERROR: ${errors[$i]}"
      continue
    fi
    echo '```'
    run_mysql --table -e "${queries[$i]}" 2>&1 | grep -v 'Using a password'
    echo '```'
  done
fi

if $has_error; then
  exit 1
fi
