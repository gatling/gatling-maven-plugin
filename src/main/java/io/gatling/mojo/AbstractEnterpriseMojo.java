
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

import java.io.File;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractEnterpriseMojo extends AbstractGatlingMojo {

  /**
   * The classifier used for the JAR file when packaging simulations to run on Gatling Enterprise.
   */
  protected static final String SHADED_CLASSIFIER = "shaded";

  @Parameter(defaultValue = "${project.build.directory}", readonly = true)
  protected File targetPath;

  protected File enterprisePackage() {
    String name =
        mavenProject.getArtifactId()
            + "-"
            + mavenProject.getVersion()
            + "-"
            + SHADED_CLASSIFIER
            + ".jar";
    return new File(targetPath.getAbsolutePath(), name);
  }
}
