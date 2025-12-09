
/*
 * Copyright 2011-2025 GatlingCorp (https://gatling.io)
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

import io.gatling.plugin.io.PluginLogger;
import io.gatling.plugin.pkg.Dependency;
import io.gatling.plugin.pkg.EnterprisePackager;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProjectHelper;

/** Mojo to package Gatling simulations to run on Gatling Enterprise (Cloud or Self-Hosted). */
@Execute(phase = LifecyclePhase.TEST_COMPILE)
@Mojo(
    name = "enterprisePackage",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.TEST)
public class EnterprisePackageMojo extends AbstractEnterpriseMojo {
  private static final String MAVEN_PACKAGER_NAME = "maven";
  private static final Set<String> GATLING_GROUP_IDS =
      Set.of(GATLING_GROUP_ID, GATLING_HIGHCHARTS_GROUP_ID);

  private final PluginLogger pluginLogger = newPluginLogger();
  @Inject private MavenProjectHelper projectHelper;

  @Override
  public void execute() throws MojoExecutionException {
    Set<Artifact> allArtifacts = getAllArtifacts();
    pluginLogger.debug("allArtifacts=" + allArtifacts);

    List<Artifact> gatlingArtifacts =
        allArtifacts.stream()
            .filter(artifact -> GATLING_GROUP_IDS.contains(artifact.getGroupId()))
            .collect(Collectors.toList());
    pluginLogger.debug("gatlingArtifacts=" + gatlingArtifacts);

    Set<Dependency> gatlingDependencies =
        gatlingArtifacts.stream()
            .map(EnterprisePackageMojo::artifactToDependency)
            .collect(Collectors.toSet());
    pluginLogger.debug("gatlingDependencies=" + gatlingDependencies);

    Set<Dependency> extraDependencies = getExtraDependencies(allArtifacts, gatlingArtifacts);
    pluginLogger.debug("extraDependencies=" + extraDependencies);

    List<File> classDirectories =
        List.of(
            new File(mavenProject.getBuild().getOutputDirectory()),
            new File(mavenProject.getBuild().getTestOutputDirectory()));

    File enterprisePackage = enterprisePackage();

    try {
      new EnterprisePackager(pluginLogger)
          .createEnterprisePackage(
              classDirectories,
              gatlingDependencies,
              extraDependencies,
              mavenProject.getGroupId(),
              mavenProject.getArtifactId(),
              mavenProject.getVersion(),
              MAVEN_PACKAGER_NAME,
              getClass().getPackage().getImplementationVersion(),
              enterprisePackage,
              mavenProject.getBasedir());
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to generate Enterprise package", e);
    }

    // attach jar so it can be deployed
    projectHelper.attachArtifact(mavenProject, "jar", SHADED_CLASSIFIER, enterprisePackage);
  }

  private Set<Dependency> getExtraDependencies(
      Set<Artifact> allDeps, List<Artifact> gatlingArtifacts) {
    Set<Artifact> gatlingAndTransitiveDependencies =
        gatlingAndTransitiveDependencies(gatlingArtifacts);
    pluginLogger.debug("gatlingAndTransitiveDependencies=" + gatlingAndTransitiveDependencies);

    return allDeps.stream()
        .filter(artifact -> MojoUtils.artifactNotIn(artifact, gatlingAndTransitiveDependencies))
        .map(EnterprisePackageMojo::artifactToDependency)
        .collect(Collectors.toSet());
  }

  private Set<Artifact> getAllArtifacts() {
    return mavenProject.getArtifacts().stream()
        .filter(artifact -> !artifact.getType().equals("pom"))
        .collect(Collectors.toSet());
  }

  private Set<Artifact> gatlingAndTransitiveDependencies(List<Artifact> artifacts) {
    return artifacts.stream()
        .flatMap(artifact -> resolveTransitively(artifact).stream())
        .collect(Collectors.toSet());
  }

  private Set<Artifact> resolveTransitively(Artifact artifact) {
    pluginLogger.debug("Resolving artifact=" + artifact);
    ArtifactResolutionRequest request =
        new ArtifactResolutionRequest()
            .setArtifact(artifact)
            .setResolveRoot(true)
            .setResolveTransitively(true)
            .setOffline(session.isOffline())
            .setServers(session.getRequest().getServers())
            .setMirrors(session.getRequest().getMirrors())
            .setProxies(session.getRequest().getProxies())
            .setLocalRepository(session.getLocalRepository())
            .setRemoteRepositories(session.getCurrentProject().getRemoteArtifactRepositories());
    pluginLogger.debug("Resolved artifact=" + artifact);
    return repository.resolve(request).getArtifacts();
  }

  private static Dependency artifactToDependency(Artifact artifact) {
    return new Dependency(
        artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getFile());
  }
}
