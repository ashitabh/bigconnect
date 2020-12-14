/*
 * Copyright (c) 2013-2020 "BigConnect,"
 * MWARE SOLUTIONS SRL
 *
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
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
package com.mware.ge.cypher.internal.ast.semantics

import com.mware.ge.cypher.internal.expressions.DummyExpression
import com.mware.ge.cypher.internal.expressions.{FilterExpression, True}
import com.mware.ge.cypher.internal.util.DummyPosition
import com.mware.ge.cypher.internal.util.symbols._

class FilterExpressionTest extends SemanticFunSuite {

  val dummyExpression = DummyExpression(
    possibleTypes = CTList(CTNode) | CTBoolean | CTList(CTString)
  )

  test("shouldHaveCollectionTypesOfInnerExpression") {
    val filter = FilterExpression(
      variable = variable("x"),
      expression = dummyExpression,
      innerPredicate = Some(True()(DummyPosition(5)))
    )(DummyPosition(0))
    val result = SemanticExpressionCheck.simple(filter)(SemanticState.clean)
    result.errors shouldBe empty
    types(filter)(result.state) should equal(CTList(CTNode) | CTList(CTString))
  }

  test("shouldRaiseSyntaxErrorIfMissingPredicate") {
    val filter = FilterExpression(
      variable = variable("x"),
      expression = dummyExpression,
      innerPredicate = None
    )(DummyPosition(0))
    val result = SemanticExpressionCheck.simple(filter)(SemanticState.clean)
    result.errors should equal(Seq(SemanticError("filter(...) requires a WHERE predicate", DummyPosition(0))))
  }
}
