package com.fooddelivery.orderservice.web;

import com.fooddelivery.orderservice.entity.Role;

/**
 * Who is calling, and from where.
 * <p>
 * The user and role come from the verified access token of the request.
 *
 * @param userId    id of the calling user
 * @param role      role of the calling user
 * @param ipAddress client IP address (forwarded by the API Gateway)
 * @param requestId correlation id of the current request
 */
public record CallerInfo(Long userId, Role role, String ipAddress, String requestId) {
}
