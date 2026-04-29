package com.moly3.dataviz.core.graph.func

actual fun avaliableCpuProcessors(cpuCores: Int?): Int {
    return cpuCores ?: 4
}