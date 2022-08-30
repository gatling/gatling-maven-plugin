
/*
 * Copyright 2011-2022 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.mojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Mojo to verify Gatling simulation results. */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY)
public final class VerifyMojo extends AbstractGatlingExecutionMojo {

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping gatling-maven-plugin");
    } else {
      executePlugin();
    }
  }

  private void executePlugin() throws MojoFailureException, MojoExecutionException {
    try {
      verifyLastRun();
    } catch (IOException e) {
      throw new MojoExecutionException("Could not read result files.", e);
    }
  }

  private void verifyLastRun() throws IOException, MojoFailureException, MojoExecutionException {
    Path results = resultsFolder.toPath().resolve(LAST_RUN_FILE);

    if (results.toFile().exists()) {
      for (String line : Files.readAllLines(results)) {
        checkError(line);
        File directory = new File(resultsFolder, line);
        searchForAssertionFailures(directory);
      }
    }
  }

  private void checkError(String line) throws MojoFailureException {
    if (line.contains(LAST_RUN_FILE_ERROR_LINE)) {
      throwFailureException(line.substring(LAST_RUN_FILE_ERROR_LINE.length()));
    }
  }

  private void searchForAssertionFailures(File runDirectory)
      throws MojoExecutionException, MojoFailureException {
    File jsDir = new File(runDirectory, "js");
    if (jsDir.exists() && jsDir.isDirectory()) {
      File assertionFile = new File(jsDir, "assertions.xml");
      if (assertionFile.exists()) {
        analyzeFile(assertionFile);
      }
    }
  }

  private void analyzeFile(File assertionFile) throws MojoExecutionException, MojoFailureException {
    AssertionsSummary summary;
    try {
      summary = AssertionsSummary.fromAssertionsFile(assertionFile);
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to parse " + assertionFile.toString(), e);
    }
    if (summary.hasFailures()) {
      throwFailureException("Gatling simulation assertions failed!");
    }
  }

  private void throwFailureException(String message) throws MojoFailureException {
    getLog().error(message);
    getLog().error("See the reports in " + resultsFolder.getPath() + " for details.");
    throw new MojoFailureException(message);
  }
}
