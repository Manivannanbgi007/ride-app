package com.fooddelivery.promotionservice.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Stable JSON shape for paginated results (Spring's Page class is not meant to be serialized directly).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
