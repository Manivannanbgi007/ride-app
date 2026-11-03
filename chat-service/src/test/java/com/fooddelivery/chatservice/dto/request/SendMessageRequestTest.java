package com.fooddelivery.chatservice.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** The body and clientMessageId rules, checked with the real Bean Validation. */
class SendMessageRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final String ID = UUID.randomUUID().toString();

    private static Set<ConstraintViolation<SendMessageRequest>> check(String body, String clientMessageId) {
        return VALIDATOR.validate(new SendMessageRequest(body, clientMessageId));
    }

    @Test
    void aNormalMessageIsValid() {
        assertThat(check("Where is my order?", ID)).isEmpty();
    }

    @Test
    void theBodyIsTrimmedBeforeItIsChecked() {
        assertThat(new SendMessageRequest("   hi   ", ID).body()).isEqualTo("hi");
        // 2000 characters plus padding is fine once trimmed
        assertThat(check("  " + "x".repeat(2000) + "  ", ID)).isEmpty();
    }

    @Test
    void aBlankOrMissingBodyIsRejected() {
        assertThat(check("   \n\t ", ID)).hasSize(1);
        assertThat(check("", ID)).hasSize(1);
        assertThat(check(null, ID)).hasSize(1);
    }

    @Test
    void aBodyOverTwoThousandCharactersIsRejected() {
        assertThat(check("x".repeat(2001), ID)).hasSize(1);
    }

    @Test
    void theClientMessageIdMustBeAUuid() {
        assertThat(check("hi", "not-a-uuid")).hasSize(1);
        assertThat(check("hi", "1234")).hasSize(1);
        assertThat(check("hi", null)).hasSize(1);
        assertThat(check("hi", ID.toUpperCase(java.util.Locale.ROOT))).isEmpty();
    }

    @Test
    void theClientMessageIdIsKeptInLowerCaseSoItIsRecognizedAgain() {
        assertThat(new SendMessageRequest("hi", ID.toUpperCase(java.util.Locale.ROOT)).clientMessageId()).isEqualTo(ID);
    }
}
