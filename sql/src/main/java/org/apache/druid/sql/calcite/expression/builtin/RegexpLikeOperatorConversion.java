/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.sql.calcite.expression.builtin;

import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.druid.query.filter.DimFilter;
import org.apache.druid.query.filter.RegexDimFilter;
import org.apache.druid.segment.column.RowSignature;
import org.apache.druid.sql.calcite.expression.DruidExpression;
import org.apache.druid.sql.calcite.expression.Expressions;
import org.apache.druid.sql.calcite.expression.OperatorConversions;
import org.apache.druid.sql.calcite.expression.SqlOperatorConversion;
import org.apache.druid.sql.calcite.planner.PlannerContext;
import org.apache.druid.sql.calcite.rel.VirtualColumnRegistry;

import javax.annotation.Nullable;
import java.util.List;

public class RegexpLikeOperatorConversion implements SqlOperatorConversion
{
  private static final SqlFunction SQL_FUNCTION = OperatorConversions
      .operatorBuilder("REGEXP_LIKE")
      .operandTypes(SqlTypeFamily.CHARACTER, SqlTypeFamily.CHARACTER)
      .requiredOperandCount(2)
      .literalOperands(1)
      .returnTypeCascadeNullable(SqlTypeName.BOOLEAN)
      .functionCategory(SqlFunctionCategory.STRING)
      .build();

  @Override
  public SqlFunction calciteOperator()
  {
    return SQL_FUNCTION;
  }

  @Override
  public DruidExpression toDruidExpression(
      final PlannerContext plannerContext,
      final RowSignature rowSignature,
      final RexNode rexNode
  )
  {
    return OperatorConversions.convertDirectCall(
        plannerContext,
        rowSignature,
        rexNode,
        "regexp_like"
    );
  }

  @Nullable
  @Override
  public DimFilter toDruidFilter(
      final PlannerContext plannerContext,
      final RowSignature rowSignature,
      @Nullable final VirtualColumnRegistry virtualColumnRegistry,
      final RexNode rexNode
  )
  {
    final List<RexNode> operands = ((RexCall) rexNode).getOperands();
    final DruidExpression druidExpression = Expressions.toDruidExpression(
        plannerContext,
        rowSignature,
        operands.get(0)
    );

    if (druidExpression == null) {
      return null;
    }

    final String pattern = RexLiteral.stringValue(operands.get(1));

    if (druidExpression.isSimpleExtraction()) {
      return new RegexDimFilter(
          druidExpression.getSimpleExtraction().getColumn(),
          pattern,
          druidExpression.getSimpleExtraction().getExtractionFn(),
          null
      );
    } else if (virtualColumnRegistry != null) {
      String v = virtualColumnRegistry.getOrCreateVirtualColumnForExpression(
          druidExpression,
          operands.get(0).getType()
      );

      return new RegexDimFilter(v, pattern, null, null);
    } else {
      return null;
    }
  }
}
