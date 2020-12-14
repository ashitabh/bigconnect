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
package com.mware.ge.cypher;

import com.mware.ge.cypher.query.ClientConnectionInfo;
import com.mware.ge.cypher.query.ExecutingQuery;
import com.mware.ge.cypher.query.KernelStatement;
import com.mware.ge.values.virtual.MapValue;

import java.util.stream.Stream;

/**
 * Query execution monitoring operations.
 *
 * @see OperationsFacade
 */
public interface QueryRegistrationOperations {
    Stream<ExecutingQuery> executingQueries(KernelStatement statement);

    ExecutingQuery startQueryExecution(
            KernelStatement statement,
            ClientConnectionInfo descriptor,
            String queryText,
            MapValue queryParameters
    );

    void registerExecutingQuery(KernelStatement statement, ExecutingQuery executingQuery);

    void unregisterExecutingQuery(KernelStatement statement, ExecutingQuery executingQuery);
}
