package com.fooddelivery.reviewservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The rating of one dish of the order. {@code dishName} is the name on the order when the review was written. */
@Entity
@Table(name = "review_dishes")
@Getter
@NoArgsConstructor
public class ReviewDish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private Review review;

    @Column(name = "menu_item_id", nullable = false, updatable = false)
    private Long menuItemId;

    @Column(name = "dish_name", nullable = false, length = 120, updatable = false)
    private String dishName;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int rating;

    ReviewDish(Review review, Long menuItemId, String dishName, int rating) {
        this.review = review;
        this.menuItemId = menuItemId;
        this.dishName = dishName;
        this.rating = rating;
    }

    void changeRating(int newRating) {
        this.rating = newRating;
    }
}
