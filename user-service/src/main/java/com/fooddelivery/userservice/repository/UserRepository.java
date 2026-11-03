package com.fooddelivery.userservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    long countByRoleAndStatus(Role role, UserStatus status);

    /** All accounts per role and status (statistics). */
    @Query("select u.role as role, u.status as status, count(u) as total from User u group by u.role, u.status")
    List<UserTotals> countPerRoleAndStatus();

    /** Sign-ups in a period, per role (status is not grouped: null). */
    @Query("""
            select u.role as role, count(u) as total from User u
            where u.createdAt >= :from and u.createdAt < :before
            group by u.role
            """)
    List<UserTotals> countSignUpsByRole(Instant from, Instant before);

    /**
     * Updates only the last login time. A direct update is used (instead of loading and saving the entity)
     * so that frequent logins do not conflict with admin changes through the @Version check.
     */
    @Modifying
    @Query("update User u set u.lastLoginAt = :loginTime where u.id = :userId")
    void updateLastLoginAt(Long userId, Instant loginTime);
}
