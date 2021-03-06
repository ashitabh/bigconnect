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
package com.mware.bigconnect.driver;

import com.mware.bigconnect.driver.async.AsyncSession;
import com.mware.bigconnect.driver.internal.Bookmark;
import com.mware.bigconnect.driver.util.Resource;

import java.util.Map;

/**
 * Provides a context of work for database interactions.
 * <p>
 * A <em>Session</em> hosts a series of {@linkplain Transaction transactions}
 * carried out against a database. Within the database, all statements are
 * carried out within a transaction. Within application code, however, it is
 * not always necessary to explicitly {@link #beginTransaction() begin a
 * transaction}. If a statement is {@link #run} directly against a {@link
 * Session}, the server will automatically <code>BEGIN</code> and
 * <code>COMMIT</code> that statement within its own transaction. This type
 * of transaction is known as an <em>autocommit transaction</em>.
 * <p>
 * Explicit transactions allow multiple statements to be committed as part of
 * a single atomic operation and can be rolled back if necessary. They can also
 * be used to ensure <em>causal consistency</em>, meaning that an application
 * can run a series of queries on different members of a cluster, while
 * ensuring that each query sees the state of graph at least as up-to-date as
 * the graph seen by the previous query. For more on causal consistency, see
 * the BigConnect clustering manual.
 * <p>
 * Typically, a session will acquire a TCP connection to execute query or
 * transaction. Such a connection will be acquired from a connection pool
 * and released back there when query result is consumed or transaction is
 * committed or rolled back. One connection can therefore be adopted by many
 * sessions, although by only one at a time. Application code should never need
 * to deal directly with connection management.
 * <p>
 * A session inherits its destination address and permissions from its
 * underlying connection. This means that for a single query/transaction one
 * session may only ever target one machine within a cluster and does not
 * support re-authentication. To achieve otherwise requires creation of a
 * separate session.
 * <p>
 * Similarly, multiple sessions should be used when working with concurrency;
 * session implementations are not thread safe.
 *
 * @since 1.0 (Removed async API to {@link AsyncSession} in 2.0)
 */
public interface Session extends Resource, StatementRunner
{
    /**
     * Begin a new <em>explicit {@linkplain Transaction transaction}</em>. At
     * most one transaction may exist in a session at any point in time. To
     * maintain multiple concurrent transactions, use multiple concurrent
     * sessions.
     *
     * @return a new {@link Transaction}
     */
    Transaction beginTransaction();

    /**
     * Begin a new <em>explicit {@linkplain Transaction transaction}</em> with the specified {@link TransactionConfig configuration}.
     * At most one transaction may exist in a session at any point in time. To
     * maintain multiple concurrent transactions, use multiple concurrent
     * sessions.
     *
     * @param config configuration for the new transaction.
     * @return a new {@link Transaction}
     */
    Transaction beginTransaction(TransactionConfig config);

    /**
     * Execute given unit of work in a  {@link AccessMode#READ read} transaction.
     * <p>
     * Transaction will automatically be committed unless exception is thrown from the unit of work itself or during committing,
     * or transaction is explicitly committed via {@link Transaction#commit()} or rolled back via {@link Transaction#rollback()}.
     *
     * @param work the {@link TransactionWork} to be applied to a new read transaction.
     * @param <T> the return type of the given unit of work.
     * @return a result as returned by the given unit of work.
     */
    <T> T readTransaction(TransactionWork<T> work);

    /**
     * Execute given unit of work in a  {@link AccessMode#READ read} transaction with the specified {@link TransactionConfig configuration}.
     * <p>
     * Transaction will automatically be committed unless exception is thrown from the unit of work itself or during committing,
     * or transaction is explicitly committed via {@link Transaction#commit()} or rolled back via {@link Transaction#rollback()}.
     *
     * @param work the {@link TransactionWork} to be applied to a new read transaction.
     * @param config configuration for all transactions started to execute the unit of work.
     * @param <T> the return type of the given unit of work.
     * @return a result as returned by the given unit of work.
     */
    <T> T readTransaction(TransactionWork<T> work, TransactionConfig config);

    /**
     * Execute given unit of work in a  {@link AccessMode#WRITE write} transaction.
     * <p>
     * Transaction will automatically be committed unless exception is thrown from the unit of work itself or during committing,
     * or transaction is explicitly committed via {@link Transaction#commit()} or rolled back via {@link Transaction#rollback()}.
     *
     * @param work the {@link TransactionWork} to be applied to a new write transaction.
     * @param <T> the return type of the given unit of work.
     * @return a result as returned by the given unit of work.
     */
    <T> T writeTransaction(TransactionWork<T> work);

