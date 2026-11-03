package com.fooddelivery.deliverypartnerservice.client;

import java.time.Instant;
import java.util.List;

import com.fooddelivery.deliverypartnerservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the partners delivery-service kept before this service existed (its internal API), for the one-off import.
 * Nothing else here calls delivery-service: day to day the two services talk through events.
 */
@Component
public class DeliveryServiceClient {

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public DeliveryServiceClient(@Qualifier("deliveryServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /**
     * A partner as delivery-service keeps it. Vehicle type and statuses are plain strings ("tolerant reader").
     *
     * @param version version of delivery-service's record (the work status is copied with it)
     */
    public record LegacyPartner(Long userId, String fullName, String phone, String vehicleType, String vehicleNumber,
                                String city, String status, String statusReason, boolean suspended,
                                String suspensionReason, Long currentDeliveryId, Instant availableSince,
                                int completedDeliveries, long version) {
    }

    /** One page of partners, oldest user id first. */
    public record LegacyPartnerPage(List<LegacyPartner> content, boolean last) {
    }

    /** @throws RemoteServiceException if delivery-service cannot be reached or fails */
    public LegacyPartnerPage findPartners(int page, int size) {
        return remoteCalls.call("delivery-service", () -> {
            try {
                return restClient.get()
                        .uri("/api/v1/internal/delivery-partners?page={page}&size={size}", page, size)
                        .retrieve()
                        .body(LegacyPartnerPage.class);
            } catch (RestClientException e) {
                throw new RemoteServiceException("delivery-service", e);
            }
        });
    }
}
