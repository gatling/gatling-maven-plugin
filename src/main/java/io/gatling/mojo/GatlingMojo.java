
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

import static io.gatling.mojo.MojoConstants.*;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import io.gatling.plugin.GatlingConstants;
import io.gatling.plugin.SimulationSelector;
import io.gatling.plugin.model.BuildPlugin;
import io.gatling.plugin.util.Fork;
import io.gatling.shared.cli.GatlingCliOptions;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.util.ExceptionUtils;

/** Mojo to execute Gatling. */
@Execute(phase = LifecyclePhase.TEST_COMPILE)
@Mojo(
    name = "test",
    defaultPhase = LifecyclePhase.INTEGRATION_TEST,
    requiresDependencyResolution = ResolutionScope.TEST)
public final class GatlingMojo extends AbstractGatlingExecutionMojo {

  /** A name of a Simulation class to run. */
  @Parameter(property = "gatling.simulationClass")
  private String simulationClass;

  /**
   * Iterate over multiple simulations if more than one simulation file is found. false by default.
   * If multiple simulations are found but {@literal runMultipleSimulations} is false the execution
   * will fail.
   */
  @Parameter(property = "gatling.runMultipleSimulations", defaultValue = "false")
  private boolean runMultipleSimulations;

  /** List of include patterns to use for scanning. Includes all simulations by default. */
  @Parameter(property = "gatling.includes")
  private List<String> includes;

  /** List of exclude patterns to use for scanning. Excludes none by default. */
  @Parameter(property = "gatling.excludes")
  private List<String> excludes;

  /** Run simulation but does not generate reports. false by default. */
  @Parameter(property = "gatling.noReports", defaultValue = "false")
  private boolean noReports;

  /** Generate the reports for the simulation in this folder. */
  @Parameter(property = "gatling.reportsOnly")
  private String reportsOnly;

  /** A short description of the run to include in the report. */
  @Parameter(property = "gatling.runDescription")
  private String runDescription;

  /**
   * Will cause the project build to look successful, rather than fail, even if there are Gatling
   * test failures. This can be useful on a continuous integration server, if your only option to be
   * able to collect output files, is if the project builds successfully.
   */
  @Parameter(property = "gatling.failOnError", defaultValue = "true")
  private boolean failOnError;

  /**
   * Continue execution of simulations despite assertion failure. If you have some stack of
   * simulations and you want to get results from all simulations despite some assertion failures in
   * previous one.
   */
  @Parameter(property = "gatling.continueOnAssertionFailure", defaultValue = "false")
  private boolean continueOnAssertionFailure;

  @Parameter(property = "gatling.useOldJenkinsJUnitSupport", defaultValue = "false")
  private boolean useOldJenkinsJUnitSupport;

  /** Extra JVM arguments to pass when running Gatling. */
  @Parameter(property = "gatling.jvmArgs")
  private List<String> jvmArgs;

  /** Override Gatling's default JVM args, instead of replacing them. */
  @Parameter(property = "gatling.overrideJvmArgs", defaultValue = "false")
  private boolean overrideJvmArgs;

  /** Propagate System properties to forked processes. */
  @Parameter(property = "gatling.propagateSystemProperties", defaultValue = "true")
  private boolean propagateSystemProperties;

  @Parameter(defaultValue = "${plugin.artifacts}", readonly = true)
  private List<Artifact> artifacts;

