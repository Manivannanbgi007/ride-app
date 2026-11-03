package com.fooddelivery.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fooddelivery.apigateway.config.GatewayTrafficProperties.ServiceTraffic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class GatewayTrafficPropertiesTest {

    private static final Map<String, Object> ORDER_SERVICE = Map.of(
            "app.gateway.services.order-service.requests-per-second", 20,
            "app.gateway.services.order-service.burst", 40,
            "app.gateway.services.order-service.connect-timeout", 2000,
            "app.gateway.services.order-service.response-timeout", 9000,
            "app.gateway.services.order-service.retries", 2);

    @Test
    void readsTheSettingsPerService() {
        GatewayTrafficProperties traffic = bind(ORDER_SERVICE, Map.of());

        assertThat(traffic.services()).containsOnly(entry("order-service", new ServiceTraffic(20, 40, 2000, 9000, 2)));
        assertThat(traffic.responseTimeoutLimit()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void anEnvironmentVariableOverridesOneSetting() {
        GatewayTrafficProperties traffic = bind(ORDER_SERVICE,
                Map.of("APP_GATEWAY_SERVICES_ORDER_SERVICE_RESPONSE_TIMEOUT", "7000"));

        assertThat(traffic.services()).containsOnly(entry("order-service", new ServiceTraffic(20, 40, 2000, 7000, 2)));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
        "requests-per-second, 0,     requests-per-second must be at least 1",
        "burst,               10,    burst must be at least requests-per-second",
        "connect-timeout,     0,     connect-timeout must be at least 1",
        "response-timeout,    10000, response-timeout must be between 1 and 9999 ms",
        "retries,             0,     retries must be between 1 and 3",
        "retries,             4,     retries must be between 1 and 3",
        "connect-timeout,     4000,  connect-timeout x (retries + 1) must stay below response-timeout-limit"})
    void refusesASettingThatWouldMisbehave(String setting, String value, String rule) {
        Map<String, Object> settings = new HashMap<>(ORDER_SERVICE);
        settings.put("app.gateway.services.order-service." + setting, value);

        assertThatThrownBy(() -> bind(settings, Map.of()))
                .isInstanceOf(BindException.class)
                .rootCause().hasMessageStartingWith("app.gateway.services.order-service: " + rule);
    }

    private static GatewayTrafficProperties bind(Map<String, Object> applicationYml, Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        environment.getPropertySources().addLast(new MapPropertySource("application.yml", applicationYml));
        return Binder.get(environment).bindOrCreate("app.gateway", GatewayTrafficProperties.class);
    }
}
