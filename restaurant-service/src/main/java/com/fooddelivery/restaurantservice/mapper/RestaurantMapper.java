package com.fooddelivery.restaurantservice.mapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.fooddelivery.restaurantservice.dto.request.AddressRequest;
import com.fooddelivery.restaurantservice.dto.request.DocumentRequest;
import com.fooddelivery.restaurantservice.dto.request.OperatingHoursRequest;
import com.fooddelivery.restaurantservice.dto.request.RestaurantRequest;
import com.fooddelivery.restaurantservice.dto.response.AddressResponse;
import com.fooddelivery.restaurantservice.dto.response.DocumentResponse;
import com.fooddelivery.restaurantservice.dto.response.OperatingHoursResponse;
import com.fooddelivery.restaurantservice.dto.response.PublicRestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.PublicRestaurantSummaryResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.dto.response.RestaurantSummaryResponse;
import com.fooddelivery.restaurantservice.entity.DocumentType;
import com.fooddelivery.restaurantservice.entity.OperatingHours;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantAddress;
import com.fooddelivery.restaurantservice.entity.RestaurantDocument;
import org.springframework.stereotype.Component;

@Component
public class RestaurantMapper {

    private static final Comparator<OperatingHours> WEEK_ORDER =
            Comparator.comparing(OperatingHours::dayOfWeek).thenComparing(OperatingHours::opensAt);

    /** Copies everything the owner can edit from the request onto the entity. */
    public void applyRequest(Restaurant restaurant, RestaurantRequest request, String timeZone, Instant now) {
        restaurant.setName(request.name().trim());
        restaurant.setDescription(blankToNull(request.description()));
        restaurant.setCuisines(normalizeCuisines(request.cuisines()));
        restaurant.setPhone(request.phone().trim());
        restaurant.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        restaurant.setAddress(toAddress(request.address()));
        restaurant.setTimeZone(timeZone);
        restaurant.setLegalBusinessName(request.legalBusinessName().trim());
        restaurant.setTaxRegistrationNumber(blankToNull(request.taxRegistrationNumber()));
        restaurant.setFoodLicenseNumber(request.foodLicenseNumber().trim());
        replaceDocuments(restaurant, request.documents(), now);
        replaceOperatingHours(restaurant, request.operatingHours());
    }

    public void replaceOperatingHours(Restaurant restaurant, List<OperatingHoursRequest> operatingHours) {
        restaurant.getOperatingHours().clear();
        operatingHours.stream()
                .map(slot -> new OperatingHours(slot.dayOfWeek(), slot.opensAt(), slot.closesAt()))
                .sorted(WEEK_ORDER)
                .forEach(restaurant.getOperatingHours()::add);
    }

    public RestaurantDetailsResponse toDetails(Restaurant restaurant) {
        return new RestaurantDetailsResponse(
                restaurant.getId(),
                restaurant.getOwnerUserId(),
                restaurant.getName(),
                restaurant.getDescription(),
                restaurant.getCuisines(),
                restaurant.getPhone(),
                restaurant.getEmail(),
                toAddressResponse(restaurant.getAddress()),
                restaurant.getTimeZone(),
                restaurant.getLegalBusinessName(),
                restaurant.getTaxRegistrationNumber(),
                restaurant.getFoodLicenseNumber(),
                restaurant.getStatus(),
                restaurant.getStatusReason(),
                restaurant.getStatusChangedAt(),
                restaurant.getApprovedAt(),
                toOperatingHoursResponses(restaurant.getOperatingHours()),
                restaurant.getDocuments().stream()
                        .map(document -> new DocumentResponse(document.type(), document.url(), document.submittedAt()))
                        .toList(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt(),
                restaurant.getVersion());
    }

    public RestaurantSummaryResponse toSummary(Restaurant restaurant) {
        return new RestaurantSummaryResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getOwnerUserId(),
                restaurant.getCuisines(),
                restaurant.getAddress().city(),
                restaurant.getStatus(),
                restaurant.getStatusReason(),
                restaurant.getCreatedAt(),
                restaurant.getStatusChangedAt());
    }

    public PublicRestaurantSummaryResponse toPublicSummary(Restaurant restaurant, boolean openNow) {
        return new PublicRestaurantSummaryResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getCuisines(),
                restaurant.getAddress().city(),
                openNow);
    }

    public PublicRestaurantDetailsResponse toPublicDetails(Restaurant restaurant, boolean openNow) {
        return new PublicRestaurantDetailsResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getDescription(),
                restaurant.getCuisines(),
                restaurant.getPhone(),
                toAddressResponse(restaurant.getAddress()),
                restaurant.getTimeZone(),
                toOperatingHoursResponses(restaurant.getOperatingHours()),
                openNow);
    }

    private void replaceDocuments(Restaurant restaurant, List<DocumentRequest> documents, Instant now) {
        // Unchanged documents keep their original submission time.
        Map<String, Instant> previousSubmissions = restaurant.getDocuments().stream()
                .collect(Collectors.toMap(document -> documentKey(document.type(), document.url()),
                        RestaurantDocument::submittedAt, (first, second) -> first));

        List<RestaurantDocument> updatedDocuments = documents.stream()
                .map(document -> {
                    String url = document.url().trim();
                    Instant submittedAt = previousSubmissions.getOrDefault(documentKey(document.type(), url), now);
                    return new RestaurantDocument(document.type(), url, submittedAt);
                })
                .toList();

        restaurant.getDocuments().clear();
        restaurant.getDocuments().addAll(updatedDocuments);
    }

    /** Trims and removes duplicates ignoring case, keeping the first spelling: "biryani", "Biryani" -> "biryani". */
    private static List<String> normalizeCuisines(List<String> cuisines) {
        Map<String, String> unique = new LinkedHashMap<>();
        cuisines.forEach(cuisine -> unique.putIfAbsent(cuisine.trim().toLowerCase(Locale.ROOT), cuisine.trim()));
        return List.copyOf(unique.values());
    }

    private static RestaurantAddress toAddress(AddressRequest address) {
        return new RestaurantAddress(
                address.line1().trim(),
                blankToNull(address.line2()),
                address.city().trim(),
                address.state().trim(),
                address.postalCode().trim(),
                address.country().trim(),
                address.latitude(),
                address.longitude());
    }

    public static AddressResponse toAddressResponse(RestaurantAddress address) {
        return new AddressResponse(address.line1(), address.line2(), address.city(), address.state(),
                address.postalCode(), address.country(), address.latitude(), address.longitude());
    }

    private static List<OperatingHoursResponse> toOperatingHoursResponses(List<OperatingHours> operatingHours) {
        return operatingHours.stream()
                .sorted(WEEK_ORDER)
                .map(slot -> new OperatingHoursResponse(slot.dayOfWeek(), slot.opensAt(), slot.closesAt()))
                .toList();
    }

    private static String documentKey(DocumentType type, String url) {
        return type + "|" + url;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
