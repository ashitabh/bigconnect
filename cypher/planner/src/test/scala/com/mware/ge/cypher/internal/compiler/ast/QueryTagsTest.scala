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
package com.mware.ge.cypher.internal.compiler.ast

import com.mware.ge.cypher.internal.util.test_helpers.CypherFunSuite

class QueryTagsTest extends CypherFunSuite {

  test("Can parse concatenated tokens") {
    QueryTags.parse("") should equal(Set.empty)
    QueryTags.parse("  ") should equal(Set.empty)
    QueryTags.parse(":match:opt-match") should equal(Set(MatchTag, OptionalMatchTag))
    QueryTags.parse(" :match  :opt-match ") should equal(Set(MatchTag, OptionalMatchTag))
  }

  test("Fails when parsing unknown token") {
    an [IllegalArgumentException] should be thrownBy {
      QueryTags.parse(":match:duck")
    }
  }

  test("Fails when parsing empty token") {
    an [IllegalArgumentException] should be thrownBy {
      QueryTags.parse(":match::")
    }
  }
}
