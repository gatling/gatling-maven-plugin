
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

import static io.gatling.mojo.MojoConstants.RECORDER_MAIN_CLASS;

import io.gatling.plugin.GatlingConstants;
import io.gatling.plugin.util.Fork;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.toolchain.Toolchain;

/** Mojo to run Gatling Recorder. */
@Mojo(
    name = "recorder",
    defaultPhase = LifecyclePhase.INTEGRATION_TEST,
    requiresDependencyResolution = ResolutionScope.TEST)
public final class RecorderMojo extends AbstractGatlingMojo {

  /** Local port used by Gatling Proxy for HTTP. */
  @Parameter(property = "gatling.recorder.localPort", alias = "lp")
  private Integer localPort;

  /** Outgoing proxy host. */
  @Parameter(property = "gatling.recorder.proxyHost", alias = "ph")
  private String proxyHost;

  /** Outgoing proxy port for HTTP. */
  @Parameter(property = "gatling.recorder.proxyPort", alias = "pp")
  private Integer proxyPort;

  /** Outgoing proxy port for HTTPS. */
  @Parameter(property = "gatling.recorder.proxySslPort", alias = "pps")
  private Integer proxySSLPort;

  /** Uses as the folder where generated simulations will be stored. */
  @Parameter(property = "gatling.recorder.simulationsFolder", alias = "sf")
  private File simulationsFolder;

  /** Use this folder as the folder where feeders are stored. */
  @Parameter(
      property = "gatling.recorder.resourcesFolder",
      alias = "rsf",
      defaultValue = "${project.basedir}/src/test/resources")
  private File resourcesFolder;

  /** The name of the generated class. */
  @Parameter(property = "gatling.recorder.className", alias = "cn")
  private String className;

  /** The package of the generated class. */
  @Parameter(
      property = "gatling.recorder.package",
      alias = "pkg",
      defaultValue = "${project.groupId}")
  private String packageName;

  /** The encoding used in the recorder. */
  @Parameter(property = "gatling.recorder.encoding", alias = "enc")
  private String encoding;

  /** The value of the "Follow Redirects" option. */
  @Parameter(property = "gatling.recorder.followRedirect", alias = "fr")
  private Boolean followRedirect;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (simulationsFolder == null) {
      // project.testCompileSourceRoots typically contains exactly one element (can be set by
      // build.testSourceDirectory in the POM); but if it is ambiguous we juste take the first one.
      simulationsFolder = new File(mavenProject.getTestCompileSourceRoots().get(0));
    }

    try {
      List<String> testClasspath = buildTestClasspath();
      List<String> recorderArgs = recorderArgs();
      Toolchain toolchain = toolchainManager.getToolchainFromBuildContext("jdk", session);
      Fork forkedRecorder =
          newFork(
              RECORDER_MAIN_CLASS,
              testClasspath,
              GatlingConstants.DEFAULT_JVM_OPTIONS_BASE,
              recorderArgs,
              toolchain,
              true,
              null);
      forkedRecorder.run();
    } catch (MojoExecutionException | MojoFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new MojoExecutionException("Recorder execution failed", e);
    }
  }

  private List<String> recorderArgs() throws Exception {
    List<String> arguments = new ArrayList<>();
    addArg(arguments, "lp", localPort);
    addArg(arguments, "ph", proxyHost);
    addArg(arguments, "pp", proxyPort);
    addArg(arguments, "pps", proxySSLPort);
    addArg(arguments, "sf", simulationsFolder.getCanonicalPath());
    addArg(arguments, "rf", resourcesFolder.getCanonicalPath());
    addArg(arguments, "cn", className);
    addArg(arguments, "pkg", packageName);
    addArg(arguments, "enc", encoding);
    addArg(arguments, "fr", followRedirect);
    return arguments;
  }
}
