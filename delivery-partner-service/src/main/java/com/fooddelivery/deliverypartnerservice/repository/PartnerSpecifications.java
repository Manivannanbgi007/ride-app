package com.fooddelivery.deliverypartnerservice.repository;

import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import com.fooddelivery.deliverypartnerservice.entity.PartnerStatus;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks of the admin partner search. */
public final class PartnerSpecifications {

    private PartnerSpecifications() {
    }

    public static Specification<DeliveryPartner> hasStatus(PartnerStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<DeliveryPartner> inCity(String city) {
        return (root, query, cb) -> cb.equal(root.get("city"), city);
    }

    /** Name or phone contains the text (case-insensitive). */
    public static Specification<DeliveryPartner> matches(String text) {
        String pattern = "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern, '\\'),
                cb.like(root.get("phone"), pattern, '\\'));
    }
}
