package com.fooddelivery.notificationservice.template;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** The wording of every notification, in one place. */
@Component
public class NotificationTemplates {

    // ---- accounts ----

    public Message welcome(String fullName, String role) {
        String nextStep = switch (role) {
            case "RESTAURANT_ADMIN" -> "Submit your restaurant for approval to start receiving orders.";
            case "DELIVERY_PARTNER" -> "Set yourself available to start receiving deliveries.";
            default -> "Find restaurants near you and order in a few taps.";
        };
        return new Message("Welcome to Food Delivery, " + fullName + "!",
                "Your account is ready. " + nextStep);
    }

    // ---- restaurants (to the owner) ----

    public Message restaurantSubmitted(String restaurantName, boolean resubmitted) {
        return new Message((resubmitted ? "We received your changes to '" : "We received '") + restaurantName + "'",
                "Our team reviews new restaurants within two working days. We will email you as soon as it is approved.");
    }

    public Message restaurantApproved(String restaurantName) {
        return new Message("'" + restaurantName + "' is approved",
                "Your documents were verified. Your restaurant goes live as soon as it is activated; "
                        + "you can prepare your menu now.");
    }

    public Message restaurantRejected(String restaurantName, String reason) {
        return new Message("'" + restaurantName + "' needs changes",
                "We could not approve your restaurant yet." + reasonSentence(reason)
                        + " Please update the details and resubmit it.");
    }

    public Message restaurantLive(String restaurantName) {
        return new Message("'" + restaurantName + "' is live",
                "Customers can now see your menu and order. New orders appear in your orders list.");
    }

    public Message restaurantDeactivated(String restaurantName, String reason) {
        return new Message("'" + restaurantName + "' is no longer visible to customers",
                "Your restaurant was deactivated." + reasonSentence(reason));
    }

    public Message restaurantSuspended(String restaurantName, String reason) {
        return new Message("'" + restaurantName + "' is suspended",
                "Your restaurant was suspended and cannot take orders." + reasonSentence(reason)
                        + " Contact platform support to resolve this.");
    }

    // ---- orders (to the customer) ----

    public Message orderPlaced(long orderId, String restaurantName) {
        return new Message(orderNo(orderId) + " placed",
                restaurantName + " has received your order and will confirm it shortly.");
    }

    public Message orderAccepted(long orderId, String restaurantName) {
        return new Message(orderNo(orderId) + " accepted", restaurantName + " accepted your order.");
    }

    public Message orderPreparing(long orderId, String restaurantName) {
        return new Message(orderNo(orderId) + " is being prepared", restaurantName + " is preparing your food.");
    }

    public Message orderReady(long orderId, String restaurantName) {
        return new Message(orderNo(orderId) + " is ready",
                "Your food is ready at " + restaurantName + " and waiting for a delivery partner.");
    }

    public Message partnerOnTheWay(long orderId, String partnerName, String vehicleType, String restaurantName) {
        String vehicle = vehicleType == null ? "" : " (" + vehicleType.toLowerCase().replace('_', ' ') + ")";
        return new Message(partnerName + " is delivering order #" + orderId,
                partnerName + vehicle + " is on the way to " + restaurantName + " to pick up your order.");
    }

    public Message deliveryOffered(long orderId, String restaurantName) {
        return new Message("New delivery: order #" + orderId,
                "Pick up order #" + orderId + " at " + restaurantName + ". Open the app to accept or decline it.");
    }

    public Message deliveryCancelledForPartner(long orderId, boolean pickedUp) {
        return new Message("Delivery of order #" + orderId + " cancelled",
                "The order was cancelled. " + (pickedUp ? "Please take the food back to the restaurant. " : "")
                        + "You can take new deliveries.");
    }

    public Message orderOutForDelivery(long orderId) {
        return new Message(orderNo(orderId) + " is on its way", "Your delivery partner has picked up your order.");
    }

    public Message orderDelivered(long orderId) {
        return new Message(orderNo(orderId) + " delivered", "Enjoy your meal! Thank you for ordering with us.");
    }

    public Message orderCancelled(long orderId, String finalStatus, String cancelledBy, String reason,
                                  boolean paid, BigDecimal total, String currency) {
        String subject;
        String body;
        if ("REJECTED".equals(finalStatus)) {
            subject = orderNo(orderId) + " was declined";
            body = "The restaurant could not take your order.";
        } else if ("CUSTOMER".equals(cancelledBy)) {
            subject = orderNo(orderId) + " cancelled";
            body = "Your order was cancelled as you asked.";
        } else {
            subject = orderNo(orderId) + " was cancelled";
            body = "We had to cancel your order.";
        }
        body += reasonSentence(reason);
        if (paid) {
            body += " Your payment of " + money(total, currency) + " will be refunded to the original payment method.";
        }
        return new Message(subject, body);
    }

    public Message paymentFailed(long orderId, String reason) {
        return new Message("Payment for order #" + orderId + " failed",
                "Your payment did not go through." + reasonSentence(reason)
                        + " You can try again or choose another payment method.");
    }

    public Message refundCompleted(long orderId, BigDecimal amount, String currency) {
        return new Message("Refund for order #" + orderId + " processed",
                money(amount, currency) + " is on its way back to your original payment method."
                        + " It can take 3-5 working days to appear.");
    }

    // ---- orders (to the restaurant) ----

    public Message newOrderForRestaurant(long orderId) {
        return new Message("New order #" + orderId, "Accept or reject it in your orders list.");
    }

    public Message orderCancelledForRestaurant(long orderId, String reason) {
        return new Message(orderNo(orderId) + " was cancelled",
                "Stop preparing this order." + reasonSentence(reason));
    }

    private static String reasonSentence(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "";
        }
        String trimmed = reason.trim();
        return " Reason: " + trimmed + (trimmed.endsWith(".") ? "" : ".");
    }

    private static String money(BigDecimal amount, String currency) {
        return currency + " " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String orderNo(long orderId) {
        return "Order #" + orderId;
    }
}
