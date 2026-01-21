package com.example.fileexplorerjc

import java.io.File

enum class ClipboardOperationType {
    COPY, CUT
}

data class ClipboardData(
    val files: List<File>,
    val operationType: ClipboardOperationType
)

object ClipboardManager {
    private var clipboardData: ClipboardData? = null

    fun copyFiles(files: List<File>) {
        clipboardData = ClipboardData(files, ClipboardOperationType.COPY)
    }

    fun cutFiles(files: List<File>) {
        clipboardData = ClipboardData(files, ClipboardOperationType.CUT)
    }

    fun getClipboardData(): ClipboardData? = clipboardData

    fun hasClipboardContent(): Boolean =
        clipboardData != null && clipboardData!!.files.isNotEmpty()

    fun isCutOperation(): Boolean =
        clipboardData?.operationType == ClipboardOperationType.CUT

    fun clear() {
        clipboardData = null
    }
}
