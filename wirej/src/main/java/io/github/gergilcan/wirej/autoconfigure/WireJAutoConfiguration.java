package io.github.gergilcan.wirej.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

import io.github.gergilcan.wirej.resolvers.ControllerConfiguration;
import io.github.gergilcan.wirej.resolvers.RepositoryConfiguration;

/**
 * Auto-configuration for WireJ library.
 * This will automatically configure repository and controller proxies
 * when the library is added to the classpath.
 */
@AutoConfiguration
@ConditionalOnClass({RepositoryConfiguration.class, ControllerConfiguration.class})
@Import({RepositoryConfiguration.class, ControllerConfiguration.class})
public class WireJAutoConfiguration {
    
    // This class serves as the entry point for auto-configuration
    // The actual configuration is done by importing the existing configuration classes
}
