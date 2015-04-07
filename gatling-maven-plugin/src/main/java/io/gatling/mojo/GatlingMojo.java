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
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.exec.ExecuteException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
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
import org.apache.maven.repository.RepositorySystem;
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
  public static final String SCALA_VERSION = "2.11.6";
  public static final String COMPILER_MAIN_CLASS = "io.gatling.compiler.ZincCompiler";
  public static final List<String> ZINC_JVM_ARGS = singletonList("-Xss10M");

  // Gatling constants
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

  @Parameter(defaultValue = "${basedir}/src/test/resources", readonly = true)
  private File defaultConfigFolder;

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
   * Maven's repository.
   */
  @Component
  private RepositorySystem repository;

  /**
   * List of list of include patterns to use for scanning. Includes all simulations by default.
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
   * Folder where the compiled classes are written.
   */
  @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
  private File compiledClassesFolder;

  /**
   * Override Gatling's default JVM args, instead of replacing them.
   */
  @Parameter(defaultValue = "false")
  private boolean overrideGatlingJvmArgs;

  /**
   * Override Zinc's default JVM args, instead of replacing them.
   */
  @Parameter(defaultValue = "false")
  private boolean overrideZincJvmArgs;

  @Parameter(defaultValue = "${plugin.artifacts}", readonly = true)
  private List<Artifact> artifacts;

  @Parameter(defaultValue = "${basedir}/target/gatling", readonly = true)
  private File reportsDirectory;

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

    List<String> compilerClasspathElements = new ArrayList<>();
    for (Artifact artifact: artifacts) {
      String groupId = artifact.getGroupId();
      if (!groupId.startsWith("org.codehaus.plexus")
        && !groupId.startsWith("org.apache.maven")
        && !groupId.startsWith("org.sonatype")) {
        compilerClasspathElements.add(artifact.getFile().getAbsolutePath());
      }
    }

    // Add plugin jar to classpath (used by MainWithArgsInFile)
    compilerClasspathElements.add(GatlingMojoUtils.locateJar(GatlingMojo.class));
    return GatlingMojoUtils.toMultiPath(compilerClasspathElements);
  }

  private String buildTestClasspath() throws Exception {
    List<String> testClasspathElements = mavenProject.getTestClasspathElements();
    if (!configFolder.getAbsolutePath().equals(defaultConfigFolder.getAbsolutePath())) {
      // src/test/resources content is already copied into test-classes
      testClasspathElements.add(configFolder.getPath());
    }
    if (!disableCompiler) {
      testClasspathElements.add(getCompilerJar().getPath());
    }
    // Add plugin jar to classpath (used by MainWithArgsInFile)
    testClasspathElements.add(GatlingMojoUtils.locateJar(GatlingMojo.class));
    return GatlingMojoUtils.toMultiPath(testClasspathElements);
  }

  private File getCompilerJar() throws Exception {
    Artifact artifact = repository.createArtifact("org.scala-lang", "scala-compiler", SCALA_VERSION, Artifact.SCOPE_RUNTIME, "jar");

    ArtifactResolutionRequest request = new ArtifactResolutionRequest();
    request.setArtifact(artifact);

    request.setResolveRoot(true).setResolveTransitively(false);
    request.setServers(session.getRequest().getServers());
    request.setMirrors(session.getRequest().getMirrors());
    request.setProxies(session.getRequest().getProxies());
    request.setLocalRepository(session.getLocalRepository());
    request.setRemoteRepositories(session.getRequest().getRemoteRepositories());
    repository.resolve(request);

    return artifact.getFile();
  }

  private List<String> gatlingJvmArgs() {
    List<String> completeGatlingJvmArgs = new ArrayList<>();
    if(jvmArgs != null) {
      completeGatlingJvmArgs.addAll(jvmArgs);
    }
    if (overrideGatlingJvmArgs) {
      completeGatlingJvmArgs.addAll(GATLING_JVM_ARGS);
    }
    return completeGatlingJvmArgs;
  }

  private List<String> zincJvmArgs() {
    List<String> completeZincJvmArgs = new ArrayList<>();
    if(zincJvmArgs != null) {
      completeZincJvmArgs.addAll(zincJvmArgs);
    }
    if (overrideZincJvmArgs) {
      completeZincJvmArgs.addAll(ZINC_JVM_ARGS);
    }
    return completeZincJvmArgs;
  }

  private List<String> simulations() throws MojoFailureException {
    // Solves the simulations, if no simulation file is defined
    if (simulationClass != null) {
      return Collections.singletonList(simulationClass);
    } else {
      List<String> simulations = resolveSimulations();

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
  private List<String> resolveSimulations() {

    try {
      ClassLoader testClassLoader = new URLClassLoader(testClassPathUrls());

      Class<?> simulationClass = testClassLoader.loadClass("io.gatling.core.scenario.Simulation");
      List<String> includes = GatlingMojoUtils.arrayAsListEmptyIfNull(this.includes);
      List<String> excludes = GatlingMojoUtils.arrayAsListEmptyIfNull(this.excludes);

      List<String> simulationsClasses = new ArrayList<>();

      for (String classFile: compiledClassFiles()) {
        String className =pathToClassName(classFile);

        boolean isIncluded = includes.isEmpty() || includes.contains(className);
        boolean isExcluded =  excludes.contains(className);

        if (isIncluded && !isExcluded) {
          // check if the class is a concrete Simulation
          Class<?> clazz = testClassLoader.loadClass(className);
          if (simulationClass.isAssignableFrom(clazz) && isConcreteClass(clazz)) {
            simulationsClasses.add(className);
          }
        }
      }

      return simulationsClasses;

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private URL[] testClassPathUrls() throws DependencyResolutionRequiredException, MalformedURLException {

    List<String> testClasspathElements = mavenProject.getTestClasspathElements();

    URL[] urls = new URL[testClasspathElements.size()];
    for (int i = 0; i < testClasspathElements.size(); i++) {

      String url = "file:" + testClasspathElements.get(i);
      if (!url.endsWith(".jar")) {
        // directory, has to end with a /
        url += "/";
      }

      urls[i] = new URL(url);
    }

    return urls;
  }

  private String[] compiledClassFiles() {
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(compiledClassesFolder.getAbsolutePath());
    scanner.setIncludes(new String[]{"**/*.class"});
    scanner.scan();
    return scanner.getIncludedFiles();
  }

  private String pathToClassName(String path) {
    return path.substring(0, path.length() - ".class".length()).replace(File.separatorChar, '.');
  }

  private boolean isConcreteClass(Class<?> clazz) {
    return !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
  }
}
