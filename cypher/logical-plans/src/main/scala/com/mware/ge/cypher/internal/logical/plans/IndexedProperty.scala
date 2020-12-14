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
package com.mware.ge.cypher.internal.logical.plans

import com.mware.ge.cypher.internal.expressions.Property
import com.mware.ge.cypher.internal.expressions.PropertyKeyName
import com.mware.ge.cypher.internal.expressions.PropertyKeyToken
import com.mware.ge.cypher.internal.expressions.Variable
import com.mware.ge.cypher.internal.util.InputPosition

case class IndexedProperty(propertyKeyToken: PropertyKeyToken, getValueFromIndex: GetValueFromIndexBehavior) {
  def shouldGetValue: Boolean = getValueFromIndex == GetValue

  def asAvailablePropertyMap(entity: String): Map[Property, CachedNodeProperty] =
    if (getValueFromIndex == GetValue)
      Map((
        Property(
          Variable(entity)(InputPosition.NONE),
          PropertyKeyName(propertyKeyToken.name)(InputPosition.NONE)
        )(InputPosition.NONE),
        asCachedNodeProperty(entity)
      ))
    else Map.empty

  def asCachedNodeProperty(node: String): CachedNodeProperty =
    CachedNodeProperty(node, PropertyKeyName(propertyKeyToken.name)(InputPosition.NONE))(InputPosition.NONE)

  def maybeCachedNodeProperty(entity: String): Option[CachedNodeProperty] =
    if (shouldGetValue)
      Some(asCachedNodeProperty(entity))
    else None
}

// This can be extended later on with: GetValuesPartially
sealed trait GetValueFromIndexBehavior
case object DoNotGetValue extends GetValueFromIndexBehavior
case object CanGetValue extends GetValueFromIndexBehavior
case object GetValue extends GetValueFromIndexBehavior
