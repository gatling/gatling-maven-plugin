package io.gatling.mojo;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

class VerifyMojoTest {
    private VerifyMojo verifyMojo = new VerifyMojo();

    @Test
    void errors() {
        verifyMojo.resultsFolder = new File("src/test/resources/golden-files/last-run/last-run-error/");
        Assertions.assertThrows(MojoFailureException.class, () -> verifyMojo.execute());
    }

    @Test
    void empty() {
        verifyMojo.resultsFolder = new File("src/test/resources/golden-files/last-run/last-run-empty/");
        Assertions.assertDoesNotThrow(() -> verifyMojo.execute());
    }
}
