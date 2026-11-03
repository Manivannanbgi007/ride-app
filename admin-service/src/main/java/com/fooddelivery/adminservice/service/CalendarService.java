package com.fooddelivery.adminservice.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.fooddelivery.adminservice.client.CalendarClient;
import com.fooddelivery.adminservice.client.PlatformService;
import com.fooddelivery.adminservice.client.RemoteRecords.CouponRow;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderCalendar;
import com.fooddelivery.adminservice.client.RemoteRecords.RegistrationCalendar;
import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.dto.request.CalendarEventType;
import com.fooddelivery.adminservice.dto.response.CalendarResponse;
import com.fooddelivery.adminservice.dto.response.CalendarResponse.Event;
import com.fooddelivery.adminservice.dto.response.CalendarResponse.Kind;
import com.fooddelivery.adminservice.dto.response.CalendarResponse.Ref;
import com.fooddelivery.adminservice.dto.response.CalendarResponse.Tone;
import com.fooddelivery.adminservice.exception.InvalidRequestException;
import com.fooddelivery.adminservice.exception.RemoteServiceException;
import com.fooddelivery.adminservice.service.ParallelCalls.Batch;
import com.fooddelivery.adminservice.service.ParallelCalls.Call;
import com.fooddelivery.adminservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The admin calendar: orders and restaurants per day and coupons by validity, made from what the owning services
 * report (nothing is stored or scheduled here). Only the asked-for sources are called, at the same time. A source
 * that fails leaves its events out and is named in "unavailable"; only when every asked source fails the whole
 * request fails (503), because an empty calendar would then look like "nothing happened".
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    /** The same limit as the services' calendar endpoints, counting both days. */
    public static final int MAX_DAYS = 400;
    static final String ACTIVE = "ACTIVE";
    static final String INACTIVE = "INACTIVE";
    static final String EXPIRED = "EXPIRED";

    private static final Comparator<Event> ORDER = Comparator.comparing(Event::start)
            .thenComparing(Event::kind).thenComparing(Event::id);

    private final CalendarClient calendarClient;
    private final ParallelCalls parallelCalls;
    private final AppProperties appProperties;
    private final Clock clock;

    /** @param types what to show; null or empty: everything */
    public CalendarResponse getCalendar(LocalDate from, LocalDate to, Collection<CalendarEventType> types,
                                        CallerInfo caller) {
        validate(from, to);
        Set<CalendarEventType> wanted = types == null || types.isEmpty() ? EnumSet.allOf(CalendarEventType.class)
                : EnumSet.copyOf(types);
        ZoneId zone = ZoneId.of(appProperties.businessTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));

        Batch batch = parallelCalls.batch();
        Call<OrderCalendar> orders = wanted.contains(CalendarEventType.ORDERS)
                ? batch.start(PlatformService.ORDER, () -> calendarClient.orderCalendar(from, to, caller)) : null;
        Call<List<CouponRow>> coupons = wanted.contains(CalendarEventType.COUPONS)
                ? batch.start(PlatformService.PROMOTION, () -> calendarClient.coupons(from, to, caller)) : null;
        Call<RegistrationCalendar> restaurants = wanted.contains(CalendarEventType.RESTAURANTS)
                ? batch.start(PlatformService.RESTAURANT, () -> calendarClient.registrationCalendar(from, to, caller))
                : null;

        List<CalendarEventType> unavailable = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        if (orders != null) {
            OrderCalendar answer = batch.await(orders);
            if (answer == null) {
                unavailable.add(CalendarEventType.ORDERS);
            } else {
                events.addAll(orderEvents(answer, from, to));
            }
        }
        if (coupons != null) {
            List<CouponRow> answer = batch.await(coupons);
            if (answer == null) {
                unavailable.add(CalendarEventType.COUPONS);
            } else {
                events.addAll(couponEvents(answer, from, to, today));
            }
        }
        if (restaurants != null) {
            RegistrationCalendar answer = batch.await(restaurants);
            if (answer == null) {
                unavailable.add(CalendarEventType.RESTAURANTS);
            } else {
                events.addAll(restaurantEvents(answer, from, to));
            }
        }
        if (unavailable.size() == wanted.size()) {
            throw new RemoteServiceException(String.join(", ", batch.unavailable()),
                    new IllegalStateException("no calendar source answered"));
        }
        events.sort(ORDER);
        return new CalendarResponse(from, to, zone.getId(), unavailable, events);
    }

    /** Both days are included; at most {@link #MAX_DAYS} days. */
    static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new InvalidRequestException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new InvalidRequestException("to must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new InvalidRequestException("The calendar covers at most " + MAX_DAYS + " days");
        }
    }

    /** One event per kind and day, only where there is something to show. */
    static List<Event> orderEvents(OrderCalendar answer, LocalDate from, LocalDate to) {
        List<Event> events = new ArrayList<>();
        for (var day : answer.days()) {
            if (outside(day.date(), from, to)) {
                continue;
            }
            if (day.placed() > 0) {
                events.add(dayEvent("orders-placed-", CalendarEventType.ORDERS, Kind.ORDERS_PLACED,
                        plural(day.placed(), "order", "orders"), day.date(), day.placed(), Tone.info));
            }
            if (day.delivered() > 0) {
                events.add(dayEvent("orders-delivered-", CalendarEventType.ORDERS, Kind.ORDERS_DELIVERED,
                        day.delivered() + " delivered", day.date(), day.delivered(), Tone.success));
            }
            if (day.cancelled() > 0) {
                events.add(dayEvent("orders-cancelled-", CalendarEventType.ORDERS, Kind.ORDERS_CANCELLED,
                        day.cancelled() + " cancelled", day.date(), day.cancelled(), Tone.danger));
            }
        }
        return events;
    }

    static List<Event> restaurantEvents(RegistrationCalendar answer, LocalDate from, LocalDate to) {
        List<Event> events = new ArrayList<>();
        for (var day : answer.days()) {
            if (day.registered() > 0 && !outside(day.date(), from, to)) {
                events.add(dayEvent("restaurants-", CalendarEventType.RESTAURANTS, Kind.RESTAURANTS_REGISTERED,
                        plural(day.registered(), "new restaurant", "new restaurants"), day.date(), day.registered(),
                        Tone.warning));
            }
        }
        return events;
    }

    /** A coupon spans its validity dates, which may begin before and end after the range. */
    static List<Event> couponEvents(List<CouponRow> coupons, LocalDate from, LocalDate to, LocalDate today) {
        List<Event> events = new ArrayList<>();
        for (CouponRow coupon : coupons) {
            if (coupon.validFrom() == null || coupon.validUntil() == null || coupon.validFrom().isAfter(to)
                    || coupon.validUntil().isBefore(from)) {
                continue;
            }
            String status = couponStatus(coupon, today);
            events.add(new Event("coupon-" + coupon.id(), CalendarEventType.COUPONS, Kind.COUPON, coupon.code(),
                    coupon.validFrom(), coupon.validUntil(), (long) coupon.timesUsed(), status, couponTone(status),
                    new Ref(coupon.id())));
        }
        return events;
    }

    /**
     * The status the calendar shows. promotion-service only knows ACTIVE or INACTIVE (set by admins); a coupon that
     * is ACTIVE but whose last valid day is before today (business time zone) has run out, so it is EXPIRED here.
     * An INACTIVE coupon stays INACTIVE, whatever its dates.
     */
    static String couponStatus(CouponRow coupon, LocalDate today) {
        if (ACTIVE.equals(coupon.status())) {
            return coupon.validUntil().isBefore(today) ? EXPIRED : ACTIVE;
        }
        return INACTIVE;
    }

    static Tone couponTone(String status) {
        return switch (status) {
            case ACTIVE -> Tone.success;
            case EXPIRED -> Tone.warning;
            default -> Tone.neutral;
        };
    }

    private static Event dayEvent(String idPrefix, CalendarEventType type, Kind kind, String title, LocalDate date,
                                  long count, Tone tone) {
        return new Event(idPrefix + date, type, kind, title, date, date, count, null, tone, null);
    }

    private static boolean outside(LocalDate day, LocalDate from, LocalDate to) {
        return day == null || day.isBefore(from) || day.isAfter(to);
    }

    private static String plural(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }
}
