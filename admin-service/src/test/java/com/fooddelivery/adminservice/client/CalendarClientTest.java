package com.fooddelivery.adminservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.CouponPage;
import com.fooddelivery.adminservice.client.RemoteRecords.CouponRow;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderCalendar;
import com.fooddelivery.adminservice.entity.Role;
import com.fooddelivery.adminservice.web.CallerInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Which service, path and parameters the calendar calls use, and how coupons are paged. */
class CalendarClientTest {

    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-31");
    private static final CallerInfo ADMIN = new CallerInfo(7L, Role.PLATFORM_ADMIN, "127.0.0.1", "req-1", "admin-7-token");

    private final PlatformClient platformClient = mock(PlatformClient.class);
    private final CalendarClient client = new CalendarClient(platformClient);

    @Test
    void ordersAreAskedFromOrderServiceForTheRange() {
        OrderCalendar answer = new OrderCalendar(List.of());
        when(platformClient.getAsAdmin(eq(PlatformService.ORDER), eq("/api/v1/admin/orders/stats/calendar"),
                eq(Map.of("from", FROM, "to", TO)), eq(OrderCalendar.class), eq(ADMIN))).thenReturn(answer);

        assertThat(client.orderCalendar(FROM, TO, ADMIN)).isSameAs(answer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void couponsArePagedUpToTheMaximum() {
        when(platformClient.getAsAdmin(eq(PlatformService.PROMOTION), eq("/api/v1/admin/coupons"), anyMap(),
                eq(CouponPage.class), eq(ADMIN)))
                .thenReturn(new CouponPage(coupons(100), false), new CouponPage(coupons(100), false),
                        new CouponPage(coupons(100), false));

        List<CouponRow> coupons = client.coupons(FROM, TO, ADMIN);

        assertThat(coupons).hasSize(CalendarClient.MAX_COUPONS); // a third page is never asked for
        ArgumentCaptor<Map<String, Object>> query = ArgumentCaptor.forClass(Map.class);
        verify(platformClient, times(2)).getAsAdmin(eq(PlatformService.PROMOTION), eq("/api/v1/admin/coupons"),
                query.capture(), eq(CouponPage.class), any());
        assertThat(query.getAllValues().get(0)).containsEntry("activeFrom", FROM).containsEntry("activeTo", TO)
                .containsEntry("page", 0).containsEntry("size", 100).containsEntry("sort", "validFrom,asc");
        assertThat(query.getAllValues().get(1)).containsEntry("page", 1);
    }

    @Test
    void aSinglePageIsOneCall() {
        when(platformClient.getAsAdmin(eq(PlatformService.PROMOTION), eq("/api/v1/admin/coupons"), anyMap(),
                eq(CouponPage.class), eq(ADMIN))).thenReturn(new CouponPage(coupons(3), true));

        assertThat(client.coupons(FROM, TO, ADMIN)).hasSize(3);
        verify(platformClient, times(1)).getAsAdmin(eq(PlatformService.PROMOTION), eq("/api/v1/admin/coupons"),
                anyMap(), eq(CouponPage.class), eq(ADMIN));
    }

    private static List<CouponRow> coupons(int count) {
        List<CouponRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new CouponRow((long) i, "C" + i, "ACTIVE", FROM, TO, 0));
        }
        return rows;
    }
}
