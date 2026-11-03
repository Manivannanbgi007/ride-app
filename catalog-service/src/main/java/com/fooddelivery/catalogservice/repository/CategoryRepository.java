package com.fooddelivery.catalogservice.repository;

import java.util.List;

import com.fooddelivery.catalogservice.entity.Category;
import com.fooddelivery.catalogservice.entity.CategoryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    // The name column's collation is case-insensitive, so these checks are too.
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Category> findByStatusOrderByDisplayOrderAsc(CategoryStatus status);

    List<Category> findAllByOrderByDisplayOrderAsc();

    @Query("select coalesce(max(c.displayOrder), 0) from Category c")
    int findMaxDisplayOrder();

    /** The next ones by id after the given one, with only their id and version (search index check). */
    @Query("select c.id as id, c.version as version from Category c where c.id > :afterId order by c.id")
    List<IdVersion> findVersionsAfter(Long afterId, Pageable pageable);
}
