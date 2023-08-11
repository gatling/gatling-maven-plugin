
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.SelectorUtils;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtilsV2_2;

/** Mojo to package Gatling simulations to run on Gatling Enterprise (Cloud or Self-Hosted). */
@Execute(phase = LifecyclePhase.TEST_COMPILE)
@Mojo(
    name = "enterprisePackage",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.TEST)
public class EnterprisePackageMojo extends AbstractEnterpriseMojo {

  private static final List<char[]> ALWAYS_EXCLUDES =
      toChars(
          new String[] {
            "module-info.class",
            "LICENSE",
            "META-INF/LICENSE",
            "META-INF/MANIFEST.MF",
            "META-INF/versions/**",
            "META-INF/maven/**",
            "*.SF",
            "*.DSA",
            "*.RSA"
          });

  private static final Set<String> EXCLUDED_NETTY_ARTIFACTS;

  static {
    Set<String> excludedNettyArtifacts = new HashSet<>();
    excludedNettyArtifacts.add("netty-all");
    excludedNettyArtifacts.add("netty-resolver-dns-classes-macos");
    excludedNettyArtifacts.add("netty-resolver-dns-native-macos");
    EXCLUDED_NETTY_ARTIFACTS = Collections.unmodifiableSet(excludedNettyArtifacts);
  }

  @Component private MavenProjectHelper projectHelper;

  /**
   * List of exclude patterns to use when scanning for simulation classes. Excludes none by default.
   */
  @Parameter(property = "gatling.excludes")
  private String[] excludes;

