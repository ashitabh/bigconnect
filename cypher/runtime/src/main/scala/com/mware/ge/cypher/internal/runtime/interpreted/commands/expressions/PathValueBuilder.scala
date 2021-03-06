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
import com.mware.ge.values.storable.Values
import com.mware.ge.values.virtual._

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

final class PathValueBuilder {

  private val nodes = ArrayBuffer.empty[NodeValue]
  private val rels = ArrayBuffer.empty[RelationshipValue]
  private var nulled = false
  private var previousNode: NodeValue = null
  def result(): AnyValue = if (nulled) Values.NO_VALUE else VirtualValues.path(nodes.toArray, rels.toArray)

  def clear(): PathValueBuilder =  {
    nodes.clear()
    rels.clear()
    nulled = false
    this
  }

  def addNode(nodeOrNull: AnyValue): PathValueBuilder = nullCheck(nodeOrNull) {
    val node = nodeOrNull.asInstanceOf[NodeValue]
    previousNode = node
    nodes += node
    this
  }

  def addIncomingRelationship(relOrNull: AnyValue): PathValueBuilder = nullCheck(relOrNull) {
    val rel = relOrNull.asInstanceOf[RelationshipValue]
    rels += rel
    previousNode = rel.startNode()
    nodes += previousNode
    this
  }

  def addOutgoingRelationship(relOrNull: AnyValue): PathValueBuilder = nullCheck(relOrNull) {
    val rel = relOrNull.asInstanceOf[RelationshipValue]
    rels += rel
    previousNode = rel.endNode()
    nodes += previousNode
    this
  }

  def addUndirectedRelationship(relOrNull: AnyValue): PathValueBuilder = nullCheck(relOrNull) {
    val rel = relOrNull.asInstanceOf[RelationshipValue]
    if (rel.startNode() == previousNode) addOutgoingRelationship(rel)
    else if (rel.endNode() == previousNode) addIncomingRelationship(rel)
    else throw new IllegalArgumentException(s"Invalid usage of PathValueBuilder, $previousNode must be a node in $rel")
  }

  def addIncomingRelationships(relsOrNull: AnyValue): PathValueBuilder = nullCheck(relsOrNull) {
    val relsToAdd = relsOrNull.asInstanceOf[ListValue]
    val iterator = relsToAdd.iterator
    while (iterator.hasNext)
      addIncomingRelationship(iterator.next().asInstanceOf[RelationshipValue])
    this
  }

  def addOutgoingRelationships(relsOrNull: AnyValue): PathValueBuilder = nullCheck(relsOrNull) {
    val relsToAdd = relsOrNull.asInstanceOf[ListValue]
    val iterator = relsToAdd.iterator
    while (iterator.hasNext)
      addOutgoingRelationship(iterator.next().asInstanceOf[RelationshipValue])
    this
  }

  def addUndirectedRelationships(relsOrNull: AnyValue): PathValueBuilder = nullCheck(relsOrNull) {
    val relsToAdd = relsOrNull.asInstanceOf[ListValue]
    val relIterator = relsToAdd.iterator

    def consumeIterator(i: Iterator[AnyValue]): Unit =
      while (i.hasNext)
        addUndirectedRelationship(i.next().asInstanceOf[RelationshipValue])


    if (relIterator.hasNext) {
      val first = relIterator.next().asInstanceOf[RelationshipValue]
      val rightDirection = first.startNode() == previousNode || first.endNode() == previousNode

      if (rightDirection) {
        addUndirectedRelationship(first)
        consumeIterator(relIterator.asScala)
      } else {
        consumeIterator(relIterator.asScala.toIndexedSeq.reverseIterator)
        addUndirectedRelationship(first)
      }
    }
    this
  }

  private def nullCheck[A](value: A)(f: => PathValueBuilder):PathValueBuilder = value match {
    case null | Values.NO_VALUE =>
      nulled = true
      this

    case _ => f
  }
}
