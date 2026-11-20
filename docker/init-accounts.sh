#!/bin/bash
set -eo pipefail

hm_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g")"
}

hm_service_account() {
  local user="$1" password="$2" database="$3"
  cat <<SQL
CREATE USER IF NOT EXISTS '$user'@'%' IDENTIFIED BY $(hm_quote "$password");
ALTER USER '$user'@'%' IDENTIFIED BY $(hm_quote "$password");
GRANT ALL PRIVILEGES ON \`$database\`.* TO '$user'@'%';
SQL
}

hm_monitor_account() {
  local user="$1" password="$2" database
  cat <<SQL
CREATE USER IF NOT EXISTS '$user'@'%' IDENTIFIED BY $(hm_quote "$password");
ALTER USER '$user'@'%' IDENTIFIED BY $(hm_quote "$password");
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON \`consistency_db\`.* TO '$user'@'%';
SQL
  for database in order_db payment_db product_db user_db notification_db; do
    echo "GRANT SELECT ON \`$database\`.* TO '$user'@'%';"
  done
}

hm_accounts_sql() {
  hm_service_account user_app "${USER_DB_PASSWORD:?USER_DB_PASSWORD를 설정하세요}" user_db
  hm_service_account product_app "${PRODUCT_DB_PASSWORD:?PRODUCT_DB_PASSWORD를 설정하세요}" product_db
  hm_service_account order_app "${ORDER_DB_PASSWORD:?ORDER_DB_PASSWORD를 설정하세요}" order_db
  hm_service_account payment_app "${PAYMENT_DB_PASSWORD:?PAYMENT_DB_PASSWORD를 설정하세요}" payment_db
  hm_service_account notification_app "${NOTIFICATION_DB_PASSWORD:?NOTIFICATION_DB_PASSWORD를 설정하세요}" notification_db
  hm_service_account settlement_app "${SETTLEMENT_DB_PASSWORD:?SETTLEMENT_DB_PASSWORD를 설정하세요}" settlement_db
  hm_monitor_account consistency_monitor "${CONSISTENCY_MONITOR_DB_PASSWORD:?CONSISTENCY_MONITOR_DB_PASSWORD를 설정하세요}"
}

hm_accounts_sql > /dev/null
hm_accounts_sql | MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD를 설정하세요}" mysql --protocol=socket -uroot
