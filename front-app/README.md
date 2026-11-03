# Front-App

The React web application of the Food Delivery platform: the customer area (under `/customer`) and the admin area
(under `/admin`). The site's root (`/`) leads to `/customer`. It talks to the Spring Boot microservices only through the **API Gateway**. The admin area follows the
"Sedap" admin dashboard design; [docs/design-analysis.md](docs/design-analysis.md)
compares that design with what the backend offers.

Every area signs in with a password and then works with a short-lived access token (JWT, Phase 18): see
[Signing in and who is acting](#signing-in-and-who-is-acting).

## Technology

| What                         | Version      | Used for                                                    |
| ---------------------------- | ------------ | ----------------------------------------------------------- |
| React                        | 19.3         | UI components                                               |
| TypeScript                   | 6.0 (strict) | Types; 6.0 is the newest version typescript-eslint supports |
| Vite                         | 8.3          | Dev server, builds per environment                          |
| React Router                 | 8.4          | Pages and addresses                                         |
| TanStack Query (React Query) | 5.104        | Calling the APIs: loading, caching, errors                  |
| Redux Toolkit / React Redux  | 2.13 / 9.3   | State several pages share                                   |
| React Context                | (React)      | UI state: toasts, the mobile sidebar                        |
| Axios                        | 1.20         | HTTP client (one instance)                                  |
| Tailwind CSS                 | 4.3          | Styling (theme in `src/index.css`)                          |
| Recharts                     | 3.10         | Dashboard charts                                            |
| Leaflet / React Leaflet      | 1.9 / 5.0    | The live map of an order (OpenStreetMap tiles, no key)      |
| lucide-react                 | 1.53         | Icons                                                       |
| React Hook Form / Zod        | 7.89 / 4.6   | Forms and their validation rules                            |
| ESLint / Prettier            | 10.12 / 3.9  | Code quality / formatting                                   |
| Vitest / Testing Library     | 5.0 / 16.3   | Tests                                                       |

## Running it

Prerequisites: Node.js 22.12 or newer (developed with 24), and the platform running (from the repository root:
`.\start-local.ps1`, which also opens a Front-App tab).

```powershell
cd front-app
npm ci          # first time: installs the exact versions of package-lock.json
npm run dev     # http://localhost:5180 -> the customer home (/customer); the admin area is /admin
```

| Area                        | Address                                                                                                              | Who                                                               |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| Customer home               | `/customer`                                                                                                          | anyone; cart, checkout and orders need a customer sign-in         |
| Customer sign-in / register | `/customer/sign-in`, `/customer/register`                                                                            | customer accounts only (other roles are refused)                  |
| Customer pages              | `/customer/restaurants`, `/customer/orders`, `/customer/account/addresses`, ...                                      | all addresses are in `CUSTOMER_ROUTES` (`src/utils/constants.ts`) |
| Owner sign-in / sign-up     | `/owner/sign-in`, `/owner/register`                                                                                  | restaurant owner accounts only (customers and admins are refused) |
| Owner area                  | `/owner` (dashboard), `/owner/orders`, `/owner/menu`, `/owner/reviews`, `/owner/restaurant`, `/owner/restaurant/new` | a signed-in restaurant owner; their own restaurants only          |
| Partner sign-in / sign-up   | `/partner/sign-in`, `/partner/register`                                                                              | delivery partner accounts only (other roles are refused)          |
| Partner area                | `/partner` (today), `/partner/deliveries`, `/partner/deliveries/:id`, `/partner/profile`                             | a signed-in delivery partner; their own deliveries only           |
| Admin sign-in               | `/admin/sign-in`                                                                                                     | platform administrator accounts only (other roles are refused)    |
| Admin area                  | `/admin/...`                                                                                                         | a signed-in administrator; the header has **Sign out**            |

The customer area used to live at the root (`/orders`, `/sign-in`, ...). Those old addresses (bookmarks, links the
assistant sends) are sent on to the same page under `/customer` (`LegacyCustomerRedirect`).

### The delivery partner area

Under `/partner`, in its own mobile-first frame (`src/layouts/partner`: a partner mostly uses it on a phone, on the
road): a see-through top bar with the partner's status, and the pages as a tab bar at the bottom (in the top bar from
the `lg` breakpoint). While that bar is at the bottom, toasts float above it (`--bottom-bar-height` in `index.css`). A
partner signs up at `/partner/register` (a phone number is required: customers call them) or signs in at
`/partner/sign-in`; every other address sends a visitor there first. Like the owner area it acts as the person who
signed in, kept for the browser tab (`front-app.partner` in sessionStorage).

