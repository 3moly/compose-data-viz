package com.threemoly.sample.func

import kotlinx.browser.window

actual fun openUrl(url: String) {
    window.open(url, "blank")
}