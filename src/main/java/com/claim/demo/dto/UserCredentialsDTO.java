package com.claim.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class UserCredentialsDTO {
    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "password is required")
    private String password;

    // Default constructor
    public UserCredentialsDTO() {}

    // Parameterized constructor
    public UserCredentialsDTO(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
