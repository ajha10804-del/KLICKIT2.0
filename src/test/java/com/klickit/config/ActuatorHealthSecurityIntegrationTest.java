package com.klickit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Actuator Health, Readiness, and Liveness Security Tests")
class ActuatorHealthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/actuator/health returns 200 OK and status UP without sensitive details")
    void healthEndpointReturnsUpWithoutSensitiveDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/actuator/health/liveness returns 200 OK and status UP")
    void livenessProbeReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/actuator/health/readiness returns 200 OK and status UP")
    void readinessProbeReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("Sensitive actuator endpoint /api/actuator/env is not exposed (404/403)")
    void sensitiveEnvEndpointNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is(not(200)));
    }

    @Test
    @DisplayName("Sensitive actuator endpoint /api/actuator/beans is not exposed (404/403)")
    void sensitiveBeansEndpointNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().is(not(200)));
    }

    @Test
    @DisplayName("Sensitive actuator endpoint /api/actuator/configprops is not exposed (404/403)")
    void sensitiveConfigpropsEndpointNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().is(not(200)));
    }

    @Test
    @DisplayName("Sensitive actuator endpoint /api/actuator/mappings is not exposed (404/403)")
    void sensitiveMappingsEndpointNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/mappings"))
                .andExpect(status().is(not(200)));
    }

    @Autowired
    private org.springframework.boot.availability.ApplicationAvailability availability;

    @Test
    @DisplayName("Root actuator endpoint /api/actuator is not exposed publicly (404/403)")
    void actuatorRootEndpointNotExposedPublicly() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().is(not(200)));
    }

    @Test
    @DisplayName("Spring Boot application availability publishes LivenessState.CORRECT and ReadinessState.ACCEPTING_TRAFFIC")
    void applicationAvailabilityProbesPublished() {
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.boot.availability.LivenessState.CORRECT,
                availability.getLivenessState());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.boot.availability.ReadinessState.ACCEPTING_TRAFFIC,
                availability.getReadinessState());
    }
}
