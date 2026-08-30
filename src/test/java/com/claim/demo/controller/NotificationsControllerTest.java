package com.claim.demo.controller;

import com.claim.demo.service.NotificationService;
import com.claim.demo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationsController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtService jwtService;

    @Test
    void validatesNotificationSubscriptionRequest() throws Exception {
        mockMvc.perform(post("/notifications/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":0,\"message\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations.userId").value("userId must be positive"))
                .andExpect(jsonPath("$.violations.message").value("message is required"));

        verify(notificationService, never()).subscribeToNotifications(0L, " ");
    }
}
