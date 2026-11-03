package com.fooddelivery.reviewservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fooddelivery.reviewservice.client.MenuItemInfo;
import com.fooddelivery.reviewservice.client.MenuItemLookup;
import com.fooddelivery.reviewservice.config.AppProperties;
import com.fooddelivery.reviewservice.dto.request.ReviewFilter;
import com.fooddelivery.reviewservice.dto.response.FeaturedDishResponse;
import com.fooddelivery.reviewservice.dto.response.PageResponse;
import com.fooddelivery.reviewservice.dto.response.TopReviewResponse;
import com.fooddelivery.reviewservice.entity.Review;
import com.fooddelivery.reviewservice.mapper.ReviewMapper;
import com.fooddelivery.reviewservice.repository.FeaturedDishRow;
import com.fooddelivery.reviewservice.repository.ReviewRepository;
import com.fooddelivery.reviewservice.repository.TopReviewRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The top-rated dishes. The ranking is computed by the database (one aggregate query for a page, never in memory).
 * catalog-service is asked afterwards, outside the transaction, only for the picture and the current name.
 */
@Service
public class FeaturedDishService {

    /** The page size limit of the carousel. */
    public static final int MAX_PAGE_SIZE = 12;

    private final ReviewRepository reviewRepository;
    private final MenuItemLookup menuItemLookup;
    private final ReviewMapper reviewMapper;
    private final ZoneId businessZone;
    private final TransactionTemplate readTransaction;

    public FeaturedDishService(ReviewRepository reviewRepository, MenuItemLookup menuItemLookup,
                               ReviewMapper reviewMapper, AppProperties appProperties,
                               PlatformTransactionManager transactionManager) {
        this.reviewRepository = reviewRepository;
        this.menuItemLookup = menuItemLookup;
        this.reviewMapper = reviewMapper;
        this.businessZone = ZoneId.of(appProperties.businessTimeZone());
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    /** What the database says, before catalog-service adds pictures. */
    private record RankedDishes(Page<FeaturedDishRow> rows, Map<Long, TopReviewResponse> topReviews) {
    }

    public PageResponse<FeaturedDishResponse> featuredDishes(ReviewFilter filter, int page, int size) {
        BusinessDateRange range = BusinessDateRange.of(filter.from(), filter.to(), businessZone);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        // One read-only transaction: the ranking and the top reviews come from the same snapshot of the data
        RankedDishes ranked = readTransaction.execute(status -> {
            Page<FeaturedDishRow> rows = reviewRepository.findFeaturedDishes(
                    filter.restaurantId(), range.from(), range.before(), pageRequest);
            return new RankedDishes(rows, topReviews(rows.getContent(), filter, range));
        });

        // No transaction is open here. If catalog-service is down this is an empty map: names come from the reviews.
        Map<Long, MenuItemInfo> catalog = menuItemLookup.findMenuItems(
                ranked.rows().getContent().stream().map(FeaturedDishRow::menuItemId).toList());

        return PageResponse.from(ranked.rows().map(row -> toResponse(row, catalog.get(row.menuItemId()),
                ranked.topReviews().get(row.menuItemId()))));
    }

    private Map<Long, TopReviewResponse> topReviews(List<FeaturedDishRow> rows, ReviewFilter filter,
                                                    BusinessDateRange range) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<TopReviewRef> refs = reviewRepository.findTopReviewIds(
                rows.stream().map(FeaturedDishRow::menuItemId).toList(),
                filter.restaurantId(), range.from(), range.before());
        Map<Long, Review> reviews = reviewRepository
                .findAllById(refs.stream().map(TopReviewRef::getReviewId).toList()).stream()
                .collect(Collectors.toMap(Review::getId, review -> review));
        return refs.stream().collect(Collectors.toMap(TopReviewRef::getMenuItemId,
                ref -> reviewMapper.toTopReview(reviews.get(ref.getReviewId()))));
    }

    private static FeaturedDishResponse toResponse(FeaturedDishRow row, MenuItemInfo catalogItem,
                                                   TopReviewResponse topReview) {
        boolean known = catalogItem != null;
        String name = known && catalogItem.name() != null ? catalogItem.name() : row.dishName();
        return new FeaturedDishResponse(
                row.menuItemId(),
                name,
                known ? catalogItem.imageUrl() : null,
                known ? catalogItem.categoryName() : null,
                row.restaurantId(),
                row.restaurantName(),
                BigDecimal.valueOf(row.averageRating()).setScale(1, RoundingMode.HALF_UP),
                row.ratingCount(),
                topReview);
    }
}
