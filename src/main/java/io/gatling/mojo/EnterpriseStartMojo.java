
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
import io.gatling.plugin.model.DeploymentInfo;
import io.gatling.plugin.model.RunSummary;
import io.gatling.plugin.model.SimulationEndResult;
import java.util.Map;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Mojo start a deployed simulation on Gatling Enterprise Cloud.
 *
 * <ul>
 *   <li>By default, this goal will prompt you to choose to run a deployed simulation
 *   <li>If a simulation name is passed as property, this goal will automatically start that
 *       simulation.
 *   <li>If Maven is run in batch mode, any interactive prompts will be disabled and the goal will
 *       fail if user input is required (see Maven's batch mode here:
 *       https://maven.apache.org/ref/3-LATEST/maven-embedder/cli.html#batch-mode).
 * </ul>
 */
@Execute(goal = "enterpriseDeploy")
@Mojo(name = "enterpriseStart", requiresDependencyResolution = ResolutionScope.TEST)
public final class EnterpriseStartMojo extends AbstractEnterprisePluginMojo {

  @Parameter(property = "gatling.enterprise.simulationName")
  private String simulationName;

  /**
   * Wait for the result after starting the simulation on Gatling Enterprise, and complete with an
   * error if the simulation ends with any error status.
   */
  @Parameter(property = "gatling.enterprise.waitForRunEnd", defaultValue = "false")
  private boolean waitForRunEnd;

  @Override
  public void execute() throws MojoFailureException {
    final Map context = getPluginContext();
    final DeploymentInfo deploymentInfo =
        (DeploymentInfo) context.get(EnterpriseDeployMojo.CONTEXT_ENTERPRISE_DEPLOY_INFO);
    final EnterprisePlugin plugin = initEnterprisePlugin(requireBatchMode());

    try {
      RunSummary runSummary = plugin.startSimulation(simulationName, deploymentInfo);
      getLog().info(CommonLogMessage.simulationStartSuccess(enterpriseUrl, runSummary.reportsPath));
      waitForRunEnd(plugin, runSummary);
    } catch (EnterprisePluginException e) {
      throw new MojoFailureException(
          "Unhandled Gatling Enterprise plugin exception: " + e.getMessage(), e);
    }
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
