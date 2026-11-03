# Decision: delivery partners get their own service; dispatch stays in delivery-service

Status: decided and built, 2026-10-12 (event-contracts 1.7.0).

## Question

Delivery partners lived inside delivery-service (Phase 10). Should they be a separate microservice, and where is
the line between it and delivery-service?

## What delivery-service did with partners

| Kind of data | Fields | Changed by |
|---|---|---|
| Who the partner is | name, phone, vehicle, city; created from `UserRegistered` | partner, admin (suspend, reactivate) |
| What the partner is doing | `OFFLINE` / `AVAILABLE` / `BUSY`, queue position, current delivery, deliveries done | partner (online/offline), assignment job |

The assignment job locks the waiting delivery and the chosen partner row (`FOR UPDATE SKIP LOCKED`) and makes the
partner `BUSY` in the same transaction as the delivery change. That one transaction is what guarantees that no
partner gets two deliveries and no delivery two partners.

## Options

| Option | Partner service owns | Assignment | Verdict |
|---|---|---|---|
| A. Profile + account (chosen) | registration, profile, suspension, audit | unchanged: one local transaction in delivery-service | Clear ownership, nothing distributed in the hot path |
| B. Also availability | A + online/offline/busy | every assignment reserves the partner over HTTP, with compensation when the delivery side fails | Two writers of one status, assignment stops when the partner service is down |
| C. Additive only | only new features (documents, earnings) | unchanged | Partner data stays split across two services with no owner |

## What was built (option A)

```text
 user-service ──UserRegistered──> delivery-partner-service ──DeliveryPartnerChanged──────> delivery-service
                                   (owner: profile, account)  <─DeliveryPartnerStatusChanged─ (owner: status,
                                                                                               assignment)
```

- **delivery-partner-service** (port 8099, `delivery_partner_db`): registration, `GET/PUT /delivery/partners/me`,
  admin search, details, activate, deactivate (audited) and audit logs. Keeps a copy of the work status, newest
  version wins, so its responses have the same shape as before.
- **delivery-service**: its `delivery_partners` table is the dispatch list: profile and suspension are a copy
  (newest version wins); status, queue and current delivery are its own. Keeps `/me/availability`,
  `/admin/delivery-partners/availability`, `/stats`, `/{id}/deliveries` and all delivery APIs.
- **Same URLs:** the API Gateway sends each path to its owner; the web application, E2E tests and AI tools did not
  change.

## Consistency rules

- A copy can be a moment behind. Where that matters, the reader asks the owner: delivery-service asks
  delivery-partner-service when its copy lacks a vehicle or city, or still says suspended, as the partner goes
  online (the same "ask the owner when the copy is missing" rule it uses for orders and restaurants).
- Every event carries the owner's row version; consumers ignore older or repeated events.
- Suspension: delivery-partner-service refuses while its copy says `BUSY` (409, as before). An offer made while
  the suspension is on its way is taken back to the queue; an accepted delivery is finished, then the partner is
  `SUSPENDED` instead of `AVAILABLE`.
- Existing partners are copied once (`POST /api/v1/internal/delivery-partners/import`, safe to repeat).

## Triggers to revisit

- Partner features that are not about dispatch arrive (documents and verification, earnings, cash-in-hand,
  ratings of partners): they belong in delivery-partner-service.
- Live locations or shifts: dispatch data, so delivery-service (or a separate dispatch service), not the partner
  service.
- JWT (Phase 18): the role checks of both services move to Spring Security; nothing in the split changes.
