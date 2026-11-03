package com.fooddelivery.reviewservice.repository;

/** The ratings of one restaurant or menu item, aggregated in the database: {@code average} is not rounded yet. */
public interface RatingAggregate {

    Long getId();

    Double getAverage();

    long getTotal();
}