  private Set<Artifact> nonGatlingDependencies(List<Artifact> artifacts) {
    return artifacts.stream()
        .flatMap(
            artifact ->
                resolveTransitively(artifact).stream()
                    .filter(art -> !GATLING_GROUP_IDS.contains(art.getGroupId())))
        .collect(Collectors.toSet());
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkPluginPreConditions();

    Set<Artifact> allDeps = mavenProject.getArtifacts();

    List<Artifact> depsWithFrontLineGroupId =
        MojoUtils.findByGroupId(allDeps, GATLING_FRONTLINE_GROUP_ID);
    if (!depsWithFrontLineGroupId.isEmpty()) {
      throw new MojoExecutionException(
          "Found a dependency with group id "
              + GATLING_FRONTLINE_GROUP_ID
              + " in projects dependencies");
    }

    List<Artifact> depsWithGatlingGroupId = MojoUtils.findByGroupId(allDeps, GATLING_GROUP_ID);
    List<Artifact> depsWithGatlingHighchartsGroupId =
        MojoUtils.findByGroupId(allDeps, GATLING_HIGHCHARTS_GROUP_ID);
    if (depsWithGatlingGroupId.isEmpty()) {
      throw new MojoExecutionException(
          "Couldn't find any dependencies with group id "
              + GATLING_GROUP_ID
              + " in project dependencies");
    }

    Set<Artifact> gatlingDependencies = new HashSet<>();
    gatlingDependencies.addAll(nonGatlingDependencies(depsWithGatlingGroupId));
    gatlingDependencies.addAll(nonGatlingDependencies(depsWithGatlingHighchartsGroupId));

    Set<Artifact> filteredDeps =
        allDeps.stream()
            .filter(
                artifact ->
                    !artifact.getType().equals("pom")
                        && !GATLING_GROUP_IDS.contains(artifact.getGroupId())
                        && !(artifact.getGroupId().equals("io.netty")
                            && EXCLUDED_NETTY_ARTIFACTS.contains(artifact.getArtifactId()))
                        && MojoUtils.artifactNotIn(artifact, gatlingDependencies))
            .collect(Collectors.toSet());

    File workingDir;
    try {
      workingDir = Files.createTempDirectory("gatling-maven").toFile();
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to create temp dir", e);
    }

    List<char[]> compiledExcludes = compileExcludes();

    // extract dep jars
    for (Artifact artifact : filteredDeps) {
      ZipUtil.unpack(
          artifact.getFile(), workingDir, name -> exclude(name, compiledExcludes) ? null : name);
    }

    // copy compiled classes
    File outputDirectory = new File(mavenProject.getBuild().getOutputDirectory());
    Path outputDirectoryPath = outputDirectory.toPath();
    File testOutputDirectory = new File(mavenProject.getBuild().getTestOutputDirectory());
    Path testOutputDirectoryPath = testOutputDirectory.toPath();

    try {
      if (outputDirectory.exists()) {
        FileUtilsV2_2.copyDirectory(
            outputDirectory,
            workingDir,
            pathname ->
                !exclude(
                    outputDirectoryPath.relativize(pathname.toPath()).toString(), compiledExcludes),
            false);
      }
      if (testOutputDirectory.exists()) {
        FileUtilsV2_2.copyDirectory(
            testOutputDirectory,
            workingDir,
            pathname ->
                !exclude(
                    testOutputDirectoryPath.relativize(pathname.toPath()).toString(),
                    compiledExcludes),
            false);
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy compiled classes", e);
    }

    // generate META-INF directory
    File metaInfDir = new File(workingDir, "META-INF");
    metaInfDir.mkdirs();

    // generate maven files directory
    File mavenDir =
        new File(
            new File(new File(metaInfDir, "maven"), mavenProject.getGroupId()),
            mavenProject.getArtifactId());
    mavenDir.mkdirs();

    // generate pom.properties
    try (FileWriter fw = new FileWriter(new File(mavenDir, "pom.properties"))) {
      fw.write("groupId=" + mavenProject.getGroupId() + "\n");
      fw.write("artifactId=" + mavenProject.getArtifactId() + "\n");
      fw.write("version=" + mavenProject.getVersion() + "\n");
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to generate pom.properties", e);
    }

    // generate pom.xml
    try (FileWriter fw = new FileWriter(new File(mavenDir, "pom.xml"))) {
      fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "\n");
      fw.write(
          "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
              + "\n");
      fw.write(
          "    xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">"
              + "\n");
      fw.write("    <modelVersion>4.0.0</modelVersion>" + "\n");
      fw.write("    <groupId>" + mavenProject.getGroupId() + "</groupId>" + "\n");
      fw.write("    <artifactId>" + mavenProject.getArtifactId() + "</artifactId>" + "\n");
      fw.write("    <version>" + mavenProject.getVersion() + "</version>" + "\n");
      fw.write("</project>");
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to generate pom.properties", e);
    }

    // generate fake manifest
    File manifest = new File(metaInfDir, "MANIFEST.MF");

    String gatlingVersion =
        MojoUtils.findByGroupIdAndArtifactId(allDeps, GATLING_GROUP_ID, GATLING_MODULE_APP)
            .getVersion();

    try (FileWriter fw = new FileWriter(manifest)) {
      fw.write("Manifest-Version: 1.0\n");
      fw.write("Implementation-Title: " + mavenProject.getArtifactId() + "\n");
      fw.write("Implementation-Version: " + mavenProject.getVersion() + "\n");
      fw.write("Implementation-Vendor: " + mavenProject.getGroupId() + "\n");
      fw.write("Specification-Vendor: GatlingCorp\n");
      fw.write("Gatling-Version: " + gatlingVersion + "\n");
      fw.write("Gatling-Packager: maven" + "\n");
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to generate manifest", e);
    }

    File shaded = shadedArtifactFile();

    // generate jar
    getLog().info("Generating Gatling Enterprise package " + shaded);
    ZipUtil.pack(workingDir, shaded);

    // attach jar so it can be deployed
    projectHelper.attachArtifact(mavenProject, "jar", shadedClassifier, shaded);

    try {
      FileUtilsV2_2.deleteDirectory(workingDir);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to delete working directory " + workingDir, e);
    }
  }

  private static List<char[]> toChars(String[] patterns) {
    return Arrays.stream(patterns).map(String::toCharArray).collect(Collectors.toList());
  }

  private List<char[]> compileExcludes() {
    if (excludes == null) {
      return ALWAYS_EXCLUDES;
    }

    List<char[]> compiledUserExcludes = toChars(excludes);
    compiledUserExcludes.addAll(ALWAYS_EXCLUDES);
    return compiledUserExcludes;
  }

  private boolean exclude(String name, List<char[]> excludes) {
    for (char[] pattern : excludes) {
      if (SelectorUtils.match(pattern, name.toCharArray(), false)) {
        getLog().info("Excluding file " + name);
        return true;
      }
    }
    return false;
  }

  private Set<Artifact> resolveTransitively(Artifact artifact) {
    ArtifactResolutionRequest request =
        new ArtifactResolutionRequest()
            .setArtifact(artifact)
            .setResolveRoot(true)
            .setResolveTransitively(true)
            .setServers(session.getRequest().getServers())
            .setMirrors(session.getRequest().getMirrors())
            .setProxies(session.getRequest().getProxies())
            .setLocalRepository(session.getLocalRepository())
            .setRemoteRepositories(session.getCurrentProject().getRemoteArtifactRepositories());
    return repository.resolve(request).getArtifacts();
  }
}
