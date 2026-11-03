package com.fooddelivery.notificationservice.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** What customers and restaurants read: the order's progress, and why and how a cancelled order ends. */
class NotificationTemplatesTest {

    private final NotificationTemplates templates = new NotificationTemplates();

    @Test
    void theCustomerFollowsTheOrderStepByStep() {
        assertThat(templates.orderPlaced(42, "Spice Hub").subject()).isEqualTo("Order #42 placed");
        assertThat(templates.orderAccepted(42, "Spice Hub"))
                .isEqualTo(new Message("Order #42 accepted", "Spice Hub accepted your order."));
        assertThat(templates.orderPreparing(42, "Spice Hub"))
                .isEqualTo(new Message("Order #42 is being prepared", "Spice Hub is preparing your food."));
        assertThat(templates.orderReady(42, "Spice Hub")).isEqualTo(new Message("Order #42 is ready",
                "Your food is ready at Spice Hub and waiting for a delivery partner."));
        assertThat(templates.orderOutForDelivery(42).subject()).isEqualTo("Order #42 is on its way");
        assertThat(templates.orderDelivered(42))
                .isEqualTo(new Message("Order #42 delivered", "Enjoy your meal! Thank you for ordering with us."));
    }

    @Test
    void aCancellationSaysWhoEndedTheOrderAndAboutTheRefund() {
        Message declined = templates.orderCancelled(42, "REJECTED", "RESTAURANT", "Out of rice", true,
                new BigDecimal("353.95"), "INR");
        assertThat(declined.subject()).isEqualTo("Order #42 was declined");
        assertThat(declined.body()).startsWith("The restaurant could not take your order.")
                .contains("Out of rice")
                .endsWith("Your payment of INR 353.95 will be refunded to the original payment method.");

        Message byCustomer = templates.orderCancelled(42, "CANCELLED", "CUSTOMER", null, false, null, "INR");
        assertThat(byCustomer.subject()).isEqualTo("Order #42 cancelled");
        assertThat(byCustomer.body()).isEqualTo("Your order was cancelled as you asked.");

        Message byPlatform = templates.orderCancelled(42, "CANCELLED", "ADMIN", "Restaurant closed", false, null, "INR");
        assertThat(byPlatform.subject()).isEqualTo("Order #42 was cancelled");
        assertThat(byPlatform.body()).startsWith("We had to cancel your order.").contains("Restaurant closed")
                .doesNotContain("refund");
    }

    @Test
    void theRestaurantIsToldToStopPreparing() {
        Message message = templates.orderCancelledForRestaurant(42, "Customer unreachable");

        assertThat(message.subject()).isEqualTo("Order #42 was cancelled");
        assertThat(message.body()).startsWith("Stop preparing this order.").contains("Customer unreachable");
    }
}
