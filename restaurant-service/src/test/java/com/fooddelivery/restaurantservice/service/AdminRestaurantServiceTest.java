package com.fooddelivery.restaurantservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.fooddelivery.restaurantservice.dto.response.RestaurantDetailsResponse;
import com.fooddelivery.restaurantservice.entity.AuditAction;
import com.fooddelivery.restaurantservice.entity.Restaurant;
import com.fooddelivery.restaurantservice.entity.RestaurantAddress;
import com.fooddelivery.restaurantservice.entity.RestaurantStatus;
import com.fooddelivery.restaurantservice.entity.Role;
import com.fooddelivery.restaurantservice.exception.ConflictException;
import com.fooddelivery.restaurantservice.exception.RestaurantNotFoundException;
import com.fooddelivery.restaurantservice.kafka.producer.RestaurantEventPublisher;
import com.fooddelivery.restaurantservice.mapper.RestaurantMapper;
import com.fooddelivery.restaurantservice.repository.RestaurantRepository;
import com.fooddelivery.restaurantservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRestaurantServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final CallerInfo ADMIN = new CallerInfo(1L, Role.PLATFORM_ADMIN, "10.0.0.1", "req-1");

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private AdminAuditService adminAuditService;

    @Mock
    private RestaurantEventPublisher restaurantEventPublisher;

    private AdminRestaurantService adminRestaurantService;

    @BeforeEach
    void setUp() {
        adminRestaurantService = new AdminRestaurantService(restaurantRepository, new RestaurantMapper(),
                adminAuditService, restaurantEventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void approvingPendingRestaurantRecordsApprovalTimeAndAudit() {
        Restaurant restaurant = givenRestaurant(10L, RestaurantStatus.PENDING);

        RestaurantDetailsResponse response = adminRestaurantService.approveRestaurant(10L, "License verified", ADMIN);

        assertThat(response.status()).isEqualTo(RestaurantStatus.APPROVED);
        assertThat(response.approvedAt()).isEqualTo(NOW);
        assertThat(response.statusReason()).isEqualTo("License verified");
        verify(adminAuditService).recordAction(ADMIN, AuditAction.RESTAURANT_APPROVED, "RESTAURANT", 10L,
                "PENDING", "APPROVED", "License verified");
        verify(restaurantEventPublisher).statusChanged(restaurant, RestaurantStatus.PENDING);
    }

    @Test
    void rejectionReasonIsKeptForTheOwner() {
        givenRestaurant(10L, RestaurantStatus.PENDING);

        RestaurantDetailsResponse response = adminRestaurantService.rejectRestaurant(10L, "License photo unreadable", ADMIN);

        assertThat(response.status()).isEqualTo(RestaurantStatus.REJECTED);
        assertThat(response.statusReason()).isEqualTo("License photo unreadable");
        assertThat(response.approvedAt()).isNull();
    }

    @Test
    void pendingRestaurantCannotBeActivatedBeforeApproval() {
        givenRestaurant(10L, RestaurantStatus.PENDING);

        assertThatThrownBy(() -> adminRestaurantService.activateRestaurant(10L, "Go live", ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("from PENDING to ACTIVE");
        verifyNoInteractions(adminAuditService, restaurantEventPublisher);
    }

    @Test
    void suspendingActiveRestaurantIsAudited() {
        givenRestaurant(10L, RestaurantStatus.ACTIVE);

        RestaurantDetailsResponse response = adminRestaurantService.suspendRestaurant(10L, "Policy violation", ADMIN);

        assertThat(response.status()).isEqualTo(RestaurantStatus.SUSPENDED);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.RESTAURANT_SUSPENDED, "RESTAURANT", 10L,
                "ACTIVE", "SUSPENDED", "Policy violation");
    }

    @Test
    void unknownRestaurantIsNotFound() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminRestaurantService.approveRestaurant(99L, "x", ADMIN))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    private Restaurant givenRestaurant(Long id, RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        restaurant.setOwnerUserId(5L);
        restaurant.setName("Spice Hub");
        restaurant.setAddress(new RestaurantAddress("45 Brigade Road", null, "Bengaluru", "Karnataka",
                "560025", "India", null, null));
        restaurant.setStatus(status);
        restaurant.setStatusChangedAt(NOW.minusSeconds(3600));
        restaurant.setVersion(0L);
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant));
        return restaurant;
    }
}
