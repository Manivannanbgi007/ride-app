package com.fooddelivery.reviewservice.client;

import java.util.Optional;

/** Asks order-service about an order. An interface, so tests can replace the remote call. */
public interface OrderLookup {

    /**
     * A review is never accepted without checking the order, so when order-service cannot be reached this throws
     * {@code DependencyUnavailableException} (HTTP 503) instead of guessing.
     *
     * @return the order, or empty when order-service does not know it
     */
    Optional<OrderReviewInfo> findOrder(long orderId);
}
