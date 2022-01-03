
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AssertionsSummaryTest {

  private AssertionsSummary summary;

  @BeforeEach
  void parseAssertionsFile() throws Exception {
    File file = new File("src/test/resources/golden-files/assertions.xml");
    summary = AssertionsSummary.fromAssertionsFile(file);
  }

  @Test
  void errors() {
    assertEquals(2, summary.getErrors());
  }

  @Test
  void failures() {
    assertEquals(1, summary.getFailures());
  }

  @ParameterizedTest
  @CsvSource({"0,0,false", "1,0,true", "0,1,true", "1,1,true"})
  void hasFailures(int errors, int failures, boolean expectedResult) {
    AssertionsSummary summary = new AssertionsSummary(errors, failures);
    assertEquals(expectedResult, summary.hasFailures());
  }
}
