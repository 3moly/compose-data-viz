package com.threemoly.sample

import kotlinx.coroutines.Dispatchers

actual val io: kotlinx.coroutines.CoroutineDispatcher
    get() = Dispatchers.Default.limitedParallelism(100)