package com.fooddelivery.reviewservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.reviewservice.config.AppProperties;
import com.fooddelivery.reviewservice.dto.request.ReviewSearchCriteria;
import com.fooddelivery.reviewservice.dto.request.ReviewSummaryFilter;
import com.fooddelivery.reviewservice.dto.response.PageResponse;
import com.fooddelivery.reviewservice.dto.response.ReviewResponse;
import com.fooddelivery.reviewservice.dto.response.ReviewSummaryResponse;
import com.fooddelivery.reviewservice.entity.Review;
import com.fooddelivery.reviewservice.mapper.ReviewMapper;
import com.fooddelivery.reviewservice.repository.RatingCount;
import com.fooddelivery.reviewservice.repository.ReviewRepository;
import com.fooddelivery.reviewservice.repository.ReviewSpecifications;
import com.fooddelivery.reviewservice.validation.SortValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The admin's side: the review list and its summary. Read-only. */
@Service
public class ReviewAdminService {

    static final List<String> SORTABLE_FIELDS = List.of("createdAt", "rating");

    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;
    private final ZoneId businessZone;

    public ReviewAdminService(ReviewRepository reviewRepository, ReviewMapper reviewMapper,
                              AppProperties appProperties) {
        this.reviewRepository = reviewRepository;
        this.reviewMapper = reviewMapper;
        this.businessZone = ZoneId.of(appProperties.businessTimeZone());
    }

    /** The mapping happens inside the transaction: tags and dishes of the whole page are loaded in two batches. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> search(ReviewSearchCriteria criteria, Pageable pageable) {
        SortValidator.validate(pageable.getSort(), SORTABLE_FIELDS);
        BusinessDateRange range = BusinessDateRange.of(criteria.from(), criteria.to(), businessZone);

        Specification<Review> spec = Specification.unrestricted();
        if (criteria.restaurantId() != null) {
            spec = spec.and(ReviewSpecifications.forRestaurant(criteria.restaurantId()));
        }
        if (criteria.menuItemId() != null) {
            spec = spec.and(ReviewSpecifications.ratesDish(criteria.menuItemId()));
        }
        if (criteria.rating() != null) {
            spec = spec.and(ReviewSpecifications.hasRating(criteria.rating()));
        }
        if (criteria.tag() != null) {
            spec = spec.and(ReviewSpecifications.hasTag(criteria.tag()));
        }
        if (range.from() != null) {
            spec = spec.and(ReviewSpecifications.createdFrom(range.from()));
        }
        if (range.before() != null) {
            spec = spec.and(ReviewSpecifications.createdBefore(range.before()));
        }

        // "id" last: reviews with the same time or rating keep one fixed order, so no review repeats or is skipped
        // between two pages
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id")));
        Page<ReviewResponse> reviews = reviewRepository.findAll(spec, page).map(reviewMapper::toResponse);
        return PageResponse.from(reviews);
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(ReviewSummaryFilter filter) {
        BusinessDateRange range = BusinessDateRange.of(filter.from(), filter.to(), businessZone);
        // For one dish the numbers are about the dish: its own ratings (review_dishes.rating), not the overall
        // rating of the reviews that mention it, so the dish's page shows how the dish is rated.
        List<RatingCount> counts = filter.menuItemId() != null
                ? reviewRepository.countByDishRating(filter.menuItemId(), filter.restaurantId(), range.from(), range.before())
                : reviewRepository.countByRating(filter.restaurantId(), range.from(), range.before());

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int stars = 1; stars <= 5; stars++) {
            distribution.put(String.valueOf(stars), 0L);
        }
        long count = 0;
        long totalStars = 0;
        for (RatingCount row : counts) {
            distribution.put(String.valueOf(row.getRating()), row.getTotal());
            count += row.getTotal();
            totalStars += (long) row.getRating() * row.getTotal();
        }
        BigDecimal average = count == 0 ? null
                : BigDecimal.valueOf(totalStars).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        return new ReviewSummaryResponse(count, average, distribution);
    }
}
