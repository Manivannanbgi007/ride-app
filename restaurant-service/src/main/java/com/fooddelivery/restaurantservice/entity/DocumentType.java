package com.fooddelivery.restaurantservice.entity;

public enum DocumentType {
    /** Food safety license (FSSAI in India). Required for every restaurant. */
    FOOD_LICENSE,
    TAX_REGISTRATION,
    OWNER_ID_PROOF,
    BANK_ACCOUNT_PROOF,
    OTHER
}
