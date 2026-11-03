package com.fooddelivery.adminservice.dto.response;

import java.time.LocalDate;
import java.util.List;

import com.fooddelivery.adminservice.dto.request.CalendarEventType;

/**
 * Events of the admin calendar for a range of days, made from real data (nothing is scheduled or stored here).
 *
 * @param timeZone    the business time zone: every date here is a calendar day in it
 * @param unavailable sources that did not answer (the others are still in "events"); empty when complete
 * @param events      sorted by start, then kind (declaration order of {@link Kind}), then id
 */
public record CalendarResponse(
        LocalDate from,
        LocalDate to,
        String timeZone,
        List<CalendarEventType> unavailable,
        List<Event> events
) {

    /** What an event is about; the declaration order is the sort order of events that start on the same day. */
    public enum Kind {
        ORDERS_PLACED, ORDERS_DELIVERED, ORDERS_CANCELLED, COUPON, RESTAURANTS_REGISTERED
    }

    /** How the calendar colours an event. */
    public enum Tone {
        info, success, danger, warning, neutral
    }

    /**
     * @param id     stable across requests ("orders-placed-2026-10-09", "coupon-7", "restaurants-2026-10-09")
     * @param end    last day, included (same as start for single-day events)
     * @param count  orders or restaurants of the day; for a coupon how many times it was used
     * @param status coupons only: ACTIVE, INACTIVE or EXPIRED
     * @param ref    what to open when the event is clicked; null when there is nothing to open
     */
    public record Event(
            String id,
            CalendarEventType type,
            Kind kind,
            String title,
            LocalDate start,
            LocalDate end,
            Long count,
            String status,
            Tone tone,
            Ref ref
    ) {
    }

    public record Ref(Long couponId) {
    }
}
