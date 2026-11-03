package com.fooddelivery.deliverypartnerservice.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A delivery partner (rider). The id is the partner's user id in user-service. This service owns who the partner is
 * (name, phone, vehicle, city) and whether an admin lets them work (account status). What they are doing right now
 * is decided by delivery-service together with the deliveries; the work* fields are a copy of its reports, so the
 * partner and admins see one complete record.
 * <p>
 * {@code status} is what everybody sees and admins search by: SUSPENDED while the account is suspended, otherwise the
 * reported work status. Every method that changes either one recomputes it.
 */
@Entity
@Table(name = "delivery_partners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_status", nullable = false, length = 20)
    private PartnerStatus workStatus;

    @Column(name = "work_status_reason", length = 500)
    private String workStatusReason;

    @Column(name = "current_delivery_id")
    private Long currentDeliveryId;

    @Column(name = "available_since")
    private Instant availableSince;

    @Column(name = "completed_deliveries", nullable = false)
    private int completedDeliveries;

    /** Version of delivery-service's record the work status was copied from; null until it reported anything. */
    @Column(name = "work_status_version")
    private Long workStatusVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Also tells Spring Data that a partner without a version is new (the id is not generated). */
    @Version
    private Long version;

    /** A new partner: active, offline, profile still to be filled in. */
    public static DeliveryPartner register(Long userId, String fullName, String phone) {
        DeliveryPartner partner = new DeliveryPartner();
        partner.userId = userId;
        partner.fullName = fullName;
        partner.phone = phone;
        partner.accountStatus = AccountStatus.ACTIVE;
        partner.workStatus = PartnerStatus.OFFLINE;
        partner.refreshStatus();
        return partner;
    }

    /**
     * Vehicle and city are what the partner fills in; deliveries are offered from restaurants in that city.
     *
     * @return whether anything changed
     */
    public boolean updateProfile(VehicleType newVehicleType, String newVehicleNumber, String newCity) {
        boolean changed = newVehicleType != vehicleType || !Objects.equals(newVehicleNumber, vehicleNumber)
                || !Objects.equals(newCity, city);
        this.vehicleType = newVehicleType;
        this.vehicleNumber = newVehicleNumber;
        this.city = newCity;
        return changed;
    }

    /** Vehicle and city are needed to receive deliveries (assignment matches the restaurant's city). */
    public boolean isReadyToWork() {
        return vehicleType != null && vehicleNumber != null && city != null;
    }

    public boolean isSuspended() {
        return accountStatus == AccountStatus.SUSPENDED;
    }

    /** An admin stops the partner from working. */
    public void suspend(String reason) {
        accountStatus = AccountStatus.SUSPENDED;
        suspensionReason = reason;
        refreshStatus();
    }

    /** An admin lets a suspended partner work again; they go online themselves. */
    public void activate() {
        accountStatus = AccountStatus.ACTIVE;
        suspensionReason = null;
        refreshStatus();
    }

    /**
     * Takes over what delivery-service reports, unless this copy is already as new (events can arrive twice, and a
     * report can overtake an older one).
     *
     * @return whether the report was newer and applied
     */
    public boolean applyWorkStatus(WorkStatus report) {
        if (workStatusVersion != null && report.sourceVersion() <= workStatusVersion) {
            return false;
        }
        workStatusVersion = report.sourceVersion();
        workStatus = report.status();
        workStatusReason = report.reason();
        currentDeliveryId = report.currentDeliveryId();
        availableSince = report.availableSince();
        completedDeliveries = report.completedDeliveries();
        refreshStatus();
        return true;
    }

    /** Why the partner has this status: the suspension reason, or what delivery-service said. */
    public String getStatusReason() {
        if (isSuspended()) {
            return suspensionReason;
        }
        // Reactivated: delivery-service's "suspended" report is out of date, and so is its reason
        return workStatus == PartnerStatus.SUSPENDED ? null : workStatusReason;
    }

    /** On a delivery right now (as far as delivery-service has reported). */
    public boolean isBusy() {
        return workStatus == PartnerStatus.BUSY;
    }

    private void refreshStatus() {
        if (isSuspended()) {
            status = PartnerStatus.SUSPENDED;
        } else {
            // Reactivated, but delivery-service has not reported the partner offline yet
            status = workStatus == PartnerStatus.SUSPENDED ? PartnerStatus.OFFLINE : workStatus;
        }
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
