package com.threemoly.sample.base

import androidx.compose.ui.window.ComposeUIViewController
import com.threemoly.sample.ExampleApp
import platform.UIKit.UIViewController

fun rootViewController(): UIViewController =
    ComposeUIViewController {
        ExampleApp()
    }
