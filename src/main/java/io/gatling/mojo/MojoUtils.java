/**
 * Copyright 2011-2017 GatlingCorp (http://gatling.io)
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.StringUtils;

public final class MojoUtils {

  public static boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

  private MojoUtils() {
  }

  public static String toMultiPath(List<String> paths) {
    return StringUtils.join(paths.iterator(), File.pathSeparator);
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

  public static <T> List<T> arrayAsListEmptyIfNull(T[] array) {
    return array == null ? Collections.<T> emptyList() : Arrays.asList(array);
  }


  /**
   * Create a jar with just a manifest containing a Main-Class entry for BooterConfiguration and a Class-Path entry
   * for all classpath elements.
   *
   * @param classPath List of all classpath elements.
   * @param startClassName The classname to start (main-class)
   * @return The file pointing to the jar
   * @throws java.io.IOException When a file operation fails.
   */
  public static File createBooterJar(List<String> classPath, String startClassName) throws IOException {
    File file = File.createTempFile("gatlingbooter", ".jar");
    file.deleteOnExit();

    FileOutputStream fos = new FileOutputStream(file);
    JarOutputStream jos = new JarOutputStream(fos);
    jos.setLevel(JarOutputStream.STORED);
    JarEntry je = new JarEntry("META-INF/MANIFEST.MF");
    jos.putNextEntry(je);

    Manifest man = new Manifest();

    // we can't use StringUtils.join here since we need to add a '/' to
    // the end of directory entries - otherwise the jvm will ignore them.
    StringBuilder cp = new StringBuilder();
    for (String el : classPath) {
      // NOTE: if File points to a directory, this entry MUST end in '/'.
      cp.append(getURL(new File(el)).toExternalForm()).append(" ");
    }
    cp.setLength(cp.length() - 1);

    man.getMainAttributes().putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
    man.getMainAttributes().putValue(Attributes.Name.CLASS_PATH.toString(), cp.toString());
    man.getMainAttributes().putValue(Attributes.Name.MAIN_CLASS.toString(), startClassName);

    man.write(jos);
    jos.close();

    return file;
  }

  public static URL getURL(File file) throws MalformedURLException {

    // encode any characters that do not comply with RFC 2396
    // this is primarily to handle Windows where the user's home directory contains spaces

    return new URL(file.toURI().toASCIIString());
  }
}
