
/*
 * Copyright 2011-2025 GatlingCorp (https://gatling.io)
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
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

  private void verifyLastRun() throws IOException, MojoFailureException {
    Path results = resultsFolder.toPath().resolve(LAST_RUN_FILE);

    if (Files.exists(results)) {
      List<String> lines = Files.readAllLines(results, StandardCharsets.UTF_8);
      Files.delete(results);
      List<String> errors =
          lines.stream()
              .filter(line -> line.startsWith(LAST_RUN_FILE_ERROR_LINE))
              .map(line -> line.substring(LAST_RUN_FILE_ERROR_LINE.length()))
              .collect(Collectors.toList());
      if (!errors.isEmpty()) {
        errors.forEach(getLog()::error);
        getLog().error("See the reports in " + resultsFolder.getPath() + " for details.");
        throw new MojoFailureException(String.join(System.lineSeparator(), errors));
      }
    }
  }
}
