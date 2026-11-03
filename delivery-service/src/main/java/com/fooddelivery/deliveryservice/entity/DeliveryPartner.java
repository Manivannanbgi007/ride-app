package com.fooddelivery.deliveryservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A delivery partner (rider) in this service's dispatch list. The id is the partner's user id in user-service.
 * delivery-partner-service owns the partner: name, phone, vehicle, city and the suspension are a copy of its
 * DeliveryPartnerChanged events (newest version wins). What the partner is doing (status, current delivery, queue
 * order) is decided here, together with the deliveries, and reported back as DeliveryPartnerStatusChanged.
 */
@Entity
@Table(name = "delivery_partners")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryPartner {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "current_delivery_id")
    private Long currentDeliveryId;

    @Column(name = "available_since")
    private Instant availableSince;

    @Column(name = "completed_deliveries", nullable = false)
    private int completedDeliveries;

    /** Version of the partner in delivery-partner-service this copy was taken from; null for partners from before it. */
    @Column(name = "profile_version")
    private Long profileVersion;

    /** Suspended by an admin: no new deliveries; one already accepted is finished first, then the partner is SUSPENDED. */
    @Column(nullable = false)
    private boolean suspended;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Also tells Spring Data that a partner without a version is new (the id is not generated). */
    @Version
    private Long version;

    public static DeliveryPartner register(Long userId, String fullName, String phone) {
        DeliveryPartner partner = new DeliveryPartner();
        partner.userId = userId;
        partner.fullName = fullName;
        partner.phone = phone;
        partner.status = PartnerStatus.OFFLINE;
        return partner;
    }

    /** Vehicle and city are needed to receive deliveries (assignment matches the restaurant's city). */
    public boolean isReadyToWork() {
        return vehicleType != null && vehicleNumber != null && city != null;
    }

    /** This copy already has the given version of the partner, or a newer one (events can repeat or overtake). */
    public boolean hasProfileVersion(long version) {
        return profileVersion != null && version <= profileVersion;
    }

    /** Takes over what delivery-partner-service says about the partner (it owns these fields). */
    public void applyProfile(String newFullName, String newPhone, VehicleType newVehicleType, String newVehicleNumber,
                             String newCity, long version) {
        fullName = newFullName;
        phone = newPhone;
        vehicleType = newVehicleType;
        vehicleNumber = newVehicleNumber;
        city = newCity;
        profileVersion = version;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
