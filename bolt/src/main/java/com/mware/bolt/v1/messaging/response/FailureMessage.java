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
package com.mware.bolt.v1.messaging.response;

import com.mware.bolt.messaging.ResponseMessage;
import com.mware.bolt.runtime.Status;

public class FailureMessage implements ResponseMessage {
    public static final byte SIGNATURE = 0x7F;
    private final Status status;
    private final String message;

    public FailureMessage(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public Status status() {
        return status;
    }

    public String message() {
        return message;
    }

    @Override
    public byte signature() {
        return SIGNATURE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FailureMessage)) {
            return false;
        }

        FailureMessage that = (FailureMessage) o;

        return (message != null ? message.equals(that.message) : that.message == null) &&
                (status != null ? status.equals(that.status) : that.status == null);
    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FAILURE " + status + " " + message;
    }

}
