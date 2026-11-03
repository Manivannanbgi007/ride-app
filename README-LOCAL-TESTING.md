# Running and testing the platform locally

How to start every microservice on one machine, test the platform by hand through the API Gateway, and see what
happens inside (Kafka, Elasticsearch, Redis, e-mails, logs, traces). The project overview is in [README.md](README.md).

## 1. Prerequisites

- Docker Desktop (running)
- JDK 21 and Node.js 22.12+ (the services and the web application run on this machine; Docker runs the
  infrastructure only)
- The project's `.env` file: `copy .env.example .env` the first time (local passwords; never committed)

## 2. Starting the platform

**One command does all of it:**

```powershell
cd "D:\Drive E\dev\app"
.\start-local.ps1           # infrastructure in Docker, every service and Front-App in its own Windows Terminal tab
.\start-local.ps1 -Build    # the same, after rebuilding every service's jar (after code changes)
.\start-local.ps1 -Detached # the services only, without windows, one after the other until UP (logs\<service>.log)
.\start-local.ps1 -Stop     # stop what -Detached started
.\start-local.ps1 -Only user-service,api-gateway -NoAi   # some services only, without the AI assistant (less memory)
```

It checks that the platform's ports are free, starts the infrastructure, then opens one Windows Terminal window with
a tab per service (its log; Ctrl+C or closing the tab stops it), a `front-app` tab (the web application's
development server; its packages are installed the first time) and a `status` tab that shows every service's health.
The gateway started this way allows 120 logins and registrations per minute, so the test scripts and E2E tests can
register many users from this machine. Tracing is off (the observability tools are disabled).

The same steps by hand:

1. Start the infrastructure: `docker compose up -d` (MySQL, Kafka, Schema Registry, Mailpit, Redis, Elasticsearch,
   phpMyAdmin, Ollama).
2. Install the shared event library once: `cd event-contracts; .\mvnw.cmd install`
3. Start each service in its own terminal, from its folder, with `.\mvnw.cmd spring-boot:run`, in this order:
   user-service, restaurant-service, catalog-service, promotion-service, order-service, payment-service,
   notification-service, delivery-partner-service, delivery-service, search-service, admin-service, and
   **api-gateway last**.
4. Check the health of all of them:
   ```powershell
   8080,8081,8082,8083,8084,8085,8086,8087,8088,8089,8092,8094,8096,8099 | ForEach-Object {
       "{0}: {1}" -f $_, (Invoke-RestMethod "http://localhost:$_/actuator/health").status }
   ```

Services started this way read their passwords from `.env` themselves; their logs are in their terminals.

## 3. Next to another project (other ports)

Another project on this machine may already use some of the platform's ports (its own Kafka on 9092, Redis on 6379,
Elasticsearch on 9200, a gateway on 8080). `start-local.ps1` checks every port first: when another program holds
one, it names the port and the program and starts nothing, so neither project is disturbed. Then move every port
of the platform by an offset:

```powershell
.\start-local.ps1 -PortOffset 10000          # or FD_PORT_OFFSET=10000 in .env (then no switch is needed)
```

| With offset 10000 | Address |
|---|---|
| API Gateway | http://localhost:18080 |
| Front-App | http://localhost:15180 |
| Services | 18081 (user) ... 18099 (delivery-partner): each default port + 10000 |
| Kafka / Schema Registry / Redis | `localhost:19092` / http://localhost:18091 / `localhost:16379` |
| Mailpit / Elasticsearch / phpMyAdmin | http://localhost:18025 / http://localhost:19200 / http://localhost:18095 |

The script tells every service, the gateway and the web application these addresses (also of services it does not
start, so no call ever reaches the other project); CORS follows the web application's port. A port set in `.env`
(`MYSQL_PORT`, `KAFKA_HOST_PORT` and so on, see `.env.example`) is used as it is. In the `.http` files change
`@gateway`; for the end-to-end tests set `E2E_BASE_URL` and `E2E_MAILPIT_URL` (section 8).

## 4. Tools and URLs