| Page          | What the partner does                                                                                                                                                                                                                  |
| ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Today         | A status-coloured hero with the online switch; the delivery offered to them as a ticket (countdown ring: accept within 2 minutes, or decline), the route with Navigate and Call, then pick it up and deliver it; a radar while waiting |
| My deliveries | Every delivery as cards grouped by day, filtered by outcome; one delivery with its route, money and history                                                                                                                            |
| Profile       | The partner's card (contact, status, deliveries), the vehicle as picture tiles with a number-plate preview, and the city (needed before going online)                                                                                  |

The profile is delivery-partner-service's API; going online and the deliveries are delivery-service's (the gateway
sends each path to its service). A new offer arrives as a live update (a toast and a refresh); the page also asks again
every 5 seconds while the partner is online, so it works without live updates too.

### The restaurant owner area

Under `/owner`, in its own frame (`src/layouts/owner`). A new owner signs up at `/owner/register` ("Become a
restaurant partner": a `RESTAURANT_ADMIN` account, signed in straight away, then on to "Register a restaurant"); an
owner signs in at `/owner/sign-in`; every other address of the area sends a visitor there first and brings them back
afterwards. Like the customer area it acts as the person
who signed in (with the owner area's access token: see "Signing in and who is acting"), kept for
the browser tab (`front-app.owner` in sessionStorage). An owner may have several restaurants: the header's selector
decides which one the pages show (`useOwnerRestaurant()`).

| Page       | What the owner does                                                                                              |
| ---------- | ---------------------------------------------------------------------------------------------------------------- |
| Dashboard  | Orders waiting for a decision, the figures of today / 7 / 30 days, top dishes, the rating, the restaurant status |
| Orders     | Tabs by status; accept or reject (with a reason), start preparing, mark ready; refreshes by itself               |
| Menu       | Add and edit dishes, change a price, mark sold out / in stock, hide or show a dish                               |
| Reviews    | The restaurant's rating, every review and the rating of each dish                                                |
| Restaurant | Details and opening hours; send a rejected restaurant for review again; register another restaurant              |

All of it uses APIs that already existed (`/api/v1/restaurants/mine/...`, see the owner rows of the main README; the
sign-up is `POST /api/v1/auth/register` with `role: RESTAURANT_ADMIN`). Not in the app yet: notifications for the
owner, editing documents other than the food licence.

**How a new restaurant goes live** (the onboarding workflow of the requirements): the owner signs up and registers the
restaurant (`/owner/restaurant/new`); it starts as **Pending**. An administrator reviews it under Restaurants
(`/admin/restaurants`, filter "Pending"): **Approve** or **Reject** (with a reason), then **Activate** an approved one.
Only then do customers see it. Administrators do not create restaurants themselves: there is no admin API for it.

| Script                                                                    | What it does                                                                           |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `npm run dev`                                                             | Development server with instant reload (environment: Development)                      |
| `npm run build`                                                           | Production build into `dist/` (environment: Production)                                |
| `npm run build:demo` / `build:uat` / `build:preprod` / `build:production` | Build for that environment                                                             |
| `npm run preview`                                                         | Serves the last build on http://localhost:4173                                         |
| `npm run typecheck`                                                       | TypeScript checks                                                                      |
| `npm run lint` / `lint:fix`                                               | ESLint (fix what can be fixed automatically)                                           |
| `npm run format` / `format:check`                                         | Prettier                                                                               |
| `npm test` / `test:watch`                                                 | Tests (once / on every change)                                                         |
| `npm run verify`                                                          | Everything above: type check, lint, format check, tests, build. Run it before you push |

## The live map of an order

"Track on map" (`/customer/orders/:orderId/track`) shows the restaurant, the customer's home and the delivery
partner's real position from their phone, with the delivery status (order confirmed, food being prepared, picked up,
on the way, delivered). It is drawn with **Leaflet / React Leaflet on OpenStreetMap tiles**: no API key, no account,
no paid service (`src/components/business/LiveTracking`).

- **The partner's side:** while their delivery is accepted or picked up, the partner area shares the phone's
  position from every page (`layouts/partner/PartnerLiveLocation.tsx`, `hooks/useShareLiveLocation.ts`): the
  browser asks for location access once, then a position is sent to delivery-service
  (`PUT /api/v1/delivery/deliveries/mine/{id}/location`) after a move of 25 m or every 10 s, never more than one
  every 3 s (`utils/liveLocationUtils.ts`). It stops by itself once the food is delivered or the order is cancelled.
  The delivery card and the header say whether the position is shared, and explain a blocked permission, a phone
  without a fix or a page without https (browsers share a position only with https pages and localhost).
- **The customer's side:** the page reads `GET /api/v1/delivery/orders/{orderId}/live` every 3 seconds
  (`services/liveTrackingService.ts`) and draws only what it gets: the partner's last position (with its accuracy
  when it is rough, and its direction when the phone knows it), and the restaurant and the home when their addresses
  have map coordinates. There is no route line and no arrival time for a real ride (there is no routing service);
  "Updated 12 s ago" says how fresh the position is, and a position older than 45 s is flagged.
