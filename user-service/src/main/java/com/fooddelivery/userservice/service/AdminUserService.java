package com.fooddelivery.userservice.service;

import java.util.ArrayList;
import java.util.List;

import com.fooddelivery.userservice.dto.request.UserSearchCriteria;
import com.fooddelivery.userservice.dto.response.AddressResponse;
import com.fooddelivery.userservice.dto.response.AdminUserDetailsResponse;
import com.fooddelivery.userservice.dto.response.AdminUserSummaryResponse;
import com.fooddelivery.userservice.dto.response.AuditLogResponse;
import com.fooddelivery.userservice.dto.response.PageResponse;
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
import com.fooddelivery.userservice.repository.UserSpecifications;
import com.fooddelivery.userservice.web.CallerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Platform administrators managing users. Every change is audited in the same transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final List<String> SORTABLE_FIELDS =
            List.of("createdAt", "lastLoginAt", "fullName", "email", "role", "status");

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final AddressService addressService;
    private final AdminAuditService adminAuditService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        validateSort(pageable.getSort());

        List<Specification<User>> conditions = new ArrayList<>();
        if (StringUtils.hasText(criteria.search())) {
            conditions.add(UserSpecifications.matchesSearchText(criteria.search()));
        }
        if (criteria.role() != null) {
            conditions.add(UserSpecifications.hasRole(criteria.role()));
        }
        if (criteria.status() != null) {
            conditions.add(UserSpecifications.hasStatus(criteria.status()));
        }

        Page<User> users = userRepository.findAll(Specification.allOf(conditions), pageable);
        return PageResponse.from(users.map(userMapper::toAdminSummary));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailsResponse getUserDetails(Long userId) {
        User user = findUser(userId);
        return userMapper.toAdminDetails(user, addressRepository.countByUserId(userId));
    }

    /** A customer's address book for the admin user screen: 404 when the user does not exist (any status). */
    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(Long userId) {
        findUser(userId);
        return addressService.listAddresses(userId);
    }

    @Transactional
    public AdminUserDetailsResponse activateUser(Long userId, String reason, CallerInfo admin) {
        User user = findUser(userId);

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new ConflictException("User " + userId + " is already active");
        }

        changeStatus(user, UserStatus.ACTIVE, AuditAction.ADMIN_USER_ACTIVATED, reason, admin);
        return toDetails(user);
    }

    @Transactional
    public AdminUserDetailsResponse deactivateUser(Long userId, String reason, CallerInfo admin) {
        User user = findUser(userId);

        if (user.getId().equals(admin.userId())) {
            throw new BusinessRuleException("Administrators cannot deactivate their own account");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new ConflictException("User " + userId + " is already inactive");
        }
        ensureNotLastActivePlatformAdmin(user);

        changeStatus(user, UserStatus.INACTIVE, AuditAction.ADMIN_USER_DEACTIVATED, reason, admin);
        // Signed out everywhere: their sessions cannot be refreshed (an access token they hold expires within minutes)
        refreshTokenService.endAllSessions(userId, RevokeReason.DEACTIVATED);
        return toDetails(user);
    }

    @Transactional
    public AdminUserDetailsResponse changeRole(Long userId, Role newRole, String reason, CallerInfo admin) {
        User user = findUser(userId);
        Role oldRole = user.getRole();

        if (user.getId().equals(admin.userId())) {
            throw new BusinessRuleException("Administrators cannot change their own role");
        }
        if (oldRole == newRole) {
            throw new ConflictException("User " + userId + " already has role " + newRole);
        }
        if (oldRole == Role.PLATFORM_ADMIN) {
            ensureNotLastActivePlatformAdmin(user);
        }

        user.setRole(newRole);
        adminAuditService.recordAction(admin, AuditAction.ADMIN_USER_ROLE_CHANGED, AdminAuditService.ENTITY_TYPE_USER,
                userId, oldRole.name(), newRole.name(), reason);
        // A session belongs to one role: the user signs in again and gets tokens with the new role
        refreshTokenService.endAllSessions(userId, RevokeReason.ROLE_CHANGED);
        log.info("User role changed by admin. userId={} oldRole={} newRole={} adminUserId={}",
                userId, oldRole, newRole, admin.userId());

        return toDetails(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getUserAuditLogs(Long userId, Pageable pageable) {
        findUser(userId);
        return adminAuditService.getAuditLogs(AdminAuditService.ENTITY_TYPE_USER, userId, pageable);
    }

    private void changeStatus(User user, UserStatus newStatus, AuditAction action, String reason, CallerInfo admin) {
        UserStatus oldStatus = user.getStatus();
        user.setStatus(newStatus);

        adminAuditService.recordAction(admin, action, AdminAuditService.ENTITY_TYPE_USER,
                user.getId(), oldStatus.name(), newStatus.name(), reason);
        log.info("User status changed by admin. userId={} oldStatus={} newStatus={} adminUserId={}",
                user.getId(), oldStatus, newStatus, admin.userId());
    }

    /** Removing the last active platform admin would leave nobody able to manage the platform. */
    private void ensureNotLastActivePlatformAdmin(User user) {
        boolean isActivePlatformAdmin = user.getRole() == Role.PLATFORM_ADMIN && user.getStatus() == UserStatus.ACTIVE;

        if (isActivePlatformAdmin && userRepository.countByRoleAndStatus(Role.PLATFORM_ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BusinessRuleException("The platform must keep at least one active PLATFORM_ADMIN");
        }
    }

    private AdminUserDetailsResponse toDetails(User user) {
        // Write the change now so the response shows the new updatedAt, and a concurrent
        // update (@Version conflict) is reported here as 409 rather than at commit time.
        userRepository.flush();
        return userMapper.toAdminDetails(user, addressRepository.countByUserId(user.getId()));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException(
                        "Cannot sort by '" + order.getProperty() + "'. Allowed fields: " + SORTABLE_FIELDS);
            }
        }
    }
}
