package com.fooddelivery.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.fooddelivery.userservice.dto.request.UserSearchCriteria;
import com.fooddelivery.userservice.dto.response.AddressResponse;
import com.fooddelivery.userservice.dto.response.AdminUserDetailsResponse;
import com.fooddelivery.userservice.entity.AddressType;
import com.fooddelivery.userservice.entity.AuditAction;
import com.fooddelivery.userservice.entity.RevokeReason;
import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.exception.BusinessRuleException;
import com.fooddelivery.userservice.exception.ConflictException;
import com.fooddelivery.userservice.exception.InvalidRequestException;
import com.fooddelivery.userservice.exception.UserNotFoundException;
import com.fooddelivery.userservice.mapper.UserMapper;
import com.fooddelivery.userservice.repository.AddressRepository;
import com.fooddelivery.userservice.repository.UserRepository;
import com.fooddelivery.userservice.web.CallerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final CallerInfo ADMIN = new CallerInfo(100L, Role.PLATFORM_ADMIN, "10.0.0.1", "req-1");

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private AdminAuditService adminAuditService;

    @Mock
    private AddressService addressService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, addressRepository, addressService, adminAuditService,
                refreshTokenService, new UserMapper());
    }

    @Test
    void deactivatingActiveUserChangesStatusAndWritesAuditRecord() {
        User customer = givenUser(5L, Role.CUSTOMER, UserStatus.ACTIVE);

        AdminUserDetailsResponse response = adminUserService.deactivateUser(5L, "Fraud suspected", ADMIN);

        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(customer.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.ADMIN_USER_DEACTIVATED, "USER", 5L,
                "ACTIVE", "INACTIVE", "Fraud suspected");
        // Signed out everywhere
        verify(refreshTokenService).endAllSessions(5L, RevokeReason.DEACTIVATED);
    }

    @Test
    void deactivatingInactiveUserIsAConflict() {
        givenUser(5L, Role.CUSTOMER, UserStatus.INACTIVE);

        assertThatThrownBy(() -> adminUserService.deactivateUser(5L, "Again", ADMIN))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(adminAuditService, refreshTokenService);
    }

    @Test
    void adminCannotDeactivateThemselves() {
        givenUser(ADMIN.userId(), Role.PLATFORM_ADMIN, UserStatus.ACTIVE);

        var adminId = ADMIN.userId();
        assertThatThrownBy(() -> adminUserService.deactivateUser(adminId, "Leaving", ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own account");
    }

    @Test
    void lastActivePlatformAdminCannotBeDeactivated() {
        givenUser(7L, Role.PLATFORM_ADMIN, UserStatus.ACTIVE);
        when(userRepository.countByRoleAndStatus(Role.PLATFORM_ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.deactivateUser(7L, "Cleanup", ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one active PLATFORM_ADMIN");
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void activatingInactiveUserWritesAuditRecord() {
        givenUser(5L, Role.CUSTOMER, UserStatus.INACTIVE);

        AdminUserDetailsResponse response = adminUserService.activateUser(5L, "Identity verified", ADMIN);

        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.ADMIN_USER_ACTIVATED, "USER", 5L,
                "INACTIVE", "ACTIVE", "Identity verified");
    }

    @Test
    void changingRoleWritesOldAndNewRoleToAudit() {
        User user = givenUser(5L, Role.CUSTOMER, UserStatus.ACTIVE);

        adminUserService.changeRole(5L, Role.RESTAURANT_ADMIN, "Owns Spice Hub", ADMIN);

        assertThat(user.getRole()).isEqualTo(Role.RESTAURANT_ADMIN);
        verify(adminAuditService).recordAction(ADMIN, AuditAction.ADMIN_USER_ROLE_CHANGED, "USER", 5L,
                "CUSTOMER", "RESTAURANT_ADMIN", "Owns Spice Hub");
        // Old sessions carry the old role: the user signs in again
        verify(refreshTokenService).endAllSessions(5L, RevokeReason.ROLE_CHANGED);
    }

    @Test
    void changingToTheSameRoleIsAConflict() {
        givenUser(5L, Role.CUSTOMER, UserStatus.ACTIVE);

        assertThatThrownBy(() -> adminUserService.changeRole(5L, Role.CUSTOMER, "No-op", ADMIN))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void adminCannotChangeOwnRole() {
        givenUser(ADMIN.userId(), Role.PLATFORM_ADMIN, UserStatus.ACTIVE);

        var adminId = ADMIN.userId();
        assertThatThrownBy(() -> adminUserService.changeRole(adminId, Role.CUSTOMER, "Test", ADMIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own role");
    }

    @Test
    void searchRejectsSortingByUnsupportedField() {
        UserSearchCriteria noFilters = new UserSearchCriteria(null, null, null);

        var page = PageRequest.of(0, 20, Sort.by("passwordHash"));
        assertThatThrownBy(() -> adminUserService.searchUsers(noFilters, page))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("passwordHash");
    }

    @Test
    void addressesOfAnInactiveUserAreStillListedForAdmins() {
        givenUser(5L, Role.CUSTOMER, UserStatus.INACTIVE);
        AddressResponse home = new AddressResponse(10L, AddressType.HOME, "12 MG Road", null, "Bengaluru", "Karnataka",
                "560001", "India", null, null, true, null, null);
        when(addressService.listAddresses(5L)).thenReturn(List.of(home));

        assertThat(adminUserService.getUserAddresses(5L)).containsExactly(home);
    }

    @Test
    void addressesOfAnUnknownUserAreNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUserAddresses(404L)).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(addressService);
    }

    private User givenUser(Long id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setFullName("User " + id);
        user.setRole(role);
        user.setStatus(status);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }
}
