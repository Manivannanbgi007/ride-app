package com.fooddelivery.e2e.support;

import org.testcontainers.DockerClientFactory;

/**
 * Stops and starts the platform's containers (docker-compose.yml names them fd-&lt;service&gt;), for the resilience
 * flows. Uses the Docker client that Testcontainers brings; Docker must be reachable from the tests.
 */
public final class Containers {

    private Containers() {
    }

    public static void stop(String service) {
        DockerClientFactory.instance().client().stopContainerCmd(containerOf(service)).withTimeout(10).exec();
    }

    public static void start(String service) {
        DockerClientFactory.instance().client().startContainerCmd(containerOf(service)).exec();
    }

    private static String containerOf(String service) {
        return "fd-" + service;
    }
}
