package com.fooddelivery.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.fooddelivery.userservice.config.AppProperties;
import com.fooddelivery.userservice.dto.response.UserStatsResponse;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.exception.InvalidRequestException;
import com.fooddelivery.userservice.repository.UserRepository;
import com.fooddelivery.userservice.repository.UserTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    // 2026-10-07 23:30 in Bengaluru: "today" is the 7th there, although it is still the 7th in UTC too
    private static final Instant NOW = Instant.parse("2026-10-07T18:00:00Z");

    @Mock
    private UserRepository userRepository;

    private UserStatsService userStatsService;

    @BeforeEach
    void setUp() {
        userStatsService = new UserStatsService(userRepository,
                new AppProperties("Asia/Kolkata", null, null, null, null), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void customersNowAndSignUpsOfTheDay() {
        when(userRepository.countPerRoleAndStatus()).thenReturn(List.of(
                totals(Role.CUSTOMER, UserStatus.ACTIVE, 120), totals(Role.CUSTOMER, UserStatus.INACTIVE, 5),
                totals(Role.RESTAURANT_ADMIN, UserStatus.ACTIVE, 9)));
        // Today in Bengaluru: from 2026-10-06T18:30Z to 2026-10-07T18:30Z
        when(userRepository.countSignUpsByRole(Instant.parse("2026-10-06T18:30:00Z"), Instant.parse("2026-10-07T18:30:00Z")))
                .thenReturn(List.of(totals(Role.CUSTOMER, null, 7), totals(Role.DELIVERY_PARTNER, null, 2)));

        UserStatsResponse stats = userStatsService.getStats(null, null);

        assertThat(stats.fromDate()).isEqualTo(LocalDate.parse("2026-10-07"));
        assertThat(stats.customers()).isEqualTo(125);
        assertThat(stats.activeCustomers()).isEqualTo(120);
        assertThat(stats.inactiveCustomers()).isEqualTo(5);
        assertThat(stats.newCustomers()).isEqualTo(7);
        assertThat(stats.newUsersByRole()).containsEntry(Role.DELIVERY_PARTNER, 2L);
        assertThat(stats.usersNow().get(Role.RESTAURANT_ADMIN)).containsEntry(UserStatus.ACTIVE, 9L);
    }

    @Test
    void aPeriodIsAtMostAYear() {
        var longAgo = LocalDate.parse("2025-01-01");
        var today = LocalDate.parse("2026-10-07");
        assertThatThrownBy(() -> userStatsService.getStats(longAgo, today))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("366 days");
        var tomorrow = LocalDate.parse("2026-10-08");
        assertThatThrownBy(() -> userStatsService.getStats(tomorrow, today))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static UserTotals totals(Role role, UserStatus status, long total) {
        return new UserTotals() {
            @Override
            public Role getRole() {
                return role;
            }

            @Override
            public UserStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
