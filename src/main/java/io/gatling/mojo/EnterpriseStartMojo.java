
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

import io.gatling.plugin.EmptyChoicesException;
import io.gatling.plugin.EnterprisePlugin;
import io.gatling.plugin.InteractiveEnterprisePlugin;
import io.gatling.plugin.exceptions.EnterprisePluginException;
import io.gatling.plugin.exceptions.SeveralTeamsFoundException;
import io.gatling.plugin.exceptions.SimulationStartException;
import io.gatling.plugin.model.RunSummary;
import io.gatling.plugin.model.Simulation;
import io.gatling.plugin.model.SimulationStartResult;
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

/**
 * Mojo to package, upload and start a simulation on Gatling Enterprise Cloud.
 *
 * <ul>
 *   <li>By default, this goal will prompt you to choose to run a simulation already configured on
 *       Gatling Enterprise or configure a new one, and provide all required details.
 *   <li>If a simulationId is set, this goal will automatically choose to start that simulation.
 *   <li>If Maven is run in batch mode, any interactive prompts will be disabled and the goal will
 *       fail if user input is required (see Maven's batch mode here:
 *       https://maven.apache.org/ref/3-LATEST/maven-embedder/cli.html#batch-mode).
 * </ul>
 */
@Execute(goal = "enterprisePackage")
@Mojo(name = "enterpriseStart", requiresDependencyResolution = ResolutionScope.TEST)
public class EnterpriseStartMojo extends AbstractEnterprisePluginMojo {

  /**
   * List of exclude patterns to use when scanning for simulation classes. Excludes none by default.
   */
  @Parameter(property = "gatling.excludes")
  private String[] excludes;

  /** The fully qualified name of the Simulation class to run. */
  @Parameter(property = "gatling.simulationClass")
  private String simulationClass;

  /** The ID of the team used when configuring a new package or simulation on Gatling Enterprise. */
  @Parameter(property = "gatling.enterprise.teamId")
  private String teamId;

  /**
   * The ID of a simulation already configured on Gatling Enterprise. If 'simulationId' is
   * configured, gatling:enterpriseStart will upload your updated code to the package configured for
   * that simulation, and start the simulation.
   */
  @Parameter(property = "gatling.enterprise.simulationId")
  private String simulationId;

  /**
   * The ID of a package already configured on Gatling Enterprise. When configuring a new simulation
   * on Gatling Enterprise, this will force the use of an existing package for that simulation.
   */
  @Parameter(property = "gatling.enterprise.packageId")
  private String packageId;

  /**
   * Provides system properties when starting a simulation, in addition to the ones which may
   * already be configured for that simulation (see
   * https://gatling.io/docs/enterprise/cloud/reference/user/simulations/#step-4--5-jvm-options--java-system-properties).
   */
  @Parameter(property = "gatling.enterprise.simulationSystemProperties")
  private Map<String, String> simulationSystemProperties;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkPluginPreConditions();

    final UUID teamIdUuid = teamId != null ? UUID.fromString(teamId) : null;
    final UUID packageIdUuid = packageId != null ? UUID.fromString(packageId) : null;
    if (simulationSystemProperties == null) {
      // @Parameter(defaultValue = ...) only works for properties with a single value
      simulationSystemProperties = Collections.emptyMap();
    }
    final File file = shadedArtifactFile();

    try {
      final RunSummary runSummary;
      if (simulationId != null) {
        runSummary = startExistingSimulation(file);
      } else if (session.getRequest().isInteractiveMode()) {
        runSummary = interactiveCreateAndStartSimulation(file, teamIdUuid, packageIdUuid);
      } else {
        runSummary = batchCreateAndStartSimulation(file, teamIdUuid, packageIdUuid);
      }
      getLog().info(CommonLogMessage.simulationStartSuccess(enterpriseUrl, runSummary.reportsPath));
    } catch (SimulationStartException e) {
      final Simulation simulation = e.getSimulation();
      final String msg =
          "Failed to start simulation.\n"
              + String.format(
                  "Simulation %s with ID %s exists but could not be started: ",
                  simulation.name, simulation.id)
              + e.getCause().getMessage()
              + "\n"
              + CommonLogMessage.simulationStartSample(simulation);
      throw new MojoFailureException(msg, e);
    } catch (EnterprisePluginException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }

