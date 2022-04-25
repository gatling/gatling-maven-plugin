
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

import io.gatling.plugin.EmptyChoicesException;
import io.gatling.plugin.exceptions.*;
import io.gatling.plugin.model.Simulation;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoFailureException;

public class RecoverEnterprisePluginException {

  @FunctionalInterface
  public static interface EnterprisePluginExceptionFunction<R> {
    R apply() throws EnterprisePluginException;
  }

  static <R> R handle(EnterprisePluginExceptionFunction<R> f) throws MojoFailureException {
    try {
      return f.apply();
    } catch (UnsupportedJavaVersionException e) {
      final String msg =
          e.getMessage()
              + "\nIn order to target the supported Java bytecode version, please use the following Maven setting:\n"
              + "<maven.compiler.release>"
              + e.supportedVersion
              + "</maven.compiler.release>\n"
              + "Or, reported class may come from your project dependencies, published targeting Java "
              + e.version
              + ".";
      throw new MojoFailureException(msg);
    } catch (SeveralTeamsFoundException e) {
      final String availableTeams =
          e.getAvailableTeams().stream()
              .map(t -> String.format("- %s (%s)\n", t.id, t.name))
              .collect(Collectors.joining());
      final String teamExample = e.getAvailableTeams().get(0).id.toString();
      final String msg =
          "Several teams were found to create a simulation.\n"
              + "Available teams:\n"
              + availableTeams
              + CommonLogMessage.missingConfiguration(
                  "team", "teamId", "gatling.enterprise.teamId", null, teamExample);
      throw new MojoFailureException(msg);
    } catch (SeveralSimulationClassNamesFoundException e) {
      final String availableClasses =
          e.getAvailableSimulationClassNames().stream()
              .map(s -> String.format("- %s\n", s))
              .collect(Collectors.joining());
      final String classExample = e.getAvailableSimulationClassNames().stream().findFirst().get();
      final String msg =
          "Several simulation classes were found.\n"
              + "Available classes:\n"
              + availableClasses
              + "\n"
              + CommonLogMessage.missingConfiguration(
                  "class", "simulationClass", "gatling.simulationClass", null, classExample);
      throw new MojoFailureException(msg);
    } catch (SimulationStartException e) {
      final Simulation simulation = e.getSimulation();
      final String msg =
          "Failed to start simulation.\n"
              + String.format(
                  "Simulation %s with ID %s exists but could not be started: ",
                  simulation.name, simulation.id)
              + e.getCause().getMessage()
              + "\n"
              + CommonLogMessage.simulationStartSample(simulation);
      throw new MojoFailureException(msg, e);
    } catch (EmptyChoicesException e) {
      throw new MojoFailureException(e.getMessage(), e);
    } catch (EnterprisePluginException e) {
      throw new MojoFailureException("Unhandled enterprise plugin exception", e);
    }
  }
}
