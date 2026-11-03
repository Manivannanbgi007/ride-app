package com.fooddelivery.deliverypartnerservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DeliveryPartnerTest {

    @Test
    void aNewPartnerIsActiveOfflineAndNotReadyToWork() {
        DeliveryPartner partner = DeliveryPartner.register(9L, "Ravi Kumar", null);

        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.OFFLINE);
        assertThat(partner.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(partner.isReadyToWork()).isFalse();
        assertThat(partner.updateProfile(VehicleType.SCOOTER, "KA01AB1234", "Bengaluru")).isTrue();
        assertThat(partner.isReadyToWork()).isTrue();
        assertThat(partner.updateProfile(VehicleType.SCOOTER, "KA01AB1234", "Bengaluru")).isFalse();
        assertThat(partner.updateProfile(VehicleType.CAR, "KA01AB1234", "Bengaluru")).isTrue();
    }

    @Test
    void aSuspendedAccountHidesTheWorkStatusUntilItIsReactivated() {
        DeliveryPartner partner = DeliveryPartner.register(9L, "Ravi Kumar", null);
        partner.applyWorkStatus(new WorkStatus(1, PartnerStatus.AVAILABLE, null, null, Instant.now(), 0));

        partner.suspend("Fraud check");
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.SUSPENDED);
        assertThat(partner.getStatusReason()).isEqualTo("Fraud check");
        // delivery-service reports it has suspended the partner too
        partner.applyWorkStatus(new WorkStatus(2, PartnerStatus.SUSPENDED, "Fraud check", null, null, 0));

        partner.activate();
        // Until delivery-service reports the partner offline, they are shown offline (never "suspended" any more)
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.OFFLINE);
        assertThat(partner.getStatusReason()).isNull();
        partner.applyWorkStatus(new WorkStatus(3, PartnerStatus.OFFLINE, "No answer within 120s", null, null, 0));
        assertThat(partner.getStatusReason()).isEqualTo("No answer within 120s");
    }

    @Test
    void onlyNewerReportsReplaceTheCopy() {
        DeliveryPartner partner = DeliveryPartner.register(9L, "Ravi Kumar", null);

        assertThat(partner.applyWorkStatus(new WorkStatus(5, PartnerStatus.BUSY, null, 40L, null, 2))).isTrue();
        assertThat(partner.isBusy()).isTrue();
        assertThat(partner.applyWorkStatus(new WorkStatus(5, PartnerStatus.AVAILABLE, null, null, null, 3))).isFalse();
        assertThat(partner.applyWorkStatus(new WorkStatus(4, PartnerStatus.AVAILABLE, null, null, null, 3))).isFalse();
        assertThat(partner.getCurrentDeliveryId()).isEqualTo(40L);
        assertThat(partner.applyWorkStatus(new WorkStatus(6, PartnerStatus.AVAILABLE, null, null, null, 3))).isTrue();
        assertThat(partner.getCompletedDeliveries()).isEqualTo(3);
    }

    @Test
    void theStatusShownFollowsTheAccountFirst() {
        DeliveryPartner partner = DeliveryPartner.register(9L, "Ravi Kumar", null);
        partner.applyWorkStatus(new WorkStatus(8, PartnerStatus.BUSY, null, 40L, null, 21));

        // Suspended while finishing a delivery: shown suspended, still known to be on the delivery
        partner.suspend("Documents expired");
        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.SUSPENDED);
        assertThat(partner.isBusy()).isTrue();
        assertThat(partner.getSuspensionReason()).isEqualTo("Documents expired");
    }
}
