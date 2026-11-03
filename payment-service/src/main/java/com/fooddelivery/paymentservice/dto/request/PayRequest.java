package com.fooddelivery.paymentservice.dto.request;

import com.fooddelivery.paymentservice.entity.PaymentEnums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Pay an order that is waiting for online payment. Fill in the details of the chosen method only. */
public record PayRequest(

        @NotNull
        Long orderId,

        @Schema(description = "Must be the method chosen at checkout: CARD, UPI or WALLET")
        @NotNull
        PaymentMethod paymentMethod,

        @Schema(description = "For CARD")
        @Valid
        CardDetails card,

        @Schema(description = "For UPI. Test: fail@upi is declined, refundfail@upi pays but refunds fail",
                example = "meera@okhdfc")
        @Size(max = 100)
        String upiId,

        @Schema(description = "For WALLET. Test: empty-wallet has insufficient balance", example = "wallet-98450")
        @Size(max = 64)
        String walletId
) {
}