| Tool | URL / address | Login |
|---|---|---|
| **Front-App** (web application: customer area at `/customer`, admin dashboard) | http://localhost:5180/admin/dashboard | sign in at `/admin/sign-in` with the bootstrap admin (`BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` of `.env`); the header has **Sign out** |
| **API Gateway** (every API call) | http://localhost:8080 | an access token, see section 5 |
| **Kafka UI** | http://localhost:8090 | none |
| Kafka broker (for clients) | `localhost:9092` | none |
| Schema Registry | http://localhost:8091/subjects | none |
| **Elasticsearch** | http://localhost:9200 | none (security is off in development) |
| **Kibana** (Elasticsearch UI) | http://localhost:5601 | none |
| **Redis** | `redis://localhost:6379` | no password |
| Mailpit (every e-mail sent) | http://localhost:8025 | none |
| MySQL | `localhost:3330` | `root` / `MYSQL_ROOT_PASSWORD` from `.env` |
| **phpMyAdmin** (MySQL UI) | http://localhost:8095 | `root` / `MYSQL_ROOT_PASSWORD`, or a service's user (e.g. `order_svc` / `ORDER_DB_PASSWORD`) |
| Grafana (dashboards, traces, logs) | http://localhost:3000 | view without login; `admin` / `GRAFANA_ADMIN_PASSWORD` to edit |
| **Jaeger** (traces: slow APIs, request flow) | http://localhost:16686 | none |
| Prometheus | http://localhost:9090 | none |
| Swagger UI per service | http://localhost:8081/swagger-ui.html … :8089, and :8092 | none |
| SonarQube (optional) | http://localhost:9000, after `docker compose --profile quality up -d` | `admin` / `SONARQUBE_ADMIN_PASSWORD` |

**Kibana** (http://localhost:5601) shows search-service's Elasticsearch indexes. The data views `restaurant`, `food`
and `category` are created for you when Kibana starts.

- **Discover** (menu → Analytics → Discover): pick a data view, search with KQL (`name : biryani and foodType : VEG`),
  open a document to see every field.
- **Dev Tools** (menu → Management → Dev Tools): run queries, e.g. `GET food/_search?q=biryani`, `GET _cat/indices?v`,
  `GET restaurant/_mapping`.
- **Stack Management → Index Management**: the indexes, their size and settings.

The indexes belong to search-service, which fills them from Kafka events. Change data through the platform's APIs:
an edit made in Kibana is overwritten by the next event, and `POST /api/v1/admin/restaurants/events/republish` (and
the same for categories and menu items) rebuilds the indexes. Without Kibana, the REST API answers in the browser
too: http://localhost:9200/_cat/indices?v, http://localhost:9200/food/_search?pretty&q=biryani.

**phpMyAdmin** (http://localhost:8095) shows the MySQL databases, one per service (`user_db`, `restaurant_db`,
`catalog_db`, `order_db`, `payment_db`, `notification_db`, `delivery_db`, `promotion_db`, `admin_db`). Log in as
`root` with `MYSQL_ROOT_PASSWORD` from `.env` to see all of them, or as one service's user (for example `order_svc`
with `ORDER_DB_PASSWORD`) to see only its database. Useful tables: `orders` and `order_timeline` (order-service),
`outbox_events` (in the services that publish events: waiting for or sent to Kafka), `processed_events` (in the
services that consume events: the ones already handled), `audit_records` (admin-service: the platform-wide audit log, which the database refuses to change).
Prefer the APIs for changes: they keep the services' events, caches and audit log in step.

**Redis** has no web UI in this stack either. Use the command line, or the free Redis Insight app connected to
`localhost:6379`:

```powershell
docker exec -it fd-redis redis-cli
PING
SCAN 0 MATCH catalog:v1:* COUNT 100          # cached menus and categories (after someone views a menu)
SCAN 0 MATCH restaurant:v1:* COUNT 100       # cached restaurant pages
SCAN 0 MATCH request_rate_limiter* COUNT 100 # the gateway's rate-limit buckets
TTL <key>
```

## 5. Calling the API

- Base URL: `http://localhost:8080` (the API Gateway).
- **Who is calling**: `Authorization: Bearer <accessToken>`, the token `POST /api/v1/auth/login` returns (valid 15
  minutes; sign in again after that). Send `"role"` with the login to sign in to one area only: `CUSTOMER`,
  `RESTAURANT_ADMIN`, `DELIVERY_PARTNER` or `PLATFORM_ADMIN`. The gateway removes any `X-User-Id` / `X-User-Role` a
  client sends and refuses a token that is not valid (401); `/api/v1/admin/**` needs an administrator's token.
- The first platform admin is `admin@fooddelivery.local` / `ChangeMe123` (`BOOTSTRAP_ADMIN_*` in `.env`).
- **`Idempotency-Key`** header (8–64 letters, digits, `-` or `_`) is required on `POST /api/v1/orders`,
  `POST /api/v1/payments` and admin refunds. Sending the same request again with the same key does not repeat it.
