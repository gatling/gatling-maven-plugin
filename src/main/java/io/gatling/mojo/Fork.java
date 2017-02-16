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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.util.StringUtils;

class Fork {

  private static final String ARG_FILE_PREFIX = "gatling-maven-plugin-";
  private static final String ARG_FILE_SUFFIX = ".args";

  private final String javaExecutable;
  private final String mainClassName;
  private final List<String> classpath;
  private final boolean propagateSystemProperties;
  private final Log log;

  private final List<String> jvmArgs = new ArrayList<>();
  private final List<String> args = new ArrayList<>();

  Fork(String mainClassName,//
              List<String> classpath,//
              List<String> jvmArgs,//
              List<String> args,//
              Toolchain toolchain,//
              boolean propagateSystemProperties,//
              Log log) throws Exception {

    this.mainClassName = mainClassName;
    this.classpath = classpath;
    this.jvmArgs.addAll(jvmArgs);
    this.args.addAll(args);
    this.javaExecutable = safe(safeWindowsPath(findJavaExecutable(toolchain)));
    this.propagateSystemProperties = propagateSystemProperties;
    this.log = log;
  }

  private String safeWindowsPath(String value) {
    return MojoUtils.IS_WINDOWS ?
            value
                    .replace("Program Files (x86)", "Progra~2")
                    .replace("Program Files", "Progra~1")
            : value;
  }

  private String safe(String value) {
    return value.contains(" ") ? '"' + value + '"' : value;
  }

  void run() throws Exception {
    if (propagateSystemProperties) {
      for (Entry<Object, Object> systemProp : System.getProperties().entrySet()) {
        String name = systemProp.getKey().toString();
        String value = safeWindowsPath(systemProp.getValue().toString());
        if (isPropagatableProperty(name)) {
          if (name.contains(" ")) {
            log.warn("System property name '" + name + "' contains a whitespace and can't be propagated");

          } else if (MojoUtils.IS_WINDOWS && value.contains(" ")) {
            log.warn("System property value '" + value + "' contains a whitespace and can't be propagated on Windows");

          } else {
            this.jvmArgs.add("-D" + name + "=" + safe(StringUtils.escape(value)));
          }
        }
      }
    }

    this.jvmArgs.add("-jar");

    if (log.isDebugEnabled()) {
      log.debug(StringUtils.join(classpath.iterator(), ",\n"));
    }

    this.jvmArgs.add(MojoUtils.createBooterJar(classpath, MainWithArgsInFile.class.getName()).getCanonicalPath());

    List<String> command = buildCommand();

    Executor exec = new DefaultExecutor();
    exec.setStreamHandler(new PumpStreamHandler(System.out, System.err, System.in));
    exec.setProcessDestroyer(new ShutdownHookProcessDestroyer());

    CommandLine cl = new CommandLine(javaExecutable);
    for (String arg : command) {
      cl.addArgument(arg, false);
    }

    if (log.isDebugEnabled()) {
      log.debug(cl.toString());
    }

    int exitValue = exec.execute(cl);
    if (exitValue != 0) {
      throw new MojoFailureException("command line returned non-zero value:" + exitValue);
    }
  }

  private List<String> buildCommand() throws IOException {
    ArrayList<String> command = new ArrayList<>(jvmArgs.size() + 2);
    command.addAll(jvmArgs);
    command.add(mainClassName);
    command.add(createArgFile(args).getCanonicalPath());
    return command;
  }

  private boolean isPropagatableProperty(String name) {
    return !name.startsWith("java.") //
      && !name.startsWith("sun.") //
      && !name.startsWith("maven.") //
      && !name.startsWith("file.") //
      && !name.startsWith("awt.") //
      && !name.startsWith("os.") //
      && !name.startsWith("user.") //
      && !name.startsWith("idea.") //
      && !name.startsWith("guice.") //
      && !name.startsWith("hudson.") //
      && !name.equals("line.separator") //
      && !name.equals("path.separator") //
      && !name.equals("classworlds.conf") //
      && !name.equals("org.slf4j.simpleLogger.defaultLogLevel");
  }

  private String findJavaExecutable(Toolchain toolchain) {
    String fromToolchain = toolchain != null ? toolchain.findTool("java") : null;
    if (fromToolchain != null) {
      return fromToolchain;
    } else {
      String javaHome;
      javaHome = System.getProperty("java.home");
      if (javaHome == null) {
        javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
          throw new IllegalStateException("Couldn't locate java, try setting JAVA_HOME environment variable.");
        }
      }
      return javaHome + File.separator + "bin" + File.separator + "java";
    }
  }

  private File createArgFile(List<String> args) throws IOException {
    final File argFile = File.createTempFile(ARG_FILE_PREFIX, ARG_FILE_SUFFIX);
    argFile.deleteOnExit();
    try (PrintWriter out = new PrintWriter(argFile)) {
      for (String arg : args) {
        out.println(arg);
      }
      return argFile;
    }
  }
}
