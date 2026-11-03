package com.fooddelivery.userservice.service;

import com.fooddelivery.userservice.dto.request.ChangePasswordRequest;
import com.fooddelivery.userservice.dto.request.UpdateProfileRequest;
import com.fooddelivery.userservice.dto.response.InternalUserResponse;
import com.fooddelivery.userservice.dto.response.UserResponse;
import com.fooddelivery.userservice.entity.RevokeReason;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.exception.BusinessRuleException;
import com.fooddelivery.userservice.exception.ConflictException;
import com.fooddelivery.userservice.exception.ForbiddenException;
import com.fooddelivery.userservice.exception.UserNotFoundException;
import com.fooddelivery.userservice.mapper.UserMapper;
import com.fooddelivery.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A user managing their own account. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return userMapper.toUserResponse(getActiveUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getActiveUser(userId);

        String phone = UserInputNormalizer.normalizePhone(request.phone());
        if (phone != null && userRepository.existsByPhoneAndIdNot(phone, userId)) {
            throw new ConflictException("Phone number is already registered");
        }

        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        log.info("Profile updated. userId={}", userId);

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getActiveUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Whoever knew the old password (or holds an old session) is signed out everywhere
        refreshTokenService.endAllSessions(userId, RevokeReason.PASSWORD_CHANGED);
        log.info("Password changed. userId={}", userId);
    }

    /** For other microservices: basic user data, whatever the account status. */
    @Transactional(readOnly = true)
    public InternalUserResponse getInternalUser(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toInternalUser)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Loads a user who is allowed to use their account. Deactivated users are blocked.
     * Must be called inside a transaction (all callers are @Transactional).
     */
    public User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("Account is deactivated. Contact support.");
        }
        return user;
    }
}
