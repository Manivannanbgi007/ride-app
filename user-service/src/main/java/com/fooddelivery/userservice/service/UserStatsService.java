package com.fooddelivery.userservice.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

import com.fooddelivery.userservice.config.AppProperties;
import com.fooddelivery.userservice.dto.response.UserStatsResponse;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.repository.UserRepository;
import com.fooddelivery.userservice.repository.UserTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User numbers for the admin dashboard and reports, counted in the database. */
@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserStatsResponse getStats(LocalDate fromDate, LocalDate toDate) {
        StatsPeriod period = StatsPeriod.of(fromDate, toDate, ZoneId.of(appProperties.businessTimeZone()), clock);

        Map<Role, Map<UserStatus, Long>> usersNow = new EnumMap<>(Role.class);
        for (UserTotals totals : userRepository.countPerRoleAndStatus()) {
            usersNow.computeIfAbsent(totals.getRole(), role -> new EnumMap<>(UserStatus.class))
                    .put(totals.getStatus(), totals.getTotal());
        }
        Map<Role, Long> newUsersByRole = new EnumMap<>(Role.class);
        for (UserTotals totals : userRepository.countSignUpsByRole(period.from(), period.before())) {
            newUsersByRole.put(totals.getRole(), totals.getTotal());
        }

        Map<UserStatus, Long> customers = usersNow.getOrDefault(Role.CUSTOMER, Map.of());
        long active = customers.getOrDefault(UserStatus.ACTIVE, 0L);
        long inactive = customers.getOrDefault(UserStatus.INACTIVE, 0L);
        return new UserStatsResponse(period.fromDate(), period.toDate(), usersNow, active + inactive, active, inactive,
                newUsersByRole.getOrDefault(Role.CUSTOMER, 0L), newUsersByRole);
    }
}
