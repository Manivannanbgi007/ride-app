package com.fooddelivery.reviewservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.reviewservice.entity.Review;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * The optional filters below use the "(:x is null or column = :x)" pattern, so one query serves every combination
 * of filters. "from" is inclusive and "before" exclusive (see BusinessDateRange).
 */
public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    boolean existsByOrderId(Long orderId);

    Optional<Review> findByOrderIdAndCustomerId(Long orderId, Long customerId);

    /** A customer's own review: someone else's review is "not found" here, so nobody can find out it exists. */
    Optional<Review> findByIdAndCustomerId(Long id, Long customerId);

    /** Number of reviews per star, counted in the database: the summary never loads the reviews. */
    @Query("""
            select r.rating as rating, count(r) as total
            from Review r
            where (:restaurantId is null or r.restaurantId = :restaurantId)
              and (:from is null or r.createdAt >= :from)
              and (:before is null or r.createdAt < :before)
            group by r.rating
            """)
    List<RatingCount> countByRating(Long restaurantId, Instant from, Instant before);

    /** Like {@link #countByRating} but over one dish's own ratings (review_dishes.rating) instead of the overall ones. */
    @Query("""
            select d.rating as rating, count(d) as total
            from ReviewDish d join d.review r
            where d.menuItemId = :menuItemId
              and (:restaurantId is null or r.restaurantId = :restaurantId)
              and (:from is null or r.createdAt >= :from)
              and (:before is null or r.createdAt < :before)
            group by d.rating
            """)
    List<RatingCount> countByDishRating(Long menuItemId, Long restaurantId, Instant from, Instant before);

    /**
     * One row per menu item with dish ratings in the period, best first. Ordering and paging happen in the database;
     * the count query counts the menu items (a menu item belongs to one restaurant, so it is one group).
     */
    @Query(value = """
            select new com.fooddelivery.reviewservice.repository.FeaturedDishRow(
                d.menuItemId, max(d.dishName), r.restaurantId, max(r.restaurantName), avg(d.rating), count(d))
            from ReviewDish d join d.review r
            where (:restaurantId is null or r.restaurantId = :restaurantId)
              and (:from is null or r.createdAt >= :from)
              and (:before is null or r.createdAt < :before)
            group by d.menuItemId, r.restaurantId
            order by avg(d.rating) desc, count(d) desc, d.menuItemId asc
            """,
            countQuery = """
                    select count(distinct d.menuItemId)
                    from ReviewDish d join d.review r
                    where (:restaurantId is null or r.restaurantId = :restaurantId)
                      and (:from is null or r.createdAt >= :from)
                      and (:before is null or r.createdAt < :before)
                    """)
    Page<FeaturedDishRow> findFeaturedDishes(Long restaurantId, Instant from, Instant before, Pageable pageable);

    /**
     * One customer's dishes by their own ratings, best first (then most ratings, then most recent), one grouped query
     * over review_dishes joined to the customer's reviews (idx_reviews_customer_created). A null "from" means all time.
     */
    @Query("""
            select new com.fooddelivery.reviewservice.repository.LikedDishRow(
                d.menuItemId, max(d.dishName), avg(d.rating), count(d), max(r.createdAt))
            from ReviewDish d join d.review r
            where r.customerId = :customerId
              and (:from is null or r.createdAt >= :from)
            group by d.menuItemId
            order by avg(d.rating) desc, count(d) desc, max(r.createdAt) desc, d.menuItemId asc
            """)
    List<LikedDishRow> findLikedDishes(Long customerId, Instant from, Limit limit);

    /** All dish ratings of one customer in the period (whether or not their dish made the liked list). */
    @Query("""
            select count(d)
            from ReviewDish d join d.review r
            where r.customerId = :customerId
              and (:from is null or r.createdAt >= :from)
            """)
    long countDishRatingsOfCustomer(Long customerId, Instant from);

    /**
     * For each given menu item, the id of its best review in the period: a review with a comment first, then the
     * highest overall rating, then the newest. A window function ranks the reviews of every dish in one query.
     */
    @Query(value = """
            select ranked.menuItemId as menuItemId, ranked.reviewId as reviewId
            from (
                select d.menu_item_id as menuItemId, r.id as reviewId,
                       row_number() over (
                           partition by d.menu_item_id
                           order by (r.comment is not null) desc, r.rating desc, r.created_at desc, r.id desc
                       ) as position
                from review_dishes d join reviews r on r.id = d.review_id
                where d.menu_item_id in (:menuItemIds)
                  and (:restaurantId is null or r.restaurant_id = :restaurantId)
                  and (:from is null or r.created_at >= :from)
                  and (:before is null or r.created_at < :before)
            ) ranked
            where ranked.position = 1
            """, nativeQuery = true)
    List<TopReviewRef> findTopReviewIds(Collection<Long> menuItemIds, Long restaurantId, Instant from, Instant before);
}
