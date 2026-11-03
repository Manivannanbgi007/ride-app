# Ratings and reviews

How customers rate restaurants and dishes, how the ratings reach the pages, and why it is built this way. Written
for a junior developer: each decision says what the alternative was.

## 1. What a customer can do (the review policy)

| Rule | How it is enforced |
|---|---|
| Only a **signed-in customer** can write a review | `review-service` accepts `POST /api/v1/reviews` for the role `CUSTOMER` only; no identity gives 401, an admin or owner gives 403 |
| Only for an order that was **delivered** and is **theirs** | The order is read from order-service; someone else's or an unknown order is 404, an order not delivered is 422 |
| **One review per order** (one per restaurant experience) | Unique key `uk_reviews_order (order_id)`; a second review is 409, also when two requests arrive at the same moment |
| Rating is **1 to 5 stars** | Bean validation on the request **and** `CHECK (rating BETWEEN 1 AND 5)` in MySQL |
| The overall stars are the **restaurant rating**; stars given to single dishes are the **dish ratings** | Dish ratings are optional, only for dishes that were on the order (else 422) |
| A customer may **edit** and **delete** their own review, any time | `PUT` / `DELETE /api/v1/reviews/{id}`; someone else's review is 404 and nothing changes; an edit with a stale `version` is 409 |
| After a delete the order can be **reviewed again** | The unique key is freed by the delete |
| Nothing rated yet | Pages say **"No ratings yet"**; the API returns count 0 and average `null` |

Why one review per order and not per restaurant? A rating should describe a real order, and an order is the only
thing the platform can check. Why may a customer edit any time? It keeps the rules small; if abuse appears, a time
limit is one `if` in `ReviewService.update`.

## 2. Where the data lives (service boundaries)

```
 Browser ──► API Gateway ──► review-service ──► review_db (reviews, review_dishes)
                │                  ▲   │
                │                  │   └─► order-service (is the order delivered, whose is it, which dishes)
                │                  │
                └──► search-service ┘  pulls the averages every 60 s (internal feed, not routed by the gateway)
                        └─► Elasticsearch (copy of the averages, for sorting and filtering)
```

* `review-service` **owns** every review and every average. Averages are computed from the real rows
  (`AVG`, `COUNT` grouped by restaurant or dish, indexes `idx_reviews_restaurant_rating`,
  `idx_review_dishes_menu_item_rating`), never stored, so they cannot drift.
* No other service reads `review_db`. `search-service` keeps a **copy** of the two numbers per restaurant and dish in
  its own index, because Elasticsearch has to sort and filter by them. The copy is refreshed by a pull
  (`RatingSyncService`, every 60 s, plus `POST /api/v1/admin/search/ratings/sync` on demand).
* Why a pull and not Kafka events? review-service has no Kafka or outbox yet; a pull is small, restart-safe and
  self-healing (every run re-applies the values). The price is up to a minute of delay **in sorting and filtering
  only**: the numbers on cards and pages are read live from review-service.

## 3. APIs

Public (no login; the gateway route is `/api/v1/ratings/**`):

| API | Answer |
|---|---|
| `GET /api/v1/ratings/restaurants?ids=1,2,3` | `[{"id":1,"average":4.3,"count":12}]`, only ids that have reviews; 1 to 100 ids, one grouped query |
| `GET /api/v1/ratings/menu-items?ids=...` | the same for dishes (from the dish ratings) |
| `GET /api/v1/ratings/restaurants/{id}` | `{"id","average","count","distribution":{"1":..,"5":..}}`; count 0 and average `null` when unrated |
| `GET /api/v1/ratings/menu-items/{id}` | the same for a dish |
| `GET /api/v1/ratings/restaurants/{id}/reviews?page&size&sort=newest\|highest\|lowest` | a page of reviews; the reviewer is shown as "Priya S." and nothing else identifies them |

Customer (role `CUSTOMER`): `POST /api/v1/reviews`, `GET /api/v1/reviews/orders/{orderId}` (existing),
`PUT /api/v1/reviews/{id}`, `DELETE /api/v1/reviews/{id}` (new).

Search (existing endpoints, new optional parameters): `minRating` (1 to 5, decimals allowed: restaurants or dishes
rated at least this high; unrated ones are left out) and `sort=RATING` (best first, then most reviews, unrated
last). Hits carry `ratingAverage` and `ratingCount`.

Internal (never routed by the gateway): `GET /api/v1/internal/ratings/restaurants|menu-items?afterId&size`, the
feed search-service reads.

## 4. Database changes

* `review-service` migration `V3__add_rating_indexes.sql`: two indexes (see above). No new tables: the review tables
  from V1 already hold everything.
* Elasticsearch: two fields (`ratingAverage`, `ratingCount`) are added to the `restaurant` and `food` mappings. On
  startup `SearchIndexes` adds only the **missing** fields to an existing index (nothing is recreated or
  reindexed).

## 5. Where the stars appear

| Page | What | Source |
|---|---|---|
| Home, Restaurants | `4.3 (12)` or "No ratings yet" on each restaurant card | one batch call per page (`/ratings/restaurants?ids=`) |
| Restaurant page, header | average and count, linking to the reviews | `/ratings/restaurants/{id}` |
| Restaurant page, menu | each dish: stars next to the price | one batch call (`/ratings/menu-items?ids=`) |
| Restaurant page, end | "Ratings and reviews": average, bars per star level, sortable list, "No ratings yet" | `/ratings/restaurants/{id}` and `.../reviews` |
| Search (restaurants and dishes) | same badge; **sort "Top rated"** and **filter "4 stars and up"** | the hit itself (same figures the sort uses) |
| Restaurants page | link "See top rated restaurants" to the search sorted by rating | search-service |
| Order page (delivered) | write, **edit** and **delete** your review | review-service |

A rating is a decoration: if the ratings cannot be loaded, cards show no stars and no error.

The plain Restaurants page (restaurant-service) keeps its own sorts (name, newest). Sorting it by rating would need
the ratings inside restaurant-service's database, which would break the rule that review-service owns them; the
search page already sorts and filters by rating, so the Restaurants page links there.

## 6. Tests

See the verification section of the final report of this change; the numbers are kept there so they cannot go
stale here. Run them: `review-service` and `search-service`: `./mvnw verify`; front end: `npm test`.

## 7. Known limits

* Search sorting and filtering lag up to about a minute behind a new review (the sync interval).
* Dish ratings count only the stars given on a review, so a dish can have fewer ratings than the restaurant has
  reviews.
* `review-service` asks order-service again when a review is edited, so editing needs order-service (503 if it is
  down); deleting does not.
* After recreating a service container, restart the API Gateway (it keeps the old container address).
