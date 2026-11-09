package dev.noahheller.pidokuengine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Facts about this build of the engine. */
public final class PidokuEngine {
    /** The engine version from pidoku-engine/build.gradle, e.g. "1.0.0". */
    public static final String VERSION = loadVersion();

    private PidokuEngine() {}

    private static String loadVersion() {
        try (InputStream in = PidokuEngine.class.getResourceAsStream("engine.properties")) {
            if (in == null) throw new IllegalStateException("engine.properties is missing from the classpath");
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("version");
            // An unexpanded placeholder means the resource wasn't processed by Gradle.
            if (version == null || version.isBlank() || version.contains("${")) {
                throw new IllegalStateException("engine.properties has no version; build the engine with Gradle");
            }
            return version;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read engine.properties", e);
        }
    }
}
