package com.fooddelivery.notificationservice.channel;

/**
 * What a sender delivers.
 *
 * @param address       email address, phone number or in-app inbox of the recipient
 * @param recipientName for the greeting ("Hi Priya")
 */
public record OutgoingMessage(String address, String recipientName, String subject, String body) {
}
