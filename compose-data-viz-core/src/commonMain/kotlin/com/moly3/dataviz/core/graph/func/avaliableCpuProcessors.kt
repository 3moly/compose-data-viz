package com.moly3.dataviz.core.graph.func

const val DEFAULT_CPU_PROCESSORS = 4

expect fun avaliableCpuProcessors(defaultCpuCores: Int?): Int
