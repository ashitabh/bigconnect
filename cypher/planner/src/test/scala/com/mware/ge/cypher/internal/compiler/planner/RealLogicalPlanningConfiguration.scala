/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mware.ge.cypher.internal.compiler.planner

import com.mware.ge.cypher.internal.compiler.CypherPlannerConfiguration
import com.mware.ge.cypher.internal.compiler.planner.logical.Metrics.{CardinalityModel, QueryGraphCardinalityModel, QueryGraphSolverInput}
import com.mware.ge.cypher.internal.compiler.planner.logical.{CardinalityCostModel, ExpressionEvaluator, Metrics, StatisticsBackedCardinalityModel}
import com.mware.ge.cypher.internal.ir.QueryGraph
import com.mware.ge.cypher.internal.planner.spi.GraphStatistics
import com.mware.ge.cypher.internal.planner.spi.PlanningAttributes.Cardinalities
import com.mware.ge.cypher.internal.logical.plans.{LogicalPlan, ProcedureSignature}
import com.mware.ge.cypher.internal.util.{Cardinality, Cost}

case class RealLogicalPlanningConfiguration(cypherCompilerConfig: CypherPlannerConfiguration)
  extends LogicalPlanningConfiguration with LogicalPlanningConfigurationAdHocSemanticTable {

  override def cardinalityModel(queryGraphCardinalityModel: QueryGraphCardinalityModel, evaluator: ExpressionEvaluator): CardinalityModel = {
    new StatisticsBackedCardinalityModel(queryGraphCardinalityModel, evaluator)
  }

  //noinspection ScalaUnnecessaryParentheses
  override def costModel(): PartialFunction[(LogicalPlan, QueryGraphSolverInput, Cardinalities), Cost] = {
    val model: Metrics.CostModel = CardinalityCostModel(cypherCompilerConfig)
    ({
      case (plan: LogicalPlan, input: QueryGraphSolverInput, cardinalities: Cardinalities) => model(plan, input, cardinalities)
    })
  }

  override def graphStatistics: GraphStatistics = HardcodedGraphStatistics
  override def indexes: Map[IndexDef, IndexType] = Map.empty
  override def procedureSignatures: Set[ProcedureSignature] = Set.empty
  override def labelCardinality: Map[String, Cardinality] = Map.empty
  override def knownLabels: Set[String] = Set.empty
  override def labelsById: Map[String, String] = Map.empty

  override def qg: QueryGraph = ???
}
