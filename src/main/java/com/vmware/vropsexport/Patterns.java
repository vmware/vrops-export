/*
 * Copyright 2017-2021 VMware, Inc. All Rights Reserved.
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

import java.util.regex.Pattern;

public class Patterns {

  public static final Pattern relationshipPattern =
      Pattern.compile("^\\$(parent|child):([_A-Za-z][_A-Za-z0-9\\s]*)\\.(.+)$");

  public static final int relationshipKindGroup = 1;
  public static final int relationshipResourceKindGroup = 2;
  public static final int relationshipMemberGroup = 3;

  public static final Pattern parentSpecPattern =
      Pattern.compile("^([_\\-A-Za-z][_\\-A-Za-z0-9\\s]*):(.+)$");

  public static final Pattern adapterAndResourceKindPattern =
      Pattern.compile("^([_\\-A-Za-z][_\\-A-Za-z0-9\\s]*):(.+)$");
}
