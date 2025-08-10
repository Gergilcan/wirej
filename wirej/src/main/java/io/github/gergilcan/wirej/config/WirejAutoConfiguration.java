package io.github.gergilcan.wirej.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        io.github.gergilcan.wirej.resolvers.ControllerConfiguration.class,
        io.github.gergilcan.wirej.resolvers.RepositoryConfiguration.class
})
public class WirejAutoConfiguration {
    // This class enables automatic registration of wirej configurations
}