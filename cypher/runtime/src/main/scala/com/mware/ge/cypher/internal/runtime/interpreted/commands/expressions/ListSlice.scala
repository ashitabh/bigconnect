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
package com.mware.ge.cypher.internal.runtime.interpreted.commands.expressions

import com.mware.ge.values.AnyValue
import com.mware.ge.cypher.internal.runtime.interpreted.commands.AstNode
import com.mware.ge.cypher.internal.runtime.interpreted.pipes.QueryState
import com.mware.ge.cypher.internal.runtime.interpreted.{CastSupport, ExecutionContext, ListSupport}
import com.mware.ge.cypher.operations.CypherFunctions
import com.mware.ge.values.storable.{NumberValue, Values}

case class ListSlice(collection: Expression, from: Option[Expression], to: Option[Expression])
  extends NullInNullOutExpression(collection) with ListSupport {
  override def arguments: Seq[Expression] = from.toIndexedSeq ++ to.toIndexedSeq :+ collection

  override def children: Seq[AstNode[_]] = arguments

  private val function: (AnyValue, ExecutionContext, QueryState) => AnyValue =
    (from, to) match {
      case (Some(f), Some(n)) => fullSlice(f, n)
      case (Some(f), None)    => fromSlice(f)
      case (None, Some(f))    => toSlice(f)
      case (None, None)       => (coll, _, _) => coll
    }

  private def fullSlice(from: Expression, to: Expression)(collectionValue: AnyValue, ctx: ExecutionContext, state: QueryState) = {
    val fromValue = from(ctx, state)
    val toValue = to(ctx, state)
    if ( fromValue == Values.NO_VALUE || toValue == Values.NO_VALUE ) Values.NO_VALUE
    else CypherFunctions.fullSlice(collectionValue, fromValue, toValue)
  }

  private def fromSlice(from: Expression)(collectionValue: AnyValue, ctx: ExecutionContext, state: QueryState) = {
    val fromValue = from(ctx, state)
    if ( fromValue == Values.NO_VALUE ) Values.NO_VALUE
    else CypherFunctions.fromSlice(collectionValue, fromValue)
  }

  private def toSlice(from: Expression)(collectionValue: AnyValue, ctx: ExecutionContext, state: QueryState) = {
    val toValue = from(ctx, state)
    if ( toValue == Values.NO_VALUE ) Values.NO_VALUE
    else CypherFunctions.toSlice(collectionValue, toValue)
  }

  def asInt(e: Expression, ctx: ExecutionContext, state: QueryState): Option[Int] = {
    val index = e(ctx, state)
    if (index == Values.NO_VALUE) None
    else Some(CastSupport.castOrFail[NumberValue](index).longValue().toInt)
  }

  override def compute(value: AnyValue, ctx: ExecutionContext, state: QueryState): AnyValue =
    function(value, ctx, state)

  override def rewrite(f: Expression => Expression): Expression =
    f(ListSlice(collection.rewrite(f), from.map(_.rewrite(f)), to.map(_.rewrite(f))))

  override def symbolTableDependencies: Set[String] = arguments.flatMap(_.symbolTableDependencies).toSet
}
