package com.fooddelivery.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fooddelivery.e2e.support.Actor;
import com.fooddelivery.e2e.support.Api;
import com.fooddelivery.e2e.support.E2ETestBase;
import com.fooddelivery.e2e.support.Platform;
import com.fooddelivery.e2e.support.Response;
import org.junit.jupiter.api.Test;

/**
 * Coupons across promotion-service and order-service: the cart checks a coupon, checkout reserves one use, and the
 * order's events apply it or give it back.
 */
class CouponE2ETest extends E2ETestBase {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Test
    void aCouponDiscountsTheOrderAndItsUseIsGivenBackWhenTheOrderIsCancelled() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        String code = "E2E" + unique.toUpperCase();
        long couponId = createCoupon(code, 2);
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Coupon Kitchen " + unique, city);
        long biryani = Platform.addDish(owner, restaurantId, "Chicken Biryani", "299.00", "NON_VEG");
        Actor customer = Platform.registerCustomer("Farah " + unique, "farah-" + unique + "@example.com");
        long addressId = Platform.addHomeAddress(customer, "4 Museum Road", city);

        // The cart must qualify: 299.00 is below the coupon's 499.00
        Platform.putInCart(customer, biryani, 1);
        Response tooSmall = applyCoupon(customer, code.toLowerCase());
        assertThat(tooSmall.status()).isEqualTo(422);
        assertThat(tooSmall.text("/detail")).contains("minimum order 499.00");

        Platform.putInCart(customer, biryani, 2);
        Response cart = applyCoupon(customer, code.toLowerCase()).expect(200);
        assertThat(cart.text("/coupon/code")).isEqualTo(code);
        assertThat(cart.json("/coupon/applied").asBoolean()).isTrue();

        // Checkout: 598.00 - 100.00, delivery 40.00 (under 499 after the discount), 5 % tax on 498.00
        Response order = Platform.checkout(customer, addressId, "CASH_ON_DELIVERY", "coupon-" + unique).expect(201);
        long orderId = order.id();
        assertThat(order.text("/couponCode")).isEqualTo(code);
        assertThat(order.amount("/price/discountAmount")).isEqualByComparingTo("100.00");
        assertThat(order.amount("/price/totalAmount")).isEqualByComparingTo("562.90");
        await().untilAsserted(() -> assertThat(redemptionOf(couponId, orderId)).isEqualTo("APPLIED"));
        assertThat(timesUsed(couponId)).isEqualTo(1);

        // Once per customer...
        Platform.putInCart(customer, biryani, 2);
        assertThat(applyCoupon(customer, code).text("/detail")).isEqualTo("You have already used this coupon");

        // ...until the order is cancelled: the use comes back
        Api.as(customer).put("/api/v1/orders/" + orderId + "/cancel", Map.of("reason", "Ordered twice")).expect(200);
        await().untilAsserted(() -> assertThat(redemptionOf(couponId, orderId)).isEqualTo("RELEASED"));
        assertThat(timesUsed(couponId)).isZero();
        assertThat(applyCoupon(customer, code).expect(200).json("/coupon/applied").asBoolean()).isTrue();
    }

    @Test
    void aCouponIsNoLongerOfferedOnceItsUsesAreTaken() {
        String unique = Platform.unique();
        String city = "E2E-" + unique;
        String code = "ONE" + unique.toUpperCase();
        createCoupon(code, 1);
        Actor owner = Platform.registerOwner(unique);
        long restaurantId = Platform.openRestaurant(owner, "Limit Kitchen " + unique, city);
        long biryani = Platform.addDish(owner, restaurantId, "Egg Biryani", "260.00", "EGG");
        assertThat(offeredAt(restaurantId)).contains(code);

        Actor first = Platform.registerCustomer("Gita " + unique, "gita-" + unique + "@example.com");
        Platform.putInCart(first, biryani, 2);
        applyCoupon(first, code).expect(200);
        Platform.checkout(first, Platform.addHomeAddress(first, "6 Cubbon Road", city), "CASH_ON_DELIVERY",
                "limit-" + unique).expect(201);

        Actor second = Platform.registerCustomer("Hari " + unique, "hari-" + unique + "@example.com");
        Platform.putInCart(second, biryani, 2);
        assertThat(applyCoupon(second, code).status()).isEqualTo(422);
        assertThat(offeredAt(restaurantId)).doesNotContain(code);
    }

    private static long createCoupon(String code, int usageLimit) {
        Response created = Api.as(Platform.admin()).post("/api/v1/admin/coupons", Map.of(
                "code", code, "description", "100 off orders of 499+", "discountType", "FIXED", "discountValue", 100,
                "minOrderAmount", 499, "validFrom", TODAY.minusDays(1).toString(),
                "validUntil", TODAY.plusDays(30).toString(), "usageLimit", usageLimit, "perCustomerLimit", 1)).expect(201);
        assertThat(created.text("/availability")).isEqualTo("AVAILABLE");
        return created.id();
    }

    private static Response applyCoupon(Actor customer, String code) {
        return Api.as(customer).put("/api/v1/cart/coupon", Map.of("code", code));
    }

    private static String redemptionOf(long couponId, long orderId) {
        Response redemptions = Api.as(Platform.admin())
                .get("/api/v1/admin/coupons/" + couponId + "/redemptions?size=50").expect(200);
        return redemptions.json("/content").valueStream()
                .filter(redemption -> redemption.path("orderId").asLong() == orderId)
                .map(redemption -> redemption.path("status").asString())
                .findFirst().orElse(null);
    }

    private static long timesUsed(long couponId) {
        return Api.as(Platform.admin()).get("/api/v1/admin/coupons/" + couponId).expect(200).number("/coupon/timesUsed");
    }

    private static List<String> offeredAt(long restaurantId) {
        return Api.anonymous().get("/api/v1/coupons?restaurantId=" + restaurantId).expect(200).body().valueStream()
                .map(coupon -> coupon.path("code").asString())
                .toList();
    }
}
