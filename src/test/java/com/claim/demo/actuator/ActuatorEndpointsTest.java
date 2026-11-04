package com.claim.demo.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JavaMailSender javaMailSender;

    @Test
    void healthIsPublicAndShowsOnlyTheOverallStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void readinessProbeIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminSeesTheDatabaseHealthComponent() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details.database").exists());
    }

    @Test
    void infoAndMetricsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "claimant", roles = "CLAIMANT")
    void infoAndMetricsAreForbiddenToNonAdmins() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanReadInfoAndMetricsButNothingElseIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
        mockMvc.perform(get("/actuator/metrics/jvm.memory.used")).andExpect(status().isOk());
        for (String hidden : new String[]{"env", "beans", "configprops", "heapdump", "loggers", "mappings"}) {
            mockMvc.perform(get("/actuator/" + hidden)).andExpect(status().isNotFound());
        }
    }
}
