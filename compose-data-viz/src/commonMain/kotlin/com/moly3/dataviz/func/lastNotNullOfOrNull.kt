package com.moly3.dataviz.func

@SinceKotlin("1.5")
public inline fun <T, R : Any> Iterable<T>.lastNotNullOfOrNull(transform: (T) -> R?): R? {
    var lastResult: R? = null
    for (element in this) {
        val result = transform(element)
        if (result != null) {
            lastResult = result
        }
    }
    return lastResult
}