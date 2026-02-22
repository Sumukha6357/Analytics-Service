package com.example.analytics.config;

import com.example.analytics.security.ApiSecurityFilter;
import com.example.analytics.web.CorrelationIdFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AnalyticsProperties.class)
@EnableCaching
public class AppConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            CorrelationIdFilter correlationIdFilter,
                                            ApiSecurityFilter apiSecurityFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/v1/health", "/error").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiSecurityFilter, CorrelationIdFilter.class);
        return http.build();
    }
}
