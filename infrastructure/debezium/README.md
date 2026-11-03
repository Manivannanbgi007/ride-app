# Debezium CDC (profile `cdc`)

Debezium reads MySQL's binary log and publishes every insert, update and delete of the captured tables to Kafka.
**Shadow mode:** nothing consumes these topics yet. The transactional outbox, every service and every API are
unchanged, so this can run next to production traffic without any effect on it. Why the outbox is kept, and
when to revisit: [docs/decision-debezium-cdc.md](../../docs/decision-debezium-cdc.md).

```bash
docker compose --profile cdc up -d        # Kafka Connect + registers the connector
```

First time on an **existing** MySQL volume, create the read-only user once (a new volume does it by itself):

```bash
docker compose exec -T mysql bash /docker-entrypoint-initdb.d/02-create-debezium-user.sh
```

(needs `DEBEZIUM_DB_PASSWORD` in `.env`; see `.env.example`).

## What is captured

| Table | Topic |
|---|---|
| `restaurant_db.restaurants` | `fdcdc.restaurant_db.restaurants` |
| `catalog_db.categories` | `fdcdc.catalog_db.categories` |
| `catalog_db.menu_items` | `fdcdc.catalog_db.menu_items` |

Key `{"id": 15}`, value `{"before": ..., "after": ..., "source": {...}, "op": "c|u|d|r", "ts_ms": ...}`
(`r` = initial snapshot row). A delete is followed by a tombstone (null value) so compacted topics forget the row.
JSON without embedded schemas, readable in Kafka UI (http://localhost:8090, Topics and Kafka Connect tabs).
Money columns are strings (`decimal.handling.mode=string`). Settings: [mysql-connector.json](mysql-connector.json).

## How it behaves (verified locally)

| Situation | Result |
|---|---|
| First start | Snapshot of every row (198 restaurants, 8 categories, 243 menu items matched MySQL exactly), then follows the log |
| Insert, update, delete | One event each, with the old and new row; delete also sends a tombstone |
| Kafka Connect stopped, MySQL changed, started again | Resumes from the stored offset: only the missed changes arrive, no new snapshot |
| Kafka down | The connector retries without limit (`errors.max.retries=-1`); nothing is lost while the binlog is kept |
| Elasticsearch down | No effect: nothing reads these topics yet |
| A business transaction | Never affected: Debezium only reads the log after the commit |

**Ordering and duplicates:** one key always goes to the same partition, so changes of one row stay in order.
Delivery is at least once: a consumer must tolerate repeats. Every row has a `version` column that a consumer can
use to ignore anything older than what it has (the same rule search-service's indexer uses today).

## Production checklist

- **MySQL:** `log_bin=ON`, `binlog_format=ROW`, `binlog_row_image=FULL` (set in `docker-compose.yml`; on RDS/Aurora
  use a parameter group and keep `binlog_expire_logs_seconds` or `binlog retention hours` above the longest
  expected connector outage; here 7 days). A connector that is off for longer than the retention must re-snapshot.
- **Least privilege:** user `debezium` reads the binlog and has `SELECT` on the captured databases only
  ([02-create-debezium-user.sh](../mysql/init/02-create-debezium-user.sh)). Use a secret manager for its password;
  use a Kafka Connect config provider instead of putting it in the connector JSON.
- **Several ECS Fargate tasks:** run Kafka Connect as **its own service**, not inside application tasks. Workers
  with the same `GROUP_ID` form one cluster; a MySQL connector has exactly one task (one binlog reader), so extra
  workers give failover, not speed. `database.server.id` must be unique among everything that reads this MySQL
  (replicas included). Use replication factor 3 for the three `fd-connect-*` topics and for `fdcdc.schema-history`.
- **Offsets and schema history** live in Kafka (`fd-connect-offsets`, `fdcdc.schema-history`): never delete them
  unless you want a new snapshot.
- **Security:** TLS and SASL on Kafka and Connect's REST port (only reachable from inside the VPC), TLS to MySQL
  (`database.ssl.mode=verify_identity`), a secured Elasticsearch if a sink is added.
- **Serialization:** JSON suits local use. For production switch the converters to Avro with the Schema Registry
  (add the Avro converter to the Connect image) so schema changes are checked like the other events.

## Monitoring

| What | Where |
|---|---|
| Connector and task state (`RUNNING`/`FAILED`) | `curl localhost:8093/connectors/fd-mysql-cdc/status`, or Kafka UI, Kafka Connect tab |
| Why it failed | `status` shows the stack trace in `trace`; `docker logs fd-kafka-connect` |
| Lag behind MySQL | `heartbeat.interval.ms=10000` writes to `__debezium-heartbeat.fdcdc`; the age of its newest message is the lag |
| Restart after a failure | `curl -X POST localhost:8093/connectors/fd-mysql-cdc/restart?includeTasks=true` |
| Consumer lag (once something reads the topics) | Kafka UI, Consumers tab |

For production, alert on: connector or task not `RUNNING`, heartbeat older than a few minutes, and the MySQL
binlog disk usage (a stopped connector pins nothing in MySQL, but long outages need the retention above).

## Rebuilding from scratch (re-snapshot)

```bash
curl -X DELETE localhost:8093/connectors/fd-mysql-cdc
# delete the topic fdcdc.schema-history and the connector's keys in fd-connect-offsets only if the history is broken
docker compose --profile cdc up -d --force-recreate cdc-register
```

Changing the captured tables later: edit `mysql-connector.json`, then run `docker compose --profile cdc up -d
--force-recreate cdc-register` (the registration is a safe `PUT`). Adding a table needs an
[incremental snapshot](https://debezium.io/documentation/reference/stable/connectors/mysql.html#mysql-incremental-snapshots)
so its existing rows are sent too.

## Rollback

`docker compose --profile cdc down` (or just stop `kafka-connect`). The application never depended on it.
The MySQL user and the extra topics are harmless; remove them with `DROP USER 'debezium'@'%'` and by deleting
the `fdcdc.*` and `fd-connect-*` topics.

## Next step (not done): feeding Elasticsearch

This phase only gets the changes into Kafka. Search-service still indexes from the outbox events.
To index from CDC, search-service would read these topics, check `version`, and apply the same denormalization
as today (a food document needs its restaurant's and category's current data), writing to a **separate**
index first so it can be compared with the live one before any switch.
