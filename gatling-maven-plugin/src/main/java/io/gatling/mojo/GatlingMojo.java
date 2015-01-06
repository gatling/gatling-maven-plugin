/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.mojo;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.exec.ExecuteException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.DirectoryScanner;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Mojo to execute Gatling.
 */
@Mojo(name = "execute",
  defaultPhase = LifecyclePhase.INTEGRATION_TEST,
  requiresDependencyResolution = ResolutionScope.TEST)
public class GatlingMojo extends AbstractMojo {

  // Compiler constants
  public static final String SCALA_VERSION = "2.11.4";
  public static final String COMPILER_MAIN_CLASS = "io.gatling.compiler.ZincCompiler";
  public static final List<String> ZINC_JVM_ARGS = singletonList("-Xss10M");

  // Gatling constants
  public static final String[] SCALA_INCLUDES = {"**/*.scala"};
  public static final String GATLING_MAIN_CLASS = "io.gatling.app.Gatling";
  public static final List<String> GATLING_JVM_ARGS = asList(
    "-server", "-XX:+UseThreadPriorities", "-XX:ThreadPriorityPolicy=42", "-Xms512M",
    "-Xmx512M", "-Xmn100M", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:+AggressiveOpts",
    "-XX:+OptimizeStringConcat", "-XX:+UseFastAccessorMethods", "-XX:+UseParNewGC",
    "-XX:+UseConcMarkSweepGC", "-XX:+CMSParallelRemarkEnabled");

  /**
   * Run simulation but does not generate reports. By default false.
   */
  @Parameter(property = "gatling.noReports", alias = "nr", defaultValue = "false")
  private boolean noReports;

  /**
   * Generate the reports for the simulation in this folder.
   */
  @Parameter(property = "gatling.reportsOnly", alias = "ro")
  private String reportsOnly;

  /**
   * Use this folder as the configuration directory.
   */
  @Parameter(property = "gatling.configFolder", alias = "cd", defaultValue = "${basedir}/src/test/resources")
  private File configFolder;

  /**
   * Use this folder to discover simulations that could be run.
   */
  @Parameter(property = "gatling.simulationsFolder", alias = "sf", defaultValue = "${basedir}/src/test/scala")
  private File simulationsFolder;

  /**
   * A name of a Simulation class to run.
   */
  @Parameter(property = "gatling.simulationClass", alias = "sc")
  private String simulationClass;

  /**
   * Use this folder as the folder where feeders are stored.
   */
  @Parameter(property = "gatling.dataFolder", alias = "df", defaultValue = "${basedir}/src/test/resources/data")
  private File dataFolder;

  /**
   * Use this folder as the folder where request bodies are stored.
   */
  @Parameter(property = "gatling.bodiesFolder", alias = "bdf", defaultValue = "${basedir}/src/test/resources/bodies")
  private File bodiesFolder;

  /**
   * Use this folder as the folder where results are stored.
   */
  @Parameter(property = "gatling.resultsFolder", alias = "rf", defaultValue = "${basedir}/target/gatling/results")
  private File resultsFolder;

  /**
   * Extra JVM arguments to pass when running Gatling.
   */
  @Parameter(property = "gatling.jvmArgs")
  private List<String> jvmArgs;

  /**
   * Extra JVM arguments to pass when running Zinc.
   */
  @Parameter(property = "gatling.zincJvmArgs")
  private List<String> zincJvmArgs;

  /**
   * Will cause the project build to look successful, rather than fail, even
   * if there are Gatling test failures. This can be useful on a continuous
   * integration server, if your only option to be able to collect output
   * files, is if the project builds successfully.
   */
  @Parameter(property = "gatling.failOnError", defaultValue = "true")
  private boolean failOnError;

  /**
   * Force the name of the directory generated for the results of the run.
   */
  @Parameter(property = "gatling.outputName", alias = "on")
  private String outputDirectoryBaseName;

  /**
   * Propagate System properties to forked processes.
   */
  @Parameter(property = "gatling.propagateSystemProperties", defaultValue = "true")
  private boolean propagateSystemProperties;

