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

import com.mware.ge.cypher.internal.ast.semantics.Scope
import com.mware.ge.cypher.internal.ast.semantics.SemanticTable
import com.mware.ge.cypher.internal.ast.semantics.SymbolUse
import com.mware.ge.cypher.internal.ast.Statement
import com.mware.ge.cypher.internal.ast._
import com.mware.ge.cypher.internal.expressions.LogicalVariable
import com.mware.ge.cypher.internal.expressions.ProcedureOutput
import com.mware.ge.cypher.internal.expressions.Variable
import com.mware.ge.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase
import com.mware.ge.cypher.internal.frontend.phases.CompilationPhaseTracer.CompilationPhase.AST_REWRITE
import com.mware.ge.cypher.internal.util.Ref
import com.mware.ge.cypher.internal.util.Rewriter
import com.mware.ge.cypher.internal.util.bottomUp
import com.mware.ge.cypher.internal.util.inSequence

object Namespacer extends Phase[BaseContext, BaseState, BaseState] {
  type VariableRenamings = Map[Ref[Variable], Variable]

  override def phase: CompilationPhase = AST_REWRITE

  override def description: String = "rename variables so they are all unique"

  override def process(from: BaseState, ignored: BaseContext): BaseState = {
    val ambiguousNames = shadowedNames(from.semantics().scopeTree)
    val variableDefinitions: Map[SymbolUse, SymbolUse] = from.semantics().scopeTree.allVariableDefinitions
    val protectedVariables = protectedVarsInStatement(from.statement())
    val renamings = variableRenamings(from.statement(), variableDefinitions, ambiguousNames, protectedVariables)

    val rewriter = renamingRewriter(renamings)
    val newStatement = from.statement().endoRewrite(rewriter)
    val table = SemanticTable(types = from.semantics().typeTable, recordedScopes = from.semantics().recordedScopes)

    val newSemanticTable = table.replaceExpressions(rewriter)
    from.withStatement(newStatement).withSemanticTable(newSemanticTable)
  }

  override def postConditions: Set[Condition] = Set.empty

  private def shadowedNames(scopeTree: Scope): Set[String] = {
    val definitions = scopeTree.allSymbolDefinitions

    definitions.collect {
      case (name, symbolDefinitions) if symbolDefinitions.size > 1 => name
    }.toSet
  }

  private def protectedVarsInStatement(statement: Statement): Set[Ref[LogicalVariable]] =
    statement.treeFold(Set.empty[Ref[LogicalVariable]]) {

      case _: With =>
        acc => (acc, Some(identity))

      case Return(_, ReturnItems(_, items), orderBy, _, _, _) =>
        val variablesInReturn = items.map(_.alias.get)
        val refVars = variablesInReturn.map(Ref[LogicalVariable])
        // If the order by refers to the alias introduced in the return, it is also protected
        val expressionsInOrderBy = for {
          order <- orderBy.toSeq
          sortItem <- order.sortItems
        } yield sortItem.expression

        val variablesInOrderBy = expressionsInOrderBy.findByAllClass[LogicalVariable]

        val protectedVariablesInOrderBy = variablesInOrderBy.filter(variablesInReturn.contains).map(Ref[LogicalVariable])
        acc => (acc ++ refVars ++ protectedVariablesInOrderBy, Some(identity))
    }

  private def variableRenamings(statement: Statement, variableDefinitions: Map[SymbolUse, SymbolUse],
                                ambiguousNames: Set[String], protectedVariables: Set[Ref[LogicalVariable]]): VariableRenamings =
    statement.treeFold(Map.empty[Ref[Variable], Variable]) {
      case i: Variable if ambiguousNames(i.name) && !protectedVariables(Ref(i)) =>
        val symbolDefinition = variableDefinitions(SymbolUse(i))
        val newVariable = i.renameId(s"  ${symbolDefinition.nameWithPosition}")
        val renaming = Ref(i) -> newVariable
        acc => (acc + renaming, Some(identity))
    }

  private def renamingRewriter(renamings: VariableRenamings): Rewriter = inSequence(
    bottomUp(Rewriter.lift {
      case item@ProcedureResultItem(None, v: Variable) if renamings.contains(Ref(v)) =>
        item.copy(output = Some(ProcedureOutput(v.name)(v.position)))(item.position)
    }),
    bottomUp(Rewriter.lift {
      case v: Variable =>
        renamings.get(Ref(v)) match {
          case Some(newVariable) => newVariable
          case None              => v
        }
    }))

}
