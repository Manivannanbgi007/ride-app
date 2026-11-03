package com.fooddelivery.paymentservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Card data for one payment attempt. It is passed to the payment provider and then forgotten:
 * only a masked description ("VISA **** 1111") is stored. No CVV in this simulation.
 * <p>
 * Records print all fields in toString() by default; this one is overridden so a card number can
 * never end up in a log file by accident.
 */
public record CardDetails(

        @Schema(description = "Test cards: 4111111111111111 succeeds, 4000000000000002 is declined, "
                + "4000000000009995 has insufficient funds", example = "4111111111111111")
        @NotBlank @Size(max = 19)
        String number,

        @Schema(example = "12")
        @NotNull @Min(1) @Max(12)
        Integer expiryMonth,

        @Schema(example = "2030")
        @NotNull @Min(2000) @Max(2100)
        Integer expiryYear
) {

    @Override
    public String toString() {
        String digits = number == null ? "" : number.replaceAll("\\D", "");
        String lastFour = digits.length() >= 4 ? digits.substring(digits.length() - 4) : "????";
        return "CardDetails[number=**** " + lastFour + ", expiry=" + expiryMonth + "/" + expiryYear + "]";
    }
}
