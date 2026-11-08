package edu.monash.registration.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * Two independent connection pools over the same database (B-2).
 *
 * <p>The business path uses the primary pool, sized for the 25-writer burst. Failure audits are
 * written after the business transaction has finished, but Spring still holds the request thread's
 * connection at that point ({@code afterCompletion} runs before {@code cleanupAfterCompletion}), so
 * a {@code REQUIRES_NEW} audit transaction would need a <em>second</em> connection from the same
 * pool. Under an all-failure burst that doubles the demand and deadlocks a 20-connection pool.
 *
 * <p>The audit pool is therefore separate and small: audit inserts are single-row and take
 * milliseconds, so five connections absorb any burst the primary pool can accept. Both pools point
 * at the same in-memory database ({@code DB_CLOSE_DELAY=-1} keeps it alive between connections), so
 * the audit rows are visible to JPA reads and vice versa.
 *
 * <p>The primary pool is declared explicitly and marked {@link Primary} because defining any
 * {@link DataSource} bean makes Spring Boot's {@code DataSourceAutoConfiguration} back off; JPA
 * must still resolve the business pool.
 */
@Configuration
public class DataSourceConfig {

    private final Environment environment;

    public DataSourceConfig(Environment environment) {
        this.environment = environment;
    }

    /** The business pool: JPA, repositories and every request-scoped transaction. */
    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("hikari-business");
        return dataSource;
    }

    /**
     * The audit pool. Deliberately small and impatient: an audit write must never queue behind
     * business traffic, and a slow audit must surface as a loud log line, not a stalled request.
     */
    @Bean(name = "auditDataSource", destroyMethod = "close")
    public DataSource auditDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("hikari-audit");
        dataSource.setJdbcUrl(environment.getRequiredProperty("spring.datasource.url"));
        dataSource.setDriverClassName(
                environment.getProperty("spring.datasource.driver-class-name", "org.h2.Driver"));
        dataSource.setUsername(environment.getProperty("spring.datasource.username", "sa"));
        dataSource.setPassword(environment.getProperty("spring.datasource.password", ""));
        dataSource.setMaximumPoolSize(
                environment.getProperty("app.audit.datasource.maximum-pool-size", Integer.class, 5));
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(
                environment.getProperty("app.audit.datasource.connection-timeout-ms", Long.class, 5000L));
        return dataSource;
    }
}
