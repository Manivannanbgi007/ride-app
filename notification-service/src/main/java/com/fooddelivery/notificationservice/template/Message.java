package com.fooddelivery.notificationservice.template;

/** Subject (email subject, SMS prefix, push title) and text of a notification. */
public record Message(String subject, String body) {
}
