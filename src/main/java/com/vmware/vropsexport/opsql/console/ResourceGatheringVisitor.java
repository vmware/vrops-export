/*
 *
 * Copyright 2017-2023 VMware, Inc. All Rights Reserved.
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
 *
 */

package com.vmware.vropsexport.opsql.console;

import com.vmware.vropsexport.opsql.OpsqlBaseVisitor;
import com.vmware.vropsexport.opsql.OpsqlParser;

import java.util.HashMap;
import java.util.Map;

public class ResourceGatheringVisitor extends OpsqlBaseVisitor {
  private final Map<String, String> aliasToType = new HashMap<>();

  @Override
  public Object visitRelativePropertyIdenfifier(
      final OpsqlParser.RelativePropertyIdenfifierContext ctx) {
    return super.visitRelativePropertyIdenfifier(ctx);
  }

  @Override
  public Object visitParentsDeclaration(final OpsqlParser.ParentsDeclarationContext ctx) {
    for (final OpsqlParser.RelationshipSpecifierContext rel :
        ctx.relatives.relationshipSpecifier()) {
      aliasToType.put(rel.alias.getText(), rel.resourceType.getText());
    }
    return super.visitParentsDeclaration(ctx);
  }

  @Override
  public Object visitChildrenDeclaration(final OpsqlParser.ChildrenDeclarationContext ctx) {
    for (final OpsqlParser.RelationshipSpecifierContext rel :
        ctx.relatives.relationshipSpecifier()) {
      aliasToType.put(rel.alias.getText(), rel.resourceType.getText());
    }
    return super.visitChildrenDeclaration(ctx);
  }
}
