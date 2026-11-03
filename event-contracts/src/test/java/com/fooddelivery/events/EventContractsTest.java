package com.fooddelivery.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fooddelivery.events.order.OrderCreated;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import org.apache.avro.util.ClassSecurityValidator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class EventContractsTest {

    @Test
    void eventClassesBecomeTrustedAndOtherClassesKeepAvrosRules() {
        EventContracts.trustEventClasses();
        EventContracts.trustEventClasses(); // services may call it more than once

        assertThatCode(() -> ClassSecurityValidator.validate(PaymentStatusChanged.class)).doesNotThrowAnyException();
        assertThat(ClassSecurityValidator.getGlobal().isTrusted(OrderCreated.class)).isTrue();
        // Classes outside the event package are judged as before (not trusted by Avro's defaults)
        assertThat(ClassSecurityValidator.getGlobal().isTrusted(Assertions.class)).isFalse();
    }

    @Test
    void onlyTheEventPackageAndItsSubPackagesCount() {
        assertThat(EventContracts.isEventPackage("com.fooddelivery.events")).isTrue();
        assertThat(EventContracts.isEventPackage("com.fooddelivery.events.order")).isTrue();
        assertThat(EventContracts.isEventPackage("com.fooddelivery.eventsx")).isFalse();
        assertThat(EventContracts.isEventPackage("com.fooddelivery")).isFalse();
    }
}
