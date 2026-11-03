package com.fooddelivery.deliveryservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryStatusTest {

    @Test
    void theNormalPathIsAllowed() {
        assertThat(DeliveryStatus.PENDING.canChangeTo(DeliveryStatus.ASSIGNED)).isTrue();
        assertThat(DeliveryStatus.ASSIGNED.canChangeTo(DeliveryStatus.ACCEPTED)).isTrue();
        assertThat(DeliveryStatus.ACCEPTED.canChangeTo(DeliveryStatus.PICKED_UP)).isTrue();
        assertThat(DeliveryStatus.PICKED_UP.canChangeTo(DeliveryStatus.DELIVERED)).isTrue();
    }

    @Test
    void stepsCannotBeSkippedOrUndone() {
        assertThat(DeliveryStatus.ASSIGNED.canChangeTo(DeliveryStatus.PICKED_UP)).isFalse();
        assertThat(DeliveryStatus.PENDING.canChangeTo(DeliveryStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatus.DELIVERED.canChangeTo(DeliveryStatus.CANCELLED)).isFalse();
        assertThat(DeliveryStatus.CANCELLED.canChangeTo(DeliveryStatus.PENDING)).isFalse();
    }

    @Test
    void onlyDeliveriesThatAreNotPickedUpCanBeReassigned() {
        assertThat(DeliveryStatus.ACCEPTED.isReassignable()).isTrue();
        assertThat(DeliveryStatus.ACCEPTED.canChangeTo(DeliveryStatus.ASSIGNED)).isTrue();
        assertThat(DeliveryStatus.PICKED_UP.isReassignable()).isFalse();
        assertThat(DeliveryStatus.PICKED_UP.canChangeTo(DeliveryStatus.PENDING)).isFalse();
    }
}
