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
package com.mware.ge.cypher.internal.compiler.planner

import com.mware.ge.cypher.internal.ir._
import com.mware.ge.cypher.internal.logical.plans.{ProcedureReadOnlyAccess, ResolvedCall}

case class ProcedureCallProjection(call: ResolvedCall) extends QueryHorizon {
  override def exposedSymbols(coveredIds: Set[String]): Set[String] = coveredIds ++ call.callResults.map { result => result.variable.name }

  override def dependingExpressions = call.callArguments

  override def preferredStrictness = call.signature.accessMode match {
    case _:ProcedureReadOnlyAccess => Some(LazyMode)
    case _ => Some(EagerMode)
  }

  override def readOnly = call.containsNoUpdates
}
