package com.fooddelivery.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fooddelivery.catalogservice.cache.MenuCache;
import com.fooddelivery.catalogservice.client.RestaurantServiceClient;
import com.fooddelivery.catalogservice.client.RestaurantSummary;
import com.fooddelivery.catalogservice.entity.RestaurantSnapshot;
import com.fooddelivery.catalogservice.exception.RemoteServiceException;
import com.fooddelivery.catalogservice.repository.RestaurantSnapshotRepository;
import com.fooddelivery.events.restaurant.RestaurantChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantDirectoryTest {

    private static final Long RESTAURANT_ID = 10L;
    private static final Long OWNER_ID = 5L;

    @Mock
    private RestaurantSnapshotRepository restaurantSnapshotRepository;

    @Mock
    private RestaurantServiceClient restaurantServiceClient;

    @Mock
    private MenuCache menuCache;

    private RestaurantDirectory restaurantDirectory;

    @BeforeEach
    void setUp() {
        restaurantDirectory = new RestaurantDirectory(restaurantSnapshotRepository, restaurantServiceClient, menuCache);
    }

    @Test
    void knownRestaurantIsReadFromTheLocalCopyWithoutCallingRestaurantService() {
        givenLocalCopy("ACTIVE", 4L);

        assertThat(restaurantDirectory.findRestaurant(RESTAURANT_ID)).get()
                .satisfies(restaurant -> {
                    assertThat(restaurant.ownerUserId()).isEqualTo(OWNER_ID);
                    assertThat(restaurant.isActive()).isTrue();
                    assertThat(restaurant.version()).isEqualTo(4L);
                });
        verifyNoInteractions(restaurantServiceClient);
    }

    @Test
    void unknownRestaurantIsFetchedFromRestaurantServiceAndRemembered() {
        when(restaurantSnapshotRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());
        when(restaurantServiceClient.findRestaurant(RESTAURANT_ID))
                .thenReturn(Optional.of(new RestaurantSummary(RESTAURANT_ID, OWNER_ID, "Spice Hub", "ACTIVE", 2L)));

        assertThat(restaurantDirectory.findRestaurant(RESTAURANT_ID)).isPresent();
        verify(restaurantSnapshotRepository).upsertIfNewer(RESTAURANT_ID, OWNER_ID, "Spice Hub", "ACTIVE", 2L);
    }

    @Test
    void unknownRestaurantWhileRestaurantServiceIsDownIsAnError() {
        when(restaurantSnapshotRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());
        when(restaurantServiceClient.findRestaurant(RESTAURANT_ID))
                .thenThrow(new RemoteServiceException("restaurant-service", new RuntimeException("timeout")));

        assertThatThrownBy(() -> restaurantDirectory.findRestaurant(RESTAURANT_ID))
                .isInstanceOf(RemoteServiceException.class);
    }

    @Test
    void newerEventUpdatesTheLocalCopy() {
        givenLocalCopy("ACTIVE", 4L);

        restaurantDirectory.apply(event("SUSPENDED", 5L));

        verify(restaurantSnapshotRepository).upsertIfNewer(RESTAURANT_ID, OWNER_ID, "Spice Hub", "SUSPENDED", 5L);
    }

    @Test
    void olderOrRepeatedEventIsIgnored() {
        givenLocalCopy("SUSPENDED", 5L);

        restaurantDirectory.apply(event("ACTIVE", 4L));
        restaurantDirectory.apply(event("SUSPENDED", 5L));

        verify(restaurantSnapshotRepository, never()).upsertIfNewer(any(), any(), any(), any(), any());
    }

    private void givenLocalCopy(String status, Long version) {
        when(restaurantSnapshotRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(
                new RestaurantSnapshot(RESTAURANT_ID, OWNER_ID, "Spice Hub", status, version, Instant.now())));
    }

    private static RestaurantChanged event(String status, long version) {
        return RestaurantChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setChangeType("STATUS_CHANGED")
                .setRestaurantId(RESTAURANT_ID)
                .setVersion(version)
                .setOwnerUserId(OWNER_ID)
                .setName("Spice Hub")
                .setCity("Bengaluru")
                .setStatus(status)
                .build();
    }
}
