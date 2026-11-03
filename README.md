# Food Delivery Platform (Microservices)

A production-style food delivery platform, built step by step as a learning project for
microservices, event-driven architecture (Kafka), the Saga and Outbox patterns, Elasticsearch,
Redis, resilience, observability and integration testing.

The full requirements are in the project requirements document (`app-requirement.txt`).
The system is built **one phase at a time**.

## Architecture (target)

```text
                    React Web Application (Customer UI + Admin UI)
                                      |
                                      v
                                 API Gateway
                                      |
     +-----------+-----------+--------+--------+-----------+
     v           v           v                 v           v
 Auth/User   Restaurant   Catalog            Order      Payment
  Service     Service     Service           Service     Service
     |           |           |                 |           |
     +-----------+-----------+-------+---------+-----------+
                                     |
                                   Kafka
                                     |
                +--------------------+--------------------+
                v                    v                    v
          Notification           Delivery <-------> Delivery Partner     Search -----> Elasticsearch
            Service               Service   events      Service          Service
                               (deliveries,         (partners: profile,
                                assignment)          suspension)

Each service owns its own MySQL database. Shared infrastructure: Kafka, Schema Registry,
Redis, Prometheus, Grafana, Loki, Tempo.
```

## Technology versions

| Technology    | Version                                    |
|---------------|--------------------------------------------|
| Java          | 21 (LTS)                                   |
| Spring Boot   | 4.1.1                                      |
| Spring Cloud  | 2025.1.3 (Gateway 5.0.x, WebFlux server)   |
| MySQL         | 8.4 (LTS)                                  |
| Kafka         | Apache Kafka 4.2 (Confluent Platform 8.2.4), KRaft mode, no ZooKeeper |
| Schema Registry / Avro | Confluent Schema Registry 8.2.4 / Apache Avro 1.12.2 |
| Kafka UI      | kafbat/kafka-ui 1.5.0                      |
| Mail (dev)    | Mailpit 1.31 (catches every email; web UI) |
| Search        | Elasticsearch 9.4.5 (Spring Data Elasticsearch 6.1, Java client 9.4) |
| Data UIs (dev) | Kibana 9.4.5 (Elasticsearch), phpMyAdmin 5.2.3 (MySQL) |
| Cache / rate limits | Redis 8.10 (Spring Data Redis 4.1, Lettuce)         |
| Resilience    | Resilience4j 2.4.0 (services: resilience4j-spring-boot4; gateway: Spring Cloud CircuitBreaker 5.0) |
| Observability | Micrometer + OpenTelemetry (Spring Boot 4.1), Prometheus 3.15, Grafana 13.2, Tempo 2.10, Jaeger 2.21, Loki 3.7, Grafana Alloy 1.20 |
| Code quality  | Checkstyle 14.3, SpotBugs 4.10, JaCoCo 0.8.15, ArchUnit 1.5, SonarQube Community Build 26.9 (PostgreSQL 17) |
| Frontend      | React 19.3, TypeScript 6.0, Vite 8.3, React Router 8.4, Redux Toolkit 2.13, TanStack Query 5, Axios 1.20, Tailwind CSS 4.3, Recharts 3.10; ESLint 10, Prettier 3.9, Vitest 5 |
| Build         | Maven (each service has its own wrapper)   |
| Containers    | Docker Compose                             |

## Repository layout

Each microservice is an **independent Maven project** in its own folder.

```text
app/
├── docker-compose.yml          infrastructure + services (services use profile "apps")
├── .env.example                copy to .env (local passwords, git-ignored)
├── infrastructure/
│   ├── mysql/init/             creates one database + one user per service
│   ├── kibana/                 creates Kibana's data views for the search indexes
│   └── observability/          Prometheus, Tempo, Jaeger, Alloy, Grafana configuration and dashboards
├── quality/                    Checkstyle and SpotBugs rules of every service, SonarQube scan script
├── e2e-tests/                  end-to-end tests of the running platform (business flows through the API Gateway)
├── front-app/                  Front-App: the React web application (port 5180 in development)
├── lombok.config               Lombok settings shared by every service
├── event-contracts/            shared library: Avro schemas of all Kafka events + topic names
├── api-gateway/                Spring Cloud Gateway (port 8080)
├── user-service/               Auth/User Service (port 8081, user_db)
├── restaurant-service/         Restaurant Service (port 8082, restaurant_db)
├── catalog-service/            Catalog Service (port 8083, catalog_db)
├── order-service/              Order Service (port 8084, order_db)
├── payment-service/            Payment Service (port 8085, payment_db)
├── notification-service/       Notification Service (port 8086, notification_db)
├── delivery-service/           Delivery Service (port 8087, delivery_db)
├── delivery-partner-service/   Delivery Partner Service: partners' profile and suspension (port 8099, delivery_partner_db)
├── search-service/             Search Service (port 8088, Elasticsearch; no MySQL)
├── promotion-service/          Promotion Service: coupons (port 8089, promotion_db)
└── admin-service/              Admin Service: dashboard, reports, platform-wide audit log (port 8092, admin_db)
```

Inside a service (example: `user-service`):

```text
com.fooddelivery.userservice
├── controller      thin REST controllers (no business logic)
├── service         business rules and transactions
├── repository      Spring Data JPA repositories
├── entity          JPA entities and enums
├── dto/request     validated request records
├── dto/response    response records (entities are never returned)
├── mapper          entity <-> DTO conversion
├── exception       business exceptions + GlobalExceptionHandler (ProblemDetail)
├── validation      reusable validation annotations and business validators
├── client          calls to other services (RestClient, timeouts, request id propagation)
├── kafka/producer  builds events and writes them to the transactional outbox; OutboxRelay publishes them
├── kafka/consumer  @KafkaListener classes (thin: they call a service)
├── web             request id, caller identity, role access checks
└── config          Spring configuration, properties, OpenAPI
src/main/resources/db/migration    Flyway SQL migrations (V1__..., V2__...)
```

## Ports

| Component            | Port | Database          |
|----------------------|------|-------------------|
| Front-App (React, dev server) | 5180: customer http://localhost:5180/customer, restaurant owner http://localhost:5180/owner, delivery partner http://localhost:5180/partner, admin http://localhost:5180/admin/dashboard | none |
| API Gateway          | 8080 | none              |
| User Service         | 8081 | user_db           |
| Restaurant Service   | 8082 | restaurant_db     |
| Catalog Service      | 8083 | catalog_db        |
| Order Service        | 8084 | order_db          |
| Payment Service      | 8085 | payment_db        |
| Notification Service | 8086 | notification_db   |
| Delivery Service     | 8087 | delivery_db       |
| Delivery Partner Service | 8099 | delivery_partner_db |
| Search Service       | 8088 | Elasticsearch     |
| Promotion Service    | 8089 | promotion_db      |
| Review Service       | 8094 | review_db         |
| Chat Service         | 8096 | chat_db           |
| AI assistant: ai-mcp-client / ai-mcp-server | 8097 / 8098 | Redis only (see docs/ai-assistant-architecture.md) |
| Ollama (profile `ai`) | 127.0.0.1:11434 (loopback only) | model volume `ollama-data` |
| Admin Service        | 8092 | admin_db          |
| MySQL (Docker)       | 3330 on host (3306 inside Docker) |   |
| phpMyAdmin (MySQL UI) | 8095: http://localhost:8095 (log in as root or as a service's database user) |   |
| Kafka (Docker)       | 9092 on host (`kafka:29092` inside Docker) |   |
| Schema Registry      | 8091 on host (8081 inside Docker) |   |
| Kafka UI             | 8090 on host: http://localhost:8090 |   |
| Kafka Connect (Debezium) | 8093 on host (profile `cdc`; see [infrastructure/debezium](infrastructure/debezium/README.md)) |   |
| Mailpit (dev mail)   | 8025 web UI: http://localhost:8025, 1025 SMTP |   |
| Elasticsearch        | 9200: http://localhost:9200 (indexes restaurant, food, category) |   |
| Kibana (Elasticsearch UI) | 5601: http://localhost:5601 (Discover, Dev Tools; no login in development) |   |
| Redis                | 6379 (cache and rate limits: `docker exec -it fd-redis redis-cli`) |   |
| Grafana              | 3000: http://localhost:3000 (dashboards, traces, logs; admin / `GRAFANA_ADMIN_PASSWORD`) |   |
| Prometheus           | 9090: http://localhost:9090 |   |
| Tempo                | 3200 (queries) |   |
| Jaeger               | 16686: http://localhost:16686 (traces: slow requests, request flow, latency per operation) |   |
| Loki / Alloy         | 3100 / 12345 (Alloy UI: what it collects), 4317/4318 (OTLP: traces of services run from the IDE) |   |
| SonarQube            | 9000: http://localhost:9000 (profile `quality`; admin / `SONARQUBE_ADMIN_PASSWORD`) |   |

## Getting started

Prerequisites: JDK 21, Docker Desktop. Maven is optional because every service has `mvnw`.

```powershell
# 1. Local configuration (first time only)
copy .env.example .env

# 2. Start infrastructure (MySQL, Kafka, Schema Registry, Kafka UI, Mailpit, Elasticsearch, Redis)
docker compose up -d

# 3. Install the shared event library (first time, and after changing a schema)
cd event-contracts
.\mvnw.cmd install

# 4. Run a service from source (each in its own terminal)
cd user-service
.\mvnw.cmd spring-boot:run

cd api-gateway
.\mvnw.cmd spring-boot:run

# 5. Check it
curl http://localhost:8080/actuator/health
```

When run from source, services read database passwords from the project's `.env` file.
In Docker they get them from `docker-compose.yml`.

Each service contains a `.http` file (for example `user-service/user-service.http`) with
ready-made requests for the VS Code REST Client extension. Swagger UI is available on each
service: http://localhost:8081/swagger-ui.html up to http://localhost:8089/swagger-ui.html,
http://localhost:8092/swagger-ui.html for admin-service and http://localhost:8099/swagger-ui.html for
delivery-partner-service.
Run the `.http` files in order: user-service, restaurant-service, catalog-service, promotion-service,
order-service, payment-service, notification-service, delivery-service, delivery-partner-service, search-service,
admin-service (each builds on the data of the previous one).

With the `dev` profile every service logs one line per request, prefixed with the request id,
so a single request can be followed through the gateway and all services it touches.

### First platform admin

Nobody can register as `PLATFORM_ADMIN`. The user-service creates the first admin at startup
from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` in `.env`, if it does not exist yet.
Log in with those credentials (`POST /api/v1/auth/login`) to get the admin's user id.

### Run the whole platform

The services run on this machine; Docker runs only the infrastructure (the services' blocks in
`docker-compose.yml` are commented out). One script starts all of it:

```powershell
.\start-local.ps1                 # infrastructure, every service and Front-App, a Windows Terminal tab each
.\start-local.ps1 -Build          # the same after code changes (rebuilds every jar)
.\start-local.ps1 -Detached       # the services only, without windows (logs in logs\<service>.log), e.g. for e2e
.\start-local.ps1 -Stop           # stop what -Detached started
.\start-local.ps1 -Only user-service,api-gateway -NoAi   # fewer services when memory is short
```

**Next to another project** that uses the same ports (another Kafka on 9092, Redis on 6379, a gateway on 8080):
the script checks every port first and, when another program holds one, names the port and the program and starts
nothing. `.\start-local.ps1 -PortOffset 10000` (or `FD_PORT_OFFSET=10000` in `.env`) then moves every port of the
platform, so both run side by side: gateway http://localhost:18080, web app http://localhost:15180, Kafka 19092,
Redis 16379, Mailpit http://localhost:18025, each service 1808x. The script tells every service where the others
and the infrastructure are; tracing is off, so no trace goes to another project's collector on 4318. Single
infrastructure ports can also be set in `.env` (`KAFKA_HOST_PORT` and so on, see `.env.example`).

### Build and test a service

```powershell
cd user-service
.\mvnw.cmd verify
```

Integration tests use Testcontainers, so Docker must be running.
If tests behave strangely right after editing code in the IDE, run `.\mvnw.cmd clean verify`:
the IDE's background compiler writes to the same `target/` folder as Maven.

`verify` also runs the code quality checks (architecture tests, coverage minimum, Checkstyle, SpotBugs), and a
finding fails the build; see [Code quality](#code-quality-phase-15a).

### Reset the databases

The MySQL init script runs only when the data volume is first created.
To recreate all databases (this deletes all data):

```powershell
docker compose down -v
docker compose up -d
```

A database added in a later phase (for example `promotion_db` in Phase 13) is not created in an existing
volume. To add it without deleting anything, run the init script again (it skips what already exists):

```powershell
docker compose up -d mysql   # recreates the container if its settings changed (new password variable); data stays
docker compose exec mysql bash /docker-entrypoint-initdb.d/01-create-databases.sh
```

## API conventions

- All APIs are versioned: `/api/v1/...`
- Admin APIs: `/api/v1/admin/...`
- Every response carries an `X-Request-Id` header, which is forwarded to every service.
  Send your own `X-Request-Id` to trace a request end to end.
- Errors use `ProblemDetail` (RFC 9457).
- Lists are paginated on the server: `?page=0&size=20&sort=createdAt,desc` (max size 100).
- Security: Spring Security + JWT (Phase 18), see [Security (JWT)](#security-jwt). Every call sends the sign-in's
  access token (`Authorization: Bearer`); the gateway and then each service check it, and it is the only identity
  they accept.
  Admin APIs require a `PLATFORM_ADMIN` token; internal APIs the services' shared `X-Service-Token`.
- Every admin change is written to an immutable audit log in the same database transaction.
- The gateway adds `X-Forwarded-For`, so services record the real client IP. It trusts private
  networks by default; in production set `GATEWAY_TRUSTED_PROXIES` to your load balancer's addresses.

## User Service API (Phase 2)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/register` | Sign up (CUSTOMER, RESTAURANT_ADMIN or DELIVERY_PARTNER) |
| POST | `/api/v1/auth/login` | Sign in: `{email, password, role?}` → userId, role, `accessToken` (JWT, `expiresIn` seconds) and the area's HttpOnly refresh cookie. With `role`, another kind of account gets 403 `code: WRONG_ACCOUNT_TYPE` and no cookie |
| POST | `/api/v1/auth/refresh` | `{role}`: a new access token (and a new refresh cookie) from that area's cookie; 401 = sign in again, 409 = another tab refreshed a moment ago (retry) |
| POST | `/api/v1/auth/logout` | `{role}`: ends that area's session and removes its cookie (204) |
| GET | `/.well-known/jwks.json` | Public key that checks access tokens (service to service; not routed by the gateway) |
| GET / PUT | `/api/v1/users/me` | View / update my profile |
| PUT | `/api/v1/users/me/password` | Change my password |
| GET / POST | `/api/v1/users/me/addresses` | List / add addresses |
| PUT / DELETE | `/api/v1/users/me/addresses/{id}` | Update / delete an address |
| PUT | `/api/v1/users/me/addresses/{id}/default` | Make an address the default |
| GET | `/api/v1/admin/users` | Search users (`search`, `role`, `status`, paging, sorting) |
| GET | `/api/v1/admin/users/{id}` | User details (created, last login, address count) |
| PUT | `/api/v1/admin/users/{id}/activate` | Activate (reason required, audited) |
| PUT | `/api/v1/admin/users/{id}/deactivate` | Deactivate (reason required, audited) |
| PUT | `/api/v1/admin/users/{id}/role` | Change role (reason required, audited) |
| GET | `/api/v1/admin/users/{id}/addresses` | The user's addresses for admins, same shape and order as `/users/me/addresses` (default first, at most 10); also for a deactivated user; 404 for an unknown user |
| GET | `/api/v1/admin/users/{id}/audit-logs` | Audit history of a user |

