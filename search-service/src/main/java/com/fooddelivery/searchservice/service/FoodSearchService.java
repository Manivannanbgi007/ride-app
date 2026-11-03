package com.fooddelivery.searchservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fooddelivery.searchservice.document.FoodDocument;
import com.fooddelivery.searchservice.document.RestaurantDocument;
import com.fooddelivery.searchservice.dto.request.FoodSearchCriteria;
import com.fooddelivery.searchservice.dto.request.FoodSearchCriteria.FoodSort;
import com.fooddelivery.searchservice.dto.response.FoodHit;
import com.fooddelivery.searchservice.dto.response.SearchResults;
import com.fooddelivery.searchservice.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Customers looking for dishes across all restaurants. Only what can be ordered now is found: listed (ACTIVE),
 * in stock, from an ACTIVE restaurant.
 */
@Service
@RequiredArgsConstructor
public class FoodSearchService {

    private static final List<String> TEXT_FIELDS = List.of("name^3", "categoryName^2", "description", "restaurantName");
    private static final String FOOD_TYPES = "foodTypes";
    private static final String CATEGORIES = "categories";
    private static final String PRICE = "price";

    private final ElasticsearchOperations operations;

    public SearchResults<FoodHit> search(FoodSearchCriteria criteria, int page, int size) {
        SearchQueries.checkPage(page, size);
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new InvalidRequestException("minPrice must not be greater than maxPrice");
        }
        List<Query> filters = filters(criteria);
        boolean hasText = StringUtils.hasText(criteria.q());

        NativeQueryBuilder query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(filters);
                    if (hasText) {
                        b.must(SearchQueries.text(criteria.q().trim(), TEXT_FIELDS));
                    }
                    return b;
                }))
                .withAggregation(FOOD_TYPES, SearchQueries.facet("foodType"))
                .withAggregation(CATEGORIES, SearchQueries.facet("categoryName.raw"))
                .withPageable(PageRequest.of(page, size));
        List<SortOptions> sort = sortOrder(criteria.sort(), hasText);
        if (!sort.isEmpty()) {
            query.withSort(sort);
        }

        SearchHits<FoodDocument> hits = operations.search(query.build(), FoodDocument.class);
        List<FoodHit> results = hits.getSearchHits().stream().map(hit -> toHit(hit.getContent())).toList();
        return new SearchResults<>(criteria.q(), hits.getTotalHits(), page, size,
                SearchQueries.totalPages(hits.getTotalHits(), size), results,
                SearchQueries.facets(hits, FOOD_TYPES, CATEGORIES));
    }

    /** What can be ordered now, narrowed down by the customer's choices. */
    private static List<Query> filters(FoodSearchCriteria criteria) {
        List<Query> filters = new ArrayList<>(orderable());
        if (StringUtils.hasText(criteria.city())) {
            filters.add(SearchQueries.exactIgnoringCase("restaurantCity", criteria.city().trim()));
        }
        if (criteria.restaurantId() != null) {
            filters.add(SearchQueries.exact("restaurantId", criteria.restaurantId()));
        }
        if (criteria.categoryId() != null) {
            filters.add(SearchQueries.exact("categoryId", criteria.categoryId()));
        }
        if (criteria.foodType() != null) {
            filters.add(SearchQueries.exact("foodType", criteria.foodType().name()));
        }
        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            filters.add(priceRange(criteria.minPrice(), criteria.maxPrice()));
        }
        if (criteria.minRating() != null) {
            filters.add(SearchQueries.minRating(criteria.minRating()));
        }
        return filters;
    }

    private static Query priceRange(BigDecimal min, BigDecimal max) {
        return Query.of(q -> q.range(r -> r.number(n -> {
            n.field(PRICE);
            if (min != null) {
                n.gte(min.doubleValue());
            }
            if (max != null) {
                n.lte(max.doubleValue());
            }
            return n;
        })));
    }

    /**
     * By price either way, or by rating; by relevance needs a text to score against, so without one dishes are listed
     * by name. Rating ties are ordered the same way (best match, or name).
     */
    private static List<SortOptions> sortOrder(FoodSort requested, boolean hasText) {
        FoodSort sort = requested == null ? FoodSort.RELEVANCE : requested;
        SortOptions relevance = hasText
                ? SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)))
                : byField("name.sort", SortOrder.Asc);
        return switch (sort) {
            case PRICE_LOW_TO_HIGH -> List.of(byField(PRICE, SortOrder.Asc));
            case PRICE_HIGH_TO_LOW -> List.of(byField(PRICE, SortOrder.Desc));
            case RATING -> SearchQueries.ratingSort(relevance);
            case RELEVANCE -> hasText ? List.of() : List.of(relevance);
        };
    }

    /** Filters every dish search and suggestion uses: listed, in stock, from a restaurant that takes orders. */
    static List<Query> orderable() {
        return List.of(
                SearchQueries.exact("status", "ACTIVE"),
                SearchQueries.exact("available", true),
                SearchQueries.exact("restaurantStatus", RestaurantDocument.VISIBLE_STATUS));
    }

    private static SortOptions byField(String field, SortOrder order) {
        return SortOptions.of(s -> s.field(f -> f.field(field).order(order)));
    }

    private static FoodHit toHit(FoodDocument food) {
        int ratingCount = food.getRatingCount() == null ? 0 : food.getRatingCount();
        return new FoodHit(food.getId(), food.getName(), food.getDescription(),
                BigDecimal.valueOf(food.getPrice()).setScale(2, RoundingMode.HALF_UP), food.getFoodType(),
                food.getImageUrl(), food.getCategoryId(), food.getCategoryName(), food.getRestaurantId(),
                food.getRestaurantName(), food.getRestaurantCity(),
                ratingCount == 0 ? null : food.getRatingAverage(), ratingCount);
    }
}
