package com.threemoly.sample.base

import androidx.compose.ui.window.ComposeUIViewController
import com.threemoly.sample.ExampleApp
import platform.UIKit.UIViewController

fun RootViewController(): UIViewController {
    return ComposeUIViewController {
        ExampleApp()
    }
}