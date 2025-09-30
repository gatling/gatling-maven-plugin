
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

import io.gatling.plugin.BatchEnterprisePlugin;
import io.gatling.plugin.ConfigurationConstants;
import io.gatling.plugin.deployment.DeploymentConfiguration;
import io.gatling.plugin.exceptions.EnterprisePluginException;
import io.gatling.plugin.model.DeploymentInfo;
import java.io.File;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Execute(goal = "enterprisePackage")
@Mojo(name = "enterpriseDeploy", requiresDependencyResolution = ResolutionScope.TEST)
public final class EnterpriseDeployMojo extends AbstractEnterprisePluginMojo {
  public static final String CONTEXT_ENTERPRISE_DEPLOY_INFO = "enterprise_deploy_info";

  @Parameter(property = ConfigurationConstants.DeployOptions.ValidateSimulationId.SYS_PROP)
  private String validateSimulationId;

  @Parameter(property = ConfigurationConstants.DeployOptions.PackageDescriptorFilename.SYS_PROP)
  private String customPackageFilename;

  @Override
  public void execute() throws MojoFailureException {
    final File packageFile = enterprisePackage();
    final File deploymentFile = getDeploymentFile();
    final Boolean isPrivateRepositoryEnabled = controlPlaneUrl != null;
    final BatchEnterprisePlugin plugin = initBatchEnterprisePlugin();
    try {
      DeploymentInfo deploymentInfo =
          (validateSimulationId == null)
              ? plugin.deployFromDescriptor(
                  deploymentFile,
                  packageFile,
                  mavenProject.getArtifactId(),
                  isPrivateRepositoryEnabled)
              : plugin.deployFromDescriptor(
                  deploymentFile,
                  packageFile,
                  mavenProject.getArtifactId(),
                  isPrivateRepositoryEnabled,
                  validateSimulationId);

      getPluginContext().put(CONTEXT_ENTERPRISE_DEPLOY_INFO, deploymentInfo);
    } catch (EnterprisePluginException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }

  private File getDeploymentFile() {
    File baseDir = mavenProject.getBasedir();
    if (customPackageFilename == null) {
      return DeploymentConfiguration.fromBaseDirectory(baseDir, null);
    } else {
      return baseDir.toPath().resolve(customPackageFilename).toFile();
    }
  }
}
