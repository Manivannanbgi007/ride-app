package com.fooddelivery.deliverypartnerservice.service;

import com.fooddelivery.deliverypartnerservice.client.UserServiceClient;
import com.fooddelivery.deliverypartnerservice.client.UserServiceClient.UserSummary;
import com.fooddelivery.deliverypartnerservice.entity.DeliveryPartner;
import com.fooddelivery.deliverypartnerservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliverypartnerservice.kafka.producer.PartnerEventPublisher;
import com.fooddelivery.deliverypartnerservice.kafka.producer.PartnerEventPublisher.ChangeType;
import com.fooddelivery.deliverypartnerservice.repository.DeliveryPartnerRepository;
import com.fooddelivery.events.user.UserRegistered;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivery partners are user accounts with the DELIVERY_PARTNER role. Their partner record is created from
 * UserRegistered; for a partner whose event has not arrived (or was published before this service existed),
 * on their first call, after asking user-service. Every new partner is announced (DeliveryPartnerChanged), so
 * delivery-service can offer them deliveries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerRegistrationService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final UserServiceClient userServiceClient;
    private final ProcessedEventGuard processedEventGuard;
    private final PartnerEventPublisher partnerEventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public void onUserRegistered(UserRegistered event) {
        if (!UserServiceClient.DELIVERY_PARTNER.equals(event.getRole())
                || !processedEventGuard.firstDelivery(event.getEventId(), "UserRegistered")) {
            return;
        }
        registerIfAbsent(event.getUserId(), event.getFullName(), event.getPhone());
    }

    /**
     * The partner record of the given user, created now if needed. Not transactional: the user-service call
     * must not hold a database connection.
     *
     * @throws ResourceNotFoundException if the user is not a delivery partner
     */
    public DeliveryPartner findOrRegister(Long userId) {
        return deliveryPartnerRepository.findById(userId).orElseGet(() -> {
            UserSummary user = userServiceClient.findUser(userId)
                    .filter(UserSummary::isDeliveryPartner)
                    .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " is not a delivery partner"));
            try {
                return transactionTemplate.execute(status -> registerIfAbsent(user.id(), user.fullName(), user.phone()));
            } catch (DataIntegrityViolationException e) {
                // The UserRegistered event created the same partner at this very moment
                return deliveryPartnerRepository.findById(userId).orElseThrow(() -> e);
            }
        });
    }

    private DeliveryPartner registerIfAbsent(Long userId, String fullName, String phone) {
        return deliveryPartnerRepository.findById(userId).orElseGet(() -> {
            DeliveryPartner saved = deliveryPartnerRepository.saveAndFlush(DeliveryPartner.register(userId, fullName, phone));
            partnerEventPublisher.partnerChanged(saved, ChangeType.REGISTERED);
            log.info("Delivery partner registered. partnerUserId={}", userId);
            return saved;
        });
    }
}
