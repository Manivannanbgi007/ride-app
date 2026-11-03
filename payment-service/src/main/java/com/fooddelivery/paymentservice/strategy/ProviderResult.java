package com.fooddelivery.paymentservice.strategy;

/** What the payment provider answered. */
public record ProviderResult(boolean success, String providerReference, String failureReason) {

    public static ProviderResult succeeded(String providerReference) {
        return new ProviderResult(true, providerReference, null);
    }

    public static ProviderResult declined(String failureReason) {
        return new ProviderResult(false, null, failureReason);
    }
}
