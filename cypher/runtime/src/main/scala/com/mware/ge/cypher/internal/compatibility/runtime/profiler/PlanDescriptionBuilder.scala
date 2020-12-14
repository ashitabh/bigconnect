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
package com.mware.ge.cypher.internal.compatibility.runtime.profiler

import com.mware.ge.cypher.internal.compatibility.runtime.RuntimeName
import com.mware.ge.cypher.internal.planner.spi.PlanningAttributes.{Cardinalities, ProvidedOrders}
import com.mware.ge.cypher.internal.runtime.planDescription.InternalPlanDescription.Arguments
import com.mware.ge.cypher.internal.runtime.planDescription.InternalPlanDescription.Arguments.{Runtime, RuntimeImpl}
import com.mware.ge.cypher.internal.runtime.planDescription.{Argument, InternalPlanDescription, LogicalPlan2PlanDescription}
import com.mware.ge.cypher.internal.logical.plans.LogicalPlan
import com.mware.ge.cypher.result.{OperatorProfile, QueryProfile}
import com.mware.ge.cypher.internal.frontend.PlannerName

class PlanDescriptionBuilder(logicalPlan: LogicalPlan,
                             plannerName: PlannerName,
                             readOnly: Boolean,
                             cardinalities: Cardinalities,
                             providedOrders: ProvidedOrders,
                             runtimeName: RuntimeName,
                             metadata: Seq[Argument]) {

  def explain(): InternalPlanDescription = {
    val description =
      LogicalPlan2PlanDescription(logicalPlan, plannerName, readOnly, cardinalities, providedOrders)
        .addArgument(Runtime(runtimeName.toTextOutput))
        .addArgument(RuntimeImpl(runtimeName.name))

    metadata.foldLeft(description)((plan, metadata) => plan.addArgument(metadata))
  }

  def profile(queryProfile: QueryProfile): InternalPlanDescription = {

    val planDescription = explain()

    planDescription map {
      input: InternalPlanDescription =>
        val data = queryProfile.operatorProfile(input.id.x)

        BuildPlanDescription(input)
          .addArgument(Arguments.Rows, data.rows)
          .addArgument(Arguments.DbHits, data.dbHits)
          .addArgument(Arguments.Time, data.time())
        .plan
    }
  }

  case class BuildPlanDescription(plan: InternalPlanDescription) {

    def addArgument[T](argument: T => Argument,
                       value: T): BuildPlanDescription =
      if (value == OperatorProfile.NO_DATA) this
      else BuildPlanDescription(plan.addArgument(argument(value)))
  }
}
