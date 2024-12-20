
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
import io.gatling.shared.cli.RecorderCliOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.model.Resource;
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

  /** The package of the generated class. */
  @Parameter(
      property = "gatling.recorder.package",
      alias = "pkg",
      defaultValue = "${project.groupId}")
  private String packageName;

  /** The name of the generated class. */
  @Parameter(property = "gatling.recorder.className", alias = "cn")
  private String className;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    List<Path> testResourcesDirectories =
        mavenProject.getTestResources().stream()
            .map(Resource::getDirectory)
            .map(Path::of)
            .filter(Files::isDirectory)
            .collect(Collectors.toList());

    Path testResourcesDirectory;
    if (testResourcesDirectories.isEmpty()) {
      throw new MojoExecutionException("Missing test resources directory");
    } else {
      testResourcesDirectory = testResourcesDirectories.get(0);
    }

    List<Path> testSourceDirectories =
        mavenProject.getTestCompileSourceRoots().stream()
            .map(Path::of)
            .filter(Files::isDirectory)
            .collect(Collectors.toList());

    Path scalaTestSourceDir = null;
    Path kotlinTestSourceDir = null;
    Path javaTestSourceDir = null;
    for (Path testSourceDirectory : testSourceDirectories) {
      if (testSourceDirectory.endsWith("scala")) {
        scalaTestSourceDir = testSourceDirectory;
      } else if (testSourceDirectory.endsWith("kotlin")) {
        kotlinTestSourceDir = testSourceDirectory;
      } else if (testSourceDirectory.endsWith("java")) {
        javaTestSourceDir = testSourceDirectory;
      }
    }

    Path simulationsDirectory;
    String format;

    if (scalaTestSourceDir != null) {
      simulationsDirectory = scalaTestSourceDir;
      format = "scala";
    } else if (kotlinTestSourceDir != null) {
      simulationsDirectory = kotlinTestSourceDir;
      format = "kotlin";
    } else if (javaTestSourceDir != null) {
      simulationsDirectory = javaTestSourceDir;
      // let the Recorder pick a default Java format based on the Java version
      format = null;
    } else {
      throw new MojoExecutionException(
          "Unable to locate testCompileSourceRoot for neither Scala nor Kotlin nor Java");
    }

    try {
      List<String> testClasspath = buildTestClasspath();
      List<String> recorderArgs =
          recorderArgs(simulationsDirectory, format, testResourcesDirectory);
      Toolchain toolchain = toolchainManager.getToolchainFromBuildContext("jdk", session);
      Fork forkedRecorder =
          newFork(
              RECORDER_MAIN_CLASS,
              testClasspath,
              GatlingConstants.DEFAULT_JVM_OPTIONS_BASE,
              recorderArgs,
              toolchain,
              null);
      forkedRecorder.run();
    } catch (MojoExecutionException | MojoFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new MojoExecutionException("Recorder execution failed", e);
    }
  }

  private List<String> recorderArgs(
      Path simulationsDirectory, String format, Path testResourcesDirectory) throws Exception {
    List<String> args =
        new ArrayList<>(
            List.of(
                RecorderCliOptions.SimulationsFolder.shortOption(),
                simulationsDirectory.toFile().getCanonicalPath()));

    if (format != null) {
      // format is option, best suited Java version will be picked
      args.addAll(List.of(RecorderCliOptions.Format.shortOption(), format));
    }
    args.addAll(
        List.of(
            RecorderCliOptions.ResourcesFolder.shortOption(),
            testResourcesDirectory.toFile().getCanonicalPath()));
    args.addAll(List.of(RecorderCliOptions.Package.shortOption(), packageName));
    if (className != null) {
      args.addAll(List.of(RecorderCliOptions.ClassName.shortOption(), className));
    }
    return args;
  }
}
