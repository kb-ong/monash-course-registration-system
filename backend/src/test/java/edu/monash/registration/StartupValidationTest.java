package edu.monash.registration;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

/**
 * T-FR7-08 / T-NFR3-03: a misconfigured window (start >= end) must fail startup loudly instead of
 * silently denying or granting every registration.
 */
@DisplayName("FR7 — Startup validation")
class StartupValidationTest {

    @Test
    @DisplayName("T-FR7-08 period-start after period-end refuses to start and names both values")
    void tFr7_08_invalidWindowFailsFast() {
        Throwable thrown = catchThrowable(() -> {
            try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(
                    CourseRegistrationApplication.class)
                    .web(WebApplicationType.NONE)
                    .profiles("test")
                    .logStartupInfo(false)
                    .run("--app.registration.period-start=2026-12-31T00:00:00",
                            "--app.registration.period-end=2026-01-01T00:00:00")) {
                fail("Application context must not start with period-start >= period-end");
            }
        });

        assertThat(thrown).as("startup failure").isNotNull();
        Throwable root = rootCause(thrown);
        assertThat(root).isInstanceOf(IllegalStateException.class);
        assertThat(root.getMessage())
                .contains("Invalid registration window")
                .contains("2026-12-31T00:00")
                .contains("2026-01-01T00:00");
    }

    private Throwable rootCause(Throwable throwable) {
        List<Throwable> chain = new ArrayList<>();
        Throwable current = throwable;
        while (current != null && chain.size() < 50) {
            chain.add(current);
            current = current.getCause();
        }
        return chain.get(chain.size() - 1);
    }
}
