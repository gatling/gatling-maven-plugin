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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
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
import scala_maven_executions.JavaMainCaller;
import scala_maven_executions.MainHelper;
import scala_maven_executions.MainWithArgsInFile;

import static java.util.Arrays.asList;
import static org.codehaus.plexus.util.StringUtils.trim;

/**
 * Mojo to execute Gatling.
 */
@Mojo(name = "execute",
      defaultPhase = LifecyclePhase.INTEGRATION_TEST,
      requiresDependencyResolution = ResolutionScope.TEST)
public class GatlingMojo extends AbstractMojo {

	public static final String SCALA_VERSION = "2.11.2";
	public static final String[] SCALA_INCLUDES = { "**/*.scala" };
	public static final String COMPILER_MAIN_CLASS = "io.gatling.compiler.ZincCompiler";
	public static final String GATLING_MAIN_CLASS = "io.gatling.app.Gatling";

	public static final String[] GATLING_JVM_ARGS = {
			"-server", "-XX:+UseThreadPriorities", "-XX:ThreadPriorityPolicy=42", "-Xms512M",
			"-Xmx512M", "-Xmn100M", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:+AggressiveOpts",
			"-XX:+OptimizeStringConcat", "-XX:+UseFastAccessorMethods", "-XX:+UseParNewGC",
			"-XX:+UseConcMarkSweepGC", "-XX:+CMSParallelRemarkEnabled" };

	public static final String[] ZINC_JVM_ARGS = { "-Xss10M" };

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
	@Parameter(property = "gatling.requestBodiesFolder", alias = "bf", defaultValue = "${basedir}/src/test/resources/request-bodies")
	private File requestBodiesFolder;

	/**
	 * Use this folder as the folder where results are stored.
	 */
	@Parameter(property = "gatling.resultsFolder", alias = "rf", defaultValue = "${basedir}/target/gatling/results")
	private File resultsFolder;

	/**
	 * Extra JVM arguments to pass when running Gatling.
	 */
	@Parameter(property = "gatling.gatlingJvmArgs")
	private List<String> gatlingJvmArgs;

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
	 * Propagate System properties in fork mode to forked process.
	 */
	@Parameter(property = "gatling.propagateSystemProperties", defaultValue = "true")
	private boolean propagateSystemProperties;

	/**
	 * Disable the plugin.
	 */
	@Parameter(property = "gatling.skip", defaultValue = "false")
	private boolean skip;

	/**
	 * The Maven Project.
	 */
	@Component
	private MavenProject mavenProject;

	/**
	 * The Maven Session Object.
	 */
	@Component
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
	@Parameter(defaultValue = "${localRepository}")
	private ArtifactRepository localRepo;

	/**
	 * List of Remote Repositories used by the resolver
	 */
	@Parameter(defaultValue = "${project.remoteArtifactRepositories}")
	protected List<ArtifactRepository> remoteRepos;

	/**
	 * Executes Gatling simulations.
	 */
	@Override
	public void execute() throws MojoExecutionException {
		if (!skip) {
			// Create results directories
			resultsFolder.mkdirs();
			try {
				String[] gatlingJvmArgs = gatlingJvmArgs().toArray(new String[gatlingJvmArgs().size()]);
				String[] gatlingArgs = gatlingArgs().toArray(new String[gatlingArgs().size()]);
				String[] zincJvmArgs = zincJvmArgs().toArray(new String[zincJvmArgs().size()]);
				executeGatling(gatlingJvmArgs, gatlingArgs, zincJvmArgs);
			} catch (Exception e) {
				if (failOnError) {
					throw new MojoExecutionException("Gatling failed.", e);
				} else {
					getLog().warn("There was some errors while running your simulation, but failOnError set to false won't fail your build.");
				}
			}
		} else
			getLog().info("Skipping gatling-maven-plugin");
	}

