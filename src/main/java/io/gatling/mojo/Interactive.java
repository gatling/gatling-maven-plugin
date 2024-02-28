
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

import java.util.List;
import java.util.Scanner;
import org.apache.maven.plugin.MojoFailureException;

public class Interactive {

  private static final int MAX_INTERACTIVE_SIMULATION_SELECT_ATTEMPTS = 5;

  static String selectSingleSimulation(List<String> simulations) throws MojoFailureException {
    return selectSingleSimulationRec(new Scanner(System.in), simulations, 0);
  }

  private static String selectSingleSimulationRec(
      Scanner scanner, List<String> simulations, int attempts) throws MojoFailureException {
    if (attempts > MAX_INTERACTIVE_SIMULATION_SELECT_ATTEMPTS) {
      throw new MojoFailureException(
          "Max attempts of reading simulation number ("
              + MAX_INTERACTIVE_SIMULATION_SELECT_ATTEMPTS
              + ") reached. Aborting.");
    } else {
      System.out.println("Choose a simulation number:");
      for (int i = 0; i < simulations.size(); i++) {
        System.out.println("     [" + i + "] " + simulations.get(i));
      }

      try {
        int selected = Integer.parseInt(scanner.nextLine());
        if (selected < 0 || selected >= simulations.size()) {
          System.out.println("Invalid selection. Please try again.");
          return selectSingleSimulationRec(scanner, simulations, attempts + 1);
        }

        return simulations.get(selected);

      } catch (NumberFormatException e) {
        System.out.println("Invalid number. Please try again.");
        return selectSingleSimulationRec(scanner, simulations, attempts + 1);
      }
    }
  }
}
