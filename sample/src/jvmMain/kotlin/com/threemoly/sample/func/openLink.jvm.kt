package com.threemoly.sample.func

actual fun openUrl(url: String) {
    java.awt.Desktop.getDesktop().browse(java.net.URI(url))
}