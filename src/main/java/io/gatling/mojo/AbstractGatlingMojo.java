
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

import static java.util.Arrays.asList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.toolchain.ToolchainManager;

public abstract class AbstractGatlingMojo extends AbstractMojo {

  /** Use this folder as the configuration directory. */
  @Parameter(
      property = "gatling.configFolder",
      alias = "cd",
      defaultValue = "${project.basedir}/src/test/resources")
  protected File configFolder;

  /** Folder where the compiled classes are written. */
  @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
  protected File compiledClassesFolder;

  /** The Maven Project. */
  @Parameter(defaultValue = "${project}", readonly = true)
  protected MavenProject mavenProject;

  /** The Maven Session Object. */
  @Parameter(defaultValue = "${session}", readonly = true)
  protected MavenSession session;

  /** The toolchain manager to use. */
  @Component protected ToolchainManager toolchainManager;

  /** Maven's repository. */
  @Component protected RepositorySystem repository;

  protected List<String> buildTestClasspath() throws Exception {
    List<String> testClasspathElements = new ArrayList<>();

    if (!new File(compiledClassesFolder, "gatling.conf").exists()) {
      // src/test/resources content is not already copied into test-classes when
      // running gatling:execute
      // it only is when running the test phase
      testClasspathElements.add(configFolder.getCanonicalPath());
    }

    testClasspathElements.addAll(mavenProject.getTestClasspathElements());

    // Add plugin jar to classpath (used by MainWithArgsInFile)
    testClasspathElements.add(MojoUtils.locateJar(GatlingMojo.class));

    return testClasspathElements;
  }

  protected void addArg(List<String> args, String flag, Object value) {
    if (value != null) {
      args.addAll(asList("-" + flag, value.toString()));
    }
  }
}
