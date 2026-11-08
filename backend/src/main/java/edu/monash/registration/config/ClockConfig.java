package edu.monash.registration.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock every time-sensitive bean uses, so tests can freeze or shift time (FR7-B3).
 */
@Configuration
public class ClockConfig {

    public static final ZoneId UNIVERSITY_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    @Bean
    public Clock clock() {
        return Clock.system(UNIVERSITY_ZONE);
    }
}