	private void executeGatling(String[] gatlingJvmArgs, String[] gatlingArgs, String[] zincJvmArgs) throws Exception {
		String testClasspath = buildTestClasspath();
		String compilerClasspath = buildCompilerClasspath();
		String[] compilerArguments = { testClasspath };
		Toolchain toolchain = toolchainManager.getToolchainFromBuildContext("jdk", session);
		JavaMainCaller compilerCaller = new GatlingJavaMainCallerByFork(this, COMPILER_MAIN_CLASS, compilerClasspath, zincJvmArgs, compilerArguments, toolchain, propagateSystemProperties);
		JavaMainCaller gatlingCaller = new GatlingJavaMainCallerByFork(this, GATLING_MAIN_CLASS, testClasspath, gatlingJvmArgs, gatlingArgs, toolchain, propagateSystemProperties);
		try {
			compilerCaller.run(false);
			gatlingCaller.run(false);
		} catch (ExecuteException e) {
			if (e.getExitValue() == 2)
				throw new GatlingSimulationAssertionsFailedException(e);
			else
				throw e; /* issue 1482*/
		}
	}

	private String buildCompilerClasspath() throws Exception {
		URL[] classpathUrls = ((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs();
		List<String> classpathElements = new ArrayList<String>();
		for (URL url : classpathUrls) {
			classpathElements.add(new File(url.toURI()).getAbsolutePath());
		}
		return MainHelper.toMultiPath(classpathElements);
	}

	private String buildTestClasspath() throws Exception {
		List<String> testClasspathElements = mavenProject.getTestClasspathElements();
		testClasspathElements.add(configFolder.getPath());
		testClasspathElements.add(getCompilerJar().getPath());
		// Find plugin jar and add it to classpath
		testClasspathElements.add(MainHelper.locateJar(GatlingMojo.class));
		// Jenkins seems to need scala-maven-plugin in the test classpath in
		// order to work
		testClasspathElements.add(MainHelper.locateJar(MainWithArgsInFile.class));
		return MainHelper.toMultiPath(testClasspathElements);
	}

	private File getCompilerJar() throws Exception {
		Artifact artifact = artifactFactory.createArtifact("org.scala-lang", "scala-compiler", SCALA_VERSION, Artifact.SCOPE_RUNTIME, "jar");
		artifactResolver.resolve(artifact, remoteRepos, localRepo);
		return artifact.getFile();
	}

	private List<String> gatlingJvmArgs() {
		return gatlingJvmArgs != null ? gatlingJvmArgs : asList(GATLING_JVM_ARGS);
	}

	private List<String> zincJvmArgs() {
		List<String> args = zincJvmArgs != null ? zincJvmArgs : asList(ZINC_JVM_ARGS);
		args.add("-Dgatling.core.directory.simulations=" + simulationsFolder);
		return args;
	}

	private List<String> gatlingArgs() throws Exception {
		// Solves the simulations, if no simulation file is defined
		if (simulationClass == null) {
			List<String> simulations = resolveSimulations(simulationsFolder);

			if (simulations.isEmpty()) {
				getLog().error("No simulations to run");
				throw new MojoFailureException("No simulations to run");

			} else if (simulations.size() > 1) {
				getLog().error("More than 1 simulation to run, need to specify one");
				throw new MojoFailureException("More than 1 simulation to run, need to specify one");

			} else {
				simulationClass = simulations.get(0);
			}
		}

		// Arguments
		List<String> args = new ArrayList<String>();
		args.addAll(asList("-df", dataFolder.getCanonicalPath(),
		                   "-rf", resultsFolder.getCanonicalPath(),
		                   "-rbf", requestBodiesFolder.getCanonicalPath(),
		                   "-sf", simulationsFolder.getCanonicalPath(),
		                   "-s", simulationClass));

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

	public static String fileNameToClassName(String fileName) {
		String trimmedFileName = trim(fileName);

		int lastIndexOfExtensionDelim = trimmedFileName.lastIndexOf(".");
		String strippedFileName = lastIndexOfExtensionDelim > 0 ? trimmedFileName.substring(0, lastIndexOfExtensionDelim) : trimmedFileName;

		return strippedFileName.replace(File.separatorChar, '.');
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
		scanner.setIncludes(SCALA_INCLUDES);

		// Resolve simulations to execute
		scanner.scan();

		String[] includedFiles = scanner.getIncludedFiles();

		List<String> includedClassNames = new ArrayList<String>();
		for (String includedFile : includedFiles) {
			includedClassNames.add(fileNameToClassName(includedFile));
		}

		getLog().debug("resolved simulation classes: " + includedClassNames);
		return includedClassNames;
	}
}
