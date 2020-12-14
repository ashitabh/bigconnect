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
package com.mware.ge.cypher.internal.result.string

import java.io.PrintWriter
import java.util

import com.mware.ge.cypher.Result.{ResultRow, ResultVisitor}
import com.mware.ge.cypher.exception.NotFoundException
import com.mware.ge.cypher.{Path, QueryStatistics}
import com.mware.ge.{Edge, Element, Vertex}
import com.mware.ge.cypher.internal.runtime.interpreted.commands.values.KeyToken
import com.mware.ge.cypher.internal.runtime.{QueryContext, RuntimeScalaValueConverter, isGraphKernelResultValue}
import com.mware.ge.values.virtual.{NodeValue, RelationshipValue}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
  * Assembles result rows into a nicely formatted string table.
  *
  * Note: this class might be used by the docs build. Consider not changing it's
  * signature if that's what you are doing.
  */
object ResultStringBuilder {

  /**
    * Create [[ResultStringBuilder]] without the capability to determine if
    * entities have been deleted in this transaction.
    *
    * @param columns the result columns
    */
  def apply(columns: Array[String]): ResultStringBuilder =
    new ResultStringBuilder(columns, NoTransactionSupport)

  /**
    * Create [[ResultStringBuilder]] which uses a [[QueryTransactionalContext]] to annotate if
    * entities have been deleted in this transaction.
    *
    * @param columns the result columns
    * @param txContext the transactional context
    */
  def apply(columns: Array[String], txContext: QueryContext): ResultStringBuilder =
    new ResultStringBuilder(columns, InternalTransactionSupport(txContext))

  // INTERNALS

  trait DeletedInTx {
    def node(id: String): Boolean
    def relationship(id: String): Boolean
  }

  object NoTransactionSupport extends DeletedInTx {
    override def node(id: String): Boolean = false
    override def relationship(id: String): Boolean = false
  }

  case class InternalTransactionSupport(transactionalContext: QueryContext) extends DeletedInTx {
    override def node(id: String): Boolean =
      transactionalContext.nodeOps.isDeletedInThisTx(id)

    override def relationship(id: String): Boolean =
      transactionalContext.relationshipOps.isDeletedInThisTx(id)
  }
}

/**
  * The actual builder.
  */
class ResultStringBuilder private(columns: Array[String],
                                  deletedInTx: ResultStringBuilder.DeletedInTx) extends ResultVisitor[Exception] {

  private val scalaValues = new RuntimeScalaValueConverter(isGraphKernelResultValue)
  private val rows = new ArrayBuffer[Array[String]]

  // ADD ROWS

  /**
    * Add a row to this result string.
    */
  def addRow(row: ResultRow): Unit = {
    val stringRow = new Array[String](columns.length)
    for (i <- columns.indices) {
      stringRow(i) = asTextValue(row.get(columns(i)))
    }
    rows += stringRow
  }

  /**
    * addRow variant that implements [[ResultVisitor]]
    */
  override def visit(row: ResultRow): Boolean = {
    addRow(row)
    true
  }

  // PRODUCE RESULT

  /**
    * Produce result by constructing a String which is returned.
    */
  def result(queryStatistics: QueryStatistics): String = {
    val sb = new StringBuilder
    result(sb, queryStatistics)
    sb.result()
  }

  /**
    * Produce result by printing all lines to a provided [[PrintWriter]].
    */
  def result(writer: PrintWriter, queryStatistics: QueryStatistics): Unit =
    FormatOutput.format(PrintWriterWrapper(writer), columns, rows, queryStatistics)

  /**
    * Produce result by printing all lines to a provided [[StringBuilder]].
    */
  def result(sb: StringBuilder, queryStatistics: QueryStatistics): Unit =
    FormatOutput.format(StringBuilderWrapper(sb), columns, rows, queryStatistics)

  // HELPERS

  private def asTextValue(a: Any): String = {
    val scalaValue = scalaValues.asShallowScalaValue(a)
    scalaValue match {
      case node: Vertex => s"Node[${node.getId}]${props(node)}"
      case relationship: Edge => s":${relationship.getLabel}[${relationship.getId}]${props(relationship)}"
      case path: Path => pathAsTextValue(path)
      case map: Map[_, _] => makeString(map)
      case opt: Option[_] => opt.map(asTextValue).getOrElse("None")
      case array: Array[_] => array.map(elem => asTextValue(elem)).mkString("[", ",", "]")
      case iterable: Iterable[_] => iterable.map(elem => asTextValue(elem)).mkString("[", ",", "]")
      case str: String => "\"" + str + "\""
      case token: KeyToken => token.name
      case null => "<null>"
      case value => value.toString
    }
  }

  private def makeString(m: Map[_, _]) = m.map { case (k, v) => s"$k -> ${asTextValue(v)}" }.mkString("{", ", ", "}")

  private def pathAsTextValue(path: Path): String = {
    val nodes = path.nodes().iterator()
    val relationships = path.relationships().iterator()
    val sb = new StringBuilder

    def formatNode(n: NodeValue) = {
      val isDeleted = deletedInTx.node(n.id)
      val deletedString = if (isDeleted) ",deleted" else ""
      sb ++= s"(${n.id}$deletedString)"
    }

    def formatRelationship(leftNode: NodeValue, r: RelationshipValue) = {
      val isDeleted = deletedInTx.relationship(r.id)
      val deletedString = if (isDeleted) ",deleted" else ""
      if (r.startNodeId() != leftNode.id())
        sb += '<'
      sb ++= s"-[${r.id}:${r.`type`()}$deletedString]-"
      if (r.endNodeId() != leftNode.id())
        sb += '>'
    }

    var n = nodes.next()
    formatNode(n)
    while (relationships.hasNext) {
      val r = relationships.next()
      formatRelationship(n, r)
      n = nodes.next()
      formatNode(n)
    }

    sb.result()
  }

  private def props(n: Vertex): String = {
    if (deletedInTx.node(n.getId))
      "{deleted}"
    else entityProps(n)
  }

  private def props(r: Edge): String = {
    if (deletedInTx.relationship(r.getId))
      "{deleted}"
    else entityProps(r)
  }

  private def entityProps(e: Element): String = {
    if (isVirtualEntityHack(e))
      "{}"
    else {
      try {
        propertiesAsTextValue(propertiesAsMap(e))
      } catch {
        case e: NotFoundException => "{}"
      }
    }
  }

  private def propertiesAsTextValue(properties: java.util.Map[String, Object]): String = {
    val keyValues = new ArrayBuffer[String]
    val propertyIterator = properties.entrySet().iterator()
    while (propertyIterator.hasNext) {
      val entry = propertyIterator.next()
      val key = entry.getKey
      val value = asTextValue(entry.getValue)
      keyValues.append(s"$key:$value")
    }
    keyValues.mkString("{", ",", "}")
  }

  private def isVirtualEntityHack(entity:Element): Boolean = entity.getId == null

  private def propertiesAsMap(element: Element): java.util.Map[String, Object] = {
    val groupedProps = element.getProperties.asScala
      .groupBy(f => f.getName)

    val result = new util.HashMap[String, AnyRef]
    groupedProps.foreach((gp) => {
      result.put(gp._1, gp._2.head.getValue);
    })

    result
  }
}
