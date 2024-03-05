
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

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;

public final class MojoUtils {

  private MojoUtils() {}

  public static String locateJar(Class<?> c) throws Exception {
    final URL location;
    final String classLocation = c.getName().replace('.', '/') + ".class";
    final ClassLoader loader = c.getClassLoader();
    if (loader == null) {
      location = ClassLoader.getSystemResource(classLocation);
    } else {
      location = loader.getResource(classLocation);
    }
    if (location != null) {
      Pattern p = Pattern.compile("^.*file:(.*)!.*$");
      Matcher m = p.matcher(location.toString());
      if (m.find()) {
        return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
      }
      throw new ClassNotFoundException(
          "Cannot parse location of '" + location + "'.  Probably not loaded from a Jar");
    }
    throw new ClassNotFoundException(
        "Cannot find class '" + c.getName() + " using the classloader");
  }

  static boolean artifactNotIn(Artifact target, Collection<Artifact> artifacts) {
    return findByGroupIdAndArtifactId(artifacts, target.getGroupId(), target.getArtifactId())
        == null;
  }

  static Artifact findByGroupIdAndArtifactId(
      Collection<Artifact> artifacts, String groupId, String artifactId) {
    return artifacts.stream()
        .filter(
            artifact ->
                artifact.getGroupId().equals(groupId)
                    && artifact.getArtifactId().equals(artifactId))
        .findFirst()
        .orElse(null);
  }
}
