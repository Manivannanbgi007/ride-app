# Decision: keep the transactional outbox, do not adopt Debezium CDC (yet)

Status: decided, 2026-10-09. Update: Debezium was then added in **shadow mode** (profile `cdc`, see
[infrastructure/debezium](../infrastructure/debezium/README.md)); the outbox remains the live path. Revisit when one of the triggers at the bottom happens.

## Question

Could Debezium CDC (MySQL binlog -> Kafka -> Elasticsearch) replace the outbox tables, so the application
no longer maintains tables for Elasticsearch synchronization?

## How data reaches Elasticsearch today

```text
restaurant-service  outbox_events -> OutboxRelay -> RestaurantChanged ->┐
catalog-service     outbox_events -> OutboxRelay -> CategoryChanged  ->┼-> Kafka -> search-service indexer -> Elasticsearch
catalog-service     outbox_events -> OutboxRelay -> MenuItemChanged  ->┘     (restaurant | food | category)
```

The event is written in the same MySQL transaction as the change, so a failed search sync can never fail
or roll back a business transaction. The indexer ignores older versions, ignores duplicates, retries on
Elasticsearch 429/5xx, and sends events it cannot apply to `<topic>.DLT`. An admin can replay the current
state (`events/republish`), and `/api/v1/admin/search/consistency` detects and repairs drift.

## Which outbox workflows exist, and what depends on them

| Outbox | Events | Consumers | Can CDC replace it? |
|---|---|---|---|
| restaurant-service | `RestaurantChanged` | catalog, delivery, notification **and** search | No: business event for four services |
| restaurant-service, catalog-service | `AdminActionAudited` | admin-service | No: audit trail |
| catalog-service | `MenuItemChanged`, `CategoryChanged` | search-service only | Technically yes (the only candidates) |
| user, order, payment, delivery, promotion, notification | business events | other services | No: nothing to do with Elasticsearch |

## Why not

1. **Nothing can be removed.** catalog-service's `outbox_events` table, `OutboxWriter` and `OutboxRelay`
   are still needed for `AdminActionAudited`. CDC would remove two publisher methods, not a table.
2. **Search documents are not table rows.** A food document carries the restaurant's name, city and status
   (restaurant_db) and the category name (catalog_db). Debezium and the Elasticsearch sink cannot join
   across databases, so search-service would still need custom code to consume change topics and denormalize.
3. **Coupling to other services' tables.** That code would read column names and enums of tables owned by
   other services. Today the versioned Avro events hide them.
4. **Replay and repair are event based.** The audited republish and the consistency repair would need a
   different mechanism (a new connector snapshot).
5. **More to run.** Kafka Connect (distributed, its own three internal topics), a connector, a replication
   user, schema history, and connector alerts.

What CDC would give: no write-side code for these two events, and capture of changes made outside the
application (manual SQL). The version guard (`version` column) and delete handling (`op=d`) would work.

## Already in place

MySQL 8.4 runs with binary logging on, `ROW` format and full row images by default, so no MySQL change is
needed if CDC is adopted later; only a dedicated user with `REPLICATION SLAVE`, `REPLICATION CLIENT` and
`SELECT` on the captured tables.

## Triggers to revisit

- Data is changed by something other than the services (imports, manual SQL) and must reach search.
- A new search-only entity appears in a service whose outbox would otherwise exist only for search.
- The outbox relay becomes a measured bottleneck (see `search-service` metrics and relay lag).
- The audit events move elsewhere, so catalog's outbox exists only for search.

If revisited: run Kafka Connect as its own ECS service (not inside application tasks), use the Debezium
outbox event router instead of table-per-entity capture so the Avro contracts and the denormalizing indexer
stay unchanged, start in shadow mode into a separate index and compare, and keep the existing rollback:
stop the connector, the outbox keeps working.
