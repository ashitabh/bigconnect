/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
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
package com.mware.bolt.runtime;

import com.mware.bolt.messaging.RequestMessage;
import com.mware.core.exception.BcException;

public interface BoltStateMachine extends AutoCloseable
{
    void process(RequestMessage message, BoltResponseHandler handler ) throws BoltConnectionFatality;

    boolean shouldStickOnThread();

    void validateTransaction() throws BcException;

    boolean hasOpenStatement();

    void interrupt();

    boolean reset() throws BoltConnectionFatality;

    void markFailed( BigConnectError error );

    void handleFailure( Throwable cause, boolean fatal ) throws BoltConnectionFatality;

    void handleExternalFailure( BigConnectError error, BoltResponseHandler handler ) throws BoltConnectionFatality;

    void markForTermination();

    boolean isClosed();

    @Override
    void close();

    String id();
}
