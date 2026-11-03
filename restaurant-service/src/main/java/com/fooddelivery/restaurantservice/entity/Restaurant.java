package com.fooddelivery.restaurantservice.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The owner belongs to user-service: we keep only the id, never a JPA relationship.
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false)
    private List<String> cuisines = List.of();

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private String email;

    @Embedded
    private RestaurantAddress address;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(name = "legal_business_name", nullable = false, length = 200)
    private String legalBusinessName;

    @Column(name = "tax_registration_number", length = 50)
    private String taxRegistrationNumber;

    @Column(name = "food_license_number", nullable = false, length = 50)
    private String foodLicenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantStatus status;

    /** Why the status last changed (for example the rejection reason shown to the owner). */
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    // Lazy by default: loaded only when a screen needs them (inside the service transaction).
    @ElementCollection
    @CollectionTable(name = "restaurant_operating_hours", joinColumns = @JoinColumn(name = "restaurant_id"))
    private List<OperatingHours> operatingHours = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "restaurant_documents", joinColumns = @JoinColumn(name = "restaurant_id"))
    private List<RestaurantDocument> documents = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Optimistic locking: an owner editing an outdated copy gets 409 instead of silently overwriting.
    @Version
    private Long version;

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
