package com.fooddelivery.searchservice.document;

/** The three indexes, each with the kind of document it holds (what the consistency check walks through). */
public enum SearchedIndex {
    RESTAURANT(RestaurantDocument.class),
    FOOD(FoodDocument.class),
    CATEGORY(CategoryDocument.class);

    private final Class<?> documentType;

    SearchedIndex(Class<?> documentType) {
        this.documentType = documentType;
    }

    public Class<?> documentType() {
        return documentType;
    }
}
