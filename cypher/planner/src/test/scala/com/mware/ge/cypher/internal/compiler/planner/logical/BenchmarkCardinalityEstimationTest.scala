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
package com.mware.ge.cypher.internal.compiler.planner.logical

import com.mware.ge.cypher.internal.compiler.planner.LogicalPlanningTestSupport2
import com.mware.ge.cypher.internal.util.test_helpers.CypherFunSuite

class BenchmarkCardinalityEstimationTest extends CypherFunSuite with LogicalPlanningTestSupport2 {
  implicit class RichLogicalPlanningEnvironment(val env: LogicalPlanningEnvironment[_]) {
    def assertNoRegression(query: String, actual: Double, expectedDifference: Double, allowedSlack: Double = 0.05): Unit = {
      val (_, plan, semanticTable, solveds, _) = env.getLogicalPlanFor(query)
      val qg = solveds.get(plan.id).queryGraph
      val estimate = env.estimate(qg).amount
      val currentDifference = Math.abs(estimate - actual)

      val differenceDifference = Math.abs(currentDifference - expectedDifference)
      val differenceDifferenceMargin = Math.round(expectedDifference * allowedSlack)

      if (differenceDifference > differenceDifferenceMargin) {
        val builder = new StringBuilder
        builder.append(s"Estimate changed by more than ${allowedSlack * 100} % for query $query:\n")
        builder.append(s" - actual cardinality: $actual\n")
        builder.append(s" - estimated cardinality: $estimate\n")
        builder.append(s" - expected difference: $expectedDifference ± ${differenceDifferenceMargin / 2}\n")
        builder.append(s" - actual difference: $currentDifference\n")

        // println(builder)
        // println()

        throw new AssertionError(builder.toString())
      }
    }
  }

}
