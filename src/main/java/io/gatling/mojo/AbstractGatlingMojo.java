
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

import io.gatling.plugin.io.PluginLogger;
import io.gatling.plugin.util.Fork;
import io.gatling.plugin.util.MainWithArgsInFile;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;

public abstract class AbstractGatlingMojo extends AbstractMojo {

  private static final String FRONTLINE_MAVEN_PLUGIN_GROUP_ID = "io.gatling.frontline";
  private static final String FRONTLINE_MAVEN_PLUGIN_ARTIFACT_ID = "frontline-maven-plugin";

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

  protected void checkPluginPreConditions() throws MojoFailureException {
    final boolean obsoletePluginFound =
        mavenProject.getPluginArtifacts().stream().anyMatch(AbstractGatlingMojo::isFrontLinePlugin);
    if (obsoletePluginFound) {
      throw new MojoFailureException(
          "Plugin `frontline-maven-plugin` is no longer needed, its functionality is now included in `gatling-maven-plugin`.\n"
              + "Please remove `frontline-maven-plugin` from your pom.xml plugins configuration.\n"
              + "Please use gatling:enterprisePackage instead of configuring it on package phase.\n"
              + "See https://gatling.io/docs/gatling/reference/current/extensions/maven_plugin/ for more information ");
    }
  }

  private static boolean isFrontLinePlugin(Artifact artifact) {
    return artifact.getGroupId().equals(FRONTLINE_MAVEN_PLUGIN_GROUP_ID)
        && artifact.getArtifactId().equals(FRONTLINE_MAVEN_PLUGIN_ARTIFACT_ID);
  }

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
    testClasspathElements.add(MojoUtils.locateJar(MainWithArgsInFile.class));

    return testClasspathElements;
  }

  protected void addArg(List<String> args, String flag, Object value) {
    if (value != null) {
      args.addAll(asList("-" + flag, value.toString()));
    }
  }

  private static String toWindowsShortName(String value) {
    if (MojoUtils.IS_WINDOWS) {
      int programFilesIndex = value.indexOf("Program Files");
      if (programFilesIndex >= 0) {
        // Could be "Program Files" or "Program Files (x86)"
        int firstSeparatorAfterProgramFiles =
            value.indexOf(File.separator, programFilesIndex + "Program Files".length());
        File longNameDir =
            firstSeparatorAfterProgramFiles < 0
                ? new File(value)
                : // C:\\Program Files with
                // trailing separator
                new File(value.substring(0, firstSeparatorAfterProgramFiles)); // chop child
        // Some other sibling dir could be PrograXXX and might shift short name index
        // so we can't be sure "Program Files" is "Progra~1" and "Program Files (x86)"
        // is "Progra~2"
        for (int i = 0; i < 10; i++) {
          File shortNameDir = new File(longNameDir.getParent(), "Progra~" + i);
          if (shortNameDir.equals(longNameDir)) {
            return shortNameDir.toString();
          }
        }
      }
    }

    return value;
  }

  private static String findJavaExecutable(Toolchain toolchain) {
    String fromToolchain = toolchain != null ? toolchain.findTool("java") : null;
    if (fromToolchain != null) {
      return fromToolchain;
    } else {
      String javaHome;
      javaHome = System.getenv("JAVA_HOME");
      if (javaHome == null) {
        javaHome = System.getProperty("java.home");
        if (javaHome == null) {
          throw new IllegalStateException(
              "Couldn't locate java, try setting JAVA_HOME environment variable.");
        }
      }
      return javaHome + File.separator + "bin" + File.separator + "java";
    }
  }

  private static String safe(String value) {
    return value.contains(" ") ? '"' + value + '"' : value;
  }

  private PluginLogger newPluginLogger() {
    return new PluginLogger() {
      @Override
      public void info(String message) {
        getLog().info(message);
      }

      @Override
      public void error(String message) {
        getLog().error(message);
      }
    };
  }

  protected Fork newFork(
      String mainClassName,
      List<String> classpath,
      List<String> jvmArgs,
      List<String> args,
      Toolchain toolchain,
      boolean propagateSystemProperties,
      File workingDirectory) {

    return new Fork(
        mainClassName,
        classpath,
        jvmArgs,
        args,
        safe(toWindowsShortName(findJavaExecutable(toolchain))),
        propagateSystemProperties,
        newPluginLogger(),
        workingDirectory);
  }
}