- **Map pins for addresses:** the customer's address form has "Use my current location", which saves the device's
  position as the address's map pin (`components/business/Address/CurrentLocationField.tsx`). The written address
  stays what the customer typed: nothing turns a position into an address.
- **Demo simulation:** with `VITE_FEATURE_FAKE_LIVE_TRACKING=true` (`.env.demo`) the answers come from
  `services/mock/liveTrackingMock.ts` instead: a sample ride in Bengaluru on two road-following routes computed once
  offline (OSRM on OpenStreetMap data), following the order's real delivery status. Every simulated answer has
  `source: 'SIMULATED'`, and the page labels the map and the arrival time as a simulation. It is off everywhere else.
- **No routing or geocoding while the page runs:** the only outside requests are the map tiles.
- **Limits of the free tiles:** OpenStreetMap's tile servers are for light use only (attribution required, no heavy
  traffic, no bulk downloading: https://operations.osmfoundation.org/policies/tiles/). A busy production site sets
  `VITE_MAP_TILE_URL` to its own tile server or a paid one; nothing else changes. OpenStreetMap only draws light
  tiles: in dark mode the tiles are darkened with a CSS filter (`index.css`).

**Later:** each new position could be pushed over the existing WebSocket (`src/realtime`) instead of the page asking
every few seconds, and a routing service (a self-hosted OSRM or Valhalla) could add the road ahead and an arrival time.

## Environments

One code base; each environment only has its own settings file. Vite reads `.env` and then `.env.<mode>`.

| Environment    | Settings file      | Command                 | API Gateway                            | Environment badge |
| -------------- | ------------------ | ----------------------- | -------------------------------------- | ----------------- |
| Development    | `.env.development` | `npm run dev`           | http://localhost:8080                  | shown             |
| Demo           | `.env.demo`        | `npm run build:demo`    | `https://demo-api...` (placeholder)    | shown             |
| UAT            | `.env.uat`         | `npm run build:uat`     | `https://uat-api...` (placeholder)     | shown             |
| Pre-Production | `.env.preprod`     | `npm run build:preprod` | `https://preprod-api...` (placeholder) | shown             |
| Production     | `.env.production`  | `npm run build`         | `https://api...` (placeholder)         | hidden            |

Replace the placeholder addresses when those environments exist. Personal changes go in `.env.local` (not committed).

