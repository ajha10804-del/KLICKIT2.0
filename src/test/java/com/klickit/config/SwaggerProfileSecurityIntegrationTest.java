package com.klickit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Swagger / OpenAPI Environment Hardening Integration Tests")
class SwaggerProfileSecurityIntegrationTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @DisplayName("Production / Default Profile: Swagger and OpenAPI endpoints disabled")
    @TestPropertySource(properties = {
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
    })
    class ProductionSwaggerDisabledTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("GET /api/v3/api-docs returns 404 Not Found when disabled")
        void apiDocsReturnsNotFoundWhenDisabled() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/swagger-ui/index.html returns 404 Not Found when disabled")
        void swaggerUiIndexReturnsNotFoundWhenDisabled() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/swagger-ui.html returns 404 Not Found when disabled")
        void swaggerUiHtmlReturnsNotFoundWhenDisabled() throws Exception {
            mockMvc.perform(get("/swagger-ui.html"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/swagger-resources returns 404 Not Found when disabled")
        void swaggerResourcesReturnsNotFoundWhenDisabled() throws Exception {
            mockMvc.perform(get("/swagger-resources"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    @DisplayName("Development Profile: Swagger and OpenAPI endpoints enabled")
    class DevelopmentSwaggerEnabledTests {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("GET /api/v3/api-docs returns 200 OK with OpenAPI JSON specification")
        void apiDocsReturnsOkWhenEnabled() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openapi").exists())
                    .andExpect(jsonPath("$.info.title").value("KLICKIT Hyperlocal Delivery API"));
        }

        @Test
        @DisplayName("GET /api/swagger-ui/index.html returns 200 OK")
        void swaggerUiIndexReturnsOkWhenEnabled() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk());
        }
    }
}
