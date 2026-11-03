package com.fooddelivery.reviewservice.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A customer's review of one delivered order. Names are snapshots taken from the order, so a later rename of the
 * restaurant or a dish never changes what the customer reviewed. Only the customer's own words can change (see
 * {@link #edit}): the order, the customer and the names stay as they were. There are no setters.
 */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false, length = 120, updatable = false)
    private String customerName;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    @Column(name = "restaurant_name", nullable = false, length = 120, updatable = false)
    private String restaurantName;

    /** The column is TINYINT: tell Hibernate, or schema validation expects an INT. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    /**
     * Loaded lazily, and in batches for a whole page of reviews (one extra query per page, not one per review),
     * so the admin list does not suffer from the "N+1 queries" problem.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_tags", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "tag", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private Set<ReviewTag> tags = EnumSet.noneOf(ReviewTag.class);

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<ReviewDish> dishes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** The time comes from the injected Clock (not from the entity), so tests can use a fixed time. */
    public Review(Long orderId, Long customerId, String customerName, Long restaurantId, String restaurantName,
                  int rating, String comment, Set<ReviewTag> tags, Instant now) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.rating = rating;
        this.comment = comment;
        this.tags = tags.isEmpty() ? EnumSet.noneOf(ReviewTag.class) : EnumSet.copyOf(tags);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addDish(Long menuItemId, String dishName, int dishRating) {
        dishes.add(new ReviewDish(this, menuItemId, dishName, dishRating));
    }

    /** The customer edits the review: rating, comment and tags are replaced (the dishes: see the two methods below). */
    public void edit(int newRating, String newComment, Set<ReviewTag> newTags, Instant now) {
        this.rating = newRating;
        this.comment = newComment;
        this.tags.clear();
        this.tags.addAll(newTags);
        this.updatedAt = now;
    }

    /** Drops the ratings of dishes that are not in the given set (the customer removed them from the review). */
    public void keepDishes(Set<Long> menuItemIds) {
        dishes.removeIf(dish -> !menuItemIds.contains(dish.getMenuItemId()));
    }

    /**
     * Rates a dish. A dish that is already rated keeps its saved name and gets the new rating (its row is changed, not
     * deleted and inserted again: the unique key on review + menu item would not allow that in one flush); a new dish
     * is added.
     */
    public void rateDish(Long menuItemId, String dishName, int dishRating) {
        for (ReviewDish dish : dishes) {
            if (dish.getMenuItemId().equals(menuItemId)) {
                dish.changeRating(dishRating);
                return;
            }
        }
        addDish(menuItemId, dishName, dishRating);
    }
}
