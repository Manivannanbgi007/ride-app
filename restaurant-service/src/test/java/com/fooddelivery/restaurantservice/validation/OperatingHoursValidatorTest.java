package com.fooddelivery.restaurantservice.validation;

import static com.fooddelivery.restaurantservice.TestData.slot;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fooddelivery.restaurantservice.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class OperatingHoursValidatorTest {

    private final OperatingHoursValidator validator = new OperatingHoursValidator();

    @Test
    void lunchAndDinnerSlotsOnTheSameDayAreValid() {
        assertThatCode(() -> validator.validate(List.of(
                slot(MONDAY, "11:00", "15:00"),
                slot(MONDAY, "18:00", "23:00"),
                slot(FRIDAY, "11:00", "23:30"))))
                .doesNotThrowAnyException();
    }

    @Test
    void slotMustOpenBeforeItCloses() {
        var slots = List.of(slot(MONDAY, "22:00", "02:00"));
        assertThatThrownBy(() -> validator.validate(slots))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Split hours past midnight");
    }

    @Test
    void overlappingSlotsAreRejectedEvenWhenGivenOutOfOrder() {
        var slots = List.of(slot(MONDAY, "14:00", "20:00"), slot(MONDAY, "11:00", "15:00"));
        assertThatThrownBy(() -> validator.validate(slots))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlap on MONDAY");
    }

    @Test
    void slotsThatTouchDoNotOverlap() {
        assertThatCode(() -> validator.validate(List.of(
                slot(MONDAY, "11:00", "15:00"),
                slot(MONDAY, "15:00", "18:00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void atMostThreeSlotsPerDay() {
        var slots = List.of(slot(MONDAY, "07:00", "09:00"), slot(MONDAY, "11:00", "13:00"),
                slot(MONDAY, "15:00", "17:00"), slot(MONDAY, "19:00", "21:00"));
        assertThatThrownBy(() -> validator.validate(slots))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("At most 3");
    }
}
