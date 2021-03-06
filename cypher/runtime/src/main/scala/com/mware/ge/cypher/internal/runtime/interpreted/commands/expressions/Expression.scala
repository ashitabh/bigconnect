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
import com.mware.ge.cypher.internal.runtime.interpreted.ExecutionContext
import com.mware.ge.cypher.internal.runtime.interpreted.commands.AstNode
import com.mware.ge.cypher.internal.runtime.interpreted.commands.predicates.{CoercedPredicate, Predicate}
import com.mware.ge.cypher.internal.runtime.interpreted.pipes.{Pipe, QueryState}
import com.mware.ge.cypher.internal.runtime.interpreted.symbols.TypeSafe
import com.mware.ge.cypher.internal.util.symbols.CypherType
import com.mware.ge.values.storable.Values

abstract class Expression extends TypeSafe with AstNode[Expression] {

  // WARNING: MUTABILITY IN IMMUTABLE CLASSES ...
  private var _owningPipe: Option[Pipe] = None

  def owningPipe: Option[Pipe] = _owningPipe

  def registerOwningPipe(pipe: Pipe): Unit = visit {
    case x:Expression => x._owningPipe = Some(pipe)
  }
  // ... TREAD WITH CAUTION

  def rewrite(f: Expression => Expression): Expression

  def rewriteAsPredicate(f: Expression => Expression): Predicate = rewrite(f) match {
    case pred: Predicate => pred
    case e               => CoercedPredicate(e)
  }

  def subExpressions: Seq[Expression] = {
    def expandAll(e: AstNode[_]): Seq[AstNode[_]] = e.children ++ e.children.flatMap(expandAll)
    expandAll(this).collect {
      case e:Expression => e
    }
  }

  // TODO check overrides here

  // Expressions that do not get anything in their context from this expression.
  def arguments: Seq[Expression]

  // Any expressions that this expression builds on
  def children: Seq[AstNode[_]]

  def containsAggregate = exists(_.isInstanceOf[AggregationExpression])

  def apply(ctx: ExecutionContext, state: QueryState): AnyValue

  override def toString = this match {
    case p: Product => scala.runtime.ScalaRunTime._toString(p)
    case _          => getClass.getSimpleName
  }

  val isDeterministic = ! exists {
    case RandFunction() => true
    case _              => false
  }
}

case class CachedExpression(key:String, typ:CypherType) extends Expression {
  def apply(ctx: ExecutionContext, state: QueryState) = ctx(key)

  override def rewrite(f: Expression => Expression): Expression = f(this)

  override def arguments: Seq[Expression] = Seq.empty

  override def children: Seq[AstNode[_]] = Seq.empty

  override def symbolTableDependencies: Set[String] = Set(key)

  override def toString = "Cached(%s of type %s)".format(key, typ)
}

abstract class Arithmetics(left: Expression, right: Expression) extends Expression {
  override def apply(ctx: ExecutionContext, state: QueryState): AnyValue = {
    val aVal = left(ctx, state)
    val bVal = right(ctx, state)

    applyWithValues(aVal, bVal)
  }

  protected def applyWithValues(aVal: AnyValue, bVal: AnyValue): AnyValue = {
    (aVal, bVal) match {
      case (x, y) if x == Values.NO_VALUE || y == Values.NO_VALUE => Values.NO_VALUE
      case (x, y) => calc(x, y)
    }
  }

  def calc(a: AnyValue, b: AnyValue): AnyValue

  override def arguments: Seq[Expression] = Seq(left, right)

  override def symbolTableDependencies: Set[String] = left.symbolTableDependencies ++ left.symbolTableDependencies
}

trait ExtendedExpression extends Expression {
  def legacy: Expression
}
