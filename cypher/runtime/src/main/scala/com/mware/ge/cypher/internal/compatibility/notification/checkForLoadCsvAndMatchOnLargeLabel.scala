/*
 * Copyright (c) 2013-2020 "BigConnect,"
 * MWARE SOLUTIONS SRL
 *
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
package com.mware.ge.cypher.internal.compatibility.notification

import com.mware.ge.cypher.internal.planner.spi.PlanContext
import com.mware.ge.cypher.internal.compiler.LargeLabelWithLoadCsvNotification
import com.mware.ge.cypher.internal.logical.plans.{LoadCSV, LogicalPlan, NodeByLabelScan}
import com.mware.ge.cypher.internal.util._

case class checkForLoadCsvAndMatchOnLargeLabel(planContext: PlanContext,
                                               nonIndexedLabelWarningThreshold: Long
                                              ) extends NotificationChecker {

  private val threshold = Cardinality(nonIndexedLabelWarningThreshold)

  def apply(plan: LogicalPlan): Seq[InternalNotification] = {
    import com.mware.ge.cypher.internal.util.Foldable._

    sealed trait SearchState
    case object NoneFound extends SearchState
    case object LargeLabelFound extends SearchState
    case object LargeLabelWithLoadCsvFound extends SearchState

    // Walk over the pipe tree and check if a large label scan is to be executed after a LoadCsv
    val resultState = plan.reverseTreeFold[SearchState](NoneFound) {
      case _: LoadCSV => {
        case LargeLabelFound => (LargeLabelWithLoadCsvFound, Some(identity))
        case e => (e, None)
      }
      case NodeByLabelScan(_, label, _) if cardinality(label.name) > threshold =>
        acc => (LargeLabelFound, Some(identity))
    }

    resultState match {
      case LargeLabelWithLoadCsvFound => Seq(LargeLabelWithLoadCsvNotification)
      case _ => Seq.empty
    }
  }

  private def cardinality(labelName: String): Cardinality =
    planContext.statistics.nodesWithLabelCardinality(planContext.getOptLabelId(labelName).map(LabelId), planContext)
}