Not yet included: the user's order summary for admins (added with the Order Service).

## Security (JWT)

Phase 18 replaced the `X-User-Id` / `X-User-Role` headers, which any client could set, with signed tokens. It was done
in six steps that each left the platform working: user-service issues the tokens (1); the web app and the end-to-end
tests send them (2); the AI assistant takes identity only from the token (3); the gateway checks every token (4);
every service checks it again, and the internal APIs need the services' shared secret (5); the old headers are gone
(6). **The access token is the only identity the platform accepts.**

```text
browser ──login / refresh / logout──> user-service: checks the password, signs an access token (RS256, 15 min),
   │                                   keeps the session (refresh token, 7 days, rotated at every refresh)
   │  memory: access token per area      GET /.well-known/jwks.json: the public key for everyone else
   │  cookie: fd_refresh_<area>, HttpOnly, SameSite=Strict, Path=/api/v1/auth
   └──Authorization: Bearer <access token>──> gateway (checks it) ──> each service (checks it again)
                                         service ──X-Service-Token──> another service's /api/v1/internal/**
```

- **Access token:** a JWT signed with user-service's RSA private key. Claims: `iss`, `aud`, `sub` (the user id),
  `role`, `iat`, `exp`, `jti`; no name, email or phone. Only user-service can sign; everyone else checks the signature
  with the public key from `/.well-known/jwks.json`.
- **Refresh token:** 256 random bits in an HttpOnly cookie, one per area (`fd_refresh_customer`, `fd_refresh_owner`,
  `fd_refresh_partner`, `fd_refresh_admin`), so the four areas can be signed in at once in one browser. The database
  stores only its SHA-256 hash (table `refresh_tokens`). Every refresh replaces it (rotation); an old one used again
  more than 10 s later means a stolen copy, so that whole session ends. The cookie has no expiry date (it goes when the
  browser closes); the server ends idle sessions after 7 days.
- **Sessions end** at sign-out, at a new sign-in to the same area, and for all areas when the password or role changes
  or an admin deactivates the account. An access token already handed out stays valid until it expires (15 min).
- **CSRF:** the cookie is SameSite=Strict and reaches only `/api/v1/auth`, the endpoints take JSON only, and user-service
  refuses an `Origin` that is not in `CORS_ALLOWED_ORIGINS`. Use `http://localhost:5180`, not `127.0.0.1`: for the
  browser `127.0.0.1` → `localhost` is another site and the cookie is not sent.
- **Signing key:** `JWT_PRIVATE_KEY` in `.env` (base64 PKCS#8 RSA, 2048+ bits, one line). Generate it once in Git Bash:
  `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -outform DER | base64 -w0`. Without it, the `dev` profile
  makes a temporary key at every start (a warning is logged; signed-in users refresh automatically after a restart)
  and every other profile refuses to start. The key id (`kid`) is derived from the key.
- **The web app** (front-app README, "Signing in and who is acting") keeps each area's access token in memory only,
  renews it with the area's cookie shortly before it expires or after a reload (one renewal at a time, also across
  tabs), sends it once more after a 401, and signs the area out when its session has ended. Signing out also ends the
  server session. The end-to-end tests sign every test user in and send its token (`e2e-tests/.../support/Actor.java`).
