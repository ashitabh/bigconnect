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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import com.mware.bigconnect.driver.Logger;
import com.mware.bigconnect.driver.Logging;
import com.mware.bigconnect.driver.internal.logging.ChannelActivityLogger;
import com.mware.bigconnect.driver.internal.messaging.MessageFormat;

import static io.netty.buffer.ByteBufUtil.hexDump;
import static java.util.Objects.requireNonNull;
import static com.mware.bigconnect.driver.internal.async.connection.ChannelAttributes.messageDispatcher;

public class InboundMessageHandler extends SimpleChannelInboundHandler<ByteBuf>
{
    private final ByteBufInput input;
    private final MessageFormat.Reader reader;
    private final Logging logging;

    private InboundMessageDispatcher messageDispatcher;
    private Logger log;

    public InboundMessageHandler( MessageFormat messageFormat, Logging logging )
    {
        this.input = new ByteBufInput();
        this.reader = messageFormat.newReader( input );
        this.logging = logging;
    }

    @Override
    public void handlerAdded( ChannelHandlerContext ctx )
    {
        messageDispatcher = requireNonNull( messageDispatcher( ctx.channel() ) );
        log = new ChannelActivityLogger( ctx.channel(), logging, getClass() );
    }

    @Override
    public void handlerRemoved( ChannelHandlerContext ctx )
    {
        messageDispatcher = null;
        log = null;
    }

    @Override
    protected void channelRead0( ChannelHandlerContext ctx, ByteBuf msg )
    {
        if ( messageDispatcher.fatalErrorOccurred() )
        {
            log.warn( "Message ignored because of the previous fatal error. Channel will be closed. Message:\n%s",
                    hexDump( msg ) );
            return;
        }

        if ( log.isTraceEnabled() )
        {
            log.trace( "S: %s", hexDump( msg ) );
        }

        input.start( msg );
        try
        {
            reader.read( messageDispatcher );
        }
        catch ( Throwable error )
        {
            throw new DecoderException( "Failed to read inbound message:\n" + hexDump( msg ) + "\n", error );
        }
        finally
        {
            input.stop();
        }
    }
}
