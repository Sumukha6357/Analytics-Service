package com.example.analytics.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationSafetyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("analytics.admin-api-key", () -> "admin-secret");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Environment environment;

    @Test
    void flywayRunsBeforeJpaValidationAndHibernateDoesNotMutateSchema() {
        Integer version1 = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Integer.class);
        Integer version2 = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success = TRUE",
                Integer.class);

        Boolean tenantsExists = jdbcTemplate.queryForObject("SELECT to_regclass('public.tenants') IS NOT NULL", Boolean.class);
        Boolean hibernateSequenceExists = jdbcTemplate.queryForObject("SELECT to_regclass('public.hibernate_sequence') IS NOT NULL", Boolean.class);

        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertTrue(version1 != null && version1 > 0, "V1 baseline must be applied");
        assertTrue(version2 != null && version2 > 0, "V2 seed must be applied");
        assertTrue(Boolean.TRUE.equals(tenantsExists), "Schema should be created by Flyway");
        assertTrue(Boolean.FALSE.equals(hibernateSequenceExists), "Hibernate must not auto-create schema artifacts");
    }
}
