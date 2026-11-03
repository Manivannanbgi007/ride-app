package com.fooddelivery.restaurantservice.validation;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fooddelivery.restaurantservice.dto.request.OperatingHoursRequest;
import com.fooddelivery.restaurantservice.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

/**
 * Business rules for a weekly schedule (Bean Validation already checked that fields are present):
 * <ul>
 *   <li>a slot must open before it closes (hours past midnight are split into two slots)</li>
 *   <li>slots on the same day must not overlap</li>
 *   <li>at most {@value #MAX_SLOTS_PER_DAY} slots per day</li>
 * </ul>
 */
@Component
public class OperatingHoursValidator {

    public static final int MAX_SLOTS_PER_DAY = 3;

    public void validate(List<OperatingHoursRequest> operatingHours) {
        Map<DayOfWeek, List<OperatingHoursRequest>> slotsByDay = new EnumMap<>(DayOfWeek.class);

        for (OperatingHoursRequest slot : operatingHours) {
            if (!slot.opensAt().isBefore(slot.closesAt())) {
                throw new BusinessRuleException("On " + slot.dayOfWeek() + ", opening time " + slot.opensAt()
                        + " must be before closing time " + slot.closesAt()
                        + ". Split hours past midnight into two slots.");
            }
            slotsByDay.computeIfAbsent(slot.dayOfWeek(), day -> new ArrayList<>()).add(slot);
        }

        slotsByDay.forEach((day, slots) -> {
            if (slots.size() > MAX_SLOTS_PER_DAY) {
                throw new BusinessRuleException("At most " + MAX_SLOTS_PER_DAY + " opening slots are allowed on " + day);
            }

            slots.sort(Comparator.comparing(OperatingHoursRequest::opensAt));
            for (int i = 1; i < slots.size(); i++) {
                OperatingHoursRequest previous = slots.get(i - 1);
                OperatingHoursRequest current = slots.get(i);
                if (current.opensAt().isBefore(previous.closesAt())) {
                    throw new BusinessRuleException("Opening hours overlap on " + day + ": "
                            + previous.opensAt() + "-" + previous.closesAt() + " and "
                            + current.opensAt() + "-" + current.closesAt());
                }
            }
        });
    }
}
