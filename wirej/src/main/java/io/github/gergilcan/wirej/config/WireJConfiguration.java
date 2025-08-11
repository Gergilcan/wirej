package io.github.gergilcan.wirej.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for WireJ library.
 * This allows applications to configure WireJ behavior through
 * application.properties or application.yml.
 */
@ConfigurationProperties(prefix = "wirej")
public class WireJConfiguration {

    /**
     * Package names to scan for WireJ controllers.
     * If not specified, WireJ will try to auto-detect the main application package
     * or fall back to scanning common base packages.
     * 
     * Example in application.properties:
     * wirej.scan.packages=com.mycompany.controllers,com.mycompany.api
     */
    private String[] scanPackages;

    /**
     * Enable or disable automatic package detection.
     * Default is true.
     */
    private boolean autoDetectPackages = true;

    /**
     * Enable debug logging for WireJ configuration.
     * Default is false.
     */
    private boolean debug = false;

    public String[] getScanPackages() {
        return scanPackages;
    }

    public void setScanPackages(String[] scanPackages) {
        this.scanPackages = scanPackages;
    }

    public boolean isAutoDetectPackages() {
        return autoDetectPackages;
    }

    public void setAutoDetectPackages(boolean autoDetectPackages) {
        this.autoDetectPackages = autoDetectPackages;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
