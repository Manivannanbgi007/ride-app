package com.fooddelivery.aimcpserver.web;

/**
 * The headers of this service besides the caller's access token (Authorization): the service token every caller of
 * this service sends, and the request id it passes on to the platform.
 */
public final class ServiceHeaders {

    public static final String SERVICE_TOKEN = "X-Service-Token";
    public static final String REQUEST_ID = "X-Request-Id";

    private ServiceHeaders() {
    }
}
