package com.fooddelivery.deliveryservice.client;

import java.util.Optional;

import com.fooddelivery.deliveryservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks delivery-partner-service (the owner of partners) about a partner whose DeliveryPartnerChanged event has not
 * arrived yet, for example one who filled in the profile and goes online the same second. All remote-call details
 * stay in this class.
 */
@Component
public class DeliveryPartnerServiceClient {

    private static final String SERVICE = "delivery-partner-service";

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public DeliveryPartnerServiceClient(@Qualifier("deliveryPartnerServiceRestClient") RestClient restClient,
                                        RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /**
     * A partner as delivery-partner-service reports it; vehicle type and account status are plain strings
     * ("tolerant reader").
     *
     * @param version version of the partner: the newest of this answer and the events wins
     */
    public record PartnerProfile(Long userId, String fullName, String phone, String vehicleType, String vehicleNumber,
                                 String city, String accountStatus, String suspensionReason, long version) {

        public boolean isSuspended() {
            return "SUSPENDED".equals(accountStatus);
        }
    }

    /**
     * Registers the partner there if needed (like their own first call).
     *
     * @return the partner, or empty if the user is not a delivery partner
     * @throws RemoteServiceException if delivery-partner-service cannot be reached or fails
     */
    public Optional<PartnerProfile> findPartner(Long userId) {
        return remoteCalls.call(SERVICE, () -> {
            try {
                return Optional.ofNullable(restClient.get()
                        .uri("/api/v1/internal/delivery-partners/{userId}", userId)
                        .retrieve()
                        .body(PartnerProfile.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, e);
            }
        });
    }
}
