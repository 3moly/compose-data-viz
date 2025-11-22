package com.threemoly.sample.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual val io: CoroutineDispatcher
    get() = Dispatchers.IO