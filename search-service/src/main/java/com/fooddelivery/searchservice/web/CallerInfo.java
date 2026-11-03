package com.fooddelivery.searchservice.web;

/**
 * Who is calling, and from where.
 * <p>
 * The user and role come from the verified access token of the request.
 *
 * @param userId      id of the calling user
 * @param role        role of the calling user
 * @param ipAddress   client IP address (forwarded by the API Gateway)
 * @param requestId   correlation id of the current request
 * @param accessToken the caller's access token: other services' admin APIs are called with it, as this admin
 *                    (never logged; null in tests that make no such call)
 */
public record CallerInfo(Long userId, Role role, String ipAddress, String requestId, String accessToken) {

    /** A caller without a token (tests that call no other service). */
    public CallerInfo(Long userId, Role role, String ipAddress, String requestId) {
        this(userId, role, ipAddress, requestId, null);
    }

    // Records print every field: the token must not reach a log line by accident
    @Override
    public String toString() {
        return "CallerInfo[userId=" + userId + ", role=" + role + ", ipAddress=" + ipAddress + ", requestId=" + requestId
                + ", accessToken=****]";
    }
}
