package edu.monash.registration.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.registration.*} — the single registration window (FR7) and the active semester.
 * Values are wall-clock in Asia/Kuala_Lumpur.
 */
@Validated
@ConfigurationProperties(prefix = "app.registration")
public class RegistrationProperties {

    @NotNull
    private LocalDateTime periodStart;

    @NotNull
    private LocalDateTime periodEnd;

    @NotBlank
    @Pattern(regexp = "^\\d{4}-S[12]$", message = "app.registration.active-semester must match YYYY-S1|S2")
    private String activeSemester;

    /** Fail fast on a misconfigured window rather than silently denying everything (FR7-B8). */
    @PostConstruct
    void validateWindow() {
        if (periodStart != null && periodEnd != null && !periodStart.isBefore(periodEnd)) {
            throw new IllegalStateException(
                    "Invalid registration window: app.registration.period-start ("
                            + periodStart + ") must be strictly before period-end (" + periodEnd + ")");
        }
    }

    public LocalDateTime getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDateTime periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDateTime getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDateTime periodEnd) {
        this.periodEnd = periodEnd;
    }

    public String getActiveSemester() {
        return activeSemester;
    }

    public void setActiveSemester(String activeSemester) {
        this.activeSemester = activeSemester;
    }
}
