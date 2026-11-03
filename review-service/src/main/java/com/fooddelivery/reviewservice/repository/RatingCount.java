package com.fooddelivery.reviewservice.repository;

/** Number of reviews with one overall rating (the summary's distribution). */
public interface RatingCount {

    int getRating();

    long getTotal();
}
