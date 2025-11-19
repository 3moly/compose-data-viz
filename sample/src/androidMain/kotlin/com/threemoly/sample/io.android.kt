package com.threemoly.sample

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val io: CoroutineDispatcher
    get() = Dispatchers.IO