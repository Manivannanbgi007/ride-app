#!/bin/sh
# Registers (or updates) the Debezium MySQL connector in Kafka Connect. Safe to run again: PUT is idempotent.
# Runs in the one-shot "cdc-register" container; the password comes from the environment, never from the file.
set -eu

CONNECT_URL="${CONNECT_URL:-http://kafka-connect:8083}"
NAME="fd-mysql-cdc"

echo "Waiting for Kafka Connect at ${CONNECT_URL}"
i=0
until curl -fsS "${CONNECT_URL}/connectors" > /dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then echo "Kafka Connect did not become ready" >&2; exit 1; fi
  sleep 3
done

# Substitute the only placeholder (keeps the secret out of the repository)
CONFIG=$(sed "s|\${DEBEZIUM_DB_PASSWORD}|${DEBEZIUM_DB_PASSWORD}|" /config/mysql-connector.json)

curl -fsS -X PUT -H "Content-Type: application/json" --data "${CONFIG}" \
  "${CONNECT_URL}/connectors/${NAME}/config" > /dev/null
echo "Connector ${NAME} registered"
