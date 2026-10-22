package com.emirkuljanin.hnfeed.core.common.dispatchers

import javax.inject.Qualifier

/**
 * The dispatcher for blocking I/O (network, disk, database). Classes inject it instead of referencing
 * `Dispatchers.IO` directly, so tests can substitute a test dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
