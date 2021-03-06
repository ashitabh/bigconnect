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
package com.mware.bigconnect.driver.internal.async.inbound;

import io.netty.buffer.ByteBuf;
import com.mware.bigconnect.driver.internal.packstream.PackInput;

import static java.util.Objects.requireNonNull;

public class ByteBufInput implements PackInput
{
    private ByteBuf buf;

    public void start( ByteBuf newBuf )
    {
        assertNotStarted();
        buf = requireNonNull( newBuf );
    }

    public void stop()
    {
        buf = null;
    }

    @Override
    public byte readByte()
    {
        return buf.readByte();
    }

    @Override
    public short readShort()
    {
        return buf.readShort();
    }

    @Override
    public int readInt()
    {
        return buf.readInt();
    }

    @Override
    public long readLong()
    {
        return buf.readLong();
    }

    @Override
    public double readDouble()
    {
        return buf.readDouble();
    }

    @Override
    public void readBytes( byte[] into, int offset, int toRead )
    {
        buf.readBytes( into, offset, toRead );
    }

    @Override
    public byte peekByte()
    {
        return buf.getByte( buf.readerIndex() );
    }

    private void assertNotStarted()
    {
        if ( buf != null )
        {
            throw new IllegalStateException( "Already started" );
        }
    }
}
