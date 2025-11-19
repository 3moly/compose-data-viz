package com.threemoly.sample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val io: kotlinx.coroutines.CoroutineDispatcher
    get() = Dispatchers.IO