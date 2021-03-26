/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier:	Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vmware.vropsexport;

import java.io.Reader;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class ConfigLoader {
  public static Config parse(final Reader rdr) {
    final Yaml yaml = new Yaml(new Constructor(Config.class));
    final Config conf = (Config) yaml.load(rdr);
    if (conf.getOutputFormat() == null) {
      conf.setOutputFormat("csv");
    }
    return conf;
  }
}