| Variable                          | Meaning                                                                                             |
| --------------------------------- | --------------------------------------------------------------------------------------------------- |
| `VITE_APP_NAME`                   | Name in the sidebar and footer                                                                      |
| `VITE_APP_ENV`                    | Environment name (badge in the header)                                                              |
| `VITE_API_BASE_URL`               | The API Gateway's address                                                                           |
| `VITE_API_TIMEOUT_MS`             | Give a request up after this long (15000: above the gateway's own 10 s limit)                       |
| `VITE_FEATURE_API_LOGGING`        | Log every request and answer in the browser console                                                 |
| `VITE_FEATURE_QUERY_DEVTOOLS`     | React Query devtools panel (development server only, never in a build)                              |
| `VITE_FEATURE_ENVIRONMENT_BADGE`  | Show the environment's name in the header                                                           |
| `VITE_FEATURE_PAYMENT_TEST_HINTS` | Show the simulated payment provider's test cards and UPI ids (off in pre-production and production) |
| `VITE_FEATURE_FAKE_LIVE_TRACKING` | Demo only: the live map shows a simulated ride (labelled as such) instead of the partner's real GPS |
| `VITE_MAP_TILE_URL`               | The live map's `{z}/{x}/{y}` tile server; empty = OpenStreetMap's (light use only, see below)       |
| `VITE_MAP_TILE_ATTRIBUTION`       | The credit that tile server requires (HTML); empty = "© OpenStreetMap contributors"                 |

Everything in these files ends up in the JavaScript every visitor downloads: never put a password, key or token
there. A missing or wrong setting does not give a blank page: the application shows which setting is wrong.

## Folder structure

```text
src/
├── api/            Axios instance, apiClient (get/post/put/patch/delete), error handling, React Query client
├── assets/         images and icons (none yet: icons come from lucide-react)
├── components/
│   ├── common/     reusable UI without business knowledge: Button, Input, Select, Textarea, SearchInput,
│   │               DatePicker (calendar), DateRangeFields, Modal, FormDialog, ConfirmationDialog,
│   │               ReasonDialog, Card, Badge, Alert, QuantityStepper, RadioCards,
│   │               StatCard, Table, Pagination, PagedResults, Tabs, DescriptionList, Loader (Spinner, PageLoader,
│   │               SkeletonLoader, TableLoader), EmptyState, ErrorMessage, AsyncContent, ErrorBoundary, Toast,
│   │               PageHeader, BackLink, Tooltip (useTooltip: an animated bubble beside, under or above
│   │               a button, drawn over the page and kept inside the window), HeaderIcon, ClearFiltersButton
│   └── business/   parts of one feature: Dashboard, Order, Customer, Food, Restaurant, Category, Coupon,
│                   Payment, Delivery, Analytics, AuditLog, Notification; the customer area: Storefront,
│                   Cart, Address, Checkout (payment forms, offers), MyOrders (progress, history,
│                   delivery tracking);
│                   StatusBadges, AuditHistory,
│                   PartialDataAlert
├── context/        React Context for UI state (toasts, mobile sidebar)
├── hooks/          custom hooks (useDebounce, useIdParam, useTabParam: the open tab in the address,
│                   useQueryParams: filters in the address, useActingUser); hooks/api/ = the React Query hooks, one file per
│                   microservice like services/ (orderHooks.ts: useOrders, useOrder, useCancelOrder, ...); the customer
│                   area's by feature: storefrontHooks, authHooks, cartHooks (cart, checkout), myOrderHooks (my
│                   orders, cancel, tracking), addressHooks
├── layouts/        MainLayout (admin frame), Header, Sidebar, Footer, navigation (the sidebar's entries);
│                   customer/: CustomerLayout, CustomerHeader, AccountMenu, RequireCustomer (signed-in pages), NotificationBell
├── pages/          one folder per feature; Storefront = the customer area (Home, Restaurants, Restaurant
│                   menu, Search, Sign in, Register, Offers, Cart, Checkout, Payment, My orders, Order, My profile, My addresses, Notifications); admin: Dashboard, Order, Customer, Food, Restaurant, Category, Coupon,
│                   Payment (payments and refunds), Delivery (deliveries and partners), Analytics, AuditLog,
│                   Notification, Search, ComingSoon, NotFound, ConfigurationError
├── services/       one file per microservice: the functions that call its APIs
├── store/          Redux store, typed hooks (useAppDispatch, useAppSelector), slices/
├── test/           test setup, renderWithProviders, sample API answers (fixtures)
├── types/          TypeScript types of API requests and answers
├── utils/          apiEndpoints (every URL), constants (routes, labels), env (settings), errorMessages,
│                   logger, validationUtils (form rules), date / number / CSV / pagination helpers, queryKeys,
│                   dashboard calculations, report CSV rows, audit and notification helpers
├── App.tsx         every route
└── main.tsx        start: providers (Redux, React Query, toasts, router) and the outer error boundary
```

## How a screen gets its data

```text
Page (pages/Dashboard/DashboardPage.tsx)            puts the parts together, no API code
  -> hook (hooks/api/dashboardHooks.ts)            React Query: loading / error / data, cache
    -> service (services/dashboardService.ts)       which API, which parameters
      -> apiClient (api/apiClient.ts)               typed get/post/...
        -> axiosInstance (api/axiosInstance.ts)     gateway address, timeout, access token, logging
          -> API Gateway -> microservice
```

Components never import Axios or write a URL: URLs are in `utils/apiEndpoints.ts`.

## Where state lives

| Kind of state                                  | Where                      | Example                                                                                                                                                                                       |
| ---------------------------------------------- | -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Data from the APIs                             | React Query (`hooks/api`)  | the dashboard, the user's name, the unread count                                                                                                                                              |
| Shared by several pages, kept while navigating | Redux (`store/slices`)     | who is acting (`sessionSlice`: who is signed in to each area), the chosen period (`reportPeriodSlice`), each list's filters and page (`orderListSlice`, `customerListSlice`, `foodListSlice`) |
| UI state used far apart in the tree            | React Context (`context/`) | toasts, the mobile sidebar                                                                                                                                                                    |
| One component's own state                      | `useState`                 | the search box's text, whether a dialog is open                                                                                                                                               |

## Errors, loading and empty states

- **Every failed API call becomes an `ApiError`** (`api/apiErrorHandler.ts`): a `kind` (network, timeout, notFound,
  conflict, validation, unavailable, ...), the HTTP status, a `userMessage` safe to show, the invalid fields of a
  form (`fieldErrors`), and the gateway's request id. The technical details go to the console (`utils/logger.ts`).

  | Answer              | Shown to the user                                                                      |
  | ------------------- | -------------------------------------------------------------------------------------- |
  | 400, 404, 409, 422  | the service's own message (written for people, e.g. "Add items worth 200.00 more ...") |
  | 401                 | "Please sign in to continue." (the area's session has ended)                           |
  | 403                 | "You do not have permission to do this."                                               |
  | 429                 | "Too many requests at once ..."                                                        |
  | 500, 502, 503, 504  | a general message; never the server's text                                             |
  | no answer / timeout | "Unable to connect to the server ..." / "The server took too long ..."                 |

  All wording is in `utils/errorMessages.ts`.

- **`AsyncContent`** shows exactly one of loading, error (with "Try again" and the request reference), empty, or
  the content: every API-driven part of a page uses it.
- **Partial answers:** admin-service answers with what it has when a service is down and lists the missing ones;
  `PartialDataAlert` names them and their figures show "—", never 0.
- **Crashes while drawing** a page are caught by `ErrorBoundary`: the page shows a message and "Try again"; the
  sidebar and header keep working. A second boundary around the whole application is the last safety net.
- Automatic retries: once, only when the connection failed or timed out (the gateway already retries reads);
  never for changes.

### Loading signs (one at a time, each tied to real loading)

| What is loading                                             | What is shown                                                                                           | Where                                                     |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| The application's code (first visit, before React runs)     | A plain-CSS loader; after 12 s "taking longer than usual ... reload the page"                           | `index.html`                                              |
| The first page's code (or the first page of the other area) | `PageLoader`, the animated loader, in place of the page (header and sidebar stay)                       | the layouts' `Suspense`                                   |
| The next page's code, when moving to a page not opened yet  | `NavigationProgress`, a thin bar at the top; the current page stays usable                              | `App.tsx`, fed by `lazyPage` (`utils/pageLoadTracker.ts`) |
| A part of a page's data                                     | That part's skeleton (`AsyncContent`), never a full-page loader; a new page of a list dims the old rows | each page                                                 |
| A change (save, cancel, pay)                                | The button's own spinner                                                                                | `Button isLoading`                                        |

- **No flashing:** the loaders fade in only after 0.15–0.2 s, so a fast connection never sees them. Nothing waits
  on purpose: a loader shows exactly as long as the download or request takes.
- **Never forever:** API requests stop after `VITE_API_TIMEOUT_MS` (15 s) and show their error with "Try again";
  a page whose code could not be downloaded shows "This page could not be downloaded" with "Reload page".
- **Timings** for DevTools > Performance (the "Timings" track) and, while developing, the console:
  `Front-App · app started` and `Front-App · page code /admin/orders`. `performance.getEntriesByType('measure')`
  in the console lists them.
- **Trying it on a slow connection:** build it (`npm run build`), stop `npm run dev`, then
  `npm run preview -- --port 5180 --strictPort` (5180 is the address the gateway allows); in DevTools > Network
  tick "Disable cache" and pick "3G" or "Slow 4G". (`npm run dev` works too, but it sends hundreds of small
  files, so it is much slower than the real build.)

## Forms and admin actions

- **Every audited admin action** (cancel an order, deactivate a user, block a dish, approve a restaurant, ...) asks
  for a reason in a dialog: `ReasonDialog` for a reason only; forms with more fields (`ChangeRoleDialog`,
  `CategoryFormDialog`, `RefundDialog`) are built on `FormDialog`, which holds the frame (buttons, the server's
  message) so each dialog only has its fields. The coupon form is a page of its own (`CouponForm`).
- **Validation rules are written once** in `utils/validationUtils.ts` (Zod schemas, used through React Hook Form)
  and match the services' own rules (a reason is required, at most 500 characters). A form that breaks them is not
  sent; the message appears under the field.
- **The server still decides.** Its refusal is shown inside the dialog, which stays open: field messages under
  their fields (400), anything else above the buttons (409 "already cancelled", 422 "the platform must keep one
  admin", a network error). `showServerErrors` does this for every form.
- **After a change** the hook (`useCancelOrder`, ...) shows the new record at once and reloads every list that may
  contain it (`QUERY_KEYS.orders.all`); the page shows a toast ("Order #42 was cancelled.").
- **Buttons follow the business rules** (no "Cancel order" once the order is on its way, no "Deactivate" on your own
  account); the services check the same rules again.
- **Search and filter boxes** ask the server once typing stops (`SearchInput`, 400 ms); boxes for ids accept digits
  only; dates are picked in a calendar (`DatePicker`: works with the keyboard too; days outside the limits cannot
  be picked), and a date range never lets "until" come before "from" (`DateRangeFields`). The filters live in
  Redux, so they are still there after opening a record and coming back.
- **The header stays at the top** in both areas; the page scrolls under it (the admin header shows a light shadow
  once the page has moved).
- **The admin sidebar** can be narrowed to a rail of icons with the header's menu button (titles appear beside the
  icons on hover; the pages take the freed width; the choice is kept in this browser). On small screens the same
  button opens the navigation over the page.
- **Never twice:** a refund sends an `Idempotency-Key` (one per opening of the refund dialog), so pressing again
  after a timeout returns the first refund instead of refunding twice. A coupon change sends the `version` it read:
  a change by someone else meanwhile is refused (409) and explained.
- **Money** is compared in paise (`toPaise`), never with floating-point numbers.
- **Ordering and paying (customer area).** Checkout and payment each send an `Idempotency-Key`, one per attempt:
  after a timeout or no answer the retry sends the same key (the order or payment already made comes back, never a
  second one); after a refusal (closed restaurant, declined card) the next attempt gets a new key
  (`keepsIdempotencyKey` in `utils/shoppingUtils.ts`). A declined payment is not an error: payment-service answers
  FAILED with the reason, shown on the page. A successful payment reaches the order a moment later (Kafka), so the
  payment page waits for the order to be confirmed (up to 30 seconds) before showing it. Prices always come from
  order-service; the page only adds the "add X more for free delivery" hint.

## Adding a page (how Order List was built)

1. Types of the API's answer in `types/order.ts`.
2. The URLs in `utils/apiEndpoints.ts`, the calls in `services/orderService.ts`.
3. A hook per call in `hooks/api/orderHooks.ts` (`useOrders`, `useCancelOrder`), with its key in `utils/queryKeys.ts`.
   Filters that must survive navigation get a slice, made with `createListSlice` in one line
   (`store/slices/orderListSlice.ts`).
4. The parts in `components/business/Order/` (`OrderFilterBar`, `OrderTable`, `OrderTimeline`), built from the
   common components.
5. The page in `pages/Order/OrderListPage.tsx`: uses the hooks and puts the parts together; `PagedResults` shows
   loading, error, empty (with "Clear filters") and the rows with their page buttons.
6. Its route in `App.tsx` (`lazy(...)` for large pages) and its sidebar entry in `layouts/navigation.ts` (remove
   `comingIn`).
7. Tests next to the files (`*.test.ts(x)`), then `npm run verify`.

## Signing in and who is acting

Each area has its own sign-in, and the four can be signed in at the same time in one browser. **The area of the open
page decides who acts** (`utils/sessionUtils.ts`, `hooks/useActingUser.ts`): the admin in `/admin/...`, the
restaurant owner in `/owner/...`, the delivery partner in `/partner/...`, the customer everywhere else. Only the
area's kind of account can sign in to it (user-service refuses another kind with `WRONG_ACCOUNT_TYPE`, and the page
checks again). Visitors who are not signed in send no identity: public pages (restaurants, menus, search) need none.

- **Signing in** (`hooks/api/authHooks.ts`, `api/areaSession.ts`): user-service checks the password and answers with
  a 15-minute **access token**, and sets the area's **refresh cookie** (`fd_refresh_customer`, `_owner`, `_partner`,
  `_admin`: HttpOnly, so no script can read it). The token is kept **in memory only** (`api/tokenStore.ts`), never in
  localStorage or sessionStorage; who signed in (id, role, name, email) is kept for the browser tab in sessionStorage
  and Redux (`store/slices/sessionSlice.ts`). The area's previous person's cached data is dropped.
