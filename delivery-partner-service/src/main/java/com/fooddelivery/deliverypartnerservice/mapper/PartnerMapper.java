package com.fooddelivery.deliverypartnerservice.mapper;

import com.fooddelivery.deliverypartnerservice.dto.response.InternalPartnerResponse;
import com.fooddelivery.deliverypartnerservice.dto.response.PartnerResponse;
import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import org.springframework.stereotype.Component;

@Component
public class PartnerMapper {

    public PartnerResponse toResponse(DeliveryPartner partner) {
        return new PartnerResponse(
                partner.getUserId(),
                partner.getFullName(),
                partner.getPhone(),
                partner.getVehicleType(),
                partner.getVehicleNumber(),
                partner.getCity(),
                partner.getStatus(),
                partner.getStatusReason(),
                partner.getCurrentDeliveryId(),
                partner.getAvailableSince(),
                partner.getCompletedDeliveries(),
                partner.isReadyToWork(),
                partner.getCreatedAt(),
                partner.getUpdatedAt(),
                partner.getVersion());
    }

    public InternalPartnerResponse toInternalResponse(DeliveryPartner partner) {
        return new InternalPartnerResponse(
                partner.getUserId(),
                partner.getFullName(),
                partner.getPhone(),
                partner.getVehicleType(),
                partner.getVehicleNumber(),
                partner.getCity(),
                partner.getAccountStatus(),
                partner.getSuspensionReason(),
                partner.getVersion());
    }
}
