/*
 * This file is part of the BigConnect project.
 *
 * Copyright (c) 2013-2020 MWARE SOLUTIONS SRL
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * MWARE SOLUTIONS SRL, MWARE SOLUTIONS SRL DISCLAIMS THE WARRANTY OF
 * NON INFRINGEMENT OF THIRD PARTY RIGHTS

 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the BigConnect software without
 * disclosing the source code of your own applications.
 *
 * These activities include: offering paid services to customers as an ASP,
 * embedding the product in a web application, shipping BigConnect with a
 * closed source product.
 */
package com.mware.ge.util;

import com.mware.ge.io.ByteUnit;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class Format
{
    /**
     * Default time zone is UTC (+00:00) so that comparing timestamped logs from different
     * sources is an easier task.
     */
    public static final TimeZone DEFAULT_TIME_ZONE = TimeZone.getTimeZone( "UTC" );

    private static final String[] BYTE_SIZES = { "B", "kB", "MB", "GB" };
    private static final String[] COUNT_SIZES = { "", "k", "M", "G", "T" };

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSZ";
    public static final String TIME_FORMAT = "HH:mm:ss.SSS";

    private static final ThreadLocalFormat DATE = new ThreadLocalFormat( DATE_FORMAT );
    private static final ThreadLocalFormat TIME = new ThreadLocalFormat( TIME_FORMAT );
    private static int KB = (int) ByteUnit.kibiBytes( 1 );

    public static String date()
    {
        return date( DEFAULT_TIME_ZONE );
    }

    public static String date( TimeZone timeZone )
    {
        return date( new Date(), timeZone );
    }

    public static String date( long millis )
    {
        return date( millis, DEFAULT_TIME_ZONE );
    }

    public static String date( long millis, TimeZone timeZone )
    {
        return date( new Date( millis ), timeZone );
    }

    public static String date( Date date )
    {
        return date( date, DEFAULT_TIME_ZONE );
    }

    public static String date( Date date, TimeZone timeZone )
    {
        return DATE.format( date, timeZone );
    }

    public static String time()
    {
        return time( DEFAULT_TIME_ZONE );
    }

    public static String time( TimeZone timeZone )
    {
        return time( new Date(), timeZone );
    }

    public static String time( long millis )
    {
        return time( millis, DEFAULT_TIME_ZONE );
    }

    public static String time( long millis, TimeZone timeZone )
    {
        return time( new Date( millis ), timeZone );
    }

    public static String time( Date date )
    {
        return time( date, DEFAULT_TIME_ZONE );
    }

    public static String time( Date date, TimeZone timeZone )
    {
        return TIME.format( date, timeZone );
    }

    public static String bytes( long bytes )
    {
        return suffixCount( bytes, BYTE_SIZES, KB );
    }

    public static String count( long count )
    {
        return suffixCount( count, COUNT_SIZES, 1_000 );
    }

    private static String suffixCount( long value, String[] sizes, int stride )
    {
        double size = value;
        for ( String suffix : sizes )
        {
            if ( size < stride )
            {
                return String.format( Locale.ROOT, "%.2f %s", size, suffix );
            }
            size /= stride;
        }
        return String.format( Locale.ROOT, "%.2f TB", size );
    }

    public static String duration( long durationMillis )
    {
        return duration( durationMillis, TimeUnit.DAYS, TimeUnit.MILLISECONDS );
    }

    public static String duration( long durationMillis, TimeUnit highestGranularity, TimeUnit lowestGranularity )
    {
        StringBuilder builder = new StringBuilder();

        TimeUnit[] units = TimeUnit.values();
        reverse( units );
        boolean use = false;
        for ( TimeUnit unit : units )
        {
            if ( unit == highestGranularity )
            {
                use = true;
            }

            if ( use )
            {
                durationMillis = extractFromDuration( durationMillis, unit, builder );
                if ( unit == lowestGranularity )
                {
                    break;
                }
            }
        }

        if ( builder.length() == 0 )
        {
            // The value is too low to extract any meaningful numbers with the given unit brackets.
            // So we append a zero of the lowest unit.
            builder.append( '0' ).append( shortName( lowestGranularity ) );
        }

        return builder.toString();
    }

    private static <T> void reverse( T[] array )
    {
        int half = array.length >> 1;
        for ( int i = 0; i < half; i++ )
        {
            T temp = array[i];
            int highIndex = array.length - 1 - i;
            array[i] = array[highIndex];
            array[highIndex] = temp;
        }
    }

    private static String shortName( TimeUnit unit )
    {
        switch ( unit )
        {
        case NANOSECONDS: return "ns";
        case MICROSECONDS: return "μs";
        case MILLISECONDS: return "ms";
        default: return unit.name().substring( 0, 1 ).toLowerCase();
        }
    }

    private static long extractFromDuration( long durationMillis, TimeUnit unit, StringBuilder target )
    {
        int count = 0;
        long millisPerUnit = unit.toMillis( 1 );
        while ( durationMillis >= millisPerUnit )
        {
            count++;
            durationMillis -= millisPerUnit;
        }
        if ( count > 0 )
        {
            target.append( target.length() > 0 ? " " : "" ).append( count ).append( shortName( unit ) );
        }
        return durationMillis;
    }

    private Format()
    {
        // No instances
    }

    private static class ThreadLocalFormat extends ThreadLocal<DateFormat>
    {
        private final String format;

        ThreadLocalFormat( String format )
        {
            this.format = format;
        }

        String format( Date date, TimeZone timeZone )
        {
            DateFormat dateFormat = get();
            dateFormat.setTimeZone( timeZone );
            return dateFormat.format( date );
        }

        @Override
        protected DateFormat initialValue()
        {
            return new SimpleDateFormat( format );
        }
    }
}
