
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

import static java.util.Arrays.asList;

import java.util.List;

public final class MojoConstants {

  private MojoConstants() {}

  // Recorder constants
  public static final String RECORDER_MAIN_CLASS = "io.gatling.recorder.GatlingRecorder";

  // Compiler constants
  public static final String COMPILER_MAIN_CLASS = "io.gatling.compiler.ZincCompiler";
  public static final List<String> COMPILER_JVM_ARGS = asList("-Xmx1G", "-Xss100M");

  // Gatling constants
  public static final String GATLING_MAIN_CLASS = "io.gatling.app.Gatling";
  public static final List<String> GATLING_JVM_ARGS =
      asList(
          "-server",
          "-Xmx1G",
          "-XX:+UseG1GC",
          "-XX:+ParallelRefProcEnabled",
          "-XX:+HeapDumpOnOutOfMemoryError",
          "-XX:MaxInlineLevel=20",
          "-XX:MaxTrivialSize=12",
          "-XX:-UseBiasedLocking");
}