  private RunSummary startExistingSimulation(File file) throws MojoFailureException {
    getLog().info("Uploading and starting simulation...");
    final EnterprisePlugin enterprisePlugin = initEnterprisePlugin();
    try {
      return enterprisePlugin.uploadPackageAndStartSimulation(
              UUID.fromString(simulationId), simulationSystemProperties, file)
          .runSummary;
    } catch (EnterprisePluginException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }

  private RunSummary batchCreateAndStartSimulation(File file, UUID teamIdUuid, UUID packageIdUuid)
      throws EnterprisePluginException, MojoFailureException {
    getLog().info("Creating and starting simulation (batch mode)...");

    final EnterprisePlugin enterprisePlugin = initEnterprisePlugin();
    final SimulationStartResult result;
    try {
      result =
          enterprisePlugin.createAndStartSimulation(
              teamIdUuid,
              mavenProject.getGroupId(),
              mavenProject.getArtifactId(),
              simulationClass(),
              packageIdUuid,
              simulationSystemProperties,
              file);
    } catch (SeveralTeamsFoundException e) {
      final String availableTeams =
          e.getAvailableTeams().stream()
              .map(t -> String.format("- %s (%s)\n", t.id, t.name))
              .collect(Collectors.joining());
      final String teamExample = e.getAvailableTeams().get(0).id.toString();
      final String msg =
          "Several teams were found to create a simulation.\n"
              + "Available teams:\n"
              + availableTeams
              + CommonLogMessage.missingConfiguration(
                  "team", "teamId", "gatling.enterprise.teamId", null, teamExample);
      throw new MojoFailureException(msg);
    }

    logSimulationCreatedOrChosen(result);
    return result.runSummary;
  }

  private RunSummary interactiveCreateAndStartSimulation(
      File file, UUID teamIdUuid, UUID packageIdUuid)
      throws EnterprisePluginException, MojoFailureException {
    final InteractiveEnterprisePlugin interactiveEnterprisePlugin =
        initInteractiveEnterprisePlugin();

    final SimulationStartResult result;
    try {
      result =
          interactiveEnterprisePlugin.createOrStartSimulation(
              teamIdUuid,
              mavenProject.getGroupId(),
              mavenProject.getArtifactId(),
              simulationClass,
              allSimulationClasses(),
              packageIdUuid,
              simulationSystemProperties,
              file);
    } catch (EmptyChoicesException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }

    logSimulationCreatedOrChosen(result);
    return result.runSummary;
  }

  private void logSimulationCreatedOrChosen(SimulationStartResult result) {
    if (result.createdSimulation) {
      getLog().info(CommonLogMessage.simulationCreated(result.simulation));
    } else {
      getLog().info(CommonLogMessage.simulationChosen(result.simulation));
    }
    getLog().info(CommonLogMessage.simulationStartSample(result.simulation));
  }

  private String simulationClass() throws MojoFailureException {
    // Solves the simulations, if no simulation file is defined
    if (simulationClass != null) {
      return simulationClass;
    } else {
      // excludes patterns are used to exclude classes from the enterprise JAR packaging, so
      // excluded classes cannot be selected here either
      final List<String> simulations = allSimulationClasses();
      if (simulations.size() == 1) {
        return simulations.get(0);
      } else if (simulations.isEmpty()) {
        throw new MojoFailureException(
            "No simulation class discovered. Your project should contain at least one simulation (https://gatling.io/docs/gatling/reference/current/core/simulation/).");
      } else {
        final String availableSimulations =
            simulations.stream().map(s -> "- " + s + "\n").collect(Collectors.joining());
        final String simulationExample = simulations.get(0);
        final String msg =
            "Several simulation classes were found.\n"
                + "Available simulations:\n"
                + availableSimulations
                + CommonLogMessage.missingConfiguration(
                    "simulation",
                    "simulationClass",
                    "gatling.simulationClass",
                    null,
                    simulationExample);
        throw new MojoFailureException(msg);
      }
    }
  }

  private List<String> allSimulationClasses() {
    return SimulationClassUtils.resolveSimulations(
        mavenProject, compiledClassesFolder, null, excludes);
  }
}
