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

import com.mware.ge.cypher.internal.expressions
import com.mware.ge.cypher.internal.expressions.DummyExpression
import com.mware.ge.cypher.internal.expressions.{ContainerIndex, DecimalDoubleLiteral, SignedDecimalIntegerLiteral}
import com.mware.ge.cypher.internal.util.DummyPosition
import com.mware.ge.cypher.internal.util.symbols._

class ContainerIndexTest extends SemanticFunSuite {

  val dummyString = DummyExpression(CTString)
  val dummyInteger = expressions.DummyExpression(CTInteger)
  val dummyNode = expressions.DummyExpression(CTNode)
  val dummyAny = expressions.DummyExpression(CTAny)
  val dummyList = expressions.DummyExpression(CTList(CTNode) | CTList(CTString))

  test("should detect list lookup") {
    val lhs = dummyList
    val rhs = dummyInteger
    val index = ContainerIndex(lhs, rhs)(DummyPosition(10))

    val result = SemanticExpressionCheck.simple(index)(SemanticState.clean)
    result.errors shouldBe empty
    assertIsList(types(lhs)(result.state))
    types(rhs)(result.state) should equal(CTInteger.covariant)
    types(index)(result.state) should equal(CTNode | CTString)
  }

  test("should detect node lookup") {
    val lhs = dummyNode
    val rhs = dummyString
    val index = ContainerIndex(lhs, rhs)(DummyPosition(10))

    val result = SemanticExpressionCheck.simple(index)(SemanticState.clean)
    result.errors shouldBe empty
    assertIsMap(types(lhs)(result.state))
    types(rhs)(result.state) should equal(CTString.covariant)
    types(index)(result.state) should equal(CTAny.covariant)
  }

  test("should type as any if given untyped lookup arguments") {
    val lhs = dummyAny
    val rhs = dummyAny
    val index = ContainerIndex(lhs, rhs)(DummyPosition(10))

    val result = SemanticExpressionCheck.simple(index)(SemanticState.clean)
    result.errors shouldBe empty
    types(lhs)(result.state) should equal(CTAny.contravariant)
    types(rhs)(result.state) should equal(CTAny.contravariant)
    types(index)(result.state) should equal(TypeSpec.all)
  }

  test("should return list inner types of expression") {
    val index = ContainerIndex(dummyList,
      SignedDecimalIntegerLiteral("1")(DummyPosition(5))
    )(DummyPosition(4))

    val result = SemanticExpressionCheck.simple(index)(SemanticState.clean)
    result.errors shouldBe empty
    types(index)(result.state) should equal(CTNode | CTString)
  }

  test("should raise error if indexing by fraction") {
    val index = ContainerIndex(dummyList,
      DecimalDoubleLiteral("1.3")(DummyPosition(5))
    )(DummyPosition(4))

    val result = SemanticExpressionCheck.simple(index)(SemanticState.clean)
    result.errors should equal(Seq(SemanticError("Type mismatch: expected Integer but was Float", index.idx.position)))
  }

  private def assertIsList(spec: TypeSpec) = {
    val intersection = spec & CTList(CTAny).covariant
    (intersection == TypeSpec.none) should be(right = false)
    spec should equal(intersection)
  }

  private def assertIsMap(spec: TypeSpec) = {
    val intersection = spec & CTMap.covariant
    (intersection == TypeSpec.none) should be(right = false)
    spec should equal(intersection)
  }
}
