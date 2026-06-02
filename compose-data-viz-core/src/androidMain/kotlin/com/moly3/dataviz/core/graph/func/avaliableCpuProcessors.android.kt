package com.moly3.dataviz.core.graph.func

actual fun avaliableCpuProcessors(defaultCpuCores: Int?): Int = defaultCpuCores ?: Runtime.getRuntime().availableProcessors()
