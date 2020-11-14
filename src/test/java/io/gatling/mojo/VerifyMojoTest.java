
/*
 * Copyright 2011-2020 GatlingCorp (https://gatling.io)
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
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VerifyMojoTest {
  @Test
  void errors() {
    VerifyMojo verifyMojo = new VerifyMojo();
    verifyMojo.resultsFolder = new File("src/test/resources/golden-files/last-run/last-run-error/");
    Assertions.assertThrows(MojoFailureException.class, () -> verifyMojo.execute());
  }

  @Test
  void empty() {
    VerifyMojo verifyMojo = new VerifyMojo();
    verifyMojo.resultsFolder = new File("src/test/resources/golden-files/last-run/last-run-empty/");
    Assertions.assertDoesNotThrow(() -> verifyMojo.execute());
  }
}
