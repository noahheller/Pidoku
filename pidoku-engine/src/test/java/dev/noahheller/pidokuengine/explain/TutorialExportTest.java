package dev.noahheller.pidokuengine.explain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorialExportTest {

    /** Gradle runs the engine's tests from pidoku-engine/. */
    private static final Path TUTORIAL = Path.of("../pidoku-frontend/src/tutorial/tutorial.json");

    @Test
    void theFrontendsTutorialIsWhatTheEngineWouldWrite() throws IOException {
        assertTrue(Files.exists(TUTORIAL), "Missing " + TUTORIAL.toAbsolutePath());
        assertEquals(TutorialExport.json(), Files.readString(TUTORIAL, StandardCharsets.UTF_8),
                "The tutorial is out of date: run ./gradlew :pidoku-engine:exportTutorial");
    }

    @Test
    void theGuidedStepsComeFirst() {
        String json = TutorialExport.json();
        int lastGuided = json.lastIndexOf("\"guided\" : true");
        int firstFree = json.indexOf("\"guided\" : false");
        assertTrue(lastGuided >= 0 && firstFree > lastGuided);
    }
}
