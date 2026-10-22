package com.emirkuljanin.hnfeed.core.network

import java.io.IOException

/**
 * The API answered, but not with something usable: a non-2xx status or a malformed body. An [IOException] like
 * connectivity failures, so callers handle "the network let us down" in one place without depending on Retrofit
 * or kotlinx.serialization types.
 */
class HnApiException(message: String, cause: Throwable) : IOException(message, cause)
