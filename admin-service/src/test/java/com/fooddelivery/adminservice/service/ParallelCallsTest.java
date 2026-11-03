package com.fooddelivery.adminservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fooddelivery.adminservice.client.PlatformService;
import com.fooddelivery.adminservice.config.AppProperties;
import com.fooddelivery.adminservice.exception.InvalidRequestException;
import com.fooddelivery.adminservice.exception.RemoteServiceException;
import com.fooddelivery.adminservice.service.ParallelCalls.Batch;
import com.fooddelivery.adminservice.service.ParallelCalls.Call;
import com.fooddelivery.adminservice.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ParallelCallsTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ParallelCalls parallelCalls = new ParallelCalls(executor,
            new AppProperties("Asia/Kolkata", Duration.ofMillis(300), null));

    @AfterEach
    void tearDown() {
        executor.close();
        MDC.clear();
    }

    @Test
    void aFailingOrSlowServiceLeavesItsPartEmptyAndIsNamed() {
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "dash-1");
        Batch batch = parallelCalls.batch();
        Call<String> users = batch.start(PlatformService.USER, () -> "requestId=" + MDC.get(RequestIdFilter.MDC_REQUEST_ID));
        Call<String> payments = batch.start(PlatformService.PAYMENT, () -> {
            throw new RemoteServiceException("payment-service", new IllegalStateException("Connection refused"));
        });
        Call<String> deliveries = batch.start(PlatformService.DELIVERY, () -> {
            sleep(2_000);
            return "too late";
        });

        long started = System.nanoTime();
        assertThat(batch.await(users)).isEqualTo("requestId=dash-1"); // the request id travels with the call
        assertThat(batch.await(payments)).isNull();
        assertThat(batch.await(deliveries)).isNull();
        // One deadline for the whole batch, not one per call
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(batch.unavailable()).containsExactly("delivery-service", "payment-service");
    }

    @Test
    void reportPeriodsDefaultToTheLast30DaysAndAreAtMostAYear() {
        LocalDate today = LocalDate.parse("2026-10-07");

        assertThat(ReportPeriod.of(null, null, today)).isEqualTo(new ReportPeriod(LocalDate.parse("2026-09-08"), today));
        assertThat(ReportPeriod.of(null, LocalDate.parse("2026-06-30"), today).fromDate())
                .isEqualTo(LocalDate.parse("2026-06-01"));
        var longAgo = LocalDate.parse("2025-01-01");
        assertThatThrownBy(() -> ReportPeriod.of(longAgo, today, today))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("366 days");
        var tomorrow = today.plusDays(1);
        assertThatThrownBy(() -> ReportPeriod.of(tomorrow, today, today)).isInstanceOf(InvalidRequestException.class);
    }

    @SuppressWarnings("java:S2925") // simulates a slow service on purpose
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
