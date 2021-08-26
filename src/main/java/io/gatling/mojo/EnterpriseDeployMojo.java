
/*
 * Copyright 2011-2020 GatlingCorp (https://gatling.io)
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
import io.gatling.plugin.util.EnterpriseClientException;
import io.gatling.plugin.util.OkHttpEnterpriseClient;
import java.io.File;
import java.net.URL;
import java.util.UUID;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Execute(goal = "enterprisePackage")
@Mojo(name = "enterpriseDeploy", defaultPhase = LifecyclePhase.DEPLOY)
public class EnterpriseDeployMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${project.build.directory}", readonly = true)
  private File targetPath;

  @Parameter(defaultValue = "shaded")
  private String shadedClassifier;

  @Parameter(defaultValue = "https://cloud.gatling.io/api/public", readonly = true)
  private URL enterpriseUrl;

  @Parameter(
      defaultValue = "${env.GATLING_ENTERPRISE_API_TOKEN}",
      property = "gatling.enterprise.apiToken",
      readonly = true)
  private String apiToken;

  @Parameter(readonly = true)
  private String packageId;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    if (apiToken == null) {
      throw new MojoFailureException(
          "API token is not configure on plugin, neither available in environment variable");
    }

    if (packageId == null) {
      throw new MojoFailureException("Artifact ID is not configure on plugin");
    }

    final File file = EnterpriseUtil.shadedArtifactFile(project, targetPath, shadedClassifier);
    final EnterpriseClient enterpriseClient = new OkHttpEnterpriseClient(enterpriseUrl, apiToken);

    try {
      enterpriseClient.uploadPackage(UUID.fromString(packageId), file);
      getLog().info("Successfully upload package");
    } catch (EnterpriseClientException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }
}
