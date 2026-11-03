package com.fooddelivery.chatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.chatservice.client.UserInfo;
import com.fooddelivery.chatservice.exception.BusinessRuleException;
import com.fooddelivery.chatservice.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

class ChatRulesTest {

    @Test
    void aPreviewIsOneLineAndNeverLongerThanTheColumn() {
        assertThat(ChatRules.preview("  Hello,\n\n   where is   my order?  ")).isEqualTo("Hello, where is my order?");

        String preview = ChatRules.preview("x".repeat(500));

        assertThat(preview).hasSize(ChatRules.MAX_PREVIEW_LENGTH).endsWith("…");
    }

    @Test
    void aShortPreviewIsLeftAlone() {
        assertThat(ChatRules.preview("x".repeat(200))).isEqualTo("x".repeat(200));
    }

    @Test
    void theRateLimitStopsTheSenderAtTheMaximum() {
        assertThatCode(() -> ChatRules.checkRateLimit(29, 30)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ChatRules.checkRateLimit(30, 30)).isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> ChatRules.checkRateLimit(31, 30)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aPlatformAdminCannotBeAParticipant() {
        assertThatThrownBy(() -> ChatRules.checkCanBeParticipant(new UserInfo(1L, "Root", "PLATFORM_ADMIN")))
                .isInstanceOf(BusinessRuleException.class);
        for (String role : new String[] {"CUSTOMER", "RESTAURANT_ADMIN", "DELIVERY_PARTNER"}) {
            assertThatCode(() -> ChatRules.checkCanBeParticipant(new UserInfo(2L, "Asha", role)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void namesAreCutToTheColumnAndNeverEmpty() {
        assertThat(ChatRules.fitName("N".repeat(300))).hasSize(ChatRules.MAX_NAME_LENGTH);
        assertThat(ChatRules.fitName("  ")).isEqualTo("Unknown");
        assertThat(ChatRules.fitName(null)).isEqualTo("Unknown");
    }

    @Test
    void aSearchTextBecomesALowerCaseContainsPatternWithLiteralWildcards() {
        assertThat(ChatRules.containsPattern("  Refund ")).isEqualTo("%refund%");
        assertThat(ChatRules.containsPattern("100%_off!")).isEqualTo("%100!%!_off!!%");
        assertThat(ChatRules.containsPattern(null)).isEqualTo("%");
        assertThat(ChatRules.containsPattern("   ")).isEqualTo("%");
    }
}
