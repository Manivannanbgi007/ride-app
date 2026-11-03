package com.fooddelivery.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

import com.fooddelivery.events.delivery.DeliveryPartnerStatusChanged;
import com.fooddelivery.events.partner.DeliveryPartnerChanged;
import com.fooddelivery.events.payment.PaymentStatusChanged;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

class AvroBinaryTest {

    @Test
    void storedEventIsReadBackWithTheSchemaItWasWrittenWith() {
        PaymentStatusChanged event = PaymentStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.parse("2026-10-07T10:00:00Z"))
                .setPaymentId(7L)
                .setOrderId(42L)
                .setCustomerId(3L)
                .setStatus("SUCCESS")
                .setMethod("UPI")
                .setAmount(new BigDecimal("353.95"))
                .setRefundedAmount(new BigDecimal("0.00"))
                .setCurrency("INR")
                .build();

        byte[] bytes = AvroBinary.encode(event);
        Schema storedSchema = new Schema.Parser().parse(event.getSchema().toString());
        GenericRecord decoded = AvroBinary.decode(bytes, storedSchema);

        assertThat(decoded.getSchema()).isEqualTo(PaymentStatusChanged.getClassSchema());
        assertThat(decoded.get("orderId")).isEqualTo(42L);
        assertThat(decoded.get("status")).hasToString("SUCCESS");
        assertThat(decoded.get("occurredAt")).isEqualTo(Instant.parse("2026-10-07T10:00:00Z").toEpochMilli());
        Schema amountSchema = storedSchema.getField("amount").schema();
        BigDecimal amount = new Conversions.DecimalConversion()
                .fromBytes((ByteBuffer) decoded.get("amount"), amountSchema, LogicalTypes.decimal(10, 2));
        assertThat(amount).isEqualByComparingTo("353.95");
    }

    @Test
    void partnerEventsKeepTheirOptionalFields() {
        DeliveryPartnerChanged changed = DeliveryPartnerChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.parse("2026-10-12T10:00:00Z"))
                .setChangeType("REGISTERED")
                .setPartnerUserId(9L)
                .setVersion(0L)
                .setFullName("Ravi Kumar")
                .setAccountStatus("ACTIVE")
                .build();
        DeliveryPartnerStatusChanged status = DeliveryPartnerStatusChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.parse("2026-10-12T10:00:00Z"))
                .setPartnerUserId(9L)
                .setVersion(4L)
                .setStatus("AVAILABLE")
                .setAvailableSince(Instant.parse("2026-10-12T09:59:00Z"))
                .setCompletedDeliveries(3)
                .build();

        GenericRecord decodedChange = AvroBinary.decode(AvroBinary.encode(changed), changed.getSchema());
        GenericRecord decodedStatus = AvroBinary.decode(AvroBinary.encode(status), status.getSchema());

        assertThat(decodedChange.get("vehicleType")).isNull();
        assertThat(decodedChange.get("accountStatus")).hasToString("ACTIVE");
        assertThat(decodedStatus.get("currentDeliveryId")).isNull();
        assertThat(decodedStatus.get("availableSince")).isEqualTo(Instant.parse("2026-10-12T09:59:00Z").toEpochMilli());
        assertThat(decodedStatus.get("completedDeliveries")).isEqualTo(3);
    }
}
