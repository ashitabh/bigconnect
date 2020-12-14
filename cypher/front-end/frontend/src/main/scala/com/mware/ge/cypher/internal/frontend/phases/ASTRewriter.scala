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
package com.mware.ge.cypher.internal.frontend.phases

import com.mware.ge.cypher.internal.ast.semantics.SemanticState
import com.mware.ge.cypher.internal.ast.{Statement, UnaliasedReturnItem}
import com.mware.ge.cypher.internal.expressions.NotEquals
import com.mware.ge.cypher.internal.rewriting.RewriterStep._
import com.mware.ge.cypher.internal.rewriting.conditions._
import com.mware.ge.cypher.internal.rewriting.rewriters.{replaceLiteralDynamicPropertyLookups, _}
import com.mware.ge.cypher.internal.rewriting.{RewriterCondition, RewriterStepSequencer}

class ASTRewriter(rewriterSequencer: String => RewriterStepSequencer,
                  literalExtraction: LiteralExtraction,
                  getDegreeRewriting: Boolean) {

  def rewrite(queryText: String, statement: Statement, semanticState: SemanticState): (Statement, Map[String, Any], Set[RewriterCondition]) = {

    val contract = rewriterSequencer("ASTRewriter")(
      recordScopes(semanticState),
      desugarMapProjection(semanticState),
      normalizeComparisons,
      enableCondition(noReferenceEqualityAmongVariables),
      enableCondition(containsNoNodesOfType[UnaliasedReturnItem]),
      enableCondition(noDuplicatesInReturnItems),
      expandStar(semanticState),
      enableCondition(containsNoReturnAll),
      foldConstants,
      nameMatchPatternElements,
      nameUpdatingClauses,
      enableCondition(noUnnamedPatternElementsInMatch),
      normalizeMatchPredicates(getDegreeRewriting),
      normalizeNotEquals,
      enableCondition(containsNoNodesOfType[NotEquals]),
      normalizeArgumentOrder,
      normalizeSargablePredicates,
      enableCondition(normalizedEqualsArguments),
      addUniquenessPredicates,
      replaceLiteralDynamicPropertyLookups,
      namePatternComprehensionPatternElements,
      enableCondition(noUnnamedPatternElementsInPatternComprehension),
      inlineNamedPathsInPatternComprehensions,
      addImplicitExistToPatternExpressions(semanticState)
    )

    val rewrittenStatement = statement.endoRewrite(contract.rewriter)
    val (extractParameters, extractedParameters) = literalReplacement(rewrittenStatement, literalExtraction)

    (rewrittenStatement.endoRewrite(extractParameters), extractedParameters, contract.postConditions)
  }
}
