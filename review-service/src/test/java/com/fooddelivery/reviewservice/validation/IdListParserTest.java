package com.fooddelivery.reviewservice.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.fooddelivery.reviewservice.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdListParserTest {

    @Test
    void positiveNumbersAreReadInOrderAndRepeatedOnesCountOnce() {
        assertThat(IdListParser.parse("3, 1,2 ,3,1", 100)).containsExactly(3L, 1L, 2L);
        assertThat(IdListParser.parse("7", 100)).containsExactly(7L);
    }

    @Test
    void aHundredDifferentIdsAreFineAndAHundredAndOneAreNot() {
        assertThat(IdListParser.parse(ids(100), 100)).hasSize(100);
        assertThat(IdListParser.parse(ids(100) + ",1,100", 100)).hasSize(100);
        assertThatThrownBy(() -> IdListParser.parse(ids(101), 100))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("At most 100 ids at a time");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "1,,2", "1,", ",1", "abc", "1,x", "0", "-1", "1,-5", "1.5", "1 2",
            "99999999999999999999"})
    void anythingElseIsRefused(String raw) {
        assertThatThrownBy(() -> IdListParser.parse(raw, 100)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void missingIdsAreRefused() {
        assertThatThrownBy(() -> IdListParser.parse(null, 100))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("ids is required");
    }

    private static String ids(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }
}
