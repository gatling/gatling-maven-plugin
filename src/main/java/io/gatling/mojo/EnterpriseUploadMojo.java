
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
import io.gatling.plugin.ConfigurationConstants;
import java.io.File;
import java.util.UUID;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo to package Gatling simulations and immediately upload them to Gatling Enterprise Cloud. The
 * package must already be configured on Gatling Enterprise (see <a
 * href="https://docs.gatling.io/reference/execute/cloud/user/package-conf/">the reference
 * documentation</a>).
 */
@Execute(goal = "enterprisePackage")
@Mojo(name = "enterpriseUpload")
public final class EnterpriseUploadMojo extends AbstractEnterprisePluginMojo {

  /**
   * The ID of the package configured on Gatling Enterprise where you want to upload your Gatling
   * simulations (see <a
   * href="https://docs.gatling.io/reference/execute/cloud/user/package-conf/">the reference
   * documentation</a>).
   */
  @Parameter(property = ConfigurationConstants.UploadOptions.PackageId.SYS_PROP)
  private String packageId;

  /**
   * Only used if 'packageId' is NOT defined. The ID of a simulation configured on Gatling
   * Enterprise; your Gatling simulations will be uploaded to the package configured for that
   * simulation (see <a
   * href="https://docs.gatling.io/reference/execute/cloud/user/simulations/#step-1-general">the
   * reference documentation</a>).
   */
  @Parameter(property = ConfigurationConstants.UploadOptions.SimulationId.SYS_PROP)
  private String simulationId;

  @Override
  public void execute() throws MojoFailureException {
    if (packageId == null && simulationId == null) {
      final String msg =
          "Missing packageID\n"
              + "You must configure the ID of an existing package in Gatling Enterprise; see https://docs.gatling.io/reference/execute/cloud/user/package-conf/ \n"
              + CommonLogMessage.missingConfiguration(
                  "package ID",
                  "packageId",
                  ConfigurationConstants.UploadOptions.PackageId.SYS_PROP,
                  null,
                  "MY_PACKAGE_ID")
              + "Alternately, if you don't configure a packageId, you can configure the simulationId of an existing simulation on Gatling Enterprise: your code will be uploaded to the package used by that simulation.";
      throw new MojoFailureException(msg);
    }

    final File file = enterprisePackage();
    final BatchEnterprisePlugin enterprisePlugin = initBatchEnterprisePlugin();

    RecoverEnterprisePluginException.handle(
        () ->
            packageId != null
                ? enterprisePlugin.uploadPackage(UUID.fromString(packageId), file)
                : enterprisePlugin.uploadPackageWithSimulationId(
                    UUID.fromString(simulationId), file),
        getLog());
    getLog().info("Package successfully uploaded");
  }
}
