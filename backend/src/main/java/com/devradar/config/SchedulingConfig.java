package com.devradar.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty(name = "devradar.scheduling.enabled", havingValue = "true")
@EnableScheduling
public class SchedulingConfig {
}
