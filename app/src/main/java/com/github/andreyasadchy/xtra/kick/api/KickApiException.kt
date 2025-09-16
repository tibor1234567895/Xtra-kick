package com.github.andreyasadchy.xtra.kick.api

import java.io.IOException

class KickApiException(
    message: String,
    val statusCode: Int,
    cause: Throwable? = null
) : IOException(message, cause)
