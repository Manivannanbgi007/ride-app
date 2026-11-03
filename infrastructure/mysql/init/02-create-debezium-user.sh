#!/bin/bash
# Creates the read-only MySQL user that Debezium (profile "cdc") reads the binary log with.
#
# Runs once on a NEW data volume. On an existing volume run it by hand:
#   docker compose exec -T mysql bash /docker-entrypoint-initdb.d/02-create-debezium-user.sh
#
# Least privilege: it can read the tables it captures, and read the binary log. It cannot write to any table.
#   REPLICATION SLAVE / REPLICATION CLIENT  read the binary log, SHOW MASTER STATUS
#   SELECT                                  the initial snapshot
#   RELOAD / FLUSH_TABLES                   short global read lock at the start of the snapshot
#   SHOW DATABASES                          list the databases to capture
set -eo pipefail

if [ -z "${DEBEZIUM_DB_PASSWORD}" ]; then
  echo "DEBEZIUM_DB_PASSWORD is empty: the Debezium user is not created (only needed for profile 'cdc')."
  exit 0
fi

echo "Creating the Debezium user"
MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot <<EOSQL
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY '${DEBEZIUM_DB_PASSWORD}';
ALTER USER 'debezium'@'%' IDENTIFIED BY '${DEBEZIUM_DB_PASSWORD}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT, RELOAD, FLUSH_TABLES, SHOW DATABASES ON *.* TO 'debezium'@'%';
GRANT SELECT ON \`restaurant_db\`.* TO 'debezium'@'%';
GRANT SELECT ON \`catalog_db\`.* TO 'debezium'@'%';
FLUSH PRIVILEGES;
EOSQL
echo "Debezium user ready."