    /**
     * Execute given unit of work in a  {@link AccessMode#WRITE write} transaction with the specified {@link TransactionConfig configuration}.
     * <p>
     * Transaction will automatically be committed unless exception is thrown from the unit of work itself or during committing,
     * or transaction is explicitly committed via {@link Transaction#commit()} or rolled back via {@link Transaction#rollback()}.
     *
     * @param work the {@link TransactionWork} to be applied to a new write transaction.
     * @param config configuration for all transactions started to execute the unit of work.
     * @param <T> the return type of the given unit of work.
     * @return a result as returned by the given unit of work.
     */
    <T> T writeTransaction(TransactionWork<T> work, TransactionConfig config);

    /**
     * Run a statement in an auto-commit transaction with the specified {@link TransactionConfig configuration} and return a result stream.
     *
     * @param statement text of a BigConnect statement.
     * @param config configuration for the new transaction.
     * @return a stream of result values and associated metadata.
     */
    StatementResult run(String statement, TransactionConfig config);

    /**
     * Run a statement with parameters in an auto-commit transaction with specified {@link TransactionConfig configuration} and return a result stream.
     * <p>
     * This method takes a set of parameters that will be injected into the
     * statement by BigConnect. Using parameters is highly encouraged, it helps avoid
     * dangerous cypher injection attacks and improves database performance as
     * BigConnect can re-use query plans more often.
     * <p>
     * This version of run takes a {@link Map} of parameters. The values in the map
     * must be values that can be converted to BigConnect types. See {@link Values#parameters(Object...)} for
     * a list of allowed types.
     *
     * <h2>Example</h2>
     * <pre>
     * {@code
     * Map<String, Object> metadata = new HashMap<>();
     * metadata.put("type", "update name");
     *
     * TransactionConfig config = TransactionConfig.builder()
     *                 .withTimeout(Duration.ofSeconds(3))
     *                 .withMetadata(metadata)
     *                 .build();
     *
     * Map<String, Object> parameters = new HashMap<>();
     * parameters.put("myNameParam", "Bob");
     *
     * StatementResult cursor = session.run("MATCH (n) WHERE n.name = {myNameParam} RETURN (n)", parameters, config);
     * }
     * </pre>
     *
     * @param statement text of a BigConnect statement.
     * @param parameters input data for the statement.
     * @param config configuration for the new transaction.
     * @return a stream of result values and associated metadata.
     */
    StatementResult run(String statement, Map<String, Object> parameters, TransactionConfig config);

    /**
     * Run a statement in an auto-commit transaction with specified {@link TransactionConfig configuration} and return a result stream.
     * <h2>Example</h2>
     * <pre>
     * {@code
     * Map<String, Object> metadata = new HashMap<>();
     * metadata.put("type", "update name");
     *
     * TransactionConfig config = TransactionConfig.builder()
     *                 .withTimeout(Duration.ofSeconds(3))
     *                 .withMetadata(metadata)
     *                 .build();
     *
     * Statement statement = new Statement("MATCH (n) WHERE n.name=$myNameParam RETURN n.age");
     * StatementResult cursor = session.run(statement.withParameters(Values.parameters("myNameParam", "Bob")));
     * }
     * </pre>
     *
     * @param statement a BigConnect statement.
     * @param config configuration for the new transaction.
     * @return a stream of result values and associated metadata.
     */
    StatementResult run(Statement statement, TransactionConfig config);

    /**
     * Return the bookmark received following the last completed
     * {@linkplain Transaction transaction}. If no bookmark was received
     * or if this transaction was rolled back, the bookmark value will
     * be null.
     *
     * @return a reference to a previous transaction
     */
    Bookmark lastBookmark();

    /**
     * Reset the current session. This sends an immediate RESET signal to the server which both interrupts
     * any statement that is currently executing and ignores any subsequently queued statements. Following
     * the reset, the current transaction will have been rolled back and any outstanding failures will
     * have been acknowledged.
     *
     * @deprecated This method should not be used and violates the expected usage pattern of {@link Session} objects.
     * They are expected to be not thread-safe and should not be shared between thread. However this method is only
     * useful when {@link Session} object is passed to another monitoring thread that calls it when appropriate.
     * It is not useful when {@link Session} is used in a single thread because in this case {@link #close()}
     * can be used. Since version 3.1, BigConnect database allows users to specify maximum transaction execution time and
     * contains procedures to list and terminate running queries. These functions should be used instead of calling
     * this method.
     */
    @Deprecated
    void reset();

    /**
     * Signal that you are done using this session. In the default driver usage, closing and accessing sessions is
     * very low cost.
     */
    @Override
    void close();
}
