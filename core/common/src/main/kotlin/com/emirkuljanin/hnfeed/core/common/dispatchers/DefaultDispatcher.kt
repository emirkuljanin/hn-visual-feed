package com.emirkuljanin.hnfeed.core.common.dispatchers

import javax.inject.Qualifier

/**
 * The dispatcher for CPU-bound work (image drawing and encoding, ML inference). Injected instead of referencing
 * `Dispatchers.Default` directly, so tests can substitute a test dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
