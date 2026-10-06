package com.richardsenger.piratesnships.platform.services;

/** Basic information about the running loader. */
public interface IPlatformHelper {

    /** The loader name, e.g. {@code "NeoForge"}. */
    String getPlatformName();

    /** Whether a mod with the given id is loaded. */
    boolean isModLoaded(String modId);

    /** Whether we run in a development environment (Gradle runs) rather than a production install. */
    boolean isDevelopmentEnvironment();

    /** Whether this is the physical client (true even when the integrated server runs inside it). */
    boolean isPhysicalClient();

    /** {@code "development"} or {@code "production"}. */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
