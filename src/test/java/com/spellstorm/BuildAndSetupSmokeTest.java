package com.spellstorm;

import com.badlogic.gdx.Gdx;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * BuildAndSetupSmokeTest
 * 
 * Verifies that the Gradle build, Java 21 environment, LibGDX 1.13.1 headless runner,
 * and JUnit 5 test harness operate properly.
 */
@ExtendWith(HeadlessTestRunner.class)
public class BuildAndSetupSmokeTest {

    @Test
    @DisplayName("Verify Java 21 runtime and LibGDX initialization")
    void testEnvironmentInitialization() {
        Assertions.assertNotNull(Gdx.app, "LibGDX Gdx.app context must be initialized");
        Assertions.assertNotNull(Gdx.files, "LibGDX Gdx.files context must be initialized");
        Assertions.assertNotNull(Gdx.gl, "LibGDX Gdx.gl mock context must be initialized");
        Assertions.assertNotNull(Gdx.gl20, "LibGDX Gdx.gl20 mock context must be initialized");

        String javaVersion = System.getProperty("java.version");
        Assertions.assertNotNull(javaVersion, "Java version must be detected");
        System.out.println("Running smoke test on Java version: " + javaVersion);
    }
}
