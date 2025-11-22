package com.threemoly.sample.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val io: CoroutineDispatcher
    get() = Dispatchers.IO