package com.fooddelivery.searchservice.dto.response;

public record CategoryHit(Long id, String name, String description, String imageUrl, int displayOrder) {
}
