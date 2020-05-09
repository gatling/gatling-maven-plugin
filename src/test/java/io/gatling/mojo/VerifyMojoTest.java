package io.gatling.mojo;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

class VerifyMojoTest {
    @Test
    void errors() {
        VerifyMojo verifyMojo = new VerifyMojo();
        verifyMojo.resultsFolder = new File("src/test/resources/golden-files/last-run/last-run-error/");
        Assertions.assertThrows(MojoFailureException.class, () -> verifyMojo.execute());
    }

    @Test
    void empty() {
        VerifyMojo verifyMojo = new VerifyMojo();
        verifyMojo.resultsFolder = new File("src/test/resources/golden-files/last-run/last-run-empty/");
        Assertions.assertDoesNotThrow(() -> verifyMojo.execute());
    }
}
