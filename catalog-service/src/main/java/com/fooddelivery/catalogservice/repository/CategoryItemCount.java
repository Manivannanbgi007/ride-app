package com.fooddelivery.catalogservice.repository;

/** Projection: how many menu items use a category. */
public interface CategoryItemCount {

    Long getCategoryId();

    long getItemCount();
}
