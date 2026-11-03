package com.fooddelivery.orderservice.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fooddelivery.orderservice.client.RemoteRecords.CouponCheck;
import com.fooddelivery.orderservice.client.RemoteRecords.CouponReservation;
import com.fooddelivery.orderservice.exception.BusinessRuleException;
import com.fooddelivery.orderservice.exception.ConflictException;
import com.fooddelivery.orderservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls promotion-service: coupon checks for the cart, and the coupon use reserved at checkout. */
@Component
public class PromotionServiceClient {

    private static final String SERVICE = "promotion-service";

    private final RestClient restClient;
    private final RemoteCalls remoteCalls;

    public PromotionServiceClient(@Qualifier("promotionServiceRestClient") RestClient restClient, RemoteCalls remoteCalls) {
        this.restClient = restClient;
        this.remoteCalls = remoteCalls;
    }

    /** Would the coupon work for this cart, and for how much? Changes nothing; an unknown code is "not applicable". */
    public CouponCheck check(String code, Long customerId, Long restaurantId, BigDecimal itemsTotal) {
        return remoteCalls.call(SERVICE, () -> {
            try {
                return restClient.post()
                        .uri("/api/v1/internal/coupons/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new CheckRequest(code, customerId, restaurantId, itemsTotal))
                        .retrieve()
                        .body(CouponCheck.class);
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, e);
            }
        });
    }

    /**
     * Reserves one use of the coupon for a checkout. The same key returns the same reservation (retried checkout),
     * which also makes an automatic retry safe.
     *
     * @throws BusinessRuleException when the coupon cannot be used, with promotion-service's reason
     */
    public CouponReservation reserve(String reservationKey, String code, Long customerId, Long restaurantId,
                                     BigDecimal itemsTotal) {
        return remoteCalls.call(SERVICE, () -> {
            try {
                return restClient.post()
                        .uri("/api/v1/internal/coupon-reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ReservationRequest(reservationKey, code, customerId, restaurantId, itemsTotal))
                        .retrieve()
                        .body(CouponReservation.class);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_CONTENT)) {
                    throw new BusinessRuleException("Coupon " + code + ": " + detail(e));
                }
                if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                    throw new ConflictException(detail(e) + ". Check out again with a new Idempotency-Key.");
                }
                throw new RemoteServiceException(SERVICE, e);
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, e);
            }
        });
    }

    /** The checkout failed after the reservation: the use is given back (nothing happens for an unknown key). */
    public void release(String reservationKey) {
        remoteCalls.call(SERVICE, () -> {
            try {
                return restClient.delete()
                        .uri("/api/v1/internal/coupon-reservations/{reservationKey}", reservationKey)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, e);
            }
        });
    }

    private static String detail(HttpClientErrorException e) {
        Problem problem = e.getResponseBodyAs(Problem.class);
        return problem != null && problem.detail() != null ? problem.detail() : e.getStatusText();
    }

    private record CheckRequest(String code, Long customerId, Long restaurantId, BigDecimal itemsTotal) {
    }

    private record ReservationRequest(String reservationKey, String code, Long customerId, Long restaurantId,
                                      BigDecimal itemsTotal) {
    }

    /** The part of promotion-service's ProblemDetail error that is shown to the customer. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Problem(String detail) {
    }
}