- **The gateway** (step 4, `api-gateway/.../config/SecurityConfig.java`) refuses a token that is not valid with 401
  (signature, expiry, issuer, audience), even on a public page, and requires `PLATFORM_ADMIN` for `/api/v1/admin/**`
  (403 for another role) and for its actuator endpoints other than health, info and prometheus (its routes name every
  service's address), and never routes `/api/v1/internal/**` (403). It removes any `X-User-Id`, `X-User-Role` or
  `X-Service-Token` a client sends (`IdentityHeadersFilter`) and passes the `Authorization` header on unchanged; it adds
  no identity of its own. Its 401/403 answers are ProblemDetails with the requestId and the CORS headers (CORS is
  answered by Spring Security from `globalcors`). Rate limits count a signed-in user by their id, a visitor by IP. If
  user-service's public key cannot be read the answer is 503.
- **Every service** (step 5, `<service>/.../config/SecurityConfig.java`) is a Spring Security resource server: it checks
  the token again (so a call that skips the gateway on a service's own port gets nothing without a valid token), and its
  rules say which role may use which area (customers their cart and orders, owners `/api/v1/restaurants/mine/**`,
  partners their deliveries, admins `/api/v1/admin/**`); public reads (restaurants, menus, categories, search, offers,
  ratings), sign-in, Razorpay's webhook and the health, info and prometheus endpoints stay open; the other actuator
  endpoints (circuit breakers, retries...) need an administrator; everything else needs a signed-in caller. `CallerInfo`
  (the caller of a controller) comes from the token's `sub` and `role`; who owns which order, restaurant or delivery is
  still checked by the services (404 for someone else's). user-service checks tokens with its own key; the others read
  user-service's public key (`USER_SERVICE_URL`/.well-known/jwks.json). Their 401 and 403 answers are ProblemDetails
  with the requestId and `WWW-Authenticate: Bearer`.
- **Internal APIs** (`/api/v1/internal/**`, between services, never routed by the gateway) need the shared
  `X-Service-Token` (`INTERNAL_SERVICE_TOKEN`, compared in constant time); a user's token is not enough (403).
  Every service adds it to its calls of internal APIs, and only to those (`ServiceTokenInterceptor`). admin-service and
  search-service call other services' admin APIs with the admin's own token. notification-service reads an owner's
  restaurants from restaurant-service's internal API (`GET /api/v1/internal/restaurants?ownerUserId=`).
- **Live updates** (`/ws/v1/live`): a browser cannot set headers on a WebSocket, so the web app sends its token as
  `?access_token=`; notification-service accepts a token in the query on `/ws/**` only. The gateway logs paths, not
  query strings.
- **Settings** (user-service `application.yml`): `app.jwt.*` (issuer, audience, `access-token-ttl`), `app.auth.*`
  (refresh token lifetime and grace, cookie flags; `secure: false` only in the dev profile for plain http). Gateway:
  `allow-credentials: true` in CORS, and refresh/logout have their own limit (`GATEWAY_SESSION_RATE_LIMIT_PER_MINUTE`,
  30 per minute per client) next to the login limit. Every service: `spring.security.oauth2.resourceserver.jwt.*`
  (`USER_SERVICE_URL`, `JWT_ISSUER`, `JWT_AUDIENCE`) and `app.security.internal-service-token`
  (`INTERNAL_SERVICE_TOKEN`: required outside the dev profile, which uses a development value).

## Restaurant Service API (Phase 3)

Restaurant lifecycle (only these transitions are allowed):

```text
 owner submits                 admin review
 -------------> PENDING ---+---> APPROVED ---> ACTIVE <---> INACTIVE
                   ^       |                     |  ^           |
                   |       v                     v  |           v
   owner resubmits +--- REJECTED               SUSPENDED <------+
```

| Who | Method | Path | Purpose |
|---|---|---|---|
| Customer | GET | `/api/v1/restaurants` | Browse ACTIVE restaurants (`search`, `city`, `cuisine`, paging) with `openNow` |
| Customer | GET | `/api/v1/restaurants/{id}` | Restaurant page with opening hours (ACTIVE only) |
| Owner | POST | `/api/v1/restaurants/mine` | Submit a restaurant (needs a FOOD_LICENSE document) |
| Owner | GET | `/api/v1/restaurants/mine` | My restaurants, any status |
| Owner | GET | `/api/v1/restaurants/mine/{id}` | My restaurant incl. review reason and `version` |
| Owner | PUT | `/api/v1/restaurants/mine/{id}` | Update profile, documents and hours (send `version`) |
| Owner | PUT | `/api/v1/restaurants/mine/{id}/operating-hours` | Replace the weekly schedule (send `version`) |
| Owner | PUT | `/api/v1/restaurants/mine/{id}/resubmit` | REJECTED -> PENDING |
| Admin | GET | `/api/v1/admin/restaurants` | Search (`search`, `status`, `city`); `?status=PENDING` = approvals waiting |
| Admin | GET | `/api/v1/admin/restaurants/{id}` | Review details + owner (from user-service) |
| Admin | PUT | `/api/v1/admin/restaurants/{id}/approve` `reject` `activate` `deactivate` `suspend` | Status changes (reason required, audited) |
| Admin | GET | `/api/v1/admin/restaurants/{id}/audit-logs` | Audit history |

Owner APIs require a `RESTAURANT_ADMIN` access token, admin APIs a `PLATFORM_ADMIN` one.

Design notes:

- **Optimistic locking across requests:** owners send the `version` they last read; an outdated
  copy gets 409 instead of silently overwriting someone else's change.
- **Service-to-service call:** the admin review screen gets owner details from user-service's
  internal API (`/api/v1/internal/users/{id}`), which the gateway does not expose. The call has
  timeouts and passes on the `X-Request-Id`. If user-service is down, the screen still loads and
  the owner is marked `UNAVAILABLE` (a circuit breaker is added in the resilience phase).
- Order statistics and revenue for a restaurant come with the Order Service.

## Catalog Service API (Phase 4)

Global food categories (seeded: Pizza, Burgers, Biryani, Chinese, South Indian, North Indian,
Desserts, Beverages) are managed by platform admins. Restaurant menus use these categories.

A menu item has two independent switches:

| Field | Values | Who changes it |
|---|---|---|
| `status` | `ACTIVE` (listed), `DISABLED` (hidden by owner), `BLOCKED` (hidden by admin) | Owner: ACTIVE <-> DISABLED. Admin: anything, incl. BLOCKED (owner cannot undo) |
| `available` | `true` / `false` (sold out today, still shown greyed out) | Owner |

| Who | Method | Path | Purpose |
|---|---|---|---|
| Customer | GET | `/api/v1/categories` | Active categories in display order |
| Customer | GET | `/api/v1/restaurants/{id}/menu` | Menu of an ACTIVE restaurant, grouped by category |
| Owner | GET / POST | `/api/v1/restaurants/mine/{id}/menu-items` | All my items / add an item |
| Owner | GET / PUT | `/api/v1/restaurants/mine/{id}/menu-items/{itemId}` | Read / update an item (send `version`; optional `ingredients` and `nutritionInfo`, up to 1000 chars each, blank = none, missing on PUT clears them; returned on owner and admin item responses only) |
| Owner | PUT | `.../menu-items/{itemId}/price` | Change the price (send `version`) |
| Owner | PUT | `.../menu-items/{itemId}/availability` | Sold out / back in stock |
| Owner | PUT | `.../menu-items/{itemId}/enable` `disable` | Show / hide on the menu |
| Admin | GET / POST | `/api/v1/admin/categories` | List (with item counts) / create |
| Admin | PUT | `/api/v1/admin/categories/{id}` `/activate` `/deactivate` | Update, change status (audited) |
| Admin | PUT | `/api/v1/admin/categories/order` | Reorder (every id once) |
| Admin | DELETE | `/api/v1/admin/categories/{id}` | Delete, only if no item uses it (409 otherwise) |
| Admin | GET | `/api/v1/admin/menu-items` | Search: `restaurantId`, `categoryId`, `status`, `available`, `minPrice`, `maxPrice` |
| Admin | POST | `/api/v1/admin/menu-items` | Add an item to any restaurant (`restaurantId` + the owner's fields; starts ACTIVE, same rules as the owner; audited) |
| Admin | PUT | `/api/v1/admin/menu-items/{id}` | Edit an item (send `version`; status and availability stay, a BLOCKED item stays BLOCKED; audited) |
| Admin | POST | `/api/v1/admin/menu-items/{id}/duplicate` | Copy named `<name> (copy)`, `(copy 2)`, ...; the copy is DISABLED (audited) |
| Admin | GET | `/api/v1/admin/menu-items/category-stats?limit=3` | Categories with the most items: total, orderable (ACTIVE and available), percent (limit 1..12) |
| Admin | PUT | `/api/v1/admin/menu-items/{id}/activate` `/deactivate` `/category` | Release, block, re-categorise (audited) |
| Admin | DELETE | `/api/v1/admin/menu-items/{id}` | Remove an invalid item (only if not ACTIVE) |
| Admin | GET | `/api/v1/admin/{categories,menu-items}/{id}/audit-logs` | Audit history (kept after deletion) |

Design notes:

- **Gateway routing by sub-path:** menu URLs live under `/api/v1/restaurants/...` but belong to
  catalog-service, so the gateway has a higher-priority route for `/api/v1/restaurants/{id}/menu`
  and `/api/v1/restaurants/mine/{id}/menu-items/**`. Everything else under `/api/v1/restaurants`
  still goes to restaurant-service.
- **Ownership and status come from restaurant-service.** Since Phase 7 catalog-service keeps a local
  copy fed by `RestaurantChanged` events (see "Kafka events"), so menus keep working while
  restaurant-service is down. Only a restaurant not known locally yet is looked up over REST
  (internal API `/api/v1/internal/restaurants/{id}`); if restaurant-service is down then, owner
  changes fail with 503 rather than guessing who owns the restaurant. The check runs *before* the
  database transaction starts (`TransactionTemplate`), so no database connection waits on another service.
- Orders store their own copy of item name and price, so removing an item never changes past orders.

## Order Service API (Phase 5)

Order lifecycle (only these transitions are allowed; every change is written to the order timeline):

```text
PAYMENT_PENDING --(paid, Phase 6)--> PLACED --> ACCEPTED --> PREPARING --> READY_FOR_PICKUP
      |                               |   \--> REJECTED (restaurant)              |  (delivery-service)
      v                               v                                           v
  CANCELLED                       CANCELLED               OUT_FOR_DELIVERY --> DELIVERED
```

Cash-on-delivery orders start as `PLACED`. Online payments (`CARD`, `UPI`, `WALLET`) start as
`PAYMENT_PENDING` and are invisible to the restaurant until payment-service confirms them (a
`PaymentStatusChanged` event, see "Checkout saga"). Unpaid orders are cancelled by the system after
15 minutes (`ORDER_PAYMENT_TIMEOUT`). Customers can cancel until the restaurant accepts; admins can
cancel any unfinished order (audited). The last two steps come from delivery-service
(`DeliveryStatusChanged`): pickup moves the order to `OUT_FOR_DELIVERY`, delivery to `DELIVERED`, and a
cash-on-delivery order is then `PAID`.

| Who | Method | Path | Purpose |
|---|---|---|---|
| Customer | GET | `/api/v1/cart` | Cart with live prices, delivery fee, 5% tax and problems blocking checkout |
| Customer | PUT / DELETE | `/api/v1/cart/items/{menuItemId}` | Set quantity / remove (one restaurant per cart) |
| Customer | PUT / DELETE | `/api/v1/cart/coupon` | Apply a coupon (422 with the reason if the cart does not qualify) / remove it (Phase 13a) |
| Customer | DELETE | `/api/v1/cart` | Empty the cart (and remove its coupon) |
| Customer | POST | `/api/v1/orders` + header `Idempotency-Key` | Checkout (201, or 200 + `Idempotent-Replayed: true` on retry) |
| Customer | GET | `/api/v1/orders`, `/api/v1/orders/{id}` | History / tracking with timeline |
| Customer | PUT | `/api/v1/orders/{id}/cancel` | Cancel before the restaurant accepts |
| Owner | GET | `/api/v1/restaurants/mine/{id}/orders` | Incoming orders (`status`, `fromDate`, `toDate`) |
| Owner | PUT | `.../orders/{orderId}/accept` `reject` `preparing` `ready` | Work through an order |
| Owner | GET | `.../orders/sales-summary?fromDate&toDate` | Sales information (default today) |
| Admin | GET | `/api/v1/admin/orders` | Search: `orderId`, `customerId`, `restaurantId`, `status`, `paymentStatus`, dates |
| Admin | GET | `/api/v1/admin/orders/{id}`, `/{id}/timeline` | Details, payment status, cancellation reason, timeline |
| Admin | PUT | `/api/v1/admin/orders/{id}/cancel` | Intervention (audited) |
| Admin | GET | `/api/v1/admin/orders/customers/{customerId}/summary` | The "user order summary" for the admin user screen |
| Admin | GET | `/api/v1/admin/orders/stats/menu-items/{menuItemId}/revenue?period=MONTHLY` | Revenue of one dish for a chart (Food Detail page): `period` MONTHLY (default, last 12 months), WEEKLY (last 12 weeks, Monday start) or DAILY (last 30 days), the current one included and every bucket listed (zeros too). Returns `menuItemId`, `period`, `totalRevenue`, `totalQuantity` and `points[]` (`label` such as `Oct 2026` / `6 Oct`, `start` yyyy-MM-dd, `revenue`, `quantity`, `orders`). Revenue is the sum of the dish's line totals in DELIVERED orders (before coupon discount, delivery fee and tax), bucketed by the order's created time in Asia/Kolkata; `orders` are distinct orders with the dish. An unknown dish gives zeros. One grouped SQL, indexed by `order_items(menu_item_id, order_id)` |
| Admin | GET | `/api/v1/admin/orders/stats/customers/{customerId}/top-foods?period=MONTHLY&limit=5` | A customer's most ordered dishes (admin customer screen). `period` MONTHLY (default, last 30 days), WEEKLY (last 7 days) or DAILY (today since business midnight, Asia/Kolkata); `limit` 1-20 (400 outside). Returns `customerId`, `period`, `from` (yyyy-MM-dd) and `items[]` (`menuItemId`, `name`, `orders`, `quantity`, `totalSpent` = sum of line totals, `unitPrice` of the latest order in the window, `lastOrderedAt`, `imageUrl`, `categoryName`), ranked by quantity, then orders, then last ordered. Orders in every status except CANCELLED, REJECTED and PAYMENT_PENDING count (the customer summary has no narrower "real order" rule). One grouped SQL (uses `orders(customer_id, created_at)`); image and category are added afterwards from catalog-service in one best-effort call: if it is down or does not know a dish, those two are null and the request still succeeds. An unknown customer gives an empty list |

Design notes:

- **Idempotency:** each checkout carries an `Idempotency-Key`. A retry returns the first order; the
  same key with a different request is refused (422). Two identical requests at the same moment are
  settled by a unique database key on `(customer_id, idempotency_key)`, so exactly one order exists.
- **Snapshots:** an order copies item names and prices (catalog-service), the restaurant name
  (restaurant-service) and the delivery address (user-service). Later changes never alter it.
- **Checkout checks** (before anything is saved): restaurant ACTIVE and open now, every item still
  listed and in stock, customer active, address belongs to the customer.
- **Remote calls never hold a database transaction open**; if a dependency is down, checkout fails
  with 503 and nothing is half-saved. Dates in filters are calendar days in `Asia/Kolkata`.
- **The user order summary lives in order-service**, not in user-service: order-service already calls
  user-service, and a call back the other way would create a dependency cycle. The admin UI shows
  both side by side.

## Payment Service API (Phase 6)

```text
Payment:  INITIATED -> PROCESSING -> SUCCESS -> REFUNDED (fully refunded)
                                 \-> FAILED      (order stays PAYMENT_PENDING: the customer can try again)
Refund:   REQUESTED -> PROCESSING -> SUCCESS | FAILED
```

Online payments go through one of two gateways, chosen with `PAYMENT_PROVIDER` in `.env`:

- `SIMULATED` (the default): the built-in `SimulatedPaymentGateway`, no keys, no real money (test data below).
- `RAZORPAY`: [Razorpay Checkout](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/).
  Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` (Test Mode keys `rzp_test_...` for
  testing). The customer endpoint `POST /api/v1/payments` then refuses online payments, so nobody can skip Razorpay.
  How to try it: README-LOCAL-TESTING.md, "Razorpay payments (Test Mode)".

The simulated gateway's test data:

| Method | Succeeds | Fails |
|---|---|---|
| CARD | `4111111111111111` | `4000000000000002` declined, `4000000000009995` insufficient funds |
| UPI | any `name@bank` | `fail@upi` declined; `refundfail@upi` pays but refunds fail |
| WALLET | any wallet id | `empty-wallet` insufficient balance |

| Who | Method | Path | Purpose |
|---|---|---|---|
| Customer | POST | `/api/v1/payments` + header `Idempotency-Key` | Pay a `PAYMENT_PENDING` order (201, or 200 + `Idempotent-Replayed` on retry) |
| Customer | GET | `/api/v1/payments?orderId=`, `/api/v1/payments/{id}` | My payments |
| Customer | GET | `/api/v1/payments/razorpay/config` | Razorpay on? (`enabled`, public `keyId`, `testMode`; never the secret) |
| Customer | POST | `/api/v1/payments/razorpay/orders` `{orderId}` | Start paying: creates (or reopens) the Razorpay order for the order's total; returns what Checkout needs |
| Customer | POST | `/api/v1/payments/razorpay/verify` | Confirm Checkout's result: signature check, then Razorpay is asked; `SUCCESS` or `PROCESSING` (422 on a bad signature) |
| Razorpay | POST | `/api/v1/payments/razorpay/webhook` | Webhook (`X-Razorpay-Signature`; repeated event ids are ignored) |
| Admin | GET | `/api/v1/admin/payments` | Search: `paymentId`, `orderId`, `customerId`, `status`, `method`, dates (`status=FAILED` = failed payment monitoring) |
| Admin | GET | `/api/v1/admin/payments/summary` | Totals per status and method (default today) |
| Admin | GET | `/api/v1/admin/payments/{id}` | Details, refunds, and the events published for it (PENDING or PUBLISHED) |
| Admin | POST | `/api/v1/admin/payments/{id}/refunds` + `Idempotency-Key` | Full or partial refund (order must be cancelled, rejected or delivered; audited) |
| Admin | GET | `/api/v1/admin/refunds`, `/api/v1/admin/refunds/{id}` | Refund list and details |

Design notes:

- **Strategy + Factory:** one `PaymentStrategy` per method (card, UPI, wallet) validates and masks its own
  details; `PaymentStrategyFactory` picks it. A new method means one new class.
- **Never charged twice:** an `Idempotency-Key` per attempt, and a unique key on a generated column
  (`active_order_id`) so an order can have only one running or successful payment, even under races.
- **The amount comes from order-service**, never from the client: from payment-service's copy of the order
  (`OrderCreated` event), or, for an order whose event has not arrived yet, from order-service directly.
  Card numbers are never stored or logged: only "VISA **** 1111" (and `CardDetails.toString()` masks the number).
- **Payment results reach order-service as `PaymentStatusChanged` events** through the transactional outbox
  (since Phase 8; Phase 6 used an HTTP outbox).
- **Automatic refunds (saga compensations):** a paid order that is cancelled or rejected later, and a payment
  that completes after its order was cancelled, are refunded in full, at most once per payment.
- **Refunds reserve the amount first**, so two refunds at the same time can never exceed what was paid;
  a failed refund releases the reservation. A refund saved but never sent to the provider (the service
  stopped in between) is sent by a recovery job; failed refunds are listed for admins (`?status=FAILED`).
- **Razorpay: the browser's word is never enough.** A payment becomes `SUCCESS` only when the Checkout signature
  (HMAC-SHA256 of `order_id|payment_id` with the key secret) is right **and** Razorpay's API reports the payment
  *captured* for that Razorpay order, amount and currency. An *authorized* payment (the bank approved, the money
  is only held) is captured first. The signed webhook reaches the same result when the customer closes the page
  early; whichever arrives first wins and the other changes nothing. A failed or cancelled attempt keeps the
  payment `PROCESSING` so the customer can try again on the same Razorpay order; unpaid orders are still cancelled
  after 15 minutes, and a payment that arrives later is refunded automatically (through Razorpay).
- **Razorpay is called over plain REST** (`RazorpayClient`, Spring `RestClient`) instead of the razorpay-java SDK:
  the SDK brings OkHttp 3 / Okio 1 (2018), which clash with the OkHttp 5 / Okio 3 used by the OpenTelemetry
  exporter, and Okio 1.14 has CVE-2023-3635.

## Notification Service API (Phase 9)

Notifications are driven by events only: no service calls notification-service. Every email lands in
Mailpit (http://localhost:8025); SMS and push are simulated (logged), and push notifications also form
each user's in-app inbox.

| Event | Who is notified | Channels |
|---|---|---|
| `UserRegistered` | the new user (welcome) | EMAIL, SMS if a phone number was given |
| `RestaurantChanged` submitted / approved / rejected / live / deactivated / suspended | the owner | EMAIL |
| `OrderStatusChanged` PLACED | the customer; the restaurant ("New order") | EMAIL + PUSH; PUSH |
| `OrderStatusChanged` ACCEPTED, PREPARING, READY_FOR_PICKUP | the customer | PUSH |
| `OrderStatusChanged` OUT_FOR_DELIVERY / DELIVERED | the customer | PUSH + SMS / EMAIL + PUSH |
| `DeliveryStatusChanged` ASSIGNED (Phase 10) | the delivery partner ("New delivery") | PUSH |
| `DeliveryStatusChanged` ACCEPTED | the customer (partner name and vehicle) | PUSH |
| `DeliveryStatusChanged` CANCELLED | the partner who was on it (take the food back if picked up) | PUSH |
| `OrderCancelled` | the customer (reason, refund if paid); the restaurant if it was working on it | EMAIL + PUSH; PUSH |
| `PaymentStatusChanged` FAILED / REFUNDED | the customer | PUSH / EMAIL + PUSH |

| Who | Method | Path | Purpose |
|---|---|---|---|
| Any user | GET | `/api/v1/notifications?unreadOnly=` | My in-app inbox, newest first |
| Any user | GET | `/api/v1/notifications/unread-count` | The badge on the bell |
| Any user | PUT | `/api/v1/notifications/{id}/read`, `/api/v1/notifications/read-all` | Mark read |
| Admin | GET | `/api/v1/admin/notifications` | Search: `recipientUserId`, `orderId`, `restaurantId`, `type`, `channel`, `status`, dates |
| Admin | GET | `/api/v1/admin/notifications/summary` | Totals per status and channel (default today) |
| Admin | GET | `/api/v1/admin/notifications/{id}` | Delivery details: attempts, last error, address used |
| Admin | POST | `/api/v1/admin/notifications/{id}/resend` | Send a FAILED or SKIPPED notification again (reason required, audited) |

Design notes:

- **Saving and sending are separate.** The Kafka consumers only save notifications (PENDING), in one
  transaction with the event id (`processed_events`), so a repeated event notifies nobody twice. The
  dispatcher sends them within a second, with no transaction open.
- **Contact details are looked up when sending** (user-service), so a changed email address or phone number
  is always used; a restaurant's notifications go to its current owner (restaurant-service).
- **Failures are retried:** user-service down or the mail server refusing means another attempt after 10s,
  1m, 5m, 15m; after 5 attempts the notification is FAILED and an admin can resend it. Nothing to send it to
  (no phone number, deactivated user) is SKIPPED with the reason.
- **Strategy + Factory:** one `NotificationSender` per channel (`EmailSender`, `SmsSender`, `PushSender`);
  a new channel is one new class. All wording is in `NotificationTemplates`.
- **Several dispatcher instances can run:** each one claims a batch with `SKIP LOCKED` and a short lease,
  so a notification is never sent by two of them at once.
- **First start reads only new events** (`auto-offset-reset: latest`): a newly deployed notification
  service does not email people about last week's orders. Afterwards it resumes where it stopped.

## Live updates (WebSocket)

Admins, restaurant owners, delivery partners and customers see changes without refreshing the page: an order that
moves to PREPARING, a delivery picked up, a payment that failed, a restaurant that was approved.

```text
 services --Kafka events (existing)--> notification-service --WebSocket--> API Gateway --> browser
   order / payment / delivery /          LiveEventsConsumer       /ws/v1/live       ws://localhost:8080/ws/v1/live
   restaurant                            LiveEventService
```

- **One place, no change to the business services.** notification-service already reads these events; a second
  consumer (`LiveEventsConsumer`, its own Kafka consumer group, so notifications are never affected) turns them into
  small "something changed" messages. Nothing new is published and no database is read.
- **A message is a hint, not data.** It holds ids and the new status only (`ORDER_STATUS_CHANGED`, `ORDER_CANCELLED`,
  `PAYMENT_STATUS_CHANGED`, `DELIVERY_STATUS_CHANGED`, `RESTAURANT_CHANGED`). The application then reads the current data
  through the normal REST APIs, which keep checking the role, so a live update can never show more than a refresh.
  REST stays the way to read and change data.
- **Who receives what** (`LiveAudience`, one rule): platform admins every event; a customer the events of their own
  orders, payments and deliveries; a restaurant owner the events of the restaurants restaurant-service says are
  theirs (asked when the connection opens, and extended when a restaurant of theirs changes) but no payments; a delivery
  partner the deliveries they are on. A client cannot subscribe to anything: it only listens.
- **Identity:** the connection sends the area's access token in its query (`/ws/v1/live?access_token=...`; a browser
  cannot set headers on a WebSocket). notification-service checks it like the token of any other request (it accepts
  a token in the query on `/ws/**` only), and `LiveHandshakeInterceptor` takes the user and role from it. A missing or
  invalid token is refused with 401, a web page that is not in `app.realtime.allowed-origins` with 403.
- **Reliability:** the server sends a heartbeat and a ping every 25 s and closes connections that answered nothing for
  75 s; a user keeps at most 5 connections (the oldest is closed); a client that does not read is closed after 5 s
  (it cannot hold others up); an event Kafka delivers twice is sent once. The application reconnects by itself (1 s,
  2 s, 4 s ... up to 30 s, with randomness), immediately when the network returns, drops a connection that went silent,
  and, after a reconnect, reads everything on screen again (events may have been missed). Refreshes are grouped: events
  within 400 ms cause one refresh per screen. Pages keep their own refresh timers as a safety net.
- **Every instance listens to every event** (its connections can be anyone's): each gets its own consumer group at
  start-up and reads only new events.
- **Settings:** `app.realtime.*` in notification-service (`REALTIME_ALLOWED_ORIGINS` in Docker), the gateway route
  `live-updates` (`/ws/**`, no rate limit or circuit breaker: they would cut a long connection), and
  `VITE_FEATURE_LIVE_UPDATES` in the front-end. Turn it off with `app.realtime.enabled=false`: the REST APIs and the
  notifications are unaffected.
- **Front-end:** `src/realtime` (`LiveConnection`: reconnecting socket; `LiveUpdatesProvider`: one connection for the
  acting user; `liveInvalidation`: which data an event makes out of date). The dashboard's "Live Delivery Tracking" shows
  LIVE while the connection is open. The application has no restaurant-owner screens yet: owners are served by the
  backend (and `LiveUpdatesProvider` would serve them) as soon as such screens exist.

## Delivery Service API (Phase 10)

```text
Delivery:  PENDING --(assignment job)--> ASSIGNED --(partner accepts)--> ACCEPTED --> PICKED_UP --> DELIVERED
              ^                             |                               |
              +--(declined / no answer in 2 min / admin takes it back)------+
           any unfinished delivery --> CANCELLED when its order is cancelled
Partner:   OFFLINE <--> AVAILABLE (partner) ; BUSY while on a delivery (system) ; SUSPENDED (admin)
```

A delivery starts when the restaurant marks the order `READY_FOR_PICKUP`. Every 5 seconds the assignment
job offers each waiting delivery to the `AVAILABLE` partner in the restaurant's city who has waited longest.
A partner who declines is not offered that delivery again; one who does not answer within 2 minutes
(`DELIVERY_ACCEPT_TIMEOUT`) loses it and goes `OFFLINE`.

Partners themselves (registration, profile, suspension) belong to the
[Delivery Partner Service](#delivery-partner-service-api) since event-contracts 1.7.0; delivery-service keeps
what partners are doing and the deliveries. The API Gateway sends each path to its service, so the URLs did not
change.

| Who | Method | Path | Purpose |
|---|---|---|---|
| Partner | PUT | `/api/v1/delivery/partners/me/availability` | `AVAILABLE` or `OFFLINE` (not while on a delivery or suspended; vehicle and city must be set) |
| Partner | GET | `/api/v1/delivery/deliveries/mine/current` | The delivery I am on now, with pickup and drop-off (204 if none) |
| Partner | GET | `/api/v1/delivery/deliveries/mine`, `/mine/{id}` | My delivery history (`status`), one delivery with its timeline |
| Partner | PUT | `/api/v1/delivery/deliveries/mine/{id}/accept` `decline` `pickup` `deliver` | Work through a delivery; cash on delivery: `{"cashCollected": total}` |
| Partner | PUT | `/api/v1/delivery/deliveries/mine/{id}/location` | My phone's GPS position while on the way (accepted or picked up): `{"latitude", "longitude", "accuracyMeters", "headingDeg"}`; 204 (one sooner than 2 s after the last is skipped), 409 when not on the way |
| Customer | GET | `/api/v1/delivery/orders/{orderId}` | Tracking: status, partner and vehicle, phone while on the way, milestones |
| Customer | GET | `/api/v1/delivery/orders/{orderId}/live` | Live map: the partner's latest GPS position (only while on the way), the restaurant and the drop-off with their coordinates; 404 until a partner accepts |
| Admin | GET | `/api/v1/admin/delivery-partners/availability` | Partners per status right now |
| Admin | GET | `/api/v1/admin/delivery-partners/stats` | Partners online, deliveries waiting, top partners of a period (dashboard, reports) |
| Admin | GET | `/api/v1/admin/delivery-partners/{id}/deliveries` | A partner's deliveries |
| Admin | GET | `/api/v1/admin/deliveries` | Search: `status`, `orderId`, `partnerUserId`, `customerId`, `restaurantId`, `city`, dates |
| Admin | GET | `/api/v1/admin/deliveries/delayed` | No partner 10 min after the food was ready, or not delivered after 45 min |
| Admin | GET | `/api/v1/admin/deliveries/summary` | Report: per status, average delivery time, delayed now (default today) |
| Admin | GET | `/api/v1/admin/deliveries/{id}`, `/{id}/audit-logs` | Full history (offers, declines, timeouts, reassignments), audit trail |
| Admin | PUT | `/api/v1/admin/deliveries/{id}/reassign` | To an `AVAILABLE` partner, or back to the queue (reason required, audited) |

Design notes:

- **The dispatch list:** `delivery_partners` here is a copy of the partners delivery-partner-service owns (name,
  phone, vehicle, city, suspension; from `DeliveryPartnerChanged`, newest version wins), plus what only this
  service decides: the status (`OFFLINE`, `AVAILABLE`, `BUSY`, `SUSPENDED`), the queue order and the current
  delivery. Every status change is published as `DeliveryPartnerStatusChanged`. A partner whose event has not
  arrived yet (a profile filled in the second before going online) is asked of delivery-partner-service.
- **Everything a delivery needs is a local copy:** the customer and drop-off address from `OrderCreated`
  (since event-contracts 1.3.0), the pickup address from `RestaurantChanged` (versioned, newest wins).
  Events of different topics can arrive in any order; details that never arrived (for example orders from
  before the service existed) are fetched from order-service or restaurant-service by the assignment job,
  outside any transaction. If they are down, the delivery simply waits for the next run.
- **No double assignment:** each delivery is assigned in its own short transaction with the delivery and the
  chosen partner row locked (`FOR UPDATE SKIP LOCKED`), so several instances can run the job at once.
  Partner and delivery rows also carry versions: a partner tapping "accept" while an admin reassigns gets 409.
- **The live position:** `rider_locations` keeps only the latest position of each delivery (one row, replaced, no
  history), accepted only from that delivery's own partner while it is `ACCEPTED` or `PICKED_UP`; a job deletes the
  rows of finished deliveries every minute (`app.live-location.*`). The customer sees it only for their own order,
  and positions are never logged. No route or arrival time is computed (there is no routing service): the map
  shows the real position and the two stops. The partner's page sends the phone's position (browser Geolocation API).
- **An order cancelled before its "ready" event was read** (two topics, any order) is recorded as a
  cancelled delivery right away, so the late event never sends a partner.
- **Every step is published** as `DeliveryStatusChanged` through the transactional outbox: order-service
  moves the order on, notification-service tells the partner and the customer.

## Delivery Partner Service API

delivery-partner-service owns the delivery partners: who they are (registration, name, phone, vehicle, city) and
whether an admin lets them work (account `ACTIVE` or `SUSPENDED`, audited). What a partner is doing (online, on a
delivery) is decided by delivery-service together with the deliveries, because assigning a delivery and making its
partner `BUSY` must happen in one database transaction (no partner gets two deliveries). Why the line is drawn here:
[docs/decision-delivery-partner-service.md](docs/decision-delivery-partner-service.md).

```text
 partner / admin ──> API Gateway ──> delivery-partner-service ──DeliveryPartnerChanged──────> delivery-service
                                       (profile, suspension)   <─DeliveryPartnerStatusChanged─  (online, BUSY,
 user-service ──UserRegistered──────────────────┘                                              assignment)
```

| Who | Method | Path | Purpose |
|---|---|---|---|
| Partner | GET | `/api/v1/delivery/partners/me` | My profile and status (created on the first call if the account is a delivery partner) |
| Partner | PUT | `/api/v1/delivery/partners/me` | Vehicle and city (needed to go online; going online is delivery-service's `/me/availability`) |
| Admin | GET | `/api/v1/admin/delivery-partners` | Search: `status`, `city`, `q` (name or phone); paging and sorting |
| Admin | GET | `/api/v1/admin/delivery-partners/{id}` | One partner |
| Admin | PUT | `/api/v1/admin/delivery-partners/{id}/deactivate` `activate` | Suspend / reactivate (reason required, audited; not while on a delivery) |
| Admin | GET | `/api/v1/admin/delivery-partners/{id}/audit-logs` | The partner's audit trail |
| Internal | GET | `/api/v1/internal/delivery-partners/{id}` | delivery-service: a partner whose event has not reached it yet |
| Internal | POST | `/api/v1/internal/delivery-partners/import` | Once: take over the partners delivery-service kept before this service existed |
| Internal | POST | `/api/v1/internal/audit-logs/events/republish` | admin-service's audit backfill |

Design notes:

- **Same URLs as before:** the API Gateway sends `/me` and the admin search, details, activate, deactivate and
  audit-logs here (route `delivery-partner-service`, numeric ids only), everything else under
  `/api/v1/delivery/**` and `/api/v1/admin/delivery-partners/**` to delivery-service. The web application, the
  E2E tests and the AI assistant did not change.
- **Responses keep their shape:** `status`, `currentDeliveryId`, `availableSince` and `completedDeliveries` come
  from a copy of delivery-service's `DeliveryPartnerStatusChanged` events (newest version wins); `status` is
  `SUSPENDED` while the account is suspended. The copy follows within a second.
- **Suspension and the race with assignment:** the admin gets 409 while this service knows the partner is on a
  delivery (reassign it first). If an offer reaches the partner while the suspension is on its way, delivery-service
  takes the offer back to the queue; a delivery the partner already accepted is finished first, then they are
  `SUSPENDED` instead of `AVAILABLE`.
- **Audit:** the same actions and entity type as before (`DELIVERY_PARTNER_DEACTIVATED`, `DELIVERY_PARTNER_ACTIVATED`
  on `DELIVERY_PARTNER`), copied to admin-service's platform-wide audit log. Audit records written by
  delivery-service before the move stay there and in the platform-wide log.
- **Taking over existing partners:** on an installation from before this service, run
  `POST http://localhost:8099/api/v1/internal/delivery-partners/import` once (profile, suspension and current status
  are copied from delivery-service; partners already here are left alone, so repeating it is safe). New
  installations need nothing: partners arrive with `UserRegistered`.

## Search Service API (Phase 11)

Elasticsearch is used only for search; MySQL in restaurant-service and catalog-service stays the source of
truth. search-service has no database of its own: it builds three indexes from Kafka events.

```text
restaurant-service ──RestaurantChanged──┐
catalog-service ─────CategoryChanged────┼──> Kafka ──> search-service (indexer) ──> Elasticsearch
catalog-service ─────MenuItemChanged────┘                                          restaurant | food | category
customer ──> API Gateway ──> search-service (queries) ─────────────────────────────────────┘
```

| Who | Method | Path | Purpose |
|---|---|---|---|
| Anyone | GET | `/api/v1/search/restaurants` | `q` (name, cuisines, description), `city`, `cuisine`, `lat`+`lon` (nearest first, `distanceKm`), `radiusKm`; facet: cuisines |
| Anyone | GET | `/api/v1/search/foods` | `q`, `city`, `restaurantId`, `categoryId`, `foodType`, `minPrice`, `maxPrice`, `sort` (RELEVANCE, PRICE_LOW_TO_HIGH, PRICE_HIGH_TO_LOW); facets: foodTypes, categories |
| Anyone | GET | `/api/v1/search/categories?q=` | Active categories (all in menu order without `q`) |
| Anyone | GET | `/api/v1/search/suggest?q=&city=` | Autocomplete: up to 5 restaurants, dishes and categories whose names start with what was typed |
| Admin | GET | `/api/v1/admin/search/indexes` | Elasticsearch health and documents per index |
| Admin | POST | `/api/v1/admin/restaurants/events/republish` (restaurant-service), `/api/v1/admin/categories/events/republish`, `/api/v1/admin/menu-items/events/republish` (catalog-service) | Replay the current MySQL state as events (audited): fills the indexes with data from before Phase 11, or rebuilds them |

Design notes:

- **Typo-tolerant, as-you-type search:** names are indexed twice, as whole words (matched with fuzziness:
  "biryni" finds Biryani) and as word beginnings (edge n-grams: "chi bir" finds Chicken Biryani). Letter case
  and accents are ignored; cities and cuisines match in any letter case, facets keep the original spelling.
- **Customers only find what they can order:** ACTIVE restaurants; dishes that are listed, in stock and from an
  ACTIVE restaurant. Every food document carries a copy of its restaurant (name, city, status) and category
  name, so one query searches, filters and displays everything. When a restaurant is renamed or suspended, its
  dishes are updated in place (update-by-query).
- **The index never goes back in time:** each document stores the version of its source entity; older events
  (late, repeated, replayed) are ignored. A dish whose event arrives before its restaurant's stays hidden until
  the restaurant is known.
- **Explicit mappings:** the indexes are created at startup from `resources/elasticsearch/*.json` (`dynamic:
  strict`), and Elasticsearch is told never to create an index on its own (`action.auto_create_index: false`).
- **Elasticsearch down:** search answers 503 "Search unavailable"; indexing waits and retries (1s up to 30s
  between attempts) without losing or reordering events, and catches up when Elasticsearch is back. Events that
  cannot be applied for another reason go to `<topic>.DLT`.
- **One indexer thread per instance** reads the three topics, so a restaurant change and the indexing of its
  dishes never run side by side; across instances a freshly indexed dish re-checks its restaurant and category.

### Where each read comes from (MySQL or Elasticsearch)

MySQL is the single source of truth. Elasticsearch is a derived search layer: nothing is ever written to
MySQL from it, and a document that disagrees with MySQL is wrong by definition.

| Read | Source | Why (measured on 300k restaurants / 500k users) |
|---|---|---|
| Storefront search and suggest (`/api/v1/search/*`) | **Elasticsearch** | Typo-tolerant, as-you-type, relevance, facets, distance sort. 1–7 ms; `LIKE '%x%'` is 350 ms + 320 ms for the count and cannot rank or tolerate typos |
| Storefront browse list (`GET /api/v1/restaurants`) | MySQL | Needs `openNow`, computed from opening hours and the clock, which is not in the index; the default sort now uses an index (346 ms to 0.3 ms) |
| Restaurant details, menus, categories | MySQL (+ Redis cache) | Key lookups, 0.3 ms; must show a change at once |
| Admin lists (users, restaurants, foods, deliveries, partners, payments, refunds, coupons, notifications, audit) | MySQL | Admin needs read-after-write; text search here is `LIKE` on tables far under the ~50k-row crossover (17 ms at 10k, 204 ms at 100k, 671 ms at 300k); indexing them would also copy personal data |
| Orders, payments, refunds, deliveries | MySQL | Transactional and financial: always authoritative and immediately consistent |

Re-measure with [`quality/search-benchmark`](quality/search-benchmark/README.md) when a list passes about 50,000
rows with text search; that is where moving it to Elasticsearch starts to pay for the extra sync and the
loss of read-after-write.

**Indexes added** (the real bottlenecks were unindexed default sorts, not missing Elasticsearch):
`restaurants (status, name)` and `(created_at)`, `deliveries (created_at)`, `refunds (requested_at)`,
`menu_items (updated_at)`.

### Keeping the indexes right: consistency check and repair

Sync is event-driven (transactional outbox, Kafka, version-guarded indexer, retries, DLT). On top of that,
search-service can compare each index with MySQL (ids and versions, read through the owners' internal feeds
`/api/v1/internal/restaurants/versions` and `/api/v1/internal/search-feed/categories|menu-items`, which the
gateway does not route):

| Who | Method | Path | Purpose |
|---|---|---|---|
| Admin | GET | `/api/v1/admin/search/consistency` | Per index: missing, stale, ahead and orphan documents, with samples. Only looks |
| Admin | POST | `/api/v1/admin/search/consistency/repair` | Deletes orphans (re-confirmed in MySQL first) and ahead documents, then asks the owner to republish. Writes nothing to MySQL |

If an owning service is down the answer is 503 and nothing is deleted. A scheduled check (every 6h, first
after 5 min; `app.consistency-check.*`) exposes the gauge `search.index.drift.documents{index}`; alert when it
stays above zero. New settings: `RESTAURANT_SERVICE_URL`, `CATALOG_SERVICE_URL`.

## Review Service API: customer reviews

Customers rate **delivered orders**; admins read the reviews on the Reviews page (Front-App: `/admin/reviews`).
review-service (port 8094, `review_db`) owns the reviews. It publishes and consumes no events: it asks
order-service whether the order is the caller's and delivered (`GET /api/v1/internal/orders/{id}/review-info`)
and catalog-service for dish photos and categories (`GET /api/v1/internal/menu-items?ids=`). If the catalog is
down the page still shows the dishes (without photo); if order-service is down a review is refused with 503.

| Who | Method | Path | Purpose |
|---|---|---|---|
| Customer | POST | `/api/v1/reviews` | `orderId`, `rating` 1-5, optional `comment` (1000), `tags` (up to 4 of DELICIOUS, EXCELLENT, GOOD_SERVICE, FAST_DELIVERY, GOOD_VALUE, GOOD_PACKAGING), optional `dishRatings` (`menuItemId`, `rating`; dishes must be on the order). One review per order: a second one is 409; an order that is not DELIVERED is 422; someone else's order is 404 |
| Customer | GET | `/api/v1/reviews/orders/{orderId}` | The caller's review of that order (404 when none): the order page uses it to show the form or the review |
| Customer | PUT | `/api/v1/reviews/{reviewId}` | Change the caller's own review (same fields as POST, dish ratings are replaced, optional `version` for optimistic locking). Someone else's review is 404, a stale `version` is 409 |
| Customer | DELETE | `/api/v1/reviews/{reviewId}` | Delete the caller's own review (204); the order can be reviewed again. Someone else's review is 404 |
| Public | GET | `/api/v1/ratings/restaurants?ids=1,2` and `/api/v1/ratings/menu-items?ids=..` | `[{id, average, count}]` for 1-100 ids; unrated ids are left out. Public ratings, the search sort/filter and the pages that show them: [docs/ratings-and-reviews.md](docs/ratings-and-reviews.md) |
| Public | GET | `/api/v1/ratings/restaurants/{id}` and `/api/v1/ratings/menu-items/{id}` | `average` (null when unrated), `count`, and `distribution` of 1-5 stars |
| Public | GET | `/api/v1/ratings/restaurants/{id}/reviews` | `page`, `size` (max 50), `sort` (`newest` default, `highest`, `lowest`); reviewers are shown as "Priya S." |
| Admin | GET | `/api/v1/admin/reviews` | `restaurantId`, `menuItemId` (only reviews that rate that dish; `dishRatings` still list all their dishes), `rating` (overall rating of the review), `tag`, `from`, `to`, `sort` (`createdAt,desc` default, `createdAt,asc`, `rating,desc`, `rating,asc`), `page`, `size` (max 50) |
| Admin | GET | `/api/v1/admin/reviews/summary` | Count, average and 1-5 distribution; filters `restaurantId`, `menuItemId`, `from`, `to`. With `menuItemId` the numbers are computed over the dish's own ratings (`review_dishes.rating`), not the overall review ratings: the dish's rating on the admin Food Detail page |
| Admin | GET | `/api/v1/admin/reviews/featured-dishes` | Top-rated dishes with their best review, paged (`size` max 12): the carousel |
| Admin | GET | `/api/v1/admin/reviews/customers/{customerId}/liked-dishes?period=ALL&limit=7` | The dishes a customer rated highest (admin customer screen): `period` ALL (default), MONTHLY (last 30 days) or WEEKLY (last 7 days) by review created time; `limit` 1-20. Returns `customerId`, `period`, `ratingCount` (all the customer's dish ratings in the period) and `dishes[]` (`menuItemId`, `dishName`, `averageRating` 1 decimal, `ratingCount`, `lastRatedAt`), best average first, then most ratings, then most recent. One grouped SQL over `review_dishes` and `reviews.customer_id` (index `reviews(customer_id, created_at)`, migration V2). Unknown customer: empty list, `ratingCount` 0 |

Run it like the other services (`start-local.ps1` starts it).
On an existing MySQL volume create its database once (a new volume does it by itself):

```bash
docker compose exec -T mysql bash -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS review_db CHARACTER SET utf8mb4; CREATE USER IF NOT EXISTS review_svc@\"%\" IDENTIFIED BY \"<REVIEW_DB_PASSWORD from .env>\"; GRANT ALL ON review_db.* TO review_svc@\"%\";"'
```

## Chat Service API: support chat

Customers, restaurant owners and delivery partners can write to **Support**; all platform admins share one inbox and any of
them can answer. Each person has one conversation, so there is no way to open someone else's chat. Plain text only
(no attachments or formatting). chat-service (port 8096, `chat_db`) owns the messages; new messages reach the screen
through the existing live WebSocket (see "Live updates").

| Who | Method | Path | Purpose |
|---|---|---|---|
| Customer, restaurant owner, delivery partner | GET | `/api/v1/chat/conversation` | My conversation with Support (404 before the first message) |
| same | GET | `/api/v1/chat/messages?beforeId=&limit=30` | Latest page, oldest first: `{messages, hasMore, nextBeforeId}` |
| same | POST | `/api/v1/chat/messages` | `{body (1-2000), clientMessageId (UUID)}`; 201. The same `clientMessageId` again returns 200 and changes nothing; more than 30 messages a minute is 429 |
| same | POST | `/api/v1/chat/read` | I read Support's messages, 204 |
| Admin | GET | `/api/v1/admin/chat/conversations?search=&page=&size=` | Shared inbox: my pinned first, then latest activity; `participant.online` when known |
| Admin | POST | `/api/v1/admin/chat/conversations` | `{userId}`: start or open a conversation (201 / 200; 404 unknown user, 422 for an admin) |
| Admin | GET | `/api/v1/admin/chat/conversations/{id}` | One conversation |
| Admin | GET | `/api/v1/admin/chat/conversations/{id}/messages?beforeId=&limit=&q=` | Messages, with optional search inside the conversation |
| Admin | POST | `/api/v1/admin/chat/conversations/{id}/messages` | Answer as Support (idempotent like above) |
| Admin | POST | `/api/v1/admin/chat/conversations/{id}/read` | Mark read for all admins, 204 |
| Admin | PUT | `/api/v1/admin/chat/conversations/{id}/pin` or `/mute` | `{pinned}` / `{muted}`, per admin |
| Admin | GET | `/api/v1/admin/chat/unread-count` | `{count}` of conversations waiting for an answer |

Design notes:

- **No IDOR:** the participant API has no conversation id; every call finds the caller's own conversation by their user id.
- **Retry safe:** the browser invents a UUID per message. Sending it again (after a network failure) returns the saved
  message and changes no counter. A unique key on `(conversation_id, client_message_id)` backs this up.
- **Hints, not data:** `ChatMessageSent` (`fooddelivery.chat.message-sent`) and `ChatMessagesRead`
  (`fooddelivery.chat.messages-read`) carry ids only and go through the transactional outbox; notification-service turns
  them into live hints and the browser reads the text over REST with the reader's own role.
- **Shared inbox, personal view:** any admin opening a conversation clears its unread count for all admins; pin and mute are
  per admin. Last message and unread counts live on the conversation row, so the inbox is one query.
- **Failure behaviour:** user-service is asked for a name only for a first message and for an admin's display name (down:
  the first message gets 503 and nothing is saved; an admin reply is signed "Support"). Presence from notification-service is
  best effort (`online` is null when unknown).
- **Search:** conversation search and in-chat search use MySQL `LIKE`; at support-chat volumes that is enough. Revisit with the
  benchmark in `quality/search-benchmark` if a table passes about 50,000 rows.

Live updates add two message types, ids only: `CHAT_MESSAGE` `{conversationId, messageId, fromStaff}` and `CHAT_READ`
`{conversationId, readByStaff, upToMessageId}`. Audience: the conversation's participant (all their open tabs) and every
connected platform admin; nobody else. notification-service also answers `GET /api/v1/internal/presence?userIds=1,2,3`
(1 to 100 ids) with `{ "online": [1,3] }`: the users with an open live connection on that instance (internal, not routed by the
gateway; several instances would need a shared presence store).

## AI assistant (Spring AI, MCP, Ollama)

A chatbot for administrators, restaurant owners and customers, built on Spring AI with a local Ollama model. The full
explanation (decisions, security model, tool-to-API map, real test results, limitations) is in
[docs/ai-assistant-architecture.md](docs/ai-assistant-architecture.md). In short:

```text
Browser -> API Gateway -> ai-mcp-client (:8097) -> ai-mcp-server (:8098) -> API Gateway -> the existing services
                          identity, conversation   Spring AI + Ollama + tools     (the services still decide who may see what)
```

- **ai-mcp-client**: what the browser calls. Takes who the user is from their access token (checked with user-service's
  public key), keeps the conversation (Redis), rate limits, and runs the Confirm and Cancel buttons of the order flow.
- **ai-mcp-server**: the LLM loop and the tools. Checks the same access token again; each role gets only its own tools; every
  tool calls one fixed existing API through the gateway *as the user* (with their token). The model can only *prepare* an order; the Confirm button places it, once.
- **Ollama** (in Docker with the infrastructure, port 11434 on `127.0.0.1` only, models in the volume
  `ollama-data`). Default model `qwen3:4b-instruct-2507-q4_K_M` (about 2.5 GB; CPU only: 10 to 120 s per answer).

### Run it

```powershell
# 1. Everything, Ollama and the two AI services included (the model downloads, about 2.5 GB, the first time)
.\start-local.ps1
docker exec fd-ollama ollama list                       # the model must be listed

# 2. Check (with -PortOffset 10000: 18080, 18097, 18098)
curl http://localhost:8080/api/v1/assistant/status       # {"available":true,"model":"qwen3:4b-instruct-2507-q4_K_M"}
curl http://localhost:8097/actuator/health               # ai-mcp-client
curl http://localhost:8098/actuator/health               # ai-mcp-server
```

Then open the front end: the round **Ask the assistant** button is at the bottom right (admin pages and the customer
pages of a signed-in customer). Starter questions appear for each role.

Run the two services from the IDE or the command line instead (infrastructure in Docker, the gateway and services running):

```bash
cd ai-mcp-server && AI_SERVICE_TOKEN=local-secret ./mvnw spring-boot:run     # needs Redis (docker compose up -d) and Ollama
cd ai-mcp-client && AI_SERVICE_TOKEN=local-secret ./mvnw spring-boot:run     # the SAME token on both
```

Settings (environment variables; `.env.example` has the Docker ones): `OLLAMA_MODEL`, `OLLAMA_BASE_URL`, `GATEWAY_URL`,
`AGENT_SERVER_URL`, `USER_SERVICE_URL`, `AI_SERVICE_TOKEN` (shared secret, no default in production), `REDIS_HOST`,
`APP_TIMEZONE`, `CHAT_RATE_LIMIT` (20 questions per 10 minutes per user), `AGENT_READ_TIMEOUT` (150 s).
Another model: `OLLAMA_MODEL=qwen3:8b`, then `docker compose --profile ai up -d ollama-pull ai-mcp-server`.

### API (through the gateway)

| Method and path | Purpose |
|---|---|
| POST `/api/v1/assistant/chat` | `{conversationId?, message}` -> text, cards (stats, tables, restaurant/dish/offer/order lists), links, `pendingAction` |
| GET / DELETE `/api/v1/assistant/conversation?conversationId=` | Restore the thread / start a new chat |
| POST `/api/v1/assistant/actions/{id}/confirm`, `/cancel` | The buttons of an order summary (confirm is idempotent: pressing twice makes one order) |
| GET `/api/v1/assistant/suggestions`, `/status` | Starter questions by role; availability of the model (never an error) |

Internal (not routed by the gateway, needs `X-Service-Token`): `ai-mcp-server` `/internal/agent/*` and the MCP endpoint `/mcp`.
New owner APIs added for the assistant: `GET /api/v1/restaurants/mine/{id}/orders/top-items` (order-service) and
`GET /api/v1/restaurants/mine/{id}/deliveries/partners` (delivery-service).

### Troubleshooting

| Symptom | Check |
|---|---|
| "The assistant is offline" in the panel | `docker ps` (fd-ollama healthy?); `curl localhost:11434/api/tags` (is the model there?); the first answer after a restart loads the model (about 2 minutes) |
| 401 from `/api/v1/assistant/*` | No access token, or one that is not valid (signature, expiry, issuer, audience) or has less than 3 minutes left: the web app renews it and asks again by itself. A `.http` request needs `Authorization: Bearer` from a sign-in. user-service must be reachable for its public key (`USER_SERVICE_URL`) |
| 429 | Too many questions (20 per 10 minutes) or three in flight: the model answers one at a time on a CPU |
| 503 "assistant unavailable" | ai-mcp-server or Ollama is down; `docker logs fd-ai-mcp-server` |
| 504 "took too long" on the first question | The model was still loading (about 2 minutes on a CPU); `ai-mcp-server` loads it at startup, check `docker exec fd-ollama ollama ps`, then ask again |
| Docker becomes unresponsive when everything runs in containers | Not enough memory: pause the observability tools or run the application services locally (see the architecture document, limitations) |
| "near me" says the address has no map location | The storefront address form does not collect coordinates yet; an address saved through the API with `latitude` and `longitude` works |
| Answers are slow | CPU model; keep questions narrow; a smaller `OLLAMA_MODEL=qwen3:1.7b` is faster and weaker |
| "restaurant is closed" when ordering | The platform's own opening-hours rule; the assistant cannot override it |

## Redis: caching and rate limiting (Phase 12)

Redis holds only data that can be rebuilt or lost: cached pages and rate-limit counters. MySQL stays the source
of truth; Redis runs without persistence (a restart starts empty), and every service keeps working without it.

**Caching (cache-aside):**

| Service | What | Key | Refreshed when |
|---|---|---|---|
| catalog-service | A restaurant's menu (`GET /api/v1/restaurants/{id}/menu`) | `catalog:v1:menu:{restaurantId}:{menuGen}.{categoryGen}` | an item of it changes, the restaurant changes (name, status), or any category changes |
| catalog-service | Active categories (`GET /api/v1/categories`) | `catalog:v1:categories:{categoryGen}` | any category changes |
| restaurant-service | The public restaurant page (`GET /api/v1/restaurants/{id}`) | `restaurant:v1:details:{restaurantId}:{gen}` | the restaurant changes (profile, hours, status) |

- **Changes show on the very next read.** Each cached group has a generation counter; a change bumps it right
  after its transaction commits, and readers only look under the current value (old entries expire after the
  TTL, 10 minutes). Unlike deleting the cached entry, this cannot be undone by a reader that loaded MySQL just
  before the change and writes its outdated copy a moment later: that copy lands under the old generation.
- **"Open now" is never cached:** it depends on the clock and is worked out from the cached opening hours on
  every request.
- **Not cached on purpose:** prices and availability read during checkout (order-service always asks
  catalog-service), and search results (Elasticsearch is the read model for those).
- **Redis down:** reads go to MySQL; the outage is logged once ("reading from MySQL until it is back"), and
  the services keep reporting UP (Redis is optional for them; it gets its own monitoring in Phase 14). Redis
  never makes a request slow: every call has a 300 ms budget (reaching a host that is gone can take seconds,
  mostly in DNS, which no client timeout covers), and after a failure Redis is left alone for 5 seconds.
  Invalidations are always attempted, so no change is missed when Redis comes back.

**Rate limiting (API Gateway, Redis token buckets per client IP):**

| Applies to | Limit (default) | Setting |
|---|---|---|
| Every route | Its service's budget: 20 requests/second with bursts up to 40; catalog and search 40/80; payment and admin 10/20. Each service has its own bucket | `app.gateway.services` in the gateway's `application.yml` (see [API Gateway: traffic settings per service](#api-gateway-traffic-settings-per-service)) |
| `/api/v1/auth/**` (login, registration) | 10 per minute, on its own budget | `GATEWAY_AUTH_RATE_LIMIT_PER_MINUTE` (120 in docker-compose, for the test scripts) |

Over the limit the gateway answers `429 Too Many Requests`; responses carry `X-RateLimit-Remaining`,
`X-RateLimit-Burst-Capacity`, `X-RateLimit-Replenish-Rate` and `X-RateLimit-Requested-Tokens`. The buckets live in
Redis, so several gateway instances share them. If Redis is down, requests are let through without delay (the
limiter waits at most 300 ms for Redis, then skips it for 5 seconds, and logs the outage once). A signed-in client is counted by
the user id of its verified access token, a visitor by IP address.

**Idempotency keys stay in MySQL.** Checkout and payment keys are stored in the same transaction as the order or
payment they protect, with a unique index that settles simultaneous duplicates. Keeping them in Redis would add a
way to lose that guarantee (a key saved without its order, or the other way round), and the requirements rule out
Redis as the source of transactional data.

## Promotion Service API (Phase 13a): coupons

Platform admins create coupons; customers apply one to their cart; order-service reserves a use at checkout,
and the order's events decide whether that use counts.

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/api/v1/admin/coupons` | PLATFORM_ADMIN | Create a coupon (ACTIVE at once; 409 if the code exists) |
| GET | `/api/v1/admin/coupons` | PLATFORM_ADMIN | Search (code contains, status, discountType, restaurantId, validOn), paged |
| GET | `/api/v1/admin/coupons/{id}` | PLATFORM_ADMIN | Details with usage: uses by status, discount given, customers |
| PUT | `/api/v1/admin/coupons/{id}` | PLATFORM_ADMIN | Change the terms (with `version`: 409 on a concurrent change) |
| PUT | `/api/v1/admin/coupons/{id}/activate`, `/deactivate` | PLATFORM_ADMIN | Pause or resume, with a reason |
| GET | `/api/v1/admin/coupons/{id}/redemptions` | PLATFORM_ADMIN | Every use and what happened to it |
| GET | `/api/v1/admin/coupons/{id}/audit-logs` | PLATFORM_ADMIN | Audit trail |
| GET | `/api/v1/coupons?restaurantId=` | anyone | Coupons on offer today |
| PUT / DELETE | `/api/v1/cart/coupon` (order-service) | CUSTOMER | Apply a coupon to the cart / remove it |

**Coupon terms:** `FIXED` (an amount off) or `PERCENTAGE` (optionally capped by `maxDiscount`), a minimum order,
valid from/until (calendar days in the business time zone), an optional restaurant, a total usage limit and a
per-customer limit. The discount applies to the items; it is never more than the items cost.

**Pricing with a coupon (order-service):** items − discount = what the customer pays for the items; the delivery
fee is waived when that reaches 499.00, and tax (5%) is charged on it. Example: 2 × 299.00 = 598.00, minus
100.00 → 498.00 + delivery 40.00 + tax 24.90 = **562.90**.

**The life of a coupon use:**

```text
cart: PUT /cart/coupon      promotion-service checks it (422 with the reason: "Add items worth 200.00 more ...")
checkout: POST /orders      order-service reserves one use (RESERVED), key "checkout:{customer}:{Idempotency-Key}"
                            ──> the order stores the discount and the reservation id ──> OrderCreated
OrderCreated                promotion-service: RESERVED ──> APPLIED (linked to the order)
OrderStatusChanged DELIVERED                     APPLIED  ──> REDEEMED (final)
OrderCancelled              promotion-service: use given back (RELEASED); the customer may use the coupon again
no order within 30 minutes  clean-up job: use given back (EXPIRED)   (COUPON_RESERVATION_EXPIRY)
```

- **Limits hold under load:** a reservation locks the coupon's row (`SELECT ... FOR UPDATE`), so simultaneous
  checkouts take the last use one after the other: exactly one gets it. The usage counter is changed by SQL
  `UPDATE` statements, so it never collides with an admin editing the coupon (which uses optimistic locking).
- **A retried checkout** (same Idempotency-Key) gets the same reservation, not a second use. If the order could
  not be saved, order-service gives the use back at once; if even that fails, the clean-up job does it.
- **Events in any order:** `OrderCancelled` carries the reservation id, so a cancellation read before its
  `OrderCreated` still gives the use back. An `OrderCreated` that arrives after the clean-up gave the use back
  counts it again (the customer did get the discount).
- **The cart re-checks the coupon every time it is shown**, because prices and the coupon may change. A coupon
  the cart no longer qualifies for stays on the cart, shown with the reason, and blocks checkout until the cart
  qualifies again or the coupon is removed: the customer never pays a different price than the one shown.
- **promotion-service down:** carts still load (the coupon is shown as "cannot be checked right now"), checkout
  with a coupon answers 503, and removing the coupon lets the customer order without it.

## Admin Service API (Phase 13b): dashboard, reports, audit log

admin-service is the platform admin's read side. It keeps no business data of its own: the dashboard and reports
are put together from the numbers each service keeps about its own data (admin functions never read another
service's database), and the audit log is a copy of every service's audit records.

| Method | Path | What |
|---|---|---|
| GET | `/api/v1/admin/dashboard?days=7` | Cards (customers, restaurants, menu items, today's orders and revenue, total revenue, payments, partners online, deliveries waiting) and charts for the last 1-90 days |
| GET | `/api/v1/admin/reports/orders` | Totals, by status, by date, by restaurant, by delivery city |
| GET | `/api/v1/admin/reports/revenue?groupBy=DAY\|WEEK\|MONTH` | Revenue per day, ISO week or month, per restaurant, per payment method; online money collected and refunded |
| GET | `/api/v1/admin/reports/restaurants` | Active, new, suspended, pending; top, low-performing and most-cancelled restaurants |
| GET | `/api/v1/admin/reports/customers` | New, active (ordered) and inactive customers, top customers, order frequency (1, 2-3, 4-5, 6+) |
| GET | `/api/v1/admin/reports/deliveries` | Completed and failed deliveries, average delivery time, delayed now, partner performance |
| GET | `/api/v1/admin/calendar?from=&to=&types=ORDERS,COUPONS,RESTAURANTS` | Read-only calendar events for 1-400 days (both dates required, `to` included; `types` default: all): orders placed / delivered / cancelled per day, coupons over their validity dates (ACTIVE, INACTIVE or EXPIRED, with uses), restaurants registered per day. A source that fails is listed in `unavailable` and the rest is returned; if all fail: 503 |
| GET | `/api/v1/admin/audit-logs` | Search every admin action: service, adminUserId, action, entityType + entityId, requestId, fromDate/toDate, q (text); paged, newest first |
| GET | `/api/v1/admin/audit-logs/{id}` | One record, with the other records of the same request |
| GET | `/api/v1/admin/audit-logs/actions` | Actions per service with counts (filter lists) |
| POST | `/api/v1/admin/audit-logs/backfill` | Once: every service sends its earlier audit records (safe to repeat; audited) |

Reports take `fromDate`/`toDate` (calendar days in Asia/Kolkata; default: the last 30 days; at most 366) and
`limit` for their lists. All of it requires a `PLATFORM_ADMIN` access token.

**Where the numbers come from.** Each service got statistics endpoints over its own data (also usable directly):
`/api/v1/admin/users/stats`, `/restaurants/stats`, `/menu-items/stats`, `/orders/stats` (+ `/restaurants`,
`/customers`, `/locations`), `/delivery-partners/stats`, plus the existing `/payments/summary` and
`/deliveries/summary`. They count and sum in the database (`GROUP BY`), never by loading rows.

```text
admin ──> gateway ──> admin-service ──┬─> user-service        /admin/users/stats
                      (in parallel,   ├─> restaurant-service  /admin/restaurants/stats
                       as the admin)  ├─> catalog-service     /admin/menu-items/stats
                                      ├─> order-service       /admin/orders/stats ...
                                      ├─> payment-service     /admin/payments/summary
                                      └─> delivery-service    /admin/deliveries/summary, /admin/delivery-partners/stats
```

- **Called as the admin who asked:** admin-service passes the admin's own access token on, so every service checks
  the token and the role itself; admin-service gets no extra rights.
- **All at once, partial answers:** the calls run in parallel (one virtual thread each), so a dashboard takes as
  long as the slowest service. A service that fails or does not answer within 5 s (`app.call-timeout`) leaves its
  figures `null` (unknown, not zero) and is listed in `unavailable`; the rest is shown.
- **Definitions:** revenue is the total of *delivered* orders (by the day they were placed); "pending orders" are
  orders not finished right now; restaurant performance leaves out unpaid online orders that expired (the
  restaurant never saw them); days are calendar days in the business time zone (MySQL shifts timestamps by the
  zone's offset, and a period crossing a daylight-saving change is queried in parts).

**Calendar sources** (made from real data, nothing is stored): order-service `GET /api/v1/admin/orders/stats/calendar`
and restaurant-service `GET /api/v1/admin/restaurants/stats/registrations` (both `?from=&to=`, at most 400 days, one
grouped query, only days with a count), and the existing promotion-service coupon search with the new `activeFrom` /
`activeTo` filters (coupons whose validity overlaps the range; admin-service reads up to 200, in pages of 100). The day
counts use the dashboard's definitions: *placed* is every order created that day, *delivered* and *cancelled*
(CANCELLED + REJECTED) are counted on the day the order was placed (there is no delivered-at column). A coupon is
shown EXPIRED when it is ACTIVE but its last valid day is before today in the business time zone (decided in
admin-service's `CalendarService`).

**Platform-wide audit log.** Every service already saved an audit record in the same transaction as each admin
action. Since Phase 13b it also writes a copy as an `AdminActionAudited` event to its outbox (same transaction:
the action, its record and the event are saved together or not at all; notification- and promotion-service got
an outbox for this). admin-service stores each copy once (unique on service + record id, so Kafka repeats and
replays are harmless).

- **Immutable** three times over: no API changes or deletes records, the repository has no update or delete
  methods, and MySQL triggers refuse `UPDATE` and `DELETE` on `audit_records` even from a SQL client. (Creating
  triggers while binary logging is on needs `--log-bin-trust-function-creators=1`, set on the MySQL container.)
- **Backfill:** records saved before Phase 13b are loaded once with `POST /api/v1/admin/audit-logs/backfill`
  (each service replays its `admin_audit_log` table as events).
- The services keep their own per-entity audit views (e.g. `/api/v1/admin/orders/{id}/audit-logs`); the
  platform-wide log is for searching across services ("everything admin 1001 did today").

## Resilience (Phase 14a)

Resilience4j guards the calls where a failure elsewhere could hurt: synchronous calls between services, the
payment provider, and the API Gateway's forwarding. Kafka consumers need none of it (Kafka keeps the events until
they are processed), and calls inside one service get none.

| Where | Pattern | Why |
|---|---|---|
| Every call to another service (order → user/restaurant/catalog/promotion, catalog → restaurant, restaurant → user, payment → order, delivery and notification → user/restaurant/order, admin → all) | **Timeout** (2 s connect, 3-4 s read on every HTTP client) | A slow service cannot hold a request forever |
| same | **Retry**: one more attempt after 200 ms, only when the connection failed (refused, reset) | A service restarting is reached on the second try. Never after a timeout, an unknown host or an error answer (that only doubles the wait), and only for calls that are safe to repeat |
| same | **Circuit breaker**, one per target service: opens when half of the last 10 calls (at least 5) failed, or 80% took over 2.5 s; fails at once for 10 s; 3 trial calls decide whether it is back | While a service is down, callers get "unavailable" at once instead of waiting, and the service gets room to recover |
| same | **Bulkhead**: at most 20 calls to one service at a time (a 21st waits 100 ms) | A slow service cannot take every request thread, and with them the requests that do not need it |
| Payment provider (charges) | **Rate limiter**: 20 charges per second (`PAYMENT_PROVIDER_CHARGES_PER_SECOND`), waiting up to 0.5 s for a slot | Providers allow so many requests per second; above it they reject or block the merchant |
| Payment provider (charges and refunds) | **Circuit breaker**; **no retry** | A repeated charge could take the money twice |
| API Gateway, every route | Per service: **rate limit**, **timeouts** (2 s connect, 5-9 s answer), **retry** of GET requests that could not reach the service, **circuit breaker**, overall **time limit** 10 s, **fallback**: `503` (or `504`) ProblemDetail with `Retry-After` | Clients get a clear answer at once while a service is down, instead of waiting for connection attempts (details below) |

- **Fallbacks:** a call that is turned away (open circuit, full bulkhead) ends as the same `RemoteServiceException`
  as any other failure, so the existing fallbacks apply: the cart still loads when promotion-service is down
  (coupon "cannot be checked right now"), the admin dashboard shows the other services' figures, delivery and
  notification jobs try again later. Otherwise the answer is `503` with `Retry-After: 10`. A charge turned away by
  the payment provider's guard never reached the provider, so the payment is marked `FAILED` with the reason
  ("The payment provider is busy. Please try again in a moment") and the customer can pay again.
- **What counts as a failure:** only "no answer" (connection problems, timeouts, 5xx). Business answers such as a
  `404` or a coupon's `422` are successes; the gateway does not count a service's own `503` either (order-service
  answering 503 because promotion-service is down must not cut off all order traffic at the gateway).
- **Settings** are in each service's `application.yml` under `resilience4j:` (one `default` for all target
  services); the gateway's are in `CircuitBreakerConfig` and, per service, in its `application.yml`
  (`app.gateway.services`, below). State changes are logged once
  ("Circuit for catalog-service: State transition from CLOSED to OPEN").
- **See the state:** `GET /actuator/circuitbreakers`, `/actuator/circuitbreakerevents`, `/actuator/retries`,
  `/actuator/bulkheads` (and `/actuator/ratelimiters` on payment-service) on each service's own port, with an
  administrator's access token (health, info and prometheus need none). The metrics
  (calls, failure rate, state) are published through Micrometer for the dashboards of Phase 14b.

### API Gateway: traffic settings per service

Every gateway route protects its service with the same filters, in this order: **RequestRateLimiter** (throttling),
**CircuitBreaker**, **Retry**, plus two **timeouts** in the route's `metadata`. The numbers are in one table at the
end of [api-gateway/src/main/resources/application.yml](api-gateway/src/main/resources/application.yml)
(`app.gateway.services`); the routes only refer to it.

| Service | Requests/s per client | Burst | Connect timeout | Response timeout | GET retries | Why |
|---|---|---|---|---|---|---|
| user-service | 20 | 40 | 2 s | 5 s | 2 | Own data only |
| restaurant-service | 20 | 40 | 2 s | 8 s | 2 | Asks user-service |
| catalog-service | 40 | 80 | 2 s | 8 s | 2 | Asks restaurant-service; many reads while browsing |
| order-service | 20 | 40 | 2 s | 9 s | 2 | Checkout asks four services one after the other |
| payment-service | 10 | 20 | 2 s | 8 s | 2 | Asks order-service and the payment provider; few payments per customer |
| notification-service | 20 | 40 | 2 s | 5 s | 2 | Own data only |
| delivery-service | 20 | 40 | 2 s | 9 s | 2 | May ask order- and restaurant-service |
| search-service | 40 | 80 | 2 s | 5 s | 2 | Search-as-you-type |
| promotion-service | 20 | 40 | 2 s | 5 s | 2 | Own data only |
| admin-service | 10 | 20 | 2 s | 9 s | 2 | Each request asks six services (waits up to 5 s for them) |

- **Throttling:** per client IP and per service (each route has its own Redis bucket). Over it: `429 Too Many
  Requests` with the `X-RateLimit-*` headers. Login and registration keep their own per-minute budget.
- **Timeouts:** no connection within the connect timeout counts as "unreachable" (`503`); no answer within the
  response timeout gives `504 Service timeout`. Both count as failures for the service's circuit breaker.
  `app.gateway.response-timeout-limit` (10 s, the circuit breakers' time limit) stays above them as a safety net.
- **Retry:** GET requests only, and only when the service could not be reached (connection refused, not opened in
  time, or closed before the answer), after 100 ms and then 200 ms. Never after a timeout, an unknown host or an
  error answer (a `5xx` is passed on as it is), and never for POST/PUT/PATCH/DELETE, which could for example place
  an order twice. The retries happen inside the circuit breaker, which counts the request once.
- **Changing a value:** edit the table, or set an environment variable (upper case, `.` and `-` become `_`), e.g.
  `APP_GATEWAY_SERVICES_ORDER_SERVICE_RESPONSE_TIMEOUT=7000`. The gateway checks the table when it starts
  (`GatewayTrafficProperties`) and refuses to start, naming the service and the rule, when a value would misbehave:
  a burst below the rate, a response timeout not below the 10 s limit, retries outside 1-3, or connection attempts
  (connect timeout x (retries + 1)) that would not fit in the limit.
- **A new service:** copy a route, change the service name in it, and add a row to the table.

## Observability (Phase 14b)

Grafana at http://localhost:3000 (folder **Food Delivery**) shows metrics, traces and logs of every service, linked
to each other. It opens without login for viewing; sign in as `admin` with `GRAFANA_ADMIN_PASSWORD` (`.env`) to edit.

```text
services --/actuator/prometheus (scraped every 15 s)--> Prometheus --+
services --traces, OTLP http://alloy:4318--> Alloy --+--> Tempo -----+--> Grafana
                                                     +--> Jaeger (its own UI, http://localhost:16686)
containers' logs (Docker) --> Alloy ------------------> Loki --------+
```

Alloy is the one collector: it receives every trace and sends a copy to Tempo (linked with logs and metrics in
Grafana) and one to Jaeger (see [Jaeger](#jaeger-slow-requests-and-the-flow-of-a-request) below).

**Metrics** (Micrometer; every metric has `application="<service>"`):

| What the requirements ask to monitor | Metric |
|---|---|
| HTTP request count, status, response time | `http_server_requests_seconds_*` (histogram: p95 per service and endpoint) |
| JVM memory, CPU, threads | `jvm_memory_used_bytes`, `process_cpu_usage`, `jvm_threads_live_threads` |
| Database connection pool | `hikaricp_connections_active`, `hikaricp_connections_pending` |
| Kafka consumer lag, producer failures | `kafka_consumer_fetch_manager_records_lag_max`, `kafka_producer_record_error_total` |
| Events waiting in the outbox (Kafka down) | `outbox_events_waiting`, `outbox_events_published_total{topic}`, `outbox_publish_failures_total` |
| Elasticsearch and Redis health | `elasticsearch_cluster_health_status` (elasticsearch-exporter), `redis_up` (redis-exporter) |
| Circuit breakers | `resilience4j_circuitbreaker_state` |
| Order count | `orders_placed_total{payment_method}`, `orders_status_changes_total{status, by}` |
| Payment failures | `payments_completed_total{outcome, method}`, `refunds_completed_total{outcome}` |
| Delivery time | `deliveries_duration_seconds` (from food ready to delivered; histogram), `deliveries_completed_total{outcome}` |

Dashboards: **Food Delivery - Services** (traffic, errors, response times, JVM, database, Kafka, outbox, circuit
breakers, Elasticsearch and Redis, recent warnings and errors) and **Food Delivery - Business** (orders, outcomes,
payment failures, refunds, delivery times). They are files in `infrastructure/observability/grafana/dashboards`;
Grafana re-reads them every 30 s (changes made in the UI are not saved: export the JSON and replace the file).

**Traces** (OpenTelemetry through Micrometer Tracing, W3C `traceparent`): one business transaction is one trace,
across the gateway, REST calls between services and Kafka:

```text
api-gateway -> order-service -> user-, restaurant-, catalog-, promotion-service (REST)
                    |
                    +-- outbox --> Kafka --> payment-, notification-, delivery-, promotion-service --> their events ...
```

The outbox needed one addition: the relay sends events later, from its own thread, so every outbox row keeps the
trace context of the request that wrote it (`trace_parent`), and the relay sends the event in that trace. Kafka's
observation (`spring.kafka.template/listener.observation-enabled`) carries it on in the message headers. Every
request is traced in development (`TRACING_SAMPLING_PROBABILITY`, default `1.0`); traces are kept 2 days.
The tests switch off only the OTLP exporter (`management.tracing.export.otlp.enabled: false`): in Spring Boot 4,
`management.tracing.export.enabled: false` would also switch off W3C propagation, and with it the trace headers.

**Logs**: every log line carries the trace and span ids and the request id, and the user, event, order, payment
and delivery ids when they are known (from the identity header, the URL path, or the event being processed):

```text
2026-10-07T20:16:43.622Z  INFO 1 --- [order-service] [nio-8084-exec-1] [4bf92f3577b34da6a3ce929d0e0e4736,00f067aa0ba902b7] [e2e-1] user=7 order=123 c.f.o.service.CustomerOrderService : ...
```

Alloy reads the containers' logs from Docker and sends them to Loki with the labels `service`, `container` and
`level`, and the trace, span and request ids as searchable fields: `{service="order-service"} | trace_id="..."`.
In Grafana a log line opens its trace, a trace shows its log lines, and a point on a response-time chart can open
the trace of that request (exemplars).

Following one request (every response carries its `X-Request-Id`):

```text
Grafana > Explore > Loki:  {service="order-service"} | request_id="<X-Request-Id>"
  -> the line's "TraceID" link opens the trace in Tempo: every service it reached, over REST and Kafka
  -> in the trace, "Logs for this span" lists the log lines of all those services
```

### Jaeger: slow requests and the flow of a request

Jaeger (http://localhost:16686, version 2.21) gets the same traces as Tempo and keeps them 2 days. It is the place
to look for slow APIs and bottlenecks:

| Question | Where in Jaeger |
|---|---|
| Which APIs are slow? | **Monitor** tab: per service, every operation (e.g. `http get /api/v1/admin/dashboard`) with its P95 latency, request rate and error rate; sort by latency |
| Show me the slow requests | **Search**: service `api-gateway` (every client request starts there), *Min Duration* e.g. `500ms`, then *Sort: Longest First* |
| Where does a request spend its time? | Open a trace: the timeline shows every span (the gateway, each service's endpoint, its REST calls to other services, Kafka hops, Redis commands) with its duration; the critical path is highlighted. Time inside a span that no child span explains is the service's own work, mostly its database queries (those are not traced separately) |
| What did one request touch? | The same trace: services, operations, and the Kafka hops to the services that reacted to its events |
| Is it slower than usual? | Select two traces of the same operation in the search results and **Compare** them |

The Monitor tab's figures come from Jaeger's span metrics (the `spanmetrics` connector, in
`infrastructure/observability/jaeger/config.yaml`), which Prometheus scrapes (`traces_span_metrics_*`) and Jaeger
reads back. Grafana has Jaeger as a data source too (Explore → Jaeger).

Work that moves to another thread keeps its trace: admin-service's parallel calls (the dashboard and reports ask
six services at once) and the Redis cache calls (catalog and restaurant) carry the caller's context with them
(Micrometer `ContextSnapshot`), so they appear inside the request's trace, not as separate traces.

## Code quality (Phase 15a)

`mvnw verify` checks every project (the 11 services and `event-contracts`) with the same rules, and fails on a
finding. Details, and how to justify an exception: [quality/README.md](quality/README.md).

| Tool | What it guards |
|---|---|
| **ArchUnit** (`ArchitectureTest` in each service) | The requirements' production coding rules as tests: no repository access from controllers, no JPA entity in the REST API, no `@Transactional` controllers, no EAGER relationships, no `System.out`, constructor injection, dependencies flowing inward (controller -> service -> repository / client / events) |
| **Checkstyle** | The code's style and naming (at most 130 columns, import order), no `catch (Exception)` unless justified, no `process()`/`handle()`/`execute()` method names, no `java.util.Date` or Lombok `@Data` |
| **SpotBugs** | Bug patterns in the compiled code (null dereferences, unsafe synchronization, ...) |
| **JaCoCo** | Line coverage report (`target/site/jacoco/index.html`); each service has a minimum it may not drop below |
| **SonarQube** | All of it in one place with history, and the **Food Delivery** quality gate per project |

```powershell
docker compose --profile quality up -d   # SonarQube on http://localhost:9000
.\quality\sonar-scan.ps1                 # analyse every project, print each quality gate
```

## Testing (Phase 15b)

Most tests are fast and local; fewer, slower ones check that the pieces work together:

```text
            /  E2E  \          e2e-tests: business flows across the running services, through the API Gateway
           / per-service \     Testcontainers integration tests: real MySQL, Kafka, Redis, Elasticsearch, Mailpit
          /  slice tests  \    MockMvc: access rules, validation, ProblemDetail errors; ArchUnit: architecture rules
         /   unit tests    \   JUnit 5 + Mockito: business rules (state transitions, prices, limits, retries)
```

`mvnw verify` in a service runs everything up to its integration tests (Docker needed, nothing else). The
end-to-end tests need the whole platform:

```powershell
.\start-local.ps1 -Detached -NoAi        # the services, until UP (next to another project: -PortOffset 10000)
cd e2e-tests
.\mvnw.cmd verify                      # the business flows (about a minute)
#   with -PortOffset 10000:  $env:E2E_BASE_URL='http://localhost:18080'; $env:E2E_MAILPIT_URL='http://localhost:18025'
cd ..; .\start-local.ps1 -Stop          # stop the services
.\mvnw.cmd verify -Presilience         # stops services' containers: only with the services in Docker
.\mvnw.cmd verify -Pobservability      # one checkout in Prometheus, Loki and Tempo
.\mvnw.cmd verify "-De2e.baseUrl=http://staging-host:8080"   # another environment (or E2E_BASE_URL)
```

| Flow (e2e-tests) | Services it crosses | What it proves |
|---|---|---|
| `CheckoutToDeliveryE2ETest` | gateway, user, restaurant, catalog, order, payment, delivery, delivery-partner, notification | UPI order paid, prepared, offered to the city's partner, delivered; timeline, tracking, in-app and e-mail notifications; cash orders and the cash check; an idempotent checkout |
| `DeliveryPartnerE2ETest` | gateway, user, delivery-partner, delivery, order, admin | A suspended partner gets no deliveries (another partner of the city does), the work status copied back, reactivation, audit trail in both logs |
| `CancellationAndRefundE2ETest` | order, payment, restaurant, notification | A declined paid order is refunded by the saga; customers cancel only before acceptance |
| `CouponE2ETest` | promotion, order | Minimum order, discount at checkout, the use applied by OrderCreated and given back by OrderCancelled, usage limits |
| `AdminE2ETest` | admin and the services it asks | Admin actions in the platform-wide audit log (who, why, request id); dashboard and reports from the owning services |
| `SecurityE2ETest` | user, gateway | Sign-in returns a JWT and sets the area's HttpOnly refresh cookie; refresh rotates it (a used one again: 409); sign-out ends it (refresh: 401); another kind of account gets 403 `WRONG_ACCOUNT_TYPE` and no cookie |
| `SearchE2ETest` | restaurant, catalog, search | Searchable only once live; typo-tolerant search, facets, price filters; suspended restaurants disappear |
| `ResilienceE2ETest` (`-Presilience`) | gateway, order, catalog, promotion | A dependency down: 503 with `Retry-After`, other APIs keep working; a whole service down: the gateway answers for it; recovery |
| `ObservabilityE2ETest` (`-Pobservability`) | all, plus Prometheus, Loki, Tempo | All services scraped; the order counted exactly once; its trace (found from its request id in the logs) spans the services |

Every flow creates its own owner, restaurant, customers and partner in a city of its own, through the public API,
so the flows do not depend on existing data or on each other.

## Front-App (Phase 16): the React web application

`front-app/` is the platform's web application (React + TypeScript, Vite). It calls only the API Gateway. The admin
area (under `/admin`) follows the "Sedap" admin dashboard design; [front-app/docs/design-analysis.md](front-app/docs/design-analysis.md)
compares every part of that design with the backend (parts the platform has no data for, such as reviews, chat and
calendar, are left out rather than filled with invented figures). How to run it, the environments, the folder
structure and the coding rules are in [front-app/README.md](front-app/README.md).

```powershell
cd front-app
npm ci
npm run dev        # http://localhost:5180 (start-local.ps1 opens it in its own tab)
npm run verify     # type check, lint, format check, tests, build
```

- **Screens so far:** Dashboard; Order List and Order Detail (cancel with a reason); Customers and Customer Detail
  (activate, deactivate, change role; a customer's orders); Foods and Food Detail (block, release, change category,
  remove); Restaurants (approve, reject, activate, deactivate, suspend); Categories (create, rename, reorder,
  activate, deactivate, delete); Coupons (create, change, pause, resume, usage); Payments and Refunds (refund all or
  part); Deliveries and delivery partners (late deliveries, reassign, suspend, reactivate); Analytics (five reports,
  saved as CSV); Audit Logs (every admin action, with the other changes of the same request); Notifications (my
  inbox, every notification, resend); Search. Customer area (Phase 17): Sign in and Register (password checked by
  user-service, no token yet), Home, Restaurants, a restaurant's page with its hours and menu, Search with
  suggestions; Add to cart, Cart with coupons, Checkout, Payment (card, UPI, wallet; cash on delivery), Offers, My
  addresses; My orders, the order page with progress, delivery tracking and Cancel, My profile, Notifications. Every admin action asks for a reason (kept in the services' audit logs) and shows the service's
  refusal in the dialog.
- **Restaurant onboarding:** a new owner signs up at `/owner/register` (**Become a restaurant partner**: a
  `RESTAURANT_ADMIN` account, signed in straight away) and registers the restaurant at `/owner/restaurant/new`; it
  starts as Pending. An administrator approves or rejects it under Restaurants (`/admin/restaurants`), then activates
  it; only then do customers see it. Administrators do not create restaurants (no admin API for it, as in the
  requirements' onboarding workflow). Code: `src/pages/Owner/OwnerRegisterPage.tsx` and the shared
  `src/components/business/Auth/StaffRegister.tsx` (also behind the partner sign-up).
- **Delivery partner area** (`/partner`, for `DELIVERY_PARTNER` accounts): Sign in and **Become a delivery partner**
  (sign-up with a phone number); **Vehicle and city** (needed before going online: deliveries are offered from
  restaurants in that city); **Today**: an online switch in a status-coloured hero, the delivery offered to the partner
  as a ticket with a countdown ring (accept within 2 minutes, or decline with an optional reason), the route with
  Navigate and Call, then "I have picked up the food" and "Mark as delivered" (cash on delivery: the exact amount is
  confirmed); **My deliveries** (all, delivered, cancelled) as cards grouped by day, and one delivery with its
  history. Mobile-first: a tab bar at the bottom on phones and tablets, in the top bar on wide screens. New offers appear by themselves (live updates, and the page asks again every 5 s while the
  partner is online). The profile comes from delivery-partner-service, going online and the deliveries from
  delivery-service; like the other areas, the signed-in partner is kept for the browser tab and the area's access
  token says who is acting. Code: `src/pages/Partner`, `src/layouts/partner`,
  `src/components/business/PartnerArea`, `src/hooks/api/partnerHooks.ts`, `src/services/partnerService.ts`.
- **Environments:** Development, Demo, UAT, Pre-Production and Production, one `.env.<mode>` file each
  (`npm run build:uat` and so on). Only settings differ: the gateway address and the feature flags.
- **Light and dark mode:** the sun/moon button in both headers (`ThemeToggle`). The first visit follows the system's
  setting; the choice is kept in this browser (`localStorage`, key `front-app.theme`) and also applies in other open
  tabs. The theme is `<html data-theme="light|dark">`, set before the first paint by a small script in
  `index.html` (no white flash). Dark mode is only other **values for the colour tokens** in `src/index.css` (the
  "Dark mode" block at the end): components never contain their own dark colours. So write `bg-surface` (not
  `bg-white`), `text-brand-text` / `-strong` / `-deep` (not `text-brand-600`/`700`/`900`), `text-danger-text`,
  `text-warning-text`, `text-on-accent` (dark text on amber) and `bg-scrim` (the veil behind dialogs). The theme colour
  is #39C576 (`brand-500`): bright fills (buttons, active items, steps, badges) carry `text-on-brand`, its deep-green
  foreground (white is unreadable on it); white text goes on the deep shades 600-900 only (hero banners). Charts read their colours from the same CSS
  variables (`utils/chartColors.ts`), so they follow the theme without being redrawn. Code: `src/theme`
  (`ThemeProvider`, `useTheme`, `applyTheme`, `themeStorage`).
- **Signing in:** every area has its own sign-in page and its own session; the area's access token is the only
  identity the application sends (see [Security (JWT)](#security-jwt)). There is no built-in user.
- **Errors:** every failed call becomes an `ApiError` with a message safe to show (wording in
  `utils/errorMessages.ts`); 4xx answers show the service's own business message, 5xx a general one, both with the
  request id as reference. Partial dashboard answers name the missing services.

## Kafka events (Phase 7) and the Outbox (Phase 8)

Events are Avro records defined once in `event-contracts` (`src/main/avro/*.avsc`) and registered in
Schema Registry, which refuses a new schema version that would break existing consumers.

| Topic | Event | Producer | Consumers | Key |
|---|---|---|---|---|
| `fooddelivery.user.registered` | `UserRegistered` | user-service (sign-up) | notification-service, delivery-partner-service | userId |
| `fooddelivery.restaurant.changed` | `RestaurantChanged` (submitted, profile updated, status changed, republished; address since 1.3.0, cuisines and description since 1.4.0) | restaurant-service | catalog-service, notification-service, delivery-service, search-service | restaurantId |
| `fooddelivery.catalog.category-changed` | `CategoryChanged` (created, updated, status changed, reordered, deleted, republished) | catalog-service | search-service | categoryId |
| `fooddelivery.catalog.menu-item-changed` | `MenuItemChanged` (created, updated, price, availability, status, category, removed, republished) | catalog-service | search-service | menuItemId |
| `fooddelivery.order.created` | `OrderCreated` (items and amounts; customer and drop-off since 1.3.0; coupon, discount and reservation id since 1.5.0) | order-service | payment-service, delivery-service, promotion-service | orderId |
| `fooddelivery.order.status-changed` | `OrderStatusChanged` (every status the order enters) | order-service | notification-service, delivery-service, promotion-service | orderId |
| `fooddelivery.order.cancelled` | `OrderCancelled` (by customer, admin or system; rejected by restaurant; coupon reservation id since 1.5.0) | order-service | payment-service, notification-service, delivery-service, promotion-service | orderId |
| `fooddelivery.order.payment-rejected` | `OrderPaymentRejected` (a payment completed after the order was cancelled) | order-service | payment-service | orderId |
| `fooddelivery.payment.status-changed` | `PaymentStatusChanged` (SUCCESS, FAILED, REFUNDED) | payment-service | order-service, notification-service | orderId |
| `fooddelivery.delivery.status-changed` | `DeliveryStatusChanged` (every step of a delivery) | delivery-service | order-service, notification-service | orderId |
| `fooddelivery.delivery-partner.changed` | `DeliveryPartnerChanged` (registered, profile updated, suspended, activated; since 1.7.0) | delivery-partner-service | delivery-service (dispatch list) | partnerUserId |
| `fooddelivery.delivery.partner-status-changed` | `DeliveryPartnerStatusChanged` (online, offline, busy, suspended; since 1.7.0) | delivery-service | delivery-partner-service | partnerUserId |
| `fooddelivery.admin.action-audited` | `AdminActionAudited` (a copy of an audit record; since 1.6.0) | every service with admin actions | admin-service | entityType:entityId |

Every topic has 3 partitions; the key keeps all events of one entity in one partition, in order.
Each consumer topic has a dead-letter topic `<topic>.DLT`.

**Transactional outbox (Phase 8).** A service never sends to Kafka inside a request. It writes the event to
its own `outbox_events` table in the same database transaction as the change, so the change and its event
are saved together or not at all. `OutboxRelay` then publishes waiting events every 500 ms, oldest first,
and marks each one once Kafka has acknowledged it. If Kafka is down, requests still succeed and events wait
(the relay retries with a growing pause, up to 10 s). Published events are kept 7 days for tracing.

```text
 request ──> service ──> one DB transaction: orders row + outbox_events row ──> commit
                                                                  │
 OutboxRelay (every 500 ms) ── reads unpublished, oldest first ───┘──> Kafka ──> mark published
```

**Checkout saga (Phase 8)**, choreographed by events (no service tells another what to do):

```text
customer: POST /orders        order-service:  order PAYMENT_PENDING ──> OrderCreated
                              payment-service: keeps a copy of the order (amount, status)
customer: POST /payments      payment-service: charge ──> PaymentStatusChanged(SUCCESS | FAILED)
                              order-service:   SUCCESS ──> order PLACED (the restaurant sees it)
restaurant accepts / rejects  order-service:   REJECTED ──> OrderCancelled(paymentStatus PAID)
restaurant: ready             order-service:   READY_FOR_PICKUP ──> OrderStatusChanged
                              delivery-service: delivery PENDING ──> offered to a partner ──> DeliveryStatusChanged
partner: pickup / deliver     order-service:   OUT_FOR_DELIVERY / DELIVERED (cash on delivery: PAID)

compensations:
  OrderCancelled with PAID           ──> payment-service refunds ──> PaymentStatusChanged(REFUNDED)
                                         ──> order-service shows paymentStatus REFUNDED
  SUCCESS for a cancelled order      ──> order-service publishes OrderPaymentRejected ──> refund
  unpaid after 15 minutes            ──> order-service cancels (SYSTEM) ──> OrderCancelled
  OrderCancelled during the delivery ──> delivery-service cancels the delivery and frees the partner
```

Design notes:

- **At least once, applied once:** the relay may send an event twice (for example if the service stops
  right after Kafka acknowledged it). Consumers record every applied event id in `processed_events`, in the
  same transaction as the change, and skip repeats. catalog-service's restaurant copy is idempotent by
  version instead, and automatic refunds by a fixed key per payment.
- **Every event carries an `eventId`, `occurredAt` and the `requestId`** of the request that started it;
  consumers log with that request id, so one checkout can be followed through all services.
- **Local copies instead of synchronous calls:** catalog-service keeps restaurants (`restaurant_snapshots`),
  payment-service keeps orders awaiting payment (`payable_orders`), delivery-service keeps drop-off and
  pickup addresses (`order_details`, `pickup_points`). Menus work while restaurant-service is down; orders
  known from their event can be paid while order-service is down, and a started delivery can be completed
  while order-service or restaurant-service is down (the order catches up from the waiting events).
- **Eventual consistency:** after a payment the order moves to PLACED a moment later (usually under a
  second). A change made at the same moment as an event (e.g. cancelling while the payment result is being
  applied) is refused with 409 "Concurrent update" (optimistic locking); the client reloads and retries.
- **Failures do not block a topic:** a failing event is retried twice, then copied to `<topic>.DLT` with
  the error in its headers. An event that is not valid Avro goes to the DLT at once.
- **Topics are created by the services** (Kafka's auto-create is off): producers create the topics they
  write, consumers the topics they read and their dead-letter topics.
- **Avro in the outbox:** the event is stored as Avro bytes together with the schema it was written with,
  so a stored event can be published even after the event class moved on to a newer schema version.

Inspect everything in Kafka UI: http://localhost:8090 (topics, messages decoded with their Avro
schema, consumer groups and lag). Schemas: http://localhost:8091/subjects. Waiting events:
`SELECT * FROM outbox_events WHERE published_at IS NULL` in each service's database.

## Implementation progress

- [x] Phase 1: Foundation + API Gateway
- [x] Phase 2: User Service (Auth/User)
- [x] Phase 3: Restaurant Service
- [x] Phase 4: Catalog Service
- [x] Phase 5: Order Service
- [x] Phase 6: Payment Service
- [x] Phase 7: Kafka + Schema Registry
- [x] Phase 8: Saga + Outbox
- [x] Phase 9: Notification Service
- [x] Phase 10: Delivery Service
- [x] Phase 11: Search Service + Elasticsearch
- [x] Phase 12: Redis
- [x] Phase 13: Admin completion
  - [x] 13a: Promotion Service (coupons), applied in the cart and at checkout
  - [x] 13b: Admin Service: dashboard, reports, platform-wide audit log
- [x] Phase 14: Resilience + Observability
  - [x] 14a: Resilience4j: timeouts, retry, circuit breakers, bulkheads, rate limiter, fallbacks (services and gateway)
  - [x] 14b: Observability: metrics (Prometheus), dashboards (Grafana), tracing (OpenTelemetry, Tempo), logs (Loki)
- [x] Phase 15: Integration testing + code quality
  - [x] 15a: Code quality: ArchUnit, Checkstyle, SpotBugs, JaCoCo, SonarQube
  - [x] 15b: Integration testing: end-to-end flows across services (e2e-tests)
- [x] Phase 16: Frontend: admin area (Front-App, following the "Sedap" admin design). The design is an admin
  dashboard, so the admin area comes first and the customer area follows (Phase 17)
  - [x] 16a: Project setup, environments, layout, common components, error handling; Dashboard
  - [x] 16b: Orders, Customers, Foods (the design's other screens: lists, details, audited admin actions), admin search
  - [x] 16c: Restaurants (approvals, status changes), Categories (create, rename, reorder, delete), Coupons (create,
    change, pause, usage), Payments and Refunds (refund with an idempotency key)
  - [x] 16d: Deliveries and delivery partners (reassign, suspend), Analytics (reports, CSV), Audit Logs,
    Notifications (inbox, resend)
- [x] Phase 17: Frontend: customer area (Home, Restaurants, Search, Menu, Cart, Checkout, Orders, Tracking, Profile,
  Addresses, Offers), under `/`
  - [x] 17a: Sign in and Register (no token yet; the customer area acts as the signed-in customer), customer layout,
    Home, Restaurants, Restaurant page and menu, Search with suggestions
  - [x] 17b: Cart, Addresses, Checkout, Payment, Offers
  - [x] 17c: My orders, Order tracking, Cancel, Profile, Notifications
- [x] Delivery Partner Service (added after Phase 17, on request): partners' registration, profile and suspension moved
  out of delivery-service into their own service (port 8099, `delivery_partner_db`); delivery-service keeps
  availability and assignment; same URLs through the gateway; event-contracts 1.7.0
- [x] Phase 18: Security (JWT): user-service issues RS256 access tokens and rotating refresh cookies (one per area);
  the web app, the end-to-end tests and the AI assistant send them; the gateway and every service check them;
  internal APIs need the services' shared token; the `X-User-*` headers are gone
