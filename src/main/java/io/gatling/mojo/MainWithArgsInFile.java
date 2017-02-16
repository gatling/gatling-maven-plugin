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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class MainWithArgsInFile {

  public static void main(String[] args) {
    try {
      String mainClassName = args[0];
      List<String> argsFromFile = readArgFile(new File(args[1]));
      runMain(mainClassName, argsFromFile);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(-1);
    }
  }

  private static void runMain(String mainClassName, List<String> args) throws Exception {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Class<?> mainClass = cl.loadClass(mainClassName);
    Method mainMethod = mainClass.getMethod("main", String[].class);
    int mods = mainMethod.getModifiers();
    if (mainMethod.getReturnType() != void.class || !Modifier.isStatic(mods) || !Modifier.isPublic(mods)) {
      throw new NoSuchMethodException("main");
    }

    String[] argsArray = args.toArray(new String[args.size()]);
    mainMethod.invoke(null, new Object[]{argsArray});
  }

  private static List<String> readArgFile(File argFile) throws IOException {
    ArrayList<String> args = new ArrayList<String>();
    try (
      final FileReader fr = new FileReader(argFile);
      final BufferedReader in = new BufferedReader(fr)
    ) {
      String line;
      while ((line = in.readLine()) != null) {
        args.add(line);
      }
      return args;
    }
  }
}
