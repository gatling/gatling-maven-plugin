
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

import java.io.File;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

public class EnterpriseUtil {

  private static final String FRONTLINE_MAVEN_PLUGIN_GROUP_ID = "io.gatling.frontline";
  private static final String FRONTLINE_MAVEN_PLUGIN_ARTIFACT_ID = "frontline-maven-plugin";

  protected static void failOnLegacyFrontLinePlugin(MavenProject project)
      throws MojoFailureException {
    final boolean exist =
        project.getPluginArtifacts().stream()
            .anyMatch(
                artifact ->
                    artifact.getGroupId().equals(FRONTLINE_MAVEN_PLUGIN_GROUP_ID)
                        && artifact.getArtifactId().equals(FRONTLINE_MAVEN_PLUGIN_ARTIFACT_ID));
    if (exist) {
      throw new MojoFailureException(
          "Plugin `frontline-maven-plugin` is no longer needed, its functionality is now included in `gatling-maven-plugin`.\n"
              + "Please remove `frontline-maven-plugin` from your pom.xml plugins configuration.\n"
              + "Please use gatling:enterprisePackage instead of configuring it on package phase.\n"
              + "See https://gatling.io/docs/gatling/reference/current/extensions/maven_plugin/ for more information ");
    }
  }

  protected static File shadedArtifactFile(
      MavenProject project, File targetPath, String classifier) {
    String name = project.getArtifactId() + "-" + project.getVersion() + "-" + classifier + ".jar";
    return new File(targetPath.getAbsolutePath(), name);
  }
}
