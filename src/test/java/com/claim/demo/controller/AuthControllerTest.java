package com.claim.demo.controller;

import com.claim.demo.dto.RegisterUserRequest;
import com.claim.demo.dto.UserDTO;
import com.claim.demo.service.UserService;
import com.claim.demo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @Test
    void registersThroughDtoWithoutExposingPassword() throws Exception {
        when(userService.registerUser(any(RegisterUserRequest.class)))
                .thenReturn(new UserDTO(5L, "new-user", "new@example.com", "CLAIMANT", "active"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"password\":\"secret12\",\"email\":\"new@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-user"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<RegisterUserRequest> request = ArgumentCaptor.forClass(RegisterUserRequest.class);
        verify(userService).registerUser(request.capture());
        assertEquals("secret12", request.getValue().password());
    }

    @Test
    void returnsTokenDtoForSuccessfulLogin() throws Exception {
        when(userService.loginUser("new-user", "secret"))
                .thenReturn(new UserDTO(5L, "new-user", "new@example.com", "CLAIMANT", "active"));
        when(jwtService.createToken(any(UserDTO.class))).thenReturn("signed.jwt.token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new-user"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void rejectsInvalidRegistrationRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"short\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations.username").value("username is required"))
                .andExpect(jsonPath("$.violations.password").value("password must be between 8 and 72 characters"))
                .andExpect(jsonPath("$.violations.email").value("email must be a valid email address"));

        verify(userService, never()).registerUser(any(RegisterUserRequest.class));
    }
}
