package com.fooddelivery.userservice.entity;

/** Why a sign-in session (refresh token family) was ended. */
public enum RevokeReason {
    /** The user signed out. */
    LOGOUT,
    /** The user signed in again in the same area; the previous session of that browser is no longer needed. */
    REPLACED,
    /** An already exchanged refresh token was used again: probably stolen, so the whole session is ended. */
    REUSE_DETECTED,
    PASSWORD_CHANGED,
    DEACTIVATED,
    ROLE_CHANGED
}
