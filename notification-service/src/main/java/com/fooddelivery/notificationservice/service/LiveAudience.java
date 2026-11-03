package com.fooddelivery.notificationservice.service;

import java.util.Objects;

import com.fooddelivery.notificationservice.web.LiveClient;

/**
 * Who may see a live event: the one rule that decides it, in one place.
 * <ul>
 *   <li>Platform admins see everything (the dashboard and lists follow the whole platform).</li>
 *   <li>A customer sees the events of their own orders and payments ({@code customerId}).</li>
 *   <li>A restaurant owner sees the events of the restaurants they own ({@code restaurantId}) and of their own
 *       restaurant's review ({@code ownerUserId}).</li>
 *   <li>A delivery partner sees the deliveries they are on ({@code partnerUserId}).</li>
 *   <li>A chat event ({@link #chat}) goes to the conversation's participant (matched on {@code customerId}, whatever
 *       their role) and the platform admins: nobody else.</li>
 * </ul>
 * Anybody else sees nothing: with no match, an event is sent to no one.
 *
 * @param customerId    the customer the event is about, if any
 * @param ownerUserId   the restaurant owner the event is about, if any
 * @param partnerUserId the delivery partner the event is about, if any
 * @param restaurantId  the restaurant the event is about, if its owners should see it
 */
public record LiveAudience(Long customerId, Long ownerUserId, Long partnerUserId, Long restaurantId) {

    /** A support conversation: its participant, plus (always) the platform admins. */
    public static LiveAudience chat(Long participantUserId) {
        return new LiveAudience(participantUserId, null, null, null);
    }

    public boolean includes(LiveClient client) {
        if (client.isPlatformAdmin()) {
            return true;
        }
        return Objects.equals(customerId, client.userId())
                || Objects.equals(ownerUserId, client.userId())
                || Objects.equals(partnerUserId, client.userId())
                || client.ownsRestaurant(restaurantId);
    }
}
