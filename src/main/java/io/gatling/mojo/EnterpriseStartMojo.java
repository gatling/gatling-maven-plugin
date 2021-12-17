
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

import io.gatling.plugin.util.EnterpriseClient;
import io.gatling.plugin.util.exceptions.EnterpriseClientException;
import io.gatling.plugin.util.exceptions.TeamConfigurationRequiredException;
import io.gatling.plugin.util.model.RunSummary;
import io.gatling.plugin.util.model.SimulationAndRunSummary;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Execute(goal = "enterprisePackage")
@Mojo(name = "enterpriseStart", requiresDependencyResolution = ResolutionScope.TEST)
public class EnterpriseStartMojo extends AbstractEnterpriseApiMojo {

  /** List of exclude patterns to use for scanning. Excludes none by default. */
  @Parameter(property = "gatling.excludes")
  private String[] excludes;

  /** A name of a Simulation class to run. */
  @Parameter(property = "gatling.simulationClass")
  private String simulationClass;

  @Parameter(property = "gatling.teamId")
  private String teamId;

  @Parameter(property = "gatling.simulationId")
  private String simulationId;

  @Parameter(property = "gatling.simulationSystemProperties")
  private Map<String, String> simulationSystemProperties;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkPluginPreConditions();

    if (simulationSystemProperties == null) {
      // @Parameter(defaultValue = ...) only works for properties with a single value
      simulationSystemProperties = Collections.emptyMap();
    }

    final File file = shadedArtifactFile();
    final EnterpriseClient enterpriseClient = initEnterpriseClient();

    try {
      final RunSummary runSummary;
      if (simulationId == null) {
        getLog().info("Creating and starting simulation...");
        final UUID teamIdUuid = teamId != null ? UUID.fromString(teamId) : null;
        final SimulationAndRunSummary result =
            enterpriseClient.createAndStartSimulation(
                teamIdUuid,
                mavenProject.getGroupId(),
                mavenProject.getArtifactId(),
                simulation(),
                simulationSystemProperties,
                file);

        final String simulationId = result.simulation.id.toString();
        final String pkgId = result.simulation.pkgId.toString();
        final String simulationName = result.simulation.name;
        runSummary = result.runSummary;
        getLog()
            .info(String.format("Created simulation %s with ID %s", simulationName, simulationId));
        final String configMsg =
            "To start again the same simulation, specify -Dgatling.simulationId="
                + simulationId
                + ", or add the configuration to your pom.xml, e.g.:\n"
                + "<plugin>\n"
                + "  <groupId>io.gatling</groupId>\n"
                + "  <artifactId>gatling-maven-plugin</artifactId>\n"
                + "  <configuration>\n"
                + "    <simulationId>"
                + simulationId
                + "</simulationId>\n"
                + "    <packageId>"
                + pkgId
                + "</packageId>\n"
                + "  </configuration>\n"
                + "</plugin>";
        getLog().info(configMsg);
      } else {
        getLog().info("Uploading and starting simulation...");
        final SimulationAndRunSummary result =
            enterpriseClient.startSimulation(
                UUID.fromString(simulationId), simulationSystemProperties, file);
        runSummary = result.runSummary;
      }

      final String startedMsg =
          "Simulation successfully started; once running, report will be available at "
              + enterpriseUrl
              + runSummary.reportsPath;
      getLog().info(startedMsg);

    } catch (TeamConfigurationRequiredException e) {
      final String teams =
          e.getAvailableTeams().stream()
              .map(t -> String.format("- %s (%s)\n", t.id, t.name))
              .collect(Collectors.joining());
      final String teamExample = e.getAvailableTeams().get(0).id.toString();
      final String msg =
          "More than 1 team were found to create a simulation.\n"
              + "Available teams:\n"
              + teams
              + "Specify the team you want to use with -Dgatling.simulationId=<simulationId>, or add the configuration to your pom.xml, e.g.:\n"
              + "<plugin>\n"
              + "  <groupId>io.gatling</groupId>\n"
              + "  <artifactId>gatling-maven-plugin</artifactId>\n"
              + "  <configuration>\n"
              + "    <teamId>"
              + teamExample
              + "</teamId>\n"
              + "  </configuration>\n"
              + "</plugin>";
      throw new MojoFailureException(msg);
    } catch (EnterpriseClientException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }

  private String simulation() throws MojoFailureException {
    // Solves the simulations, if no simulation file is defined
    if (simulationClass != null) {
      return simulationClass;
    } else {
      // excludes patterns are used to exclude classes from the enterprise JAR packaging, so
      // excluded classes cannot be selected here either
      List<String> simulations =
          SimulationClassUtils.resolveSimulations(
              mavenProject, compiledClassesFolder, null, excludes);
      if (simulations.size() == 1) {
        return simulations.get(0);
      } else if (simulations.isEmpty()) {
        throw new MojoFailureException("No simulation class to start was found");
      } else {
        final String simulationsMsg =
            simulations.stream().map(s -> "- " + s + "\n").collect(Collectors.joining());
        final String simulationExample = simulations.get(0);
        final String msg =
            "More than 1 simulations were found.\n"
                + "Available simulation classes:\n"
                + simulationsMsg
                + "Specify the simulation you want to use with -Dgatling.simulationClass=<className>, or add the configuration to your pom.xml, e.g.:\n"
                + "<plugin>\n"
                + "  <groupId>io.gatling</groupId>\n"
                + "  <artifactId>gatling-maven-plugin</artifactId>\n"
                + "  <configuration>\n"
                + "    <simulationClass>"
                + simulationExample
                + "</simulationClass>\n"
                + "  </configuration>\n"
                + "</plugin>";
        throw new MojoFailureException(msg);
      }
    }
  }
}