  /**
   * Disable the plugin.
   */
  @Parameter(property = "gatling.skip", defaultValue = "false")
  private boolean skip;

  /**
   * Disable the Scala compiler, if scala-maven-plugin is already in charge
   * of compiling the simulations.
   */
  @Parameter(property = "gatling.disableCompiler", defaultValue = "false")
  private boolean disableCompiler;

  /**
   * The Maven Project.
   */
  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject mavenProject;

  /**
   * The Maven Session Object.
   */
  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  /**
   * The toolchain manager to use.
   */
  @Component
  private ToolchainManager toolchainManager;

  /**
   * Maven's artifact resolver.
   */
  @Component
  private ArtifactResolver artifactResolver;

  /**
   * Maven's artifact resolver.
   */
  @Component
  private ArtifactFactory artifactFactory;

  /**
   * Location of the local repository.
   */
  @Parameter(defaultValue = "${localRepository}", readonly = true)
  private ArtifactRepository localRepo;

  /**
   * List of Remote Repositories used by the resolver
   */
  @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
  protected List<ArtifactRepository> remoteRepos;

  /**
   * List of list of include patterns to use for scanning. By default &#42;&#42;&#47;&#42;.scala
   */
  @Parameter
  private String[] includes;

  /**
   * List of list of exclude patterns to use for scanning. By default empty.
   */
  @Parameter
  private String[] excludes;

  /**
   * Iterate over multiple simulations if more than one simulation file is found. By default false.
   * If multiple simulations are found but {@literal runMultipleSimulations} is false the execution will fail.
   */
  @Parameter(defaultValue = "false")
  private boolean runMultipleSimulations;

  /**
   * Executes Gatling simulations.
   */
  @Override
  public void execute() throws MojoExecutionException {
    if (!skip) {
      // Create results directories
      resultsFolder.mkdirs();
      try {
        String testClasspath = buildTestClasspath();
        Toolchain toolchain = toolchainManager.getToolchainFromBuildContext("jdk", session);
        if (!disableCompiler) {
          executeCompiler(zincJvmArgs(), testClasspath, toolchain);
        }

        Collection<String> simulations = simulations();
        for (String simulation : simulations) {
          executeGatling(gatlingJvmArgs(), gatlingArgs(simulation), testClasspath, toolchain);
        }
      } catch (Exception e) {
        if (failOnError) {
          throw new MojoExecutionException("Gatling failed.", e);
        } else {
          getLog().warn("There was some errors while running your simulation, but failOnError set to false won't fail your build.");
        }
      }
    } else {
      getLog().info("Skipping gatling-maven-plugin");
    }
  }

  private void executeCompiler(List<String> zincJvmArgs, String testClasspath, Toolchain toolchain) throws Exception {
    String compilerClasspath = buildCompilerClasspath();
    List<String> compilerArguments = compilerArgs(testClasspath);
    Fork forkedCompiler = new Fork(COMPILER_MAIN_CLASS, compilerClasspath, zincJvmArgs, compilerArguments, toolchain, false);
    try {
      forkedCompiler.run();
    } catch (ExecuteException e) {
      throw new CompilationException(e);
    }
  }

  private void executeGatling(List<String> gatlingJvmArgs, List<String> gatlingArgs, String testClasspath, Toolchain toolchain) throws Exception {
    Fork forkedGatling = new Fork(GATLING_MAIN_CLASS, testClasspath, gatlingJvmArgs, gatlingArgs, toolchain, propagateSystemProperties);
    try {
      forkedGatling.run();
    } catch (ExecuteException e) {
      if (e.getExitValue() == 2)
        throw new GatlingSimulationAssertionsFailedException(e);
      else
        throw e; /* issue 1482*/
    }
  }