- **Every API call** (`api/axiosInstance.ts`) sends `Authorization: Bearer <token>` of the open page's area, and
  nothing else that names a user: the gateway and every service read the caller from the token only.
- **Renewal** (`api/sessionRefresh.ts`): a token with less than 4 minutes left (the assistant needs 3), or none after a reload, is renewed first with
  the refresh cookie (`POST /api/v1/auth/refresh`). One renewal at a time per area, shared by all waiting requests and,
  where the browser has the Web Locks API, by its tabs; a 409 (another tab renewed at that moment) is tried once more.
  A request refused with 401 is renewed and sent once more. When the session has ended (401 on renewal: signed out
  elsewhere, password or role changed, account deactivated) the area is signed out and its pages ask for a sign-in;
  when someone else signed in to the same area in another tab, this tab signs out. When user-service cannot be
  reached, the person stays signed in and the next request tries again.
- **Signing out** ends the session on the server too (`POST /api/v1/auth/logout`: the cookie is removed) and drops the
  area's data; the admin's sign-out clears the whole cache.
- **The sign-in calls** have their own Axios instance (`api/sessionApi.ts`): the only one that sends cookies, no
  renewal on 401 (it would loop), nothing logged. Failed requests are logged without their token or body.
- Use `http://localhost:5180`, not `127.0.0.1`: the cookie is SameSite=Strict, and for the browser `127.0.0.1` and
  `localhost` are different sites. The bootstrap admin of development is `BOOTSTRAP_ADMIN_EMAIL` /
  `BOOTSTRAP_ADMIN_PASSWORD` of the platform's `.env`.
- **The live connection** (`src/realtime`) cannot set headers on a WebSocket: it sends the area's access token as
  `?access_token=` (renewed first when it is about to expire), and notification-service accepts a token in the query
  on `/ws/**` only.

## Code rules

- TypeScript `strict`; no `any`. Formatting by Prettier, never by hand. `no-console`: use `utils/logger.ts`.
- One component per file; a folder with `index.ts` per common component.
- Colours, font and shadows come from the theme (`src/index.css`): `bg-brand-500`, `text-ink-soft`, `shadow-card`.
  Charts use the same values from `utils/chartColors.ts`.
- Business rules and calculations live in `utils/` or services, not in JSX (e.g. `utils/dashboardUtils.ts`).
