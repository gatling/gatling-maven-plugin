/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
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
import java.net.URLDecoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.StringUtils;

import static org.codehaus.plexus.util.StringUtils.trim;

public class GatlingMojoUtils {

  private GatlingMojoUtils() {
    throw new AssertionError();
  }

  public static String toMultiPath(List<String> paths) {
    return StringUtils.join(paths.iterator(), File.pathSeparator);
  }

  public static String fileNameToClassName(String fileName) {
    String trimmedFileName = trim(fileName);

    int lastIndexOfExtensionDelim = trimmedFileName.lastIndexOf(".");
    String strippedFileName = lastIndexOfExtensionDelim > 0 ? trimmedFileName.substring(0, lastIndexOfExtensionDelim) : trimmedFileName;

    return strippedFileName.replace(File.separatorChar, '.');
  }

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
        return URLDecoder.decode(m.group(1), "UTF-8");
      }
      throw new ClassNotFoundException("Cannot parse location of '" + location + "'.  Probably not loaded from a Jar");
    }
    throw new ClassNotFoundException("Cannot find class '" + c.getName() + " using the classloader");
  }
}
