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
package com.mware.ge.cypher.internal.rewriting

import com.mware.ge.cypher.internal.ast.Statement
import com.mware.ge.cypher.internal.ast.semantics.{SemanticState, SyntaxExceptionCreator}
import com.mware.ge.cypher.internal.parser.ParserFixture.parser
import com.mware.ge.cypher.internal.rewriting.rewriters.{desugarMapProjection, normalizeWithAndReturnClauses, recordScopes}
import com.mware.ge.cypher.internal.util.test_helpers.CypherFunSuite
import com.mware.ge.cypher.internal.util.{Rewriter, inSequence}

class DesugarDesugaredMapProjectionTest extends CypherFunSuite {

  assertRewrite(
    "match (n) return n{k:42} as x",
    "match (n) return n{k:42} as x")

  assertRewrite(
    "match (n) return n{.id} as x",
    "match (n) return n{id: n.id} as x")

  assertRewrite(
    "with '42' as existing match (n) return n{existing} as x",
    "with '42' as existing match (n) return n{existing: existing} as x")

  assertRewrite(
    "match (n) return n{.foo,.bar,.baz} as x",
    "match (n) return n{foo: n.foo, bar: n.bar, baz: n.baz} as x")

  assertRewrite(
    "match (n) return n{.*, .apa} as x",
    "match (n) return n{.*, apa: n.apa} as x"
  )

  assertRewrite(
    """match (n), (m)
      |return n {
      | .foo,
      | .bar,
      | inner: m {
      |   .baz,
      |   .apa
      | }
      |} as x""".stripMargin,

    """match (n), (m)
      |return n {
      | foo: n.foo,
      | bar: n.bar,
      | inner: m {
      |   baz: m.baz,
      |   apa: m.apa
      | }
      |} as x""".stripMargin)

  def assertRewrite(originalQuery: String, expectedQuery: String) {
    test(originalQuery + " is rewritten to " + expectedQuery) {
      def rewrite(q: String): Statement = {
        val mkException = new SyntaxExceptionCreator(originalQuery, None)
        val sequence: Rewriter = inSequence(normalizeWithAndReturnClauses(mkException))
        val originalAst = parser.parse(q).endoRewrite(sequence)
        val semanticCheckResult = originalAst.semanticCheck(SemanticState.clean)
        val withScopes = originalAst.endoRewrite(recordScopes(semanticCheckResult.state))

        withScopes.endoRewrite(desugarMapProjection(semanticCheckResult.state))
      }

      val rewrittenOriginal = rewrite(originalQuery)
      val rewrittenExpected = rewrite(expectedQuery)

      assert(rewrittenOriginal === rewrittenExpected)

    }
  }
}
