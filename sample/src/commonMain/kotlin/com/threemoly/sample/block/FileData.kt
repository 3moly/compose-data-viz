package com.threemoly.sample.block

sealed class FileData {
    data class Text(val text: String) : FileData()

}