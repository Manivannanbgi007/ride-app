package com.fooddelivery.adminservice.client;

import java.net.http.HttpClient;
import java.util.EnumMap;
import java.util.Map;

import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.exception.RemoteServiceException;
import com.fooddelivery.adminservice.web.CallerInfo;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the services that own the data. Admin APIs are called as the admin who asked (their access token is passed
 * on), so every service still checks the token and the role itself: admin-service gets no extra rights. Internal APIs
 * get the services' shared token instead (InternalCallsConfig). Each service has
 * its own circuit breaker (RemoteCalls): while one is down, dashboards skip it at once instead of waiting for it.
 */
@Component
public class PlatformClient {

    private final Map<PlatformService, RestClient> clients = new EnumMap<>(PlatformService.class);
    private final RemoteCalls remoteCalls;

    public PlatformClient(RestClient.Builder builder, AppProperties appProperties, RemoteCalls remoteCalls) {
        this.remoteCalls = remoteCalls;
        AppProperties.Services services = appProperties.services();
        clients.put(PlatformService.USER, build(builder, services.user()));
        clients.put(PlatformService.RESTAURANT, build(builder, services.restaurant()));
        clients.put(PlatformService.CATALOG, build(builder, services.catalog()));
        clients.put(PlatformService.ORDER, build(builder, services.order()));
        clients.put(PlatformService.PAYMENT, build(builder, services.payment()));
        clients.put(PlatformService.NOTIFICATION, build(builder, services.notification()));
        clients.put(PlatformService.DELIVERY, build(builder, services.delivery()));
        clients.put(PlatformService.DELIVERY_PARTNER, build(builder, services.deliveryPartner()));
        clients.put(PlatformService.PROMOTION, build(builder, services.promotion()));
    }

    /**
     * GET an admin API of a service as the calling admin.
     *
     * @param query parameters; null values are left out
     * @throws RemoteServiceException when the service cannot be reached or answers with an error
     */
    public <T> T getAsAdmin(PlatformService service, String path, Map<String, ?> query, Class<T> type, CallerInfo caller) {
        return remoteCalls.call(service.serviceName(), () -> {
            try {
                return clients.get(service).get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(path);
                            query.forEach((name, value) -> {
                                if (value != null) {
                                    uriBuilder.queryParam(name, value);
                                }
                            });
                            return uriBuilder.build();
                        })
                        .headers(headers -> headers.setBearerAuth(caller.accessToken()))
                        .retrieve()
                        .body(type);
            } catch (RestClientException e) {
                throw new RemoteServiceException(service.serviceName(), e);
            }
        });
    }

    /** POST an internal API of a service (service-to-service; not routed by the API Gateway). Safe to repeat. */
    public <T> T postInternal(PlatformService service, String path, Class<T> type) {
        return remoteCalls.call(service.serviceName(), () -> {
            try {
                return clients.get(service).post().uri(path).retrieve().body(type);
            } catch (RestClientException e) {
                throw new RemoteServiceException(service.serviceName(), e);
            }
        });
    }

    private static RestClient build(RestClient.Builder builder, AppProperties.RemoteService service) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(service.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(service.readTimeout());
        return builder.clone()
                .baseUrl(service.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(new RequestIdPropagationInterceptor())
                .build();
    }
}
