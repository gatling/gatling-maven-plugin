
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

import io.gatling.plugin.*;
import io.gatling.plugin.exceptions.EnterprisePluginException;
import io.gatling.plugin.exceptions.UnsupportedClientException;
import io.gatling.plugin.io.JavaPluginScanner;
import io.gatling.plugin.io.PluginIO;
import io.gatling.plugin.io.PluginLogger;
import io.gatling.plugin.io.PluginScanner;
import io.gatling.plugin.model.BuildTool;
import java.net.URL;
import java.util.Scanner;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractEnterprisePluginMojo extends AbstractEnterpriseMojo {

  @Parameter(defaultValue = "https://cloud.gatling.io", readonly = true)
  protected URL enterpriseUrl;

  /**
   * The API token used to connect to Gatling Enterprise (see
   * https://gatling.io/docs/enterprise/cloud/reference/admin/api_tokens/).
   *
   * <p>Note: the API token is an authentication secret and should generally not be committed to
   * your code repository. You can instead provide the API token in an environment variable named
   * GATLING_ENTERPRISE_API_TOKEN.
   */
  @Parameter(
      defaultValue = "${env.GATLING_ENTERPRISE_API_TOKEN}",
      property = "gatling.enterprise.apiToken")
  protected String apiToken;

  @Parameter(
      defaultValue = "${env.GATLING_CONTROL_PLANE_URL}",
      property = "gatling.enterprise.controlPlaneUrl")
  protected URL controlPlaneUrl;

  private final PluginLogger pluginLogger =
      new PluginLogger() {
        @Override
        public void info(String message) {
          getLog().info(message);
        }

        @Override
        public void error(String message) {
          getLog().error(message);
        }
      };

  private final Scanner scanner = new Scanner(System.in);
  private final PluginScanner pluginScanner = new JavaPluginScanner(scanner);

  private final PluginIO pluginIO =
      new PluginIO() {
        @Override
        public PluginLogger getLogger() {
          return pluginLogger;
        }

        @Override
        public PluginScanner getScanner() {
          return pluginScanner;
        }
      };

  private PluginConfiguration pluginConfiguration(Boolean forceBatchMode)
      throws MojoFailureException {
    if (apiToken == null) {
      final String msg =
          "Missing API token\n"
              + "An API token is required to call the Gatling Enterprise server; see https://gatling.io/docs/enterprise/cloud/reference/admin/api_tokens/ and create a token with the role 'Configure'.\n"
              + CommonLogMessage.missingConfiguration(
                  "API token",
                  "apiToken",
                  "gatling.enterprise.apiToken",
                  "GATLING_ENTERPRISE_API_TOKEN",
                  "MY_API_TOKEN_VALUE");
      throw new MojoFailureException(msg);
    }
    return new PluginConfiguration(
        enterpriseUrl,
        apiToken,
        controlPlaneUrl,
        BuildTool.MAVEN,
        pluginVersion(),
        forceBatchMode,
        pluginIO);
  }

  protected BatchEnterprisePlugin initBatchEnterprisePlugin() throws MojoFailureException {
    try {
      return EnterprisePluginProvider.getBatchInstance(pluginConfiguration(true));
    } catch (UnsupportedClientException e) {
      throw new UnsupportedClientMojoException(e);
    } catch (EnterprisePluginException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }

  protected EnterprisePlugin initEnterprisePlugin(Boolean forceBatchMode)
      throws MojoFailureException {
    try {
      return EnterprisePluginProvider.getInstance(pluginConfiguration(forceBatchMode));
    } catch (UnsupportedClientException e) {
      throw new UnsupportedClientMojoException(e);
    } catch (EnterprisePluginException e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }
}
