package com.claim.demo.domain;

public enum UserRole {
    CLAIMANT,
    CLAIMS_OFFICER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
