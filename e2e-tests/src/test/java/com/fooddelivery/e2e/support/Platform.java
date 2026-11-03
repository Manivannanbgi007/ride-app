package com.fooddelivery.e2e.support;

import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The people and things a flow needs, created through the public API as a real user would: an owner with an open
 * restaurant and dishes, customers with an address, a delivery partner waiting for orders. Every flow creates its own,
 * in a city of its own, so flows never see each other's data.
 */
public final class Platform {

    private static final List<Map<String, String>> ALWAYS_OPEN = Arrays.stream(DayOfWeek.values())
            .map(day -> Map.of("dayOfWeek", day.name(), "opensAt", "00:00", "closesAt", "23:59"))
            .toList();
    public static final String CUSTOMER_PASSWORD = "Eater1234";
    public static final String OWNER_PASSWORD = "Owner1234";
    public static final String RIDER_PASSWORD = "Rider1234";
    /** "Biryani" in the categories created by catalog-service's migrations. */
    public static final long CATEGORY_BIRYANI = 3;

    private static Actor admin;

    private Platform() {
    }

    /** A short random text that makes names, e-mails and cities unique to one run of one flow. */
    public static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** The platform's first admin (created by user-service at startup). */
    public static synchronized Actor admin() {
        if (admin == null) {
            admin = Actor.signIn(E2EConfig.adminEmail(), E2EConfig.adminPassword(), Actor.PLATFORM_ADMIN);
        }
        return admin;
    }

    /** A new customer, signed in (password {@link #CUSTOMER_PASSWORD}). */
    public static Actor registerCustomer(String fullName, String email) {
        Api.anonymous().post("/api/v1/auth/register", Map.of("email", email,
                "password", CUSTOMER_PASSWORD, "fullName", fullName, "phone", randomPhone())).expect(201);
        return Actor.signIn(email, CUSTOMER_PASSWORD, Actor.CUSTOMER);
    }

    public static long addHomeAddress(Actor user, String line1, String city) {
        return Api.as(user).post("/api/v1/users/me/addresses", Map.of("type", "HOME", "line1", line1, "city", city,
                "state", "Karnataka", "postalCode", "560001", "country", "India")).expect(201).id();
    }

    /** A new restaurant owner, signed in: owner-{unique}@example.com, password {@link #OWNER_PASSWORD}. */
    public static Actor registerOwner(String unique) {
        String email = "owner-" + unique + "@example.com";
        Api.anonymous().post("/api/v1/auth/register", Map.of("email", email,
                "password", OWNER_PASSWORD, "fullName", "Owner " + unique, "role", Actor.RESTAURANT_ADMIN)).expect(201);
        return Actor.signIn(email, OWNER_PASSWORD, Actor.RESTAURANT_ADMIN);
    }

    /** A restaurant waiting for the platform's approval (PENDING), open around the clock once it is live. */
    public static long submitRestaurant(Actor owner, String name, String city) {
        String unique = unique();
        return Api.as(owner).post("/api/v1/restaurants/mine", Map.of(
                "name", name,
                "cuisines", List.of("Biryani", "Mughlai"),
                "phone", randomPhone(),
                "email", "kitchen-" + unique + "@example.com",
                "address", Map.of("line1", "12 Brigade Road", "city", city, "state", "Karnataka",
                        "postalCode", "560025", "country", "India"),
                "legalBusinessName", name + " LLP",
                "foodLicenseNumber", "LIC" + unique,
                "documents", List.of(Map.of("type", "FOOD_LICENSE", "url", "https://docs.example.com/" + unique + ".pdf")),
                "operatingHours", ALWAYS_OPEN)).expect(201).id();
    }

    /** The admin approves the documents and lets the restaurant go live. */
    public static void approveAndActivate(long restaurantId, String requestId) {
        Api.as(admin()).header("X-Request-Id", requestId)
                .put("/api/v1/admin/restaurants/" + restaurantId + "/approve", Map.of("reason", "Documents verified"))
                .expect(200);
        Api.as(admin()).header("X-Request-Id", requestId)
                .put("/api/v1/admin/restaurants/" + restaurantId + "/activate", Map.of("reason", "Ready"))
                .expect(200);
    }