  /** Specify a different working directory. */
  @Parameter(property = "gatling.workingDirectory")
  private File workingDirectory;

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  /** Executes Gatling simulations. */
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping gatling-maven-plugin");
      return;
    }

    // Create results directories
    if (!resultsFolder.exists() && !resultsFolder.mkdirs()) {
      throw new MojoExecutionException(
          "Could not create resultsFolder " + resultsFolder.getAbsolutePath());
    }
    Set<File> existingRunDirectories = runDirectories();
    Exception ex = null;

    try {
      List<String> testClasspath = buildTestClasspath();

      Toolchain toolchain = toolchainManager.getToolchainFromBuildContext("jdk", session);

      List<String> jvmArgs = gatlingJvmArgs();

      if (reportsOnly != null) {
        executeGatling(jvmArgs, gatlingArgs(null), testClasspath, toolchain);

      } else {
        List<String> simulations = simulations();
        iterateBySimulations(toolchain, jvmArgs, testClasspath, simulations);
      }

    } catch (Exception e) {
      if (failOnError) {
        if (e instanceof GatlingSimulationAssertionsFailedException) {
          throw new MojoFailureException(e.getMessage(), e);
        } else if (e instanceof MojoFailureException) {
          throw (MojoFailureException) e;
        } else if (e instanceof MojoExecutionException) {
          throw (MojoExecutionException) e;
        } else {
          throw new MojoExecutionException("Gatling failed.", e);
        }
      } else {
        getLog()
            .warn(
                "There were some errors while running your simulation, but failOnError was set to false won't fail your build.");
      }
      ex = e instanceof GatlingSimulationAssertionsFailedException ? null : e;
    } finally {
      try {
        saveSimulationResultToFile(existingRunDirectories, ex);
        copyJUnitReports();
      } catch (IOException e) {
        throw new MojoExecutionException("Could not record simulation results.", e);
      }
    }
  }

  private Set<File> runDirectories() {
    File[] directories = resultsFolder.listFiles(File::isDirectory);
    return directories == null ? Set.of() : Set.of(directories);
  }

  private void iterateBySimulations(
      Toolchain toolchain,
      List<String> jvmArgs,
      List<String> testClasspath,
      List<String> simulations)
      throws Exception {
    Exception exc = null;
    int simulationsCount = simulations.size();
    for (int i = 0; i < simulationsCount; i++) {
      try {
        String selectedSimulation = simulations.get(i);

        List<String> gatlingArgs = gatlingArgs(selectedSimulation);
        getLog().info("Running simulation " + selectedSimulation + ".");
        executeGatling(jvmArgs, gatlingArgs, testClasspath, toolchain);
      } catch (GatlingSimulationAssertionsFailedException e) {
        if (exc == null && i == simulationsCount - 1) {
          throw e;
        }

        if (continueOnAssertionFailure) {
          if (exc != null) {
            continue;
          }
          exc = e;
          continue;
        }
        throw e;
      }
    }

    if (exc != null) {
      getLog()
          .warn(
              "There were some errors while running your simulation, but continueOnAssertionFailure was set to true, so your simulations continue to perform.");
      throw exc;
    }
  }

  private void executeGatling(
      List<String> gatlingJvmArgs,
      List<String> gatlingArgs,
      List<String> testClasspath,
      Toolchain toolchain)
      throws Exception {
    Fork forkedGatling =
        newFork(
            GATLING_MAIN_CLASS,
            testClasspath,
            gatlingJvmArgs,
            gatlingArgs,
            toolchain,
            propagateSystemProperties,
            workingDirectory);
    try {
      forkedGatling.run();
    } catch (Fork.ForkException e) {
      if (e.exitValue == 2) throw new GatlingSimulationAssertionsFailedException(e);
      else throw e; /* issue 1482 */
    }
  }

  private void saveSimulationResultToFile(Set<File> existingRunDirectories, Exception exception)
      throws IOException {
    Path resultsFile = resultsFolder.toPath().resolve(LAST_RUN_FILE);

    try (BufferedWriter writer = Files.newBufferedWriter(resultsFile)) {
      for (File directory : runDirectories()) {
        if (!existingRunDirectories.contains(directory)) {
          writer.write(directory.getName() + System.lineSeparator());
        }
      }
      if (exception != null) {
        writer.write(
            LAST_RUN_FILE_ERROR_LINE + getRecursiveCauses(exception) + System.lineSeparator());
      }
    }
  }

  private static String getRecursiveCauses(Throwable e) {
    return Arrays.stream(ExceptionUtils.getThrowables(e))
        .map(
            ex -> {
              String exceptionClassName = ex.getClass().getName();
              String exceptionMessage = ex.getMessage();
              return exceptionMessage != null
                  ? exceptionClassName + ": " + exceptionMessage
                  : exceptionClassName;
            })
        .collect(Collectors.joining(" | "));
  }

  private void copyJUnitReports() throws MojoExecutionException {

    try {
      if (useOldJenkinsJUnitSupport) {
        for (File directory : runDirectories()) {
          File jsDir = new File(directory, "js");
          if (jsDir.exists() && jsDir.isDirectory()) {
            File assertionFile = new File(jsDir, "assertions.xml");
            if (assertionFile.exists()) {
              File newAssertionFile =
                  new File(resultsFolder, "assertions-" + directory.getName() + ".xml");
              Files.copy(
                  assertionFile.toPath(),
                  newAssertionFile.toPath(),
                  COPY_ATTRIBUTES,
                  REPLACE_EXISTING);
              getLog()
                  .info(
                      "Copying assertion file "
                          + assertionFile.getCanonicalPath()
                          + " to "
                          + newAssertionFile.getCanonicalPath());
            }
          }
        }
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy JUnit reports", e);
    }
  }

  private List<String> gatlingJvmArgs() {
    if (jvmArgs.isEmpty()) {
      return GatlingConstants.DEFAULT_JVM_OPTIONS_GATLING;
    }
    if (overrideJvmArgs) {
      List<String> merged = new ArrayList<>(jvmArgs);
      merged.addAll(GatlingConstants.DEFAULT_JVM_OPTIONS_GATLING);
      return merged;
    }
    return Collections.unmodifiableList(jvmArgs);
  }

  private List<String> simulations() throws MojoFailureException {
    List<String> testClasspath;
    try {
      testClasspath = mavenProject.getTestClasspathElements();
    } catch (DependencyResolutionRequiredException e) {
      throw new MojoFailureException("Failed to build test classpath", e);
    }

    SimulationSelector.Result result =
        SimulationSelector.simulations(
            simulationClass,
            testClasspath,
            includes,
            excludes,
            runMultipleSimulations,
            BuildPlugin.getInstance(buildTool, pluginVersion(), requireBatchMode()).interactive);

    SimulationSelector.Result.Error error = result.error;

    if (error != null) {
      // switch on null is only introduced in Java 18
      String errorMessage;

      switch (error) {
        case NoSimulations:
          errorMessage = "No simulations to run";
          break;
        case MoreThanOneSimulationInNonInteractiveMode:
          errorMessage =
              "Running in non-interactive mode, yet more than 1 simulation is available. Either specify one with -Dgatling.simulationClass=<className> or run them all sequentially with -Dgatling.runMultipleSimulations=true.";
          break;
        case TooManyInteractiveAttempts:
          errorMessage = "Max attempts of reading simulation number reached. Aborting.";
          break;
        default:
          throw new MojoFailureException("Unknown error: " + error);
      }

      getLog().error(errorMessage);
      throw new MojoFailureException(errorMessage);
    }

    return result.simulations;
  }

  private List<String> gatlingArgs(String simulationClass) throws Exception {
    List<String> args = new ArrayList<>();
    if (simulationClass != null) {
      args.addAll(List.of(GatlingCliOptions.Simulation.shortOption(), simulationClass));
    }
    args.addAll(
        List.of(GatlingCliOptions.ResultsFolder.shortOption(), resultsFolder.getCanonicalPath()));
    if (reportsOnly != null) {
      args.addAll(List.of(GatlingCliOptions.ReportsOnly.shortOption(), reportsOnly));
    }
    if (runDescription != null) {
      // encode runDescription in Base64 because it could contain characters that would break the
      // command
      String encodedRunDescription =
          Base64.getEncoder().encodeToString(runDescription.getBytes(StandardCharsets.UTF_8));
      args.addAll(List.of(GatlingCliOptions.RunDescription.shortOption(), encodedRunDescription));
    }
    if (noReports) {
      args.add(GatlingCliOptions.NoReports.shortOption());
    }

    String[] gatlingVersion =
        MojoUtils.findByGroupIdAndArtifactId(
                mavenProject.getArtifacts(), GATLING_GROUP_ID, GATLING_MODULE_APP)
            .getVersion()
            .split("\\.");
    int gatlingMajorVersion = Integer.parseInt(gatlingVersion[0]);
    int gatlingMinorVersion = Integer.parseInt(gatlingVersion[1]);

    if ((gatlingMajorVersion == 3 && gatlingMinorVersion >= 8) || gatlingMajorVersion > 4) {
      args.addAll(List.of(GatlingCliOptions.Launcher.shortOption(), "maven"));
      args.addAll(
          List.of(
              GatlingCliOptions.BuildToolVersion.shortOption(),
              MavenProject.class.getPackage().getImplementationVersion()));
    }

    return args;
  }
}
