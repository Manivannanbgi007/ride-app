package com.fooddelivery.reviewservice.repository;

import java.util.Collection;
import java.util.List;

import com.fooddelivery.reviewservice.entity.Review;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The public rating numbers, always counted in the database from the actual rows (nothing is cached): restaurants
 * from reviews.rating (idx_reviews_restaurant_rating), dishes from review_dishes.rating
 * (idx_review_dishes_menu_item_rating). Each method is one grouped query, never one query per id.
 */
public interface RatingRepository extends Repository<Review, Long> {

    @Query("""
            select r.restaurantId as id, avg(r.rating) as average, count(r) as total
            from Review r
            where r.restaurantId in :ids
            group by r.restaurantId
            order by r.restaurantId
            """)
    List<RatingAggregate> averageByRestaurant(Collection<Long> ids);

    @Query("""
            select d.menuItemId as id, avg(d.rating) as average, count(d) as total
            from ReviewDish d
            where d.menuItemId in :ids
            group by d.menuItemId
            order by d.menuItemId
            """)
    List<RatingAggregate> averageByMenuItem(Collection<Long> ids);

    @Query("""
            select r.rating as rating, count(r) as total
            from Review r
            where r.restaurantId = :restaurantId
            group by r.rating
            """)
    List<RatingCount> countRestaurantStars(Long restaurantId);

    @Query("""
            select d.rating as rating, count(d) as total
            from ReviewDish d
            where d.menuItemId = :menuItemId
            group by d.rating
            """)
    List<RatingCount> countMenuItemStars(Long menuItemId);

    /** Keyset paging: the restaurants with reviews after the given id, in id order. */
    @Query("""
            select r.restaurantId as id, avg(r.rating) as average, count(r) as total
            from Review r
            where r.restaurantId > :afterId
            group by r.restaurantId
            order by r.restaurantId
            """)
    List<RatingAggregate> averageByRestaurantAfter(long afterId, Limit limit);

    /** Keyset paging: the menu items with dish ratings after the given id, in id order. */
    @Query("""
            select d.menuItemId as id, avg(d.rating) as average, count(d) as total
            from ReviewDish d
            where d.menuItemId > :afterId
            group by d.menuItemId
            order by d.menuItemId
            """)
    List<RatingAggregate> averageByMenuItemAfter(long afterId, Limit limit);
}