    /** A live restaurant: submitted, approved, activated, and known to catalog-service (its menu answers). */
    public static long openRestaurant(Actor owner, String name, String city) {
        long restaurantId = submitRestaurant(owner, name, city);
        approveAndActivate(restaurantId, "e2e-" + unique());
        await("catalog-service hears that restaurant " + restaurantId + " is active")
                .until(() -> Api.anonymous().get("/api/v1/restaurants/" + restaurantId + "/menu").status() == 200);
        return restaurantId;
    }

    public static long addDish(Actor owner, long restaurantId, String name, String price, String foodType) {
        return Api.as(owner).post("/api/v1/restaurants/mine/" + restaurantId + "/menu-items", Map.of("name", name,
                "price", new BigDecimal(price), "categoryId", CATEGORY_BIRYANI, "foodType", foodType)).expect(201).id();
    }

    /**
     * A delivery partner with a vehicle, online in the city. delivery-partner-service creates partners from
     * UserRegistered and owns the profile; going online is delivery-service's (it asks for the profile if the
     * DeliveryPartnerChanged event has not reached it yet).
     */
    public static Actor availablePartner(String fullName, String city) {
        String email = "rider-" + unique() + "@example.com";
        Api.anonymous().post("/api/v1/auth/register", Map.of("email", email, "password", RIDER_PASSWORD,
                "fullName", fullName, "phone", randomPhone(), "role", Actor.DELIVERY_PARTNER)).expect(201);
        Actor partner = Actor.signIn(email, RIDER_PASSWORD, Actor.DELIVERY_PARTNER);
        await("delivery-partner-service creates partner " + partner.userId())
                .until(() -> Api.as(partner).get("/api/v1/delivery/partners/me").status() == 200);
        Api.as(partner).put("/api/v1/delivery/partners/me", Map.of("vehicleType", "MOTORCYCLE",
                "vehicleNumber", "KA01E2" + ThreadLocalRandom.current().nextInt(1000, 10_000), "city", city)).expect(200);
        Api.as(partner).put("/api/v1/delivery/partners/me/availability", Map.of("status", "AVAILABLE")).expect(200);
        return partner;
    }

    public static void putInCart(Actor customer, long menuItemId, int quantity) {
        Api.as(customer).put("/api/v1/cart/items/" + menuItemId, Map.of("quantity", quantity)).expect(200);
    }

    public static Response checkout(Actor customer, long addressId, String paymentMethod, String idempotencyKey) {
        return Api.as(customer).header("Idempotency-Key", idempotencyKey)
                .post("/api/v1/orders", Map.of("addressId", addressId, "paymentMethod", paymentMethod));
    }

    public static Response payByUpi(Actor customer, long orderId, String idempotencyKey) {
        return Api.as(customer).header("Idempotency-Key", idempotencyKey).post("/api/v1/payments",
                Map.of("orderId", orderId, "paymentMethod", "UPI", "upiId", "e2e@okhdfc"));
    }

    public static Response order(Actor customer, long orderId) {
        return Api.as(customer).get("/api/v1/orders/" + orderId).expect(200);
    }

    /** Accepted, prepared, ready for pickup: the delivery is then offered to a partner. */
    public static void restaurantPrepares(Actor owner, long restaurantId, long orderId) {
        for (String step : List.of("accept", "preparing", "ready")) {
            Api.as(owner).put("/api/v1/restaurants/mine/" + restaurantId + "/orders/" + orderId + "/" + step).expect(200);
        }
    }

    /** The delivery offered to the partner, once delivery-service's assignment job has picked them. */
    public static Response awaitOffer(Actor partner) {
        return await("a delivery offered to partner " + partner.userId())
                .until(() -> Api.as(partner).get("/api/v1/delivery/deliveries/mine/current"), r -> r.status() == 200);
    }

    /** The partner hands over the food; cashCollected is null for an order that was paid online. */
    public static Response deliver(Actor partner, long deliveryId, BigDecimal cashCollected) {
        return Api.as(partner).put("/api/v1/delivery/deliveries/mine/" + deliveryId + "/deliver",
                Collections.singletonMap("cashCollected", cashCollected));
    }

    /** Titles of the in-app notifications about one order, newest first. */
    public static List<String> inboxTitles(Actor user, long orderId) {
        Response inbox = Api.as(user).get("/api/v1/notifications?size=100").expect(200);
        return inbox.json("/content").valueStream()
                .filter(notification -> notification.path("orderId").asLong() == orderId)
                .map(notification -> notification.path("title").asString())
                .toList();
    }

    private static String randomPhone() {
        return "+9198" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
    }
}
