
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
import java.io.File;
import java.util.UUID;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Execute(goal = "enterprisePackage")
@Mojo(name = "enterpriseUpload")
public class EnterpriseUploadMojo extends AbstractEnterpriseApiMojo {

  @Parameter(readonly = true)
  private String packageId;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkPluginPreConditions();

    if (packageId == null) {
      throw new MojoFailureException("Packaged ID is not configured");
    }

    final File file = shadedArtifactFile();
    final EnterpriseClient enterpriseClient = initEnterpriseClient();

    try {
      enterpriseClient.uploadPackage(UUID.fromString(packageId), file);
      getLog().info("Package successfully uploaded");
    } catch (EnterpriseClientException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }
}
