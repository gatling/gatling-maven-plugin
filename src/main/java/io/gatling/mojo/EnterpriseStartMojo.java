
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

import io.gatling.plugin.EnterprisePlugin;
import io.gatling.plugin.exceptions.EnterprisePluginException;
import io.gatling.plugin.model.RunSummary;
import io.gatling.plugin.model.SimulationEndResult;
import io.gatling.plugin.model.SimulationStartResult;
import io.gatling.plugin.util.PropertiesParserUtil;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
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
public final class EnterpriseStartMojo extends AbstractEnterprisePluginMojo {

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
   * already be defined for that simulation (see
   * https://gatling.io/docs/enterprise/cloud/reference/user/simulations/#step-3-injector-parameters).
   * To provide system properties on the command line, use the format
   * -Dgatling.enterprise.simulationSystemProperties=key1=value1,key2=value2
   */
  @Parameter private Map<String, String> simulationSystemProperties;

  /**
   * Alternative to simulationSystemProperties. Use the following format: key1=value1,key2=value2
   * This is meant to be used on the command line, rather than in the pom.xml.
   */
  @Parameter(property = "gatling.enterprise.simulationSystemProperties")
  private String simulationSystemPropertiesString;

  /**
   * Provides additional environment variables when starting a simulation, in addition to the ones
   * which may already be defined for that simulation (see
   * https://gatling.io/docs/enterprise/cloud/reference/user/simulations/#step-3-injector-parameters).
   * To provide environment variables on the command line, use the format use
   * -Dgatling.enterprise.simulationEnvironmentVariables=key1=value1,key2=value2
   */
  @Parameter private Map<String, String> simulationEnvironmentVariables;

  /**
   * Alternative to simulationEnvironmentVariables. Use the following format:
   * key1=value1,key2=value2 This is meant to be used on the command line, rather than in the
   * pom.xml.
   */
  @Parameter(property = "gatling.enterprise.simulationEnvironmentVariables")
  private String simulationEnvironmentVariablesString;

  /**
   * Wait for the result after starting the simulation on Gatling Enterprise, and complete with an
   * error if the simulation ends with any error status.
   */
  @Parameter(property = "gatling.enterprise.waitForRunEnd", defaultValue = "false")
  private boolean waitForRunEnd;

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

    final EnterprisePlugin plugin = initEnterprisePlugin(session.getRequest().isInteractiveMode());

    final SimulationStartResult startResult =
        RecoverEnterprisePluginException.handle(
            () ->
                simulationId == null
                    ? createAndStartSimulation(plugin, file, teamIdUuid, packageIdUuid)
                    : startExistingSimulation(plugin, file),
            getLog());

    getLog()
        .info(
            CommonLogMessage.simulationStartSuccess(
                enterpriseUrl, startResult.runSummary.reportsPath));

    if (simulationId == null || !waitForRunEnd) {
      getLog()
          .info(
              CommonLogMessage.simulationConfiguration(
                  startResult.simulation, simulationId, waitForRunEnd));
    }

    waitForRunEnd(plugin, startResult.runSummary);
  }

  private EnterprisePlugin initEnterprisePlugin(boolean isInteractive) throws MojoFailureException {
    return isInteractive ? initInteractiveEnterprisePlugin() : initBatchEnterprisePlugin();
  }

  private SimulationStartResult startExistingSimulation(
      EnterprisePlugin enterprisePlugin, File file) throws EnterprisePluginException {
    getLog().info("Uploading and starting simulation...");
    return enterprisePlugin.uploadPackageAndStartSimulation(
        UUID.fromString(simulationId),
        selectProperties(simulationSystemProperties, simulationSystemPropertiesString),
        selectProperties(simulationEnvironmentVariables, simulationEnvironmentVariablesString),
        simulationClass,
        file);
  }

  private SimulationStartResult createAndStartSimulation(
      EnterprisePlugin enterprisePlugin, File file, UUID teamIdUuid, UUID packageIdUuid)
      throws EnterprisePluginException {
    final SimulationStartResult result =
        enterprisePlugin.createAndStartSimulation(
            teamIdUuid,
            mavenProject.getGroupId(),
            mavenProject.getArtifactId(),
            simulationClass,
            packageIdUuid,
            selectProperties(simulationSystemProperties, simulationSystemPropertiesString),
            selectProperties(simulationEnvironmentVariables, simulationEnvironmentVariablesString),
            file);

    logSimulationCreatedOrChosen(result);
    return result;
  }

  private void logSimulationCreatedOrChosen(SimulationStartResult result) {
    if (result.createdSimulation) {
      getLog().info(CommonLogMessage.simulationCreated(result.simulation));
    } else {
      getLog().info(CommonLogMessage.simulationChosen(result.simulation));
    }
  }

  private Map<String, String> selectProperties(
      Map<String, String> propertiesMap, String propertiesString) {
    return (propertiesMap == null || propertiesMap.isEmpty())
        ? PropertiesParserUtil.parseProperties(propertiesString)
        : propertiesMap;
  }

  private void waitForRunEnd(EnterprisePlugin plugin, RunSummary startedRun)
      throws MojoFailureException {
    if (waitForRunEnd) {
      final SimulationEndResult finishedRun =
          RecoverEnterprisePluginException.handle(() -> plugin.waitForRunEnd(startedRun), getLog());
      if (!finishedRun.status.successful) {
        throw new MojoFailureException("Simulation failed.");
      }
    }
  }
}
