package com.moly3.dataviz.core.graph.func

actual fun avaliableCpuProcessors(defaultCpuCores: Int?): Int {
    return defaultCpuCores ?: Runtime.getRuntime().availableProcessors()
}