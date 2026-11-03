#!/bin/bash
# Runs once, when the MySQL data volume is first created.
# Creates one database and one user per microservice.
# Each user can only access its own database (database-per-service rule).
#
# Note: the MySQL image may "source" this file instead of executing it,
# so keep the same shell options the entrypoint uses (no "set -u").
set -eo pipefail

create_service_db() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  if [ -z "${db_password}" ]; then
    echo "ERROR: password for ${db_user} is empty. Check your .env file." >&2
    exit 1
  fi

  echo "Creating database ${db_name} with user ${db_user}"
  MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<EOSQL
CREATE DATABASE IF NOT EXISTS \`${db_name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${db_user}'@'%' IDENTIFIED BY '${db_password}';
GRANT ALL PRIVILEGES ON \`${db_name}\`.* TO '${db_user}'@'%';
EOSQL
}

create_service_db user_db         user_svc         "${USER_DB_PASSWORD}"
create_service_db restaurant_db   restaurant_svc   "${RESTAURANT_DB_PASSWORD}"
create_service_db catalog_db      catalog_svc      "${CATALOG_DB_PASSWORD}"
create_service_db order_db        order_svc        "${ORDER_DB_PASSWORD}"
create_service_db payment_db      payment_svc      "${PAYMENT_DB_PASSWORD}"
create_service_db notification_db notification_svc "${NOTIFICATION_DB_PASSWORD}"
create_service_db delivery_db     delivery_svc     "${DELIVERY_DB_PASSWORD}"
create_service_db delivery_partner_db delivery_partner_svc "${DELIVERY_PARTNER_DB_PASSWORD}"
create_service_db promotion_db    promotion_svc    "${PROMOTION_DB_PASSWORD}"
create_service_db chat_db          chat_svc         "${CHAT_DB_PASSWORD}"
create_service_db review_db        review_svc       "${REVIEW_DB_PASSWORD}"
create_service_db admin_db        admin_svc        "${ADMIN_DB_PASSWORD}"

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot -e "FLUSH PRIVILEGES;"
echo "All service databases created."
