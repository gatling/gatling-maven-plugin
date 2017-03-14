/**
 * Copyright 2011-2017 GatlingCorp (http://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.mojo;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.toolchain.Toolchain;

import java.util.ArrayList;
import java.util.List;

import static io.gatling.mojo.MojoConstants.GATLING_JVM_ARGS;
import static io.gatling.mojo.MojoConstants.RECORDER_MAIN_CLASS;

/**
 * Mojo to run Gatling Recorder.
 */
@Mojo(name = "recorder", defaultPhase = LifecyclePhase.INTEGRATION_TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class RecorderMojo extends AbstractGatlingMojo {

  /**
   * Local port used by Gatling Proxy for HTTP.
   */
  @Parameter(property = "gatling.recorder.localPort", alias = "lp")
  private Integer localPort;

  /**
   * Outgoing proxy host.
   */
  @Parameter(property = "gatling.recorder.proxyHost", alias = "ph")
  private String proxyHost;

  /**
   * Outgoing proxy port for HTTP.
   */
  @Parameter(property = "gatling.recorder.proxyPort", alias = "pp")
  private Integer proxyPort;

  /**
   * Outgoing proxy port for HTTPS.
   */
  @Parameter(property = "gatling.recorder.proxySslPort", alias = "pps")
  private Integer proxySSLPort;

  /**
   * Uses as the folder where generated simulations will be stored.
   */
  @Parameter(property = "gatling.recorder.outputFolder", alias = "of", defaultValue = "${project.basedir}/src/test/scala")
  private String outputFolder;

  /**
   * The name of the generated class.
   */
  @Parameter(property = "gatling.recorder.className", alias = "cn")
  private String className;

  /**
   * The package of the generated class.
   */
  @Parameter(property = "gatling.recorder.package", alias = "pkg", defaultValue = "${project.groupId}")
  private String packageName;

  /**
   * The encoding used in the recorder.
   */
  @Parameter(property = "gatling.recorder.encoding", alias = "enc")
  private String encoding;

  /**
   * The value of the "Follow Redirects" option.
   */
  @Parameter(property = "gatling.recorder.followRedirect", alias = "fr")
  private Boolean followRedirect;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      List<String> testClasspath = buildTestClasspath(false);
      List<String> recorderArgs = recorderArgs();
      Toolchain toolchain = toolchainManager.getToolchainFromBuildContext("jdk", session);
      Fork forkedRecorder = new Fork(RECORDER_MAIN_CLASS, testClasspath, GATLING_JVM_ARGS, recorderArgs, toolchain, false, getLog());
      forkedRecorder.run();
    } catch (Exception e) {
      throw new MojoExecutionException("Recorder execution failed", e);
    }
  }

  private List<String> recorderArgs() {
    List<String> arguments = new ArrayList<>();
    addToArgsIfNotNull(arguments, outputFolder, "of");
    addToArgsIfNotNull(arguments, bodiesFolder, "bdf");
    addToArgsIfNotNull(arguments, localPort, "lp");
    addToArgsIfNotNull(arguments, proxyHost, "ph");
    addToArgsIfNotNull(arguments, proxyPort, "pp");
    addToArgsIfNotNull(arguments, proxySSLPort, "pps");
    addToArgsIfNotNull(arguments, className, "cn");
    addToArgsIfNotNull(arguments, packageName, "pkg");
    addToArgsIfNotNull(arguments, encoding, "enc");
    addToArgsIfNotNull(arguments, followRedirect, "fr");
    return arguments;
  }
}
