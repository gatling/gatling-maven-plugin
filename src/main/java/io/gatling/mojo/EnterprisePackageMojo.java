
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

import io.gatling.plugin.io.PluginLogger;
import io.gatling.plugin.pkg.Dependency;
import io.gatling.plugin.pkg.EnterprisePackager;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
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
  private static final String META_INF_PACKAGER = "maven";

  private final PluginLogger pluginLogger = newPluginLogger();
  @Component private MavenProjectHelper projectHelper;

  @Override
  public void execute() throws MojoExecutionException {
    Set<Artifact> allDeps = getAllDeps();
    List<Artifact> depsWithGatlingGroupId = MojoUtils.findByGroupId(allDeps, GATLING_GROUP_ID);
    Set<Dependency> extraDependencies = getExtraDependencies(allDeps, depsWithGatlingGroupId);

    String gatlingVersion = getGatlingVersion(depsWithGatlingGroupId);

    List<File> classDirectories =
        List.of(
            new File(mavenProject.getBuild().getOutputDirectory()),
            new File(mavenProject.getBuild().getTestOutputDirectory()));

    File enterprisePackage = enterprisePackage();

    try {
      new EnterprisePackager(pluginLogger)
          .createEnterprisePackage(
              classDirectories,
              extraDependencies,
              mavenProject.getGroupId(),
              mavenProject.getArtifactId(),
              mavenProject.getVersion(),
              gatlingVersion,
              META_INF_PACKAGER,
              getClass().getPackage().getImplementationVersion(),
              enterprisePackage);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to generate Enterprise package", e);
    }

    // attach jar so it can be deployed
    projectHelper.attachArtifact(mavenProject, "jar", SHADED_CLASSIFIER, enterprisePackage);
  }

  private Set<Dependency> getExtraDependencies(
      Set<Artifact> allDeps, List<Artifact> depsWithGatlingGroupId) {
    List<Artifact> allGatlingDeps = getAllGatlingDeps(allDeps, depsWithGatlingGroupId);
    Set<Artifact> gatlingAndTransitiveDependencies =
        getGatlingAndTransitiveDependencies(allGatlingDeps);

    return allDeps.stream()
        .filter(artifact -> MojoUtils.artifactNotIn(artifact, gatlingAndTransitiveDependencies))
        .map(
            artifact ->
                new Dependency(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getVersion(),
                    artifact.getFile()))
        .collect(Collectors.toSet());
  }

  private Set<Artifact> getAllDeps() {
    return mavenProject.getArtifacts().stream()
        .filter(artifact -> !artifact.getType().equals("pom"))
        .collect(Collectors.toSet());
  }

  private Set<Artifact> getGatlingAndTransitiveDependencies(List<Artifact> allGatlingDeps) {
    Set<Artifact> gatlingAndTransitiveDependencies = new HashSet<>();

    gatlingAndTransitiveDependencies.addAll(gatlingTransitiveDependencies(allGatlingDeps));
    gatlingAndTransitiveDependencies.addAll(allGatlingDeps);

    return gatlingAndTransitiveDependencies;
  }

  private Set<Artifact> gatlingTransitiveDependencies(List<Artifact> artifacts) {
    return artifacts.stream()
        .flatMap(artifact -> resolveTransitively(artifact).stream())
        .filter(art -> !GATLING_GROUP_IDS.contains(art.getGroupId()))
        .collect(Collectors.toSet());
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

  private static List<Artifact> getAllGatlingDeps(
      Set<Artifact> allDeps, List<Artifact> depsWithGatlingGroupId) {
    List<Artifact> depsWithGatlingHighchartsGroupId =
        MojoUtils.findByGroupId(allDeps, GATLING_HIGHCHARTS_GROUP_ID);
    List<Artifact> allGatlingDeps = new ArrayList<>();

    allGatlingDeps.addAll(depsWithGatlingGroupId);
    allGatlingDeps.addAll(depsWithGatlingHighchartsGroupId);

    return allGatlingDeps;
  }

  private static String getGatlingVersion(List<Artifact> depsWithGatlingGroupId) {
    return depsWithGatlingGroupId.stream()
        .filter(artifact -> "gatling-app".equals(artifact.getArtifactId()))
        .findFirst()
        .orElseThrow(() -> new NullPointerException("Can't find Gatling libraries in dependencies"))
        .getVersion();
  }
}
