
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

import io.gatling.scanner.SimulationScanResult;
import io.gatling.scanner.SimulationScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.SelectorUtils;

public final class SimulationClassUtils {

  private SimulationClassUtils() {}

  /**
   * Resolves simulation files to execute from the simulation folder.
   *
   * @return a list of simulation class names.
   */
  public static List<String> resolveSimulations(
      MavenProject mavenProject, String[] includes, String[] excludes) {
    try {
      List<String> testClasspathElements = mavenProject.getTestClasspathElements();

      List<File> dependencies = new ArrayList<>();
      List<File> classDirectories = new ArrayList<>();

      for (String testClasspathElement : testClasspathElements) {
        File file = new File(testClasspathElement);
        if (file.isDirectory()) {
          classDirectories.add(file);
        } else if (file.isFile()) {
          dependencies.add(file);
        }
      }

      SimulationScanResult simulationScanResult =
          SimulationScanner.scan(dependencies, classDirectories);

      List<String> includesList = MojoUtils.arrayAsListEmptyIfNull(includes);
      List<String> excludesList = MojoUtils.arrayAsListEmptyIfNull(excludes);

      return simulationScanResult.getSimulationClasses().stream()
          .filter(
              className -> {
                boolean isIncluded = includesList.isEmpty() || match(includesList, className);
                boolean isExcluded = !excludesList.isEmpty() && match(excludesList, className);
                return isIncluded && !isExcluded;
              })
          .collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean match(List<String> patterns, String string) {
    for (String pattern : patterns) {
      if (pattern != null && SelectorUtils.match(pattern, string)) {
        return true;
      }
    }
    return false;
  }
}