- `X-Request-Id` (optional) follows the request through every service's logs and is echoed in the response.
- **Rate limits** (per signed-in user, otherwise per IP): login and registration 10 per minute (120 in Docker Compose); every other service has its own budget, mostly 20 per second (catalog and search 40, payment and admin 10). A 429 answer means: wait. Limits, timeouts and retries per service: `app.gateway.services` in the gateway's `application.yml` (README, "API Gateway: traffic settings per service").
- Seeded food categories: 1 Pizza, 2 Burgers, 3 Biryani, 4 Chinese, 5 South Indian, 6 North Indian, 7 Desserts,
  8 Beverages.

Each service also has a `.http` file (VS Code REST Client) with ready-made requests. Run them in this order:
user, restaurant, catalog, promotion, order, payment, notification, delivery, search, admin.

## 6. Walkthrough: one order from restaurant sign-up to delivery (PowerShell)

Run the blocks one after the other. Each run uses new e-mails and a city of its own (so the delivery goes to this
run's rider), so the walkthrough can be repeated.

```powershell
# Setup
$api = "http://localhost:8080"
function Send($Method, $Path, $Body = $null, $Headers = @{}) {
    $p = @{ Method = $Method; Uri = "$api$Path"; Headers = $Headers; ContentType = "application/json" }
    if ($null -ne $Body) { $p.Body = ($Body | ConvertTo-Json -Depth 6) }
    Invoke-RestMethod @p
}
# Signs in to one area and returns the headers of that person's calls
function SignIn($Email, $Password, $Role) {
    $login = Send POST "/api/v1/auth/login" @{ email = $Email; password = $Password; role = $Role }
    @{ Authorization = "Bearer $($login.accessToken)" }
}
$s = Get-Random -Minimum 10000 -Maximum 99999
$city = "Mysuru-$s"

# 1. The admin logs in
$admin = SignIn "admin@fooddelivery.local" "ChangeMe123" "PLATFORM_ADMIN"

# 2. An owner submits a restaurant (PENDING); the admin approves and activates it
$ownerId = (Send POST "/api/v1/auth/register" @{ email = "owner$s@example.com"; password = "Owner1234"; fullName = "Arjun Mehta"; role = "RESTAURANT_ADMIN" }).id
$owner = SignIn "owner$s@example.com" "Owner1234" "RESTAURANT_ADMIN"
$hours = "MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY" | ForEach-Object { @{ dayOfWeek = $_; opensAt = "00:00"; closesAt = "23:59" } }
$restaurant = Send POST "/api/v1/restaurants/mine" @{
    name = "Spice Hub $s"; cuisines = @("Biryani", "North Indian"); phone = "+919876543210"; email = "spicehub$s@example.com"
    address = @{ line1 = "12 MG Road"; city = $city; state = "Karnataka"; postalCode = "570001"; country = "India" }
    legalBusinessName = "Spice Hub LLP"; foodLicenseNumber = "LIC$s"
    documents = @(@{ type = "FOOD_LICENSE"; url = "https://docs.example.com/license-$s.pdf" }); operatingHours = @($hours) } $owner
$rid = $restaurant.id
Send PUT "/api/v1/admin/restaurants/$rid/approve" @{ reason = "Documents verified" } $admin
Send PUT "/api/v1/admin/restaurants/$rid/activate" @{ reason = "Ready to go live" } $admin
Start-Sleep 3      # catalog-service hears about the activation through Kafka

# 3. The owner adds a dish (category 3 = Biryani)
$dish = Send POST "/api/v1/restaurants/mine/$rid/menu-items" @{ name = "Chicken Biryani"; price = 299.00; categoryId = 3; foodType = "NON_VEG" } $owner

# 4. A customer with an address, and a delivery partner online in the same city
$customerId = (Send POST "/api/v1/auth/register" @{ email = "priya$s@example.com"; password = "Priya1234"; fullName = "Priya Sharma"; phone = "+91987650$s" }).id
$customer = SignIn "priya$s@example.com" "Priya1234" "CUSTOMER"
$addressId = (Send POST "/api/v1/users/me/addresses" @{ type = "HOME"; line1 = "45 Residency Road"; city = $city; state = "Karnataka"; postalCode = "570002"; country = "India" } $customer).id
$riderId = (Send POST "/api/v1/auth/register" @{ email = "ravi$s@example.com"; password = "Rider1234"; fullName = "Ravi Kumar"; phone = "+91998870$s"; role = "DELIVERY_PARTNER" }).id
$rider = SignIn "ravi$s@example.com" "Rider1234" "DELIVERY_PARTNER"
Start-Sleep 3      # delivery-partner-service creates the partner from the UserRegistered event
Send PUT "/api/v1/delivery/partners/me" @{ vehicleType = "MOTORCYCLE"; vehicleNumber = "KA09AB$s"; city = $city } $rider
Send PUT "/api/v1/delivery/partners/me/availability" @{ status = "AVAILABLE" } $rider

# 5. Cart -> checkout (UPI) -> payment
Send PUT "/api/v1/cart/items/$($dish.id)" @{ quantity = 2 } $customer
(Send GET "/api/v1/cart" $null $customer).price
$order = Send POST "/api/v1/orders" @{ addressId = $addressId; paymentMethod = "UPI" } ($customer + @{ "Idempotency-Key" = "order-$s" })
$order.status                                    # PAYMENT_PENDING
$payment = Send POST "/api/v1/payments" @{ orderId = $order.id; paymentMethod = "UPI"; upiId = "priya@okhdfc" } ($customer + @{ "Idempotency-Key" = "pay-$s" })
$payment.status                                  # SUCCESS
Send GET "/api/v1/orders/$($order.id)" $null $customer | Select-Object status, paymentStatus    # PLACED, PAID

# 6. The restaurant prepares the order; the partner accepts, picks it up and delivers it
"accept","preparing","ready" | ForEach-Object { (Send PUT "/api/v1/restaurants/mine/$rid/orders/$($order.id)/$_" $null $owner).status }
Start-Sleep 5      # the assignment job offers the delivery to the partner
$delivery = Send GET "/api/v1/delivery/deliveries/mine/current" $null $rider
Send PUT "/api/v1/delivery/deliveries/mine/$($delivery.id)/accept" $null $rider
Send GET "/api/v1/delivery/orders/$($order.id)" $null $customer            # who brings the food
Send PUT "/api/v1/delivery/deliveries/mine/$($delivery.id)/pickup" $null $rider
Send PUT "/api/v1/delivery/deliveries/mine/$($delivery.id)/deliver" @{ cashCollected = $null } $rider

# 7. Check the results
Send GET "/api/v1/orders/$($order.id)" $null $customer | Format-List status, paymentStatus
(Send GET "/api/v1/orders/$($order.id)" $null $customer).timeline | Format-Table status, note
(Send GET "/api/v1/notifications" $null $customer).content | Format-Table title
(Send GET "/api/v1/admin/audit-logs?entityType=RESTAURANT&entityId=$rid" $null $admin).content | Format-Table service, action, reason
(Send GET "/api/v1/search/foods?city=$city&q=biriyani").results | Format-Table name, price, restaurantName   # a typo, on purpose
(Send GET "/api/v1/admin/dashboard" $null $admin).summary
```

Expected at the end: the order is `DELIVERED` and `PAID`, its timeline goes from `PAYMENT_PENDING` to `DELIVERED`,
the customer has notifications for every step, the audit log shows `RESTAURANT_APPROVED` and `RESTAURANT_ACTIVATED`,
and the search finds the dish despite the typo.

### What to look at in the tools afterwards

- **Kafka UI** (http://localhost:8090): topics `fooddelivery.order.created`, `fooddelivery.order.status-changed` and
  `fooddelivery.payment.status-changed`, with the messages decoded through the Schema Registry (Avro).
- **Mailpit** (http://localhost:8025): "Order #… placed" and "Order #… delivered".
- **Grafana** (http://localhost:3000) → Explore → Loki: `{service="order-service"} |= "orderId=<id>"`, then the
  TraceID link opens the order's trace in Tempo. The dashboards are in the "Food Delivery" folder.
- **Redis**: keys `catalog:v1:menu:*` appear once someone opens `GET /api/v1/restaurants/<id>/menu`.
- **Kibana** (http://localhost:5601) → Discover → data view `food`: the new dish, searchable at once.
- **phpMyAdmin** (http://localhost:8095) → `order_db` → `order_timeline`: every step of the order;
  `payment_db` → `outbox_events`: the payment's event, with its `published_at`.
- **Jaeger** (http://localhost:16686): see below.

### Finding slow APIs and following a request (Jaeger)

1. **Which APIs are slow?** Monitor tab → service `api-gateway` (or any service): every operation with its P95
   latency, request rate and error rate. Sort by latency. The figures need a minute of traffic to appear.
2. **The slow requests themselves:** Search tab → Service `api-gateway`, Min Duration `300ms`, Find Traces, then
   sort by *Longest First*. Every client request starts at the gateway.
3. **Where the time goes:** open a trace. The timeline shows each service's part (gateway → service → REST calls to
   other services → Kafka → the services that reacted), with durations; the critical path is highlighted. Time in a
   span that no child span explains is the service's own work, mostly database queries.
4. **One particular request:** every log line starts with `[traceId,spanId]`. Copy the trace id from a service's log
   (its tab, or Grafana → Explore → Loki → `{service="order-service"} | request_id="<X-Request-Id>"`) and paste it
   into Jaeger's *Lookup by Trace ID* box (top left).
5. **Before and after:** select two traces of the same operation in the results and press **Compare**.

Try it with the admin dashboard: it asks six services at once, so its trace shows the gateway, admin-service and
user-, restaurant-, catalog-, order-, payment- and delivery-service side by side:
`Send GET "/api/v1/admin/dashboard" $null $admin`.

### A failure test

```powershell
docker stop fd-catalog-service
Send GET "/api/v1/cart" $null $customer     # 503 "Dependency unavailable", header Retry-After: 10
Send GET "/api/v1/orders" $null $customer   # still works: it does not need catalog-service
docker start fd-catalog-service             # within about 30 s the cart answers again
```

(`Invoke-RestMethod` shows a 503 as an error; the response body is in the error message.)

## 7. All API Gateway endpoints

Base `http://localhost:8080`. **Who** can call (the role of the access token): *admin* = `PLATFORM_ADMIN`; *owner* =
`RESTAURANT_ADMIN` (only for their own restaurants); *partner* = `DELIVERY_PARTNER`; *customer* = `CUSTOMER`; *user* =
any signed-in user; *public* = no token. `/api/v1/internal/**` is service-to-service only and not routed by the
gateway.

### user-service: authentication, profile, users

| Method | Path | Who | What |
|---|---|---|---|
| POST | /api/v1/auth/register | public | Register (customer by default; `role` for owners and partners) |
| POST | /api/v1/auth/login | public | Log in (returns `userId`) |
| GET / PUT | /api/v1/users/me | user | My profile / update name and phone |
| PUT | /api/v1/users/me/password | user | Change my password |
| GET / POST | /api/v1/users/me/addresses | user | List / add addresses |
| PUT / DELETE | /api/v1/users/me/addresses/{addressId} | user | Update / delete an address |
| PUT | /api/v1/users/me/addresses/{addressId}/default | user | Make it the default address |
| GET | /api/v1/admin/users | admin | Search users (filters, paging, sorting) |
| GET | /api/v1/admin/users/stats | admin | Accounts per role and status, sign-ups in a period |
| GET | /api/v1/admin/users/{userId} | admin | User details |
| PUT | /api/v1/admin/users/{userId}/activate, /deactivate, /role | admin | Activate, deactivate, change role (audited) |
| GET | /api/v1/admin/users/{userId}/audit-logs | admin | The user's audit history |

### restaurant-service

| Method | Path | Who | What |
|---|---|---|---|
| GET | /api/v1/restaurants | public | Active restaurants |
| GET | /api/v1/restaurants/{restaurantId} | public | Restaurant page: details, hours, open now |
| POST / GET | /api/v1/restaurants/mine | owner | Submit a restaurant (PENDING) / list mine |
| GET / PUT | /api/v1/restaurants/mine/{restaurantId} | owner | My restaurant / update it (send its `version`) |
| PUT | /api/v1/restaurants/mine/{restaurantId}/operating-hours | owner | Replace the weekly opening hours |
| PUT | /api/v1/restaurants/mine/{restaurantId}/resubmit | owner | Send a REJECTED restaurant for review again |
| GET | /api/v1/admin/restaurants | admin | Search restaurants |
| GET | /api/v1/admin/restaurants/stats | admin | Restaurants per status, new ones in a period |
| GET | /api/v1/admin/restaurants/{restaurantId} | admin | Review details: business, documents, hours, owner |
| PUT | /api/v1/admin/restaurants/{restaurantId}/approve, /reject, /activate, /deactivate, /suspend | admin | Status changes (body `{"reason": ...}`, audited) |
| GET | /api/v1/admin/restaurants/{restaurantId}/audit-logs | admin | The restaurant's audit history |
| POST | /api/v1/admin/restaurants/events/republish | admin | Publish every restaurant again (re-index search) |

### catalog-service: categories and menus

| Method | Path | Who | What |
|---|---|---|---|
| GET | /api/v1/categories | public | Active categories, in display order |
| GET | /api/v1/restaurants/{restaurantId}/menu | public | Menu grouped by category (cached in Redis) |
| GET / POST | /api/v1/restaurants/mine/{restaurantId}/menu-items | owner | My menu items / add one |
| GET / PUT | /api/v1/restaurants/mine/{restaurantId}/menu-items/{itemId} | owner | One item / update it (send its `version`) |
| PUT | …/menu-items/{itemId}/price, /availability, /enable, /disable | owner | Price, sold out, show or hide |
| GET / POST | /api/v1/admin/categories | admin | List / create categories |
| GET / PUT / DELETE | /api/v1/admin/categories/{categoryId} | admin | One / update / delete (409 while in use) |
| PUT | /api/v1/admin/categories/{categoryId}/activate, /deactivate | admin | Category status |
| PUT | /api/v1/admin/categories/order | admin | Display order (every category id once) |
| GET | /api/v1/admin/categories/{categoryId}/audit-logs | admin | The category's audit history |
| POST | /api/v1/admin/categories/events/republish | admin | Publish every category again |
| GET | /api/v1/admin/menu-items, /stats | admin | Search menu items / item counts |
| GET / DELETE | /api/v1/admin/menu-items/{itemId} | admin | One item / remove a disabled or blocked item |
| PUT | /api/v1/admin/menu-items/{itemId}/activate, /deactivate, /category | admin | Status, move to another category |
| GET | /api/v1/admin/menu-items/{itemId}/audit-logs | admin | The item's audit history |
| POST | /api/v1/admin/menu-items/events/republish | admin | Publish every menu item again |

### order-service: cart and orders

| Method | Path | Who | What |
|---|---|---|---|
| GET / DELETE | /api/v1/cart | customer | My cart with live prices / empty it |
| PUT / DELETE | /api/v1/cart/items/{menuItemId} | customer | Add or change the quantity / remove |
| PUT / DELETE | /api/v1/cart/coupon | customer | Apply a coupon `{"code": ...}` / remove it |
| POST | /api/v1/orders | customer | Checkout (`Idempotency-Key`; body `{addressId, paymentMethod}`) |
| GET | /api/v1/orders | customer | My order history |
| GET | /api/v1/orders/{orderId} | customer | Track an order: status and timeline |
| PUT | /api/v1/orders/{orderId}/cancel | customer | Cancel before the restaurant accepts |
| GET | /api/v1/restaurants/mine/{restaurantId}/orders | owner | My restaurant's orders |
| GET | /api/v1/restaurants/mine/{restaurantId}/orders/sales-summary | owner | Sales for a period |
| GET | /api/v1/restaurants/mine/{restaurantId}/orders/{orderId} | owner | One order with its timeline |
| PUT | …/orders/{orderId}/accept, /reject, /preparing, /ready | owner | Kitchen steps (reject takes `{"reason": ...}`) |
| GET | /api/v1/admin/orders | admin | Search orders |
| GET | /api/v1/admin/orders/{orderId}, /timeline, /audit-logs | admin | Details, timeline, audit history |
| PUT | /api/v1/admin/orders/{orderId}/cancel | admin | Cancel an order (audited) |
| GET | /api/v1/admin/orders/customers/{customerId}/summary | admin | A customer's order summary |
| GET | /api/v1/admin/orders/stats, /stats/restaurants, /stats/customers, /stats/locations | admin | Figures for the dashboard and reports |

### payment-service

| Method | Path | Who | What |
|---|---|---|---|
| POST | /api/v1/payments | customer | Pay an order (`Idempotency-Key`; CARD, UPI, WALLET, simulated) |
| GET | /api/v1/payments, /{paymentId} | customer | My payments / one payment |
| GET | /api/v1/payments/razorpay/config | customer | Razorpay on? public key id, Test Mode |
| POST | /api/v1/payments/razorpay/orders, /verify | customer | Start a Razorpay Checkout payment / confirm it |
| POST | /api/v1/payments/razorpay/webhook | Razorpay | Webhook, checked by its signature (no user headers) |
| GET | /api/v1/admin/payments, /summary | admin | Search payments / totals per status and method |
| GET | /api/v1/admin/payments/{paymentId}, /audit-logs | admin | Details with refunds and events / audit history |
| POST | /api/v1/admin/payments/{paymentId}/refunds | admin | Refund, full or partial (`Idempotency-Key`) |
| GET | /api/v1/admin/refunds, /{refundId} | admin | Search refunds / one refund |

### notification-service

| Method | Path | Who | What |
|---|---|---|---|
| GET | /api/v1/notifications | user | My notifications, newest first |
| GET | /api/v1/notifications/unread-count | user | The unread badge |
| PUT | /api/v1/notifications/{notificationId}/read, /read-all | user | Mark one / all as read |
| GET | /api/v1/admin/notifications, /summary | admin | Search / totals per status and channel |
| GET | /api/v1/admin/notifications/{notificationId}, /audit-logs | admin | Delivery details / audit history |
| POST | /api/v1/admin/notifications/{notificationId}/resend | admin | Send a FAILED or SKIPPED notification again |

### delivery-service

| Method | Path | Who | What |
|---|---|---|---|
| PUT | /api/v1/delivery/partners/me/availability | partner | Go AVAILABLE or OFFLINE (vehicle and city set in delivery-partner-service) |
| GET | /api/v1/delivery/deliveries/mine, /current, /{deliveryId} | partner | History / the current offer / one delivery |
| PUT | /api/v1/delivery/deliveries/mine/{deliveryId}/accept, /decline, /pickup, /deliver | partner | Delivery steps (`deliver` takes `{"cashCollected": ...}`) |
| GET | /api/v1/delivery/orders/{orderId} | customer | Where is my food? |
| GET | /api/v1/admin/deliveries, /delayed, /summary | admin | Search / delayed deliveries / report |
| GET | /api/v1/admin/deliveries/{deliveryId}, /audit-logs | admin | One delivery with its history / audit history |
| PUT | /api/v1/admin/deliveries/{deliveryId}/reassign | admin | Reassign a delivery not picked up yet (audited) |
| GET | /api/v1/admin/delivery-partners/availability, /stats | admin | Partners per status now / partner figures |
| GET | /api/v1/admin/delivery-partners/{userId}/deliveries | admin | A partner's deliveries |

### delivery-partner-service

Owns the partners (registration, profile, suspension); the gateway sends these paths here (same URLs as before).
On a database from before this service existed, run `Invoke-RestMethod -Method Post http://localhost:8099/api/v1/internal/delivery-partners/import` once.

| Method | Path | Who | What |
|---|---|---|---|
| GET / PUT | /api/v1/delivery/partners/me | partner | My profile and status / set my vehicle and city |
| GET | /api/v1/admin/delivery-partners | admin | Search partners (`status`, `city`, `q`) |
| GET | /api/v1/admin/delivery-partners/{userId}, /audit-logs | admin | One partner / audit history |
| PUT | /api/v1/admin/delivery-partners/{userId}/activate, /deactivate | admin | Suspend / reactivate (audited; not while on a delivery) |

### search-service (Elasticsearch)

| Method | Path | Who | What |
|---|---|---|---|
| GET | /api/v1/search/restaurants | public | `q`, `city`, `cuisine`, near me (`lat`, `lon`) |
| GET | /api/v1/search/foods | public | `q`, `city`, `foodType`, `minPrice`/`maxPrice`, `sort`; with facets |
| GET | /api/v1/search/categories | public | Search categories |
| GET | /api/v1/search/suggest | public | Autocomplete for the search box |
| GET | /api/v1/admin/search/indexes | admin | Elasticsearch health and documents per index |

### promotion-service: coupons

| Method | Path | Who | What |
|---|---|---|---|
| GET | /api/v1/coupons | public | Coupons usable today (filter `restaurantId`) |
| POST / GET | /api/v1/admin/coupons | admin | Create / search coupons |
| GET / PUT | /api/v1/admin/coupons/{couponId} | admin | One coupon with its usage / change its terms |
| PUT | /api/v1/admin/coupons/{couponId}/activate, /deactivate | admin | Coupon status (audited) |
| GET | /api/v1/admin/coupons/{couponId}/redemptions, /audit-logs | admin | Its uses / audit history |

### admin-service: dashboard, reports, platform-wide audit log

| Method | Path | Who | What |
|---|---|---|---|
| GET | /api/v1/admin/dashboard | admin | Today's figures and charts (`days`) |
| GET | /api/v1/admin/reports/orders, /revenue, /restaurants, /customers, /deliveries | admin | Reports (`fromDate`, `toDate`; revenue also `groupBy=DAY`, `WEEK` or `MONTH`) |
| GET | /api/v1/admin/audit-logs | admin | Search the audit log of all services |
| GET | /api/v1/admin/audit-logs/actions | admin | Which actions the log has, per service |
| GET | /api/v1/admin/audit-logs/{auditId} | admin | One record, with the rest of the same admin request |
| POST | /api/v1/admin/audit-logs/backfill | admin | Ask every service to send its audit records again |

### The gateway itself

| Method | Path | What |
|---|---|---|
| GET | /actuator/health | Gateway health |
| GET | /actuator/gateway/routes | The routes and where they lead (an administrator's token) |

### Razorpay payments (Test Mode)

By default online payments use the built-in simulated gateway. To pay with Razorpay Checkout instead:

1. **Keys.** Sign in at https://dashboard.razorpay.com, switch the toggle to **Test Mode**, then Account & Settings
   -> API Keys -> Generate Key. Test Mode keys start with `rzp_test_`: real cards and real money are never
   involved, and nothing is ever settled to a bank.
2. **`.env`** (git-ignored; never put keys in code or in docker-compose.yml):
   ```dotenv
   PAYMENT_PROVIDER=RAZORPAY
   RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxxxx
   RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx
   RAZORPAY_WEBHOOK_SECRET=any-long-random-text
   ```
   Then restart payment-service (in its tab: Ctrl+C, then the same command again; it re-reads `.env`). With `RAZORPAY` but no keys,
   payment-service refuses to start and says which variable is missing. The key secret stays in payment-service:
   the browser only ever receives the key id.
3. **Pay in the app** (http://localhost:5180): add dishes, check out with Card, UPI or Wallet, and press
   **Pay** on the payment page. Razorpay Checkout opens:
   - UPI: `success@razorpay` succeeds, `failure@razorpay` fails.
   - Card: a test card from https://razorpay.com/docs/payments/payments/test-card-details/ (any future expiry,
     any CVV); on the test bank page choose Success or Failure.
   - Closing Checkout shows "Payment cancelled" and the order can be paid again (until the 15-minute limit).
   After Checkout reports success, the page sends the result to `/razorpay/verify`; the order is confirmed only
   when payment-service has checked the signature and Razorpay's own record of the payment.
4. **Webhook (optional, recommended).** Razorpay must reach your machine, so expose the gateway with a tunnel, for
   example `ngrok http 8080`. In the Dashboard (Test Mode) -> Account & Settings -> Webhooks -> Add New Webhook:
   URL `https://<your-tunnel>/api/v1/payments/razorpay/webhook`, secret = `RAZORPAY_WEBHOOK_SECRET`, events
   `payment.authorized`, `payment.captured`, `payment.failed`, `order.paid`. Without the webhook everything still
   works while the customer stays on the page; the webhook covers a customer who closes it too early.
5. **Check** in the admin payment screen or `GET /api/v1/admin/payments/{id}`: provider `RAZORPAY`, the Razorpay
   order (`providerOrderId`), the Razorpay payment id (`providerReference`) and the published event.
   The payment also appears in the Razorpay Dashboard -> Transactions -> Payments (Test Mode).

Payment states with Razorpay: **authorized** = the bank approved and holds the money; **captured** = the money is
taken (payment-service captures authorized payments itself, so the Dashboard's automatic-capture setting may be
on or off); only then is our payment `SUCCESS` and the order `PLACED`. A failed or cancelled attempt leaves our
payment `PROCESSING` (with the failure reason) so the customer can try again.

**Send a signed test webhook** without a tunnel (PowerShell). Use the `razorpayOrderId` from
`POST /api/v1/payments/razorpay/orders` and the order's total in paise:

```powershell
$secret = "<RAZORPAY_WEBHOOK_SECRET>"
$body = '{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_LocalTest0001","order_id":"order_XXXXXXXXXXXXXX","status":"captured","amount":35395,"currency":"INR","method":"upi","vpa":"success@razorpay"}}}}'
$hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($secret))
$signature = -join ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($body)) | ForEach-Object { $_.ToString("x2") })
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/payments/razorpay/webhook `
  -ContentType "application/json" -Body $body `
  -Headers @{ "X-Razorpay-Signature" = $signature; "X-Razorpay-Event-Id" = "evt_LocalTest0001" }
# -> result PROCESSED; the same command again -> DUPLICATE; another secret -> 401
```

Going live later means Live Mode keys (`rzp_live_...`), a Live Mode webhook, and Razorpay's account activation;
no code changes.

## 8. Automated checks

```powershell
.\start-local.ps1 -Detached -NoAi          # the services, until UP
cd e2e-tests; .\mvnw.cmd verify            # the business flows across the running services (about a minute)
cd ..; .\start-local.ps1 -Stop             # stop them again
cd user-service; .\mvnw.cmd verify         # one service: unit, slice and Testcontainers integration tests
```

## 9. Stopping and resetting

```powershell
.\start-local.ps1 -Stop                    # the services started with -Detached (the others: close their tabs)
docker compose stop                        # the infrastructure, keeping the data
docker compose down -v                     # remove the containers AND all data (databases, Kafka, indexes)
```

The e2e tests with `-PortOffset 10000`: `$env:E2E_BASE_URL='http://localhost:18080'` and
`$env:E2E_MAILPIT_URL='http://localhost:18025'` before `.\mvnw.cmd verify`.
