package com.example.switching.usermgmt.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class SmosSecurityConfiguration {
    @Bean
    PasswordEncoder smosPasswordEncoder() { return new BCryptPasswordEncoder(12); }
}
