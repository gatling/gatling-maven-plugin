
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
import io.gatling.plugin.util.OkHttpEnterpriseClient;
import io.gatling.plugin.util.exceptions.UnsupportedClientException;
import java.net.URL;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractEnterpriseApiMojo extends AbstractEnterpriseMojo {

  @Parameter(defaultValue = "https://cloud.gatling.io", readonly = true)
  protected URL enterpriseUrl;

  @Parameter(
      defaultValue = "${env.GATLING_ENTERPRISE_API_TOKEN}",
      property = "gatling.enterprise.apiToken",
      readonly = true)
  protected String apiToken;

  protected EnterpriseClient initEnterpriseClient() throws MojoFailureException {
    if (apiToken == null) {
      throw new MojoFailureException(
          "API token is neither configured on the plugin's configuration nor available as the GATLING_ENTERPRISE_API_TOKEN environment variable");
    }
    final String pluginTitle = getClass().getPackage().getImplementationTitle();
    final String pluginVersion = getClass().getPackage().getImplementationVersion();
    if (pluginTitle == null || pluginVersion == null) {
      // Should no happen if the plugin is built and packaged properly
      throw new IllegalStateException("Gatling plugin title and version not found");
    }

    try {
      final URL apiUrl = new URL(enterpriseUrl, "api/public");
      final EnterpriseClient enterpriseClient = new OkHttpEnterpriseClient(apiUrl, apiToken);

      enterpriseClient.checkVersionSupport(pluginTitle, pluginVersion);

      return enterpriseClient;
    } catch (UnsupportedClientException e) {
      throw new MojoFailureException(
          "Please update the Gatling Maven plugin to the latest version for compatibility with Gatling Enterprise. See https://gatling.io/docs/gatling/reference/current/extensions/maven_plugin/ for more information about this plugin.",
          e);
    } catch (Exception e) {
      throw new MojoFailureException(e.getMessage(), e);
    }
  }
}