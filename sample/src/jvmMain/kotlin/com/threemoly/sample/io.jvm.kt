package com.threemoly.sample

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val io: CoroutineDispatcher
    get() = Dispatchers.IO