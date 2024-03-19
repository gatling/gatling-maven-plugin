
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

import io.gatling.plugin.BatchEnterprisePlugin;
import io.gatling.plugin.deployment.DeploymentConfiguration;
import io.gatling.plugin.exceptions.EnterprisePluginException;
import io.gatling.plugin.model.BuildTool;
import java.io.File;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Execute(goal = "enterprisePackage")
@Mojo(name = "enterpriseDeploy", requiresDependencyResolution = ResolutionScope.TEST)
public final class EnterpriseDeployMojo extends AbstractEnterprisePluginMojo {

  @Override
  public void execute() throws MojoFailureException {
    final File packageFile = enterprisePackage();
    final File deploymentFile =
        DeploymentConfiguration.fromBaseDirectory(mavenProject.getBasedir());
    final Boolean isPrivateRepositoryEnabled = controlPlaneUrl != null;
    final BatchEnterprisePlugin plugin = initBatchEnterprisePlugin();
    try {
      plugin.deployFromDescriptor(
          deploymentFile,
          packageFile,
          mavenProject.getArtifactId(),
          isPrivateRepositoryEnabled,
          BuildTool.MAVEN,
          getClass().getPackage().getImplementationVersion());
    } catch (EnterprisePluginException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }
}
