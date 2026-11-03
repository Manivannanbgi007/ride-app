package com.fooddelivery.events;

/**
 * Kafka topic names, in one place for producers and consumers.
 * Each topic carries one event type; the message key is the id of the entity, so all events
 * of one restaurant (or order, or user) land in the same partition and stay in order.
 */
public final class Topics {

    public static final String USER_REGISTERED = "fooddelivery.user.registered";
    public static final String RESTAURANT_CHANGED = "fooddelivery.restaurant.changed";
    public static final String ORDER_CREATED = "fooddelivery.order.created";
    public static final String ORDER_STATUS_CHANGED = "fooddelivery.order.status-changed";
    public static final String ORDER_CANCELLED = "fooddelivery.order.cancelled";
    public static final String ORDER_PAYMENT_REJECTED = "fooddelivery.order.payment-rejected";
    public static final String PAYMENT_STATUS_CHANGED = "fooddelivery.payment.status-changed";
    public static final String DELIVERY_STATUS_CHANGED = "fooddelivery.delivery.status-changed";
    /** Since 1.7.0: a partner's work status (delivery-service to delivery-partner-service). */
    public static final String DELIVERY_PARTNER_STATUS_CHANGED = "fooddelivery.delivery.partner-status-changed";
    /** Since 1.7.0: a partner's profile and account status (delivery-partner-service to delivery-service). */
    public static final String DELIVERY_PARTNER_CHANGED = "fooddelivery.delivery-partner.changed";
    public static final String MENU_ITEM_CHANGED = "fooddelivery.catalog.menu-item-changed";
    public static final String CATEGORY_CHANGED = "fooddelivery.catalog.category-changed";
    public static final String ADMIN_ACTION_AUDITED = "fooddelivery.admin.action-audited";
    public static final String CHAT_MESSAGE_SENT = "fooddelivery.chat.message-sent";
    public static final String CHAT_MESSAGES_READ = "fooddelivery.chat.messages-read";

    /** Suffix of the dead-letter topic that receives messages a consumer could not process. */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    private Topics() {
    }
}
