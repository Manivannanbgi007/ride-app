package com.fooddelivery.restaurantservice.service;

import static com.fooddelivery.restaurantservice.TestData.restaurantRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.restaurantservice.config.AppProperties;
import com.fooddelivery.restaurantservice.dto.request.DocumentRequest;
import com.fooddelivery.restaurantservice.dto.request.RestaurantRequest;
import com.fooddelivery.restaurantservice.dto.response.RestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.entity.DocumentType;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import com.fooddelivery.restaurantservice.exception.BusinessRuleException;
import com.fooddelivery.restaurantservice.exception.ConflictException;
import com.fooddelivery.restaurantservice.exception.InvalidRequestException;
import com.fooddelivery.restaurantservice.exception.RestaurantNotFoundException;
import com.fooddelivery.restaurantservice.kafka.producer.RestaurantEventPublisher;
import com.fooddelivery.restaurantservice.mapper.RestaurantMapper;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.validation.OperatingHoursValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantOwnerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Long OWNER_ID = 5L;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantEventPublisher restaurantEventPublisher;

    private final RestaurantMapper restaurantMapper = new RestaurantMapper();

    private RestaurantOwnerService restaurantOwnerService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties("Asia/Kolkata",
                new AppProperties.UserService("http://localhost:8081", null, null), "Asia/Kolkata");
        restaurantOwnerService = new RestaurantOwnerService(restaurantRepository, restaurantMapper,
                new OperatingHoursValidator(), appProperties, restaurantEventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submittedRestaurantStartsAsPendingForTheCallingOwner() {
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RestaurantDetailsResponse response = restaurantOwnerService.submitRestaurant(OWNER_ID, restaurantRequest(" Spice Hub "));

        assertThat(response.status()).isEqualTo(RestaurantStatus.PENDING);
        assertThat(response.ownerUserId()).isEqualTo(OWNER_ID);
        assertThat(response.name()).isEqualTo("Spice Hub");
        assertThat(response.operatingHours()).hasSize(7);
        assertThat(response.documents()).singleElement()
                .satisfies(document -> assertThat(document.submittedAt()).isEqualTo(NOW));
        verify(restaurantEventPublisher).restaurantSubmitted(any(Restaurant.class));
    }

    @Test
    void submissionWithoutFoodLicenseIsRejected() {
        RestaurantRequest valid = restaurantRequest("Spice Hub");
        RestaurantRequest withoutLicense = new RestaurantRequest(valid.name(), valid.description(), valid.cuisines(),
                valid.phone(), valid.email(), valid.address(), valid.timeZone(), valid.legalBusinessName(),
                valid.taxRegistrationNumber(), valid.foodLicenseNumber(),
                List.of(new DocumentRequest(DocumentType.TAX_REGISTRATION, "https://docs.example.com/gst.pdf")),
                valid.operatingHours(), null);

        assertThatThrownBy(() -> restaurantOwnerService.submitRestaurant(OWNER_ID, withoutLicense))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("FOOD_LICENSE");
        verify(restaurantRepository, never()).save(any());
        verify(restaurantEventPublisher, never()).restaurantSubmitted(any());
    }

    @Test
    void unknownTimeZoneIsAnInvalidRequest() {
        RestaurantRequest valid = restaurantRequest("Spice Hub");
        RestaurantRequest badZone = new RestaurantRequest(valid.name(), valid.description(), valid.cuisines(),
                valid.phone(), valid.email(), valid.address(), "Mars/Olympus", valid.legalBusinessName(),
                valid.taxRegistrationNumber(), valid.foodLicenseNumber(), valid.documents(),
                valid.operatingHours(), null);

        assertThatThrownBy(() -> restaurantOwnerService.submitRestaurant(OWNER_ID, badZone))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Mars/Olympus");
    }

    @Test
    void updateWithOutdatedVersionIsAConflict() {
        givenOwnedRestaurant(10L, RestaurantStatus.ACTIVE, 3L);

        var restaurantRequest = restaurantRequest("New name", 2L);
        assertThatThrownBy(() -> restaurantOwnerService.updateRestaurant(OWNER_ID, 10L, restaurantRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("current version 3");
    }

    @Test
    void suspendedRestaurantCannotBeEdited() {
        givenOwnedRestaurant(10L, RestaurantStatus.SUSPENDED, 3L);

        var restaurantRequest = restaurantRequest("New name", 3L);
        assertThatThrownBy(() -> restaurantOwnerService.updateRestaurant(OWNER_ID, 10L, restaurantRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void onlyRejectedRestaurantsCanBeResubmitted() {
        givenOwnedRestaurant(10L, RestaurantStatus.ACTIVE, 3L);

        assertThatThrownBy(() -> restaurantOwnerService.resubmitRestaurant(OWNER_ID, 10L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resubmittingRejectedRestaurantClearsTheRejectionReason() {
        Restaurant restaurant = givenOwnedRestaurant(10L, RestaurantStatus.REJECTED, 3L);
        restaurant.setStatusReason("License photo unreadable");

        RestaurantDetailsResponse response = restaurantOwnerService.resubmitRestaurant(OWNER_ID, 10L);

        assertThat(response.status()).isEqualTo(RestaurantStatus.PENDING);
        assertThat(response.statusReason()).isNull();
    }

    @Test
    void anotherOwnersRestaurantIsNotFound() {
        when(restaurantRepository.findByIdAndOwnerUserId(10L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantOwnerService.getMyRestaurant(OWNER_ID, 10L))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    private Restaurant givenOwnedRestaurant(Long id, RestaurantStatus status, Long version) {
        Restaurant restaurant = new Restaurant();
        restaurantMapper.applyRequest(restaurant, restaurantRequest("Spice Hub"), "Asia/Kolkata", NOW);
        restaurant.setId(id);
        restaurant.setOwnerUserId(OWNER_ID);
        restaurant.setStatus(status);
        restaurant.setStatusChangedAt(NOW);
        restaurant.setVersion(version);
        when(restaurantRepository.findByIdAndOwnerUserId(id, OWNER_ID)).thenReturn(Optional.of(restaurant));
        return restaurant;
    }
}
