/*
 * Copyright (c) 2013-2020 "BigConnect,"
 * MWARE SOLUTIONS SRL
 *
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
package com.mware.bigconnect.driver.exceptions;

/**
 * A <em>ClientException</em> indicates that the client has carried out an operation incorrectly.
 * The error code provided can be used to determine further detail for the problem.
 * @since 1.0
 */
public class ClientException extends BigConnectException
{
    public ClientException( String message )
    {
        super( message );
    }

    public ClientException(String message, Throwable cause )
    {
        super( message, cause );
    }

    public ClientException(String code, String message )
    {
        super( code, message );
    }
}
