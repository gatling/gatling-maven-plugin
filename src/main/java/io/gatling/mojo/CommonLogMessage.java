
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

import io.gatling.plugin.model.Simulation;
import java.net.URL;

public final class CommonLogMessage {

  private CommonLogMessage() {}

  public static String simulationCreated(Simulation simulation) {
    return "Successfully created simulation " + simulation.name + " with ID " + simulation.id;
  }

  public static String simulationChosen(Simulation simulation) {
    return "Chose to start simulation " + simulation.name + " with ID " + simulation.id;
  }

  public static String simulationConfiguration(
      Simulation simulation, String simulationIdSetting, boolean waitForRunEnd) {
    final StringBuilder builder = new StringBuilder();
    if (simulationIdSetting == null) {
      builder
          .append("To start the same simulation again, specify -Dgatling.enterprise.simulationId=")
          .append(simulation.id)
          .append(", or add the configuration to your pom.xml, e.g.:\n")
          .append(pluginConfiguration("simulationId", simulation.id.toString()))
          .append("\n");
    }
    if (!waitForRunEnd) {
      builder
          .append(
              "To wait for the end of the run when starting a simulation on Gatling Enterprise, specify -Dgatling.enterprise.waitForRunEnd=true, or add the configuration to your pom.xml, e.g.:\n")
          .append(pluginConfiguration("waitForRunEnd", "true"))
          .append("\n");
    }
    return builder.toString();
  }

  public static String simulationStartSuccess(URL enterpriseUrl, String reportsPath) {
    return "Simulation successfully started; the report will be available at "
        + enterpriseUrl
        + reportsPath;
  }

  /**
   * @param commonName Required
   * @param confName Required
   * @param sysPropName Required
   * @param envVarName Optional
   * @param sampleValue Required
   */
  public static String missingConfiguration(
      String commonName,
      String confName,
      String sysPropName,
      String envVarName,
      String sampleValue) {
    final String envVarMsg =
        envVarName != null ? " in the environment variable " + envVarName + ", pass it" : "";
    final String firstLine =
        String.format(
            "Specify the %s you want to use%s with -D%s=<%s>, or add the configuration to your pom.xml, e.g.:\n",
            commonName, envVarMsg, sysPropName, confName);
    return firstLine + pluginConfiguration(confName, sampleValue);
  }

  private static String pluginConfiguration(String confName, String confValue) {
    return "<plugin>\n"
        + "  <groupId>io.gatling</groupId>\n"
        + "  <artifactId>gatling-maven-plugin</artifactId>\n"
        + "  <configuration>\n"
        + "    <"
        + confName
        + ">"
        + confValue
        + "</"
        + confName
        + ">\n"
        + "  </configuration>\n"
        + "</plugin>";
  }
}
