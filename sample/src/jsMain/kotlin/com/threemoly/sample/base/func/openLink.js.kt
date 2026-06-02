package com.threemoly.sample.base.func

import kotlinx.browser.window

const val TARGET_URL = "blank"

actual fun openUrl(url: String) {
    window.open(url, TARGET_URL)
}
