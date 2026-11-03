package com.fooddelivery.adminservice.client;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.adminservice.client.RemoteRecords.CouponPage;
import com.fooddelivery.adminservice.client.RemoteRecords.CouponRow;
import com.fooddelivery.adminservice.client.RemoteRecords.OrderCalendar;
import com.fooddelivery.adminservice.client.RemoteRecords.RegistrationCalendar;
import com.fooddelivery.adminservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What the admin calendar asks the services for: orders per day, restaurants per day, coupons by validity. */
@Component
@RequiredArgsConstructor
public class CalendarClient {

    /** promotion-service allows at most 100 per page. */
    static final int COUPON_PAGE_SIZE = 100;
    /** More coupons than this overlapping one calendar range is not a realistic case; the rest is left out. */
    public static final int MAX_COUPONS = 200;

    private final PlatformClient platformClient;

    public OrderCalendar orderCalendar(LocalDate from, LocalDate to, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.ORDER, "/api/v1/admin/orders/stats/calendar", range(from, to),
                OrderCalendar.class, caller);
    }

    public RegistrationCalendar registrationCalendar(LocalDate from, LocalDate to, CallerInfo caller) {
        return platformClient.getAsAdmin(PlatformService.RESTAURANT, "/api/v1/admin/restaurants/stats/registrations",
                range(from, to), RegistrationCalendar.class, caller);
    }

    /** Coupons whose validity dates overlap the range, by start date, at most {@link #MAX_COUPONS}. */
    public List<CouponRow> coupons(LocalDate from, LocalDate to, CallerInfo caller) {
        List<CouponRow> coupons = new ArrayList<>();
        for (int page = 0; coupons.size() < MAX_COUPONS; page++) {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("activeFrom", from);
            query.put("activeTo", to);
            query.put("page", page);
            query.put("size", COUPON_PAGE_SIZE);
            query.put("sort", "validFrom,asc");
            CouponPage result = platformClient.getAsAdmin(PlatformService.PROMOTION, "/api/v1/admin/coupons", query,
                    CouponPage.class, caller);
            coupons.addAll(result.content());
            if (result.last() || result.content().isEmpty()) {
                break;
            }
        }
        return coupons.size() > MAX_COUPONS ? coupons.subList(0, MAX_COUPONS) : coupons;
    }

    private static Map<String, Object> range(LocalDate from, LocalDate to) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("from", from);
        query.put("to", to);
        return query;
    }
}
