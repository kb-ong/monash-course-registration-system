package edu.monash.registration.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.registration.*} — the single registration window (FR7), the active semester and the
 * per-semester credit limit (FR10). Window values are wall-clock in Asia/Kuala_Lumpur.
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

    /**
     * FR10: maximum credit points a student may hold in the active semester. Configurable per
     * deployment so a semester limit such as 18 can be enforced without a code change.
     */
    @Min(value = 1, message = "app.registration.max-credits must be at least 1")
    private int maxCredits = 24;

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

    public int getMaxCredits() {
        return maxCredits;
    }

    public void setMaxCredits(int maxCredits) {
        this.maxCredits = maxCredits;
    }
}
