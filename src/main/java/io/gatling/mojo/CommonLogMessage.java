
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

public final class CommonLogMessage {

  private CommonLogMessage() {}

  public static String simulationStartSuccess(URL enterpriseWebAppUrl, String reportsPath) {
    return "Simulation successfully started; reports are available at "
        + enterpriseWebAppUrl
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
