package com.fooddelivery.catalogservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.catalogservice.entity.MenuItem;
import com.fooddelivery.catalogservice.entity.MenuItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long>, JpaSpecificationExecutor<MenuItem> {

    List<MenuItem> findByRestaurantId(Long restaurantId);

    List<MenuItem> findByRestaurantIdAndStatus(Long restaurantId, MenuItemStatus status);

    /** Owner access: an item id from another restaurant simply is not found. */
    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);

    long countByRestaurantId(Long restaurantId);

    long countByCategoryId(Long categoryId);

    boolean existsByRestaurantIdAndName(Long restaurantId, String name);

    boolean existsByRestaurantIdAndNameAndIdNot(Long restaurantId, String name, Long id);

    /** Item counts for several categories in one query (instead of one count query per category). */
    @Query("""
            select m.categoryId as categoryId, count(m) as itemCount
            from MenuItem m
            where m.categoryId in :categoryIds
            group by m.categoryId
            """)
    List<CategoryItemCount> countItemsByCategoryIds(Collection<Long> categoryIds);

    /** All menu items per status and availability (statistics). */
    @Query("""
            select m.status as status, m.available as available, count(m) as total
            from MenuItem m
            group by m.status, m.available
            """)
    List<MenuItemTotals> countPerStatusAndAvailability();

    /** The categories with the most items, ties by name; orderable = the given status and available. */
    @Query("""
            select c.id as categoryId, c.name as categoryName, count(m) as totalItems,
                   coalesce(sum(case when m.status = :orderableStatus and m.available = true then 1 else 0 end), 0)
                       as orderableItems
            from MenuItem m join Category c on c.id = m.categoryId
            group by c.id, c.name
            order by count(m) desc, c.name asc
            """)
    List<CategoryItemStats> findCategoryStats(MenuItemStatus orderableStatus, Pageable pageable);

    /** The next ones by id after the given one, with only their id and version (search index check). */
    @Query("select m.id as id, m.version as version from MenuItem m where m.id > :afterId order by m.id")
    List<IdVersion> findVersionsAfter(Long afterId, Pageable pageable);
}
