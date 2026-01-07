package com.petlytic.models.enums;

import lombok.Getter;

@Getter
public enum ResourceType {
    USER("User"),
    ROLE("Role"),
    PRODUCT("Product"),
    ORDER("Order"),
    VERIFICATION_TOKEN("Verification Token"),
    TOKEN("Token");

    private final String label;

    ResourceType(String label) {
        this.label = label;
    }
}
