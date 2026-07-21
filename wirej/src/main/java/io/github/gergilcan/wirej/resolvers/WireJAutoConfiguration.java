package io.github.gergilcan.wirej.resolvers;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.gergilcan.wirej.database.ConnectionHandler;
import io.github.gergilcan.wirej.rsql.RsqlParser;

/**
 * Registers the beans that generated repository/controller implementations
 * depend on. {@code ConnectionHandler} and {@code RsqlParser} live in the
 * wirej library's own packages, outside a consuming application's scan root,
 * so they need this explicit auto-configuration; the generated
 * {@code *Impl} classes themselves live in the consumer's own package tree
 * and are picked up by its ordinary component scan.
 */
@Configuration(proxyBeanMethods = false)
public class WireJAutoConfiguration {
    @Bean
    public ConnectionHandler connectionHandler(DataSource dataSource) {
        return new ConnectionHandler(dataSource);
    }

    @Bean
    public RsqlParser rsqlParser() {
        return new RsqlParser();
    }
}
