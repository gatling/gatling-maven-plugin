
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
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractGatlingExecutionMojo extends AbstractGatlingMojo {

  static final String LAST_RUN_FILE = "lastRun.txt";
  static final String LAST_RUN_FILE_ERROR_LINE = "ExecutionError: ";

  /** Use this folder as the folder where results are stored. */
  @Parameter(
      property = "gatling.resultsFolder",
      defaultValue = "${project.build.directory}/gatling")
  protected File resultsFolder;

  /** Disable the plugin. */
  @Parameter(property = "gatling.skip", defaultValue = "false")
  protected boolean skip;
}
