package com.example.switching.rtp.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RtpConfiguration {

    @Bean(name = "rtpClock")
    Clock rtpClock() {
        return Clock.systemUTC();
    }
}
