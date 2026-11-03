package com.fooddelivery.e2e.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeAll;

/**
 * Every flow needs the whole platform running (.\start-local.ps1 -Detached; E2E_BASE_URL with -PortOffset). Events travel through
 * Kafka, so later steps are awaited: at most 30 s each, checked twice a second.
 */
public abstract class E2ETestBase {

    @BeforeAll
    static void platformIsRunning() {
        Awaitility.setDefaultTimeout(Duration.ofSeconds(30));
        Awaitility.setDefaultPollInterval(Duration.ofMillis(500));
        Awaitility.setDefaultPollDelay(Duration.ZERO);
        try {
            await("the API Gateway at " + E2EConfig.baseUrl()).atMost(Duration.ofSeconds(60)).ignoreExceptions()
                    .until(() -> Api.anonymous().get("/actuator/health").status() == 200);
        } catch (ConditionTimeoutException e) {
            throw new IllegalStateException("The platform is not running at " + E2EConfig.baseUrl()
                    + ". Start it with: .\\start-local.ps1 -Detached (next to another project: -PortOffset 10000 and"
                    + " E2E_BASE_URL=http://localhost:18080)", e);
        }
    }
}
