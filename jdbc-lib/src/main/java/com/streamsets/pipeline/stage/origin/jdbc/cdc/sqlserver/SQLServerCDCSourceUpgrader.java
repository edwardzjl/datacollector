/*
 * Copyright 2017 StreamSets Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.jdbc.cdc.sqlserver;

import com.google.common.base.Strings;
import com.streamsets.pipeline.api.Config;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.StageUpgrader;
import com.streamsets.pipeline.api.impl.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQLServerCDCSourceUpgrader implements StageUpgrader {
  private static final String TABLECONFIG = "cdcTableJdbcConfigBean.tableConfigs";
  private static final String SCHEMA_CONFIG = "schema";
  private static final String TABLEPATTERN_CONFIG = "tablePattern";
  private static final String TABLE_EXCLUSION_CONFIG = "tableExclusionPattern";
  private static final String TABLE_INITIALOFFSET_CONFIG = "initialOffset";
  private static final String TABLE_CAPTURE_INSTANCE_CONFIG = "capture_instance";

  @Override
  public List<Config> upgrade(
      String library, String stageName, String stageInstance, int fromVersion, int toVersion, List<Config> configs
  ) throws StageException {
    switch (fromVersion) {
      case 1:
        upgradeV1ToV2(configs);
        break;
      default:
        throw new IllegalStateException(Utils.format("Unexpected fromVersion {}", fromVersion));
    }
    return configs;
  }

  private static void upgradeV1ToV2(List<Config> configs) {
    Config removeConfig = null;
    Config addConfig = null;
    for (Config config : configs) {
      if (TABLECONFIG.equals(config.getName())) {
        List<Map<String, String>> tableConfig = (List<Map<String, String>>) config.getValue();
        ArrayList<Map<String, String>> newconfig = new ArrayList<>();

        for (Map<String, String> tableConfig2 : tableConfig) {
          String tablePattern = tableConfig2.get(TABLEPATTERN_CONFIG);
          String schema = tableConfig2.get(SCHEMA_CONFIG);
          String initialOffset = tableConfig2.get(TABLE_INITIALOFFSET_CONFIG);
          String tableExclusion = tableConfig2.get(TABLE_EXCLUSION_CONFIG);

          StringBuilder captureInstance = new StringBuilder();
          captureInstance.append(Strings.isNullOrEmpty(schema) ? "%" : schema);
          captureInstance.append("_");
          captureInstance.append(Strings.isNullOrEmpty(tablePattern) ? "%" : tablePattern);

          Map<String, String> newTableConfig = new HashMap<>();
          newTableConfig.put(TABLE_CAPTURE_INSTANCE_CONFIG, captureInstance.toString());

          if (!Strings.isNullOrEmpty(initialOffset)) {
            newTableConfig.put(TABLE_INITIALOFFSET_CONFIG, initialOffset);
          }

          if (!Strings.isNullOrEmpty(tableExclusion)) {
            newTableConfig.put(TABLE_EXCLUSION_CONFIG, tableExclusion);
          }

          newconfig.add(newTableConfig);
        }
        removeConfig = config;
        addConfig = new Config(TABLECONFIG, newconfig);
        break;
      }
    }

    if (removeConfig != null) {
      configs.add(addConfig);
      configs.remove(removeConfig);
    }
  }
}
