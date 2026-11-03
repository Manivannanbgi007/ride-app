package com.fooddelivery.reviewservice.repository;

/** Which review is the top review of a menu item. */
public interface TopReviewRef {

    Long getMenuItemId();

    Long getReviewId();
}