  private String buildCompilerClasspath() throws Exception {
    URL[] classpathUrls = ((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs();
    List<String> compilerClasspathElements = new ArrayList<String>();
    for (URL url : classpathUrls) {
      compilerClasspathElements.add(new File(url.toURI()).getAbsolutePath());
    }
    // Add plugin jar to classpath (used by MainWithArgsInFile)
    compilerClasspathElements.add(GatlingMojoUtils.locateJar(GatlingMojo.class));
    return GatlingMojoUtils.toMultiPath(compilerClasspathElements);
  }

  private String buildTestClasspath() throws Exception {
    List<String> testClasspathElements = mavenProject.getTestClasspathElements();
    testClasspathElements.add(configFolder.getPath());
    if (!disableCompiler) {
      testClasspathElements.add(getCompilerJar().getPath());
    }
    // Add plugin jar to classpath (used by MainWithArgsInFile)
    testClasspathElements.add(GatlingMojoUtils.locateJar(GatlingMojo.class));
    return GatlingMojoUtils.toMultiPath(testClasspathElements);
  }

  private File getCompilerJar() throws Exception {
    Artifact artifact = artifactFactory.createArtifact("org.scala-lang", "scala-compiler", SCALA_VERSION, Artifact.SCOPE_RUNTIME, "jar");
    artifactResolver.resolve(artifact, remoteRepos, localRepo);
    return artifact.getFile();
  }

  private List<String> gatlingJvmArgs() {
    return jvmArgs != null ? jvmArgs : GATLING_JVM_ARGS;
  }

  private List<String> zincJvmArgs() {
    return zincJvmArgs != null ? zincJvmArgs : ZINC_JVM_ARGS;
  }

  private List<String> simulations() throws MojoFailureException {
    // Solves the simulations, if no simulation file is defined
    if (simulationClass != null) {
      return Collections.singletonList(simulationClass);
    } else {
      List<String> simulations = resolveSimulations(simulationsFolder);

      if (simulations.isEmpty()) {
        getLog().error("No simulations to run");
        throw new MojoFailureException("No simulations to run");
      }

      if (simulations.size() > 1 && !runMultipleSimulations) {
        String message = "More than 1 simulation to run, need to specify one, or enable runMultipleSimulations";
        getLog().error(message);
        throw new MojoFailureException(message);
      }

      return simulations;
    }
  }

  private List<String> gatlingArgs(String simulationClass) throws Exception {
    // Arguments
    List<String> args = new ArrayList<>();
    args.addAll(asList("-df", dataFolder.getCanonicalPath(),
      "-rf", resultsFolder.getCanonicalPath(),
      "-bdf", bodiesFolder.getCanonicalPath(),
      "-sf", simulationsFolder.getCanonicalPath(),
      "-s", simulationClass,
      "-m"));

    if (noReports) {
      args.add("-nr");
    }

    if (reportsOnly != null) {
      args.addAll(asList("-ro", reportsOnly));
    }

    if (outputDirectoryBaseName != null) {
      args.addAll(asList("-on", outputDirectoryBaseName));
    }

    return args;
  }

  private List<String> compilerArgs(String classpathElements) throws Exception {
    List<String> args = new ArrayList<>();
    args.addAll(asList("-ccp", classpathElements));
    args.addAll(asList("-sf", simulationsFolder.getCanonicalPath()));
    return args;
  }

  /**
   * Resolve simulation files to execute from the simulation folder.
   *
   * @return a comma separated String of simulation class names.
   */
  private List<String> resolveSimulations(File simulationsFolder) {
    DirectoryScanner scanner = new DirectoryScanner();

    // Set Base Directory
    getLog().debug("effective simulationsFolder: " + simulationsFolder.getPath());
    scanner.setBasedir(simulationsFolder);

    // Resolve includes
    if (includes == null || includes.length == 0) {
      scanner.setIncludes(SCALA_INCLUDES);
    } else {
      scanner.setIncludes(includes);
    }

    if (excludes != null && excludes.length != 0) {
      scanner.setExcludes(excludes);
    }

    // Resolve simulations to execute
    scanner.scan();

    String[] includedFiles = scanner.getIncludedFiles();

    List<String> includedClassNames = new ArrayList<String>();
    for (String includedFile : includedFiles) {
      includedClassNames.add(GatlingMojoUtils.fileNameToClassName(includedFile));
    }

    getLog().debug("resolved simulation classes: " + includedClassNames);
    return includedClassNames;
  }
}
