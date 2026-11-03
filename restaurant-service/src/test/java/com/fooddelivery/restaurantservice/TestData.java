package com.fooddelivery.restaurantservice;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import com.fooddelivery.restaurantservice.dto.request.AddressRequest;
import com.fooddelivery.restaurantservice.dto.request.DocumentRequest;
import com.fooddelivery.restaurantservice.dto.request.OperatingHoursRequest;
import com.fooddelivery.restaurantservice.dto.request.RestaurantRequest;
import com.fooddelivery.restaurantservice.entity.DocumentType;

/** Ready-made request objects for tests. */
public final class TestData {

    private TestData() {
    }

    public static RestaurantRequest restaurantRequest(String name) {
        return restaurantRequest(name, null);
    }

    public static RestaurantRequest restaurantRequest(String name, Long version) {
        return new RestaurantRequest(
                name,
                "Hyderabadi biryani and kebabs",
                List.of("Biryani", "North Indian"),
                "+919876543210",
                "contact@spicehub.example.com",
                new AddressRequest("45 Brigade Road", null, "Bengaluru", "Karnataka", "560025", "India", null, null),
                "Asia/Kolkata",
                "Spice Hub Foods Pvt Ltd",
                "29ABCDE1234F1Z5",
                "11223344556677",
                List.of(new DocumentRequest(DocumentType.FOOD_LICENSE, "https://docs.example.com/spice-hub/fssai.pdf")),
                everyDay(LocalTime.of(11, 0), LocalTime.of(23, 0)),
                version);
    }

    public static List<OperatingHoursRequest> everyDay(LocalTime opensAt, LocalTime closesAt) {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> new OperatingHoursRequest(day, opensAt, closesAt))
                .toList();
    }

    public static OperatingHoursRequest slot(DayOfWeek day, String opensAt, String closesAt) {
        return new OperatingHoursRequest(day, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
    }
}
