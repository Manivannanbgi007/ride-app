package com.fooddelivery.adminservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fooddelivery.adminservice.client.CalendarClient;
import com.fooddelivery.adminservice.client.RemoteRecords.CouponRow;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderCalendar;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderCalendarDay;
import com.fooddelivery.adminservice.client.RemoteRecords.RegistrationCalendar;
import com.fooddelivery.adminservice.client.RemoteRecords.RegistrationDay;
import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.dto.request.CalendarEventType;
import com.fooddelivery.adminservice.dto.response.CalendarResponse;
import com.fooddelivery.adminservice.dto.response.CalendarResponse.Event;
import com.fooddelivery.adminservice.dto.response.CalendarResponse.Kind;
import com.fooddelivery.adminservice.dto.response.CalendarResponse.Tone;
import com.fooddelivery.adminservice.entity.Role;
import com.fooddelivery.adminservice.exception.InvalidRequestException;
import com.fooddelivery.adminservice.exception.RemoteServiceException;
import com.fooddelivery.adminservice.web.CallerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** How the calendar is put together from the three sources: events, order, partial answers and validation. */
class CalendarServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-09");
    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-31");
    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "127.0.0.1", "req-1");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final CalendarClient client = mock(CalendarClient.class);
    private final AppProperties properties = new AppProperties("Asia/Kolkata", Duration.ofMillis(500), null);
    // 2026-10-09 10:00 UTC is the 9th, 15:30 in Kolkata
    private final CalendarService service = new CalendarService(client, new ParallelCalls(executor, properties),
            properties, Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void eventsFromAllSourcesAreMergedAndSortedByStartThenKindThenId() {
        when(client.orderCalendar(FROM, TO, ADMIN)).thenReturn(new OrderCalendar(List.of(
                new OrderCalendarDay(date("2026-10-09"), 12, 9, 2), new OrderCalendarDay(date("2026-10-03"), 1, 0, 0))));
        when(client.registrationCalendar(FROM, TO, ADMIN)).thenReturn(new RegistrationCalendar(List.of(
                new RegistrationDay(date("2026-10-09"), 3), new RegistrationDay(date("2026-10-03"), 1))));
        when(client.coupons(FROM, TO, ADMIN)).thenReturn(List.of(
                coupon(7, "WELCOME100", "ACTIVE", "2026-10-09", "2026-11-30", 4),
                coupon(5, "EARLY", "ACTIVE", "2026-09-20", "2026-10-03", 0)));

        CalendarResponse calendar = service.getCalendar(FROM, TO, null, ADMIN);

        assertThat(calendar.from()).isEqualTo(FROM);
        assertThat(calendar.to()).isEqualTo(TO);
        assertThat(calendar.timeZone()).isEqualTo("Asia/Kolkata");
        assertThat(calendar.unavailable()).isEmpty();
        assertThat(calendar.events()).extracting(Event::id).containsExactly(
                "coupon-5",                       // starts 09-20: before the range, still listed, first
                "orders-placed-2026-10-03", "restaurants-2026-10-03",
                "orders-placed-2026-10-09", "orders-delivered-2026-10-09", "orders-cancelled-2026-10-09",
                "coupon-7", "restaurants-2026-10-09");
    }

    @Test
    void dayEventsHaveTitlesCountsTonesAndStayOnOneDay() {
        List<Event> events = CalendarService.orderEvents(new OrderCalendar(List.of(
                new OrderCalendarDay(date("2026-10-09"), 12, 9, 2),
                new OrderCalendarDay(date("2026-10-10"), 1, 0, 0),
                new OrderCalendarDay(date("2026-10-11"), 0, 0, 0),         // nothing to show
                new OrderCalendarDay(date("2026-11-20"), 5, 5, 0))), FROM, TO); // outside the range

        assertThat(events).extracting(Event::title).containsExactly("12 orders", "9 delivered", "2 cancelled", "1 order");
        Event placed = events.getFirst();
        assertThat(placed).isEqualTo(new Event("orders-placed-2026-10-09", CalendarEventType.ORDERS, Kind.ORDERS_PLACED,
                "12 orders", date("2026-10-09"), date("2026-10-09"), 12L, null, Tone.info, null));
        assertThat(events.get(1).tone()).isEqualTo(Tone.success);
        assertThat(events.get(2).tone()).isEqualTo(Tone.danger);
        assertThat(events.get(2).kind()).isEqualTo(Kind.ORDERS_CANCELLED);

        List<Event> restaurants = CalendarService.restaurantEvents(new RegistrationCalendar(List.of(
                new RegistrationDay(date("2026-10-09"), 3), new RegistrationDay(date("2026-10-10"), 1),
                new RegistrationDay(date("2026-10-11"), 0))), FROM, TO);
        assertThat(restaurants).extracting(Event::title).containsExactly("3 new restaurants", "1 new restaurant");
        assertThat(restaurants.getFirst().id()).isEqualTo("restaurants-2026-10-09");
        assertThat(restaurants.getFirst().kind()).isEqualTo(Kind.RESTAURANTS_REGISTERED);
        assertThat(restaurants.getFirst().tone()).isEqualTo(Tone.warning);
        assertThat(restaurants.getFirst().count()).isEqualTo(3);
    }

    @Test
    void couponsSpanTheirValidityAndShowStatusToneAndRef() {
        List<Event> events = CalendarService.couponEvents(List.of(
                coupon(1, "RUNNING", "ACTIVE", "2026-09-01", "2026-12-31", 40),   // before and after the range
                coupon(2, "PAUSED", "INACTIVE", "2026-10-01", "2026-10-31", 0),
                coupon(3, "OLD", "ACTIVE", "2026-09-01", "2026-10-08", 9),        // ended yesterday
                coupon(4, "LASTDAY", "ACTIVE", "2026-10-01", "2026-10-09", 2),    // ends today: still active
                coupon(5, "PASTPAUSED", "INACTIVE", "2026-09-01", "2026-10-02", 1),
                coupon(6, "FAR", "ACTIVE", "2027-01-01", "2027-02-01", 0)),      // outside the range
                FROM, TO, TODAY);

        assertThat(events).extracting(Event::id).containsExactly("coupon-1", "coupon-2", "coupon-3", "coupon-4", "coupon-5");
        Event running = events.getFirst();
        assertThat(running.title()).isEqualTo("RUNNING");
        assertThat(running.type()).isEqualTo(CalendarEventType.COUPONS);
        assertThat(running.kind()).isEqualTo(Kind.COUPON);
        assertThat(running.start()).isEqualTo(date("2026-09-01"));
        assertThat(running.end()).isEqualTo(date("2026-12-31"));
        assertThat(running.count()).isEqualTo(40);
        assertThat(running.status()).isEqualTo("ACTIVE");
        assertThat(running.tone()).isEqualTo(Tone.success);
        assertThat(running.ref().couponId()).isEqualTo(1L);
        assertThat(events.get(1)).extracting(Event::status, Event::tone).containsExactly("INACTIVE", Tone.neutral);
        // ACTIVE with a last day before today (business zone) is EXPIRED; INACTIVE stays INACTIVE
        assertThat(events.get(2)).extracting(Event::status, Event::tone).containsExactly("EXPIRED", Tone.warning);
        assertThat(events.get(3).status()).isEqualTo("ACTIVE");
        assertThat(events.get(4).status()).isEqualTo("INACTIVE");
    }

    @Test
    void expiredIsDecidedByTheBusinessDayNotUtc() {
        // 2026-10-09 20:00 UTC is already the 10th in Kolkata (01:30): a coupon ending on the 9th has expired there
        CalendarService lateService = new CalendarService(client, new ParallelCalls(executor, properties), properties,
                Clock.fixed(Instant.parse("2026-10-09T20:00:00Z"), ZoneOffset.UTC));
        when(client.coupons(FROM, TO, ADMIN)).thenReturn(List.of(coupon(4, "LASTDAY", "ACTIVE", "2026-10-01", "2026-10-09", 2)));

        CalendarResponse calendar = lateService.getCalendar(FROM, TO, List.of(CalendarEventType.COUPONS), ADMIN);

        assertThat(calendar.events().getFirst().status()).isEqualTo("EXPIRED");
    }

    @Test
    void onlyTheAskedTypesAreCalled() {
        when(client.orderCalendar(FROM, TO, ADMIN)).thenReturn(new OrderCalendar(List.of()));

        CalendarResponse calendar = service.getCalendar(FROM, TO, List.of(CalendarEventType.ORDERS), ADMIN);

        assertThat(calendar.events()).isEmpty();
        assertThat(calendar.unavailable()).isEmpty();
        verify(client).orderCalendar(FROM, TO, ADMIN);
        verify(client, never()).coupons(any(), any(), any());
        verify(client, never()).registrationCalendar(any(), any(), any());
    }

    @Test
    void aFailingSourceIsListedAndTheRestIsStillReturned() {
        when(client.orderCalendar(FROM, TO, ADMIN)).thenReturn(new OrderCalendar(List.of(
                new OrderCalendarDay(date("2026-10-09"), 2, 1, 0))));
        when(client.coupons(FROM, TO, ADMIN)).thenThrow(new RemoteServiceException("promotion-service",
                new IllegalStateException("Connection refused")));
        when(client.registrationCalendar(FROM, TO, ADMIN)).thenReturn(new RegistrationCalendar(List.of()));

        CalendarResponse calendar = service.getCalendar(FROM, TO, null, ADMIN);

        assertThat(calendar.unavailable()).containsExactly(CalendarEventType.COUPONS);
        assertThat(calendar.events()).extracting(Event::id).containsExactly("orders-placed-2026-10-09",
                "orders-delivered-2026-10-09");
    }

    @Test
    void aSlowSourceCountsAsUnavailable() {
        when(client.orderCalendar(FROM, TO, ADMIN)).thenAnswer(call -> {
            Thread.sleep(3_000);
            return new OrderCalendar(List.of());
        });
        when(client.registrationCalendar(FROM, TO, ADMIN)).thenReturn(new RegistrationCalendar(List.of()));

        CalendarResponse calendar = service.getCalendar(FROM, TO, List.of(CalendarEventType.ORDERS,
                CalendarEventType.RESTAURANTS), ADMIN);

        assertThat(calendar.unavailable()).containsExactly(CalendarEventType.ORDERS);
    }

    @Test
    void whenEverySourceFailsTheRequestFailsWith503() {
        RemoteServiceException down = new RemoteServiceException("x", new IllegalStateException("down"));
        when(client.orderCalendar(FROM, TO, ADMIN)).thenThrow(down);
        when(client.coupons(FROM, TO, ADMIN)).thenThrow(down);
        when(client.registrationCalendar(FROM, TO, ADMIN)).thenThrow(down);

        assertThatThrownBy(() -> service.getCalendar(FROM, TO, null, ADMIN)).isInstanceOf(RemoteServiceException.class);
        // One asked source that fails is all of the asked sources
        assertThatThrownBy(() -> service.getCalendar(FROM, TO, List.of(CalendarEventType.COUPONS), ADMIN))
                .isInstanceOf(RemoteServiceException.class);
    }

    @Test
    void theRangeIsChecked() {
        assertThatThrownBy(() -> service.getCalendar(TO, FROM, null, ADMIN))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("before");
        // 400 days (both days included) are fine, 401 are not
        when(client.orderCalendar(any(), any(), any())).thenReturn(new OrderCalendar(List.of()));
        assertThat(service.getCalendar(FROM, FROM.plusDays(399), List.of(CalendarEventType.ORDERS), ADMIN).events()).isEmpty();
        assertThatThrownBy(() -> service.getCalendar(FROM, FROM.plusDays(400), null, ADMIN))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("400");
        assertThat(CalendarService.MAX_DAYS).isEqualTo(400);
    }

    @Test
    void aSingleDayRangeIsAllowed() {
        when(client.orderCalendar(TODAY, TODAY, ADMIN)).thenReturn(new OrderCalendar(List.of(
                new OrderCalendarDay(TODAY, 1, 0, 0))));

        assertThat(service.getCalendar(TODAY, TODAY, List.of(CalendarEventType.ORDERS), ADMIN).events()).hasSize(1);
    }

    @Test
    void invalidRequestsDoNotCallAnySource() {
        assertThatThrownBy(() -> service.getCalendar(TO, FROM, null, ADMIN)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(client);
    }

    private static LocalDate date(String day) {
        return LocalDate.parse(day);
    }

    private static CouponRow coupon(long id, String code, String status, String validFrom, String validUntil, int used) {
        return new CouponRow(id, code, status, date(validFrom), date(validUntil), used);
    }
}
