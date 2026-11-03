package com.fooddelivery.adminservice.dto.request;

/** How the revenue report adds up its days: per day, per ISO week (Monday to Sunday) or per calendar month. */
public enum RevenueGroupBy {
    DAY, WEEK, MONTH
}
