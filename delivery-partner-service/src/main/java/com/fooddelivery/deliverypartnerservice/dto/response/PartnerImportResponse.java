package com.fooddelivery.deliverypartnerservice.dto.response;

/**
 * Internal: result of taking over the partners delivery-service kept before this service existed.
 *
 * @param read      partners delivery-service has
 * @param imported  partners added here
 * @param completed partners registered here meanwhile but never changed, now completed from delivery-service
 * @param skipped   partners this service already knows better (changed here since), or knew all about
 */
public record PartnerImportResponse(long read, long imported, long completed, long skipped) {
}
