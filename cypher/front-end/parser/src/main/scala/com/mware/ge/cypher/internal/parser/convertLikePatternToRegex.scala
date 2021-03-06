/*
 * Copyright (c) 2013-2020 "BigConnect,"
 * MWARE SOLUTIONS SRL
 *
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mware.ge.cypher.internal.parser

import java.util.regex.Pattern._

/**
 * Converts [[ParsedLikePattern]] into a regular expression string
 */
case object convertLikePatternToRegex {
  def apply(in: ParsedLikePattern, caseInsensitive: Boolean = false): String =
    in.ops.map(convert).mkString(if (caseInsensitive) "(?i)" else "", "", "")

  private def convert(in: LikePatternOp): String = in match {
    case MatchText(s) => quote(s)
    case MatchMany => ".*"
    case MatchSingle => "."
  }
}
