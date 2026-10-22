package com.emirkuljanin.hnfeed.core.database

import androidx.room3.withWriteTransaction
import javax.inject.Inject

/**
 * Runs a block of DAO calls in one database transaction: either all writes are committed (and observers such as
 * the feed's PagingSource see them at once) or, if the block throws, none are.
 *
 * An interface rather than direct access to the database, so the data layer can use transactions across DAOs
 * and still be tested with fakes.
 */
interface DatabaseTransactionRunner {
    suspend operator fun <T> invoke(block: suspend () -> T): T
}

internal class RoomDatabaseTransactionRunner @Inject constructor(private val database: HnDatabase) :
    DatabaseTransactionRunner {

    override suspend fun <T> invoke(block: suspend () -> T): T = database.withWriteTransaction { block() }
}
