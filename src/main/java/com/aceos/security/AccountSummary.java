package com.aceos.security;

import com.aceos.model.UserRole;

/** Deliberately excludes hashes and temporary secrets. */
public record AccountSummary(
        int userId,
        String name,
        String email,
        UserRole role,
        String state,
        boolean passwordChangeRequired,
        boolean locked) {}
