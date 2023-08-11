
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

import java.util.*;

public final class MojoConstants {

  private MojoConstants() {}

  // Recorder constants
  static final String RECORDER_MAIN_CLASS = "io.gatling.recorder.GatlingRecorder";

  // Gatling constants
  static final String GATLING_MAIN_CLASS = "io.gatling.app.Gatling";
  static final String GATLING_GROUP_ID = "io.gatling";
  static final String GATLING_MODULE_APP = "gatling-app";
  static final String GATLING_HIGHCHARTS_GROUP_ID = "io.gatling.highcharts";
  static final String GATLING_FRONTLINE_GROUP_ID = "io.gatling.frontline";
  static final Set<String> GATLING_GROUP_IDS;

  static {
    HashSet<String> groupIds = new HashSet<>();
    groupIds.add(GATLING_GROUP_ID);
    groupIds.add(GATLING_HIGHCHARTS_GROUP_ID);
    groupIds.add(GATLING_FRONTLINE_GROUP_ID);
    GATLING_GROUP_IDS = Collections.unmodifiableSet(groupIds);
  }
}
