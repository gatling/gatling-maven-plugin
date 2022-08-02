
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.SelectorUtils;

public final class SimulationClassUtils {

  private SimulationClassUtils() {}

  /**
   * Resolves simulation files to execute from the simulation folder.
   *
   * @return a list of simulation class names.
   */
  public static List<String> resolveSimulations(
      MavenProject mavenProject, File compiledClassesFolder, String[] includes, String[] excludes) {

    try {
      ClassLoader testClassLoader = new URLClassLoader(testClassPathUrls(mavenProject));

      Class<?> scalaSimulationClass =
          testClassLoader.loadClass("io.gatling.core.scenario.Simulation");
      Optional<Class<?>> javaSimulationClass = loadJavaSimulationClass(testClassLoader);

      List<String> includesList = MojoUtils.arrayAsListEmptyIfNull(includes);
      List<String> excludesList = MojoUtils.arrayAsListEmptyIfNull(excludes);

      List<String> simulationsClasses = new ArrayList<>();

      for (String classFile : compiledClassFiles(compiledClassesFolder)) {
        String className = pathToClassName(classFile);

        boolean isIncluded = includesList.isEmpty() || match(includesList, className);
        boolean isExcluded = !excludesList.isEmpty() && match(excludesList, className);

        if (isIncluded && !isExcluded) {
          // check if the class is a concrete Simulation
          Class<?> clazz = testClassLoader.loadClass(className);
          if (isConcreteClass(clazz)
              && (javaSimulationClass
                      .map(simClass -> simClass.isAssignableFrom(clazz))
                      .orElse(false)
                  || scalaSimulationClass.isAssignableFrom(clazz))) {
            simulationsClasses.add(className);
          }
        }
      }

      return simulationsClasses;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static URL[] testClassPathUrls(MavenProject mavenProject)
      throws DependencyResolutionRequiredException, MalformedURLException {

    List<String> testClasspathElements = mavenProject.getTestClasspathElements();

    URL[] urls = new URL[testClasspathElements.size()];
    for (int i = 0; i < testClasspathElements.size(); i++) {
      String testClasspathElement = testClasspathElements.get(i);
      URL url = Paths.get(testClasspathElement).toUri().toURL();
      urls[i] = url;
    }

    return urls;
  }

  private static Optional<Class<?>> loadJavaSimulationClass(ClassLoader testClassLoader) {
    try {
      return Optional.of(testClassLoader.loadClass("io.gatling.javaapi.core.Simulation"));
    } catch (ClassNotFoundException e) {
      // ignore
      return Optional.empty();
    }
  }

  private static String[] compiledClassFiles(File compiledClassesFolder) throws IOException {
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(compiledClassesFolder.getCanonicalPath());
    scanner.setIncludes(new String[] {"**/*.class"});
    scanner.scan();
    String[] files = scanner.getIncludedFiles();
    Arrays.sort(files);
    return files;
  }

  private static String pathToClassName(String path) {
    return path.substring(0, path.length() - ".class".length()).replace(File.separatorChar, '.');
  }

  private static boolean match(List<String> patterns, String string) {
    for (String pattern : patterns) {
      if (pattern != null && SelectorUtils.match(pattern, string)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isConcreteClass(Class<?> clazz) {
    return !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
  }
}
