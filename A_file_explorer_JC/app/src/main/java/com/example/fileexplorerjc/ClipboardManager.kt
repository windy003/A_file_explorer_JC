package com.example.fileexplorerjc

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClipboardOperationType {
    COPY, CUT
}

data class ClipboardData(
    val files: List<File>,
    val operationType: ClipboardOperationType
)

object ClipboardManager {
    private var clipboardData: ClipboardData? = null

    private val _hasContent = MutableStateFlow(false)
    val hasContentFlow: StateFlow<Boolean> = _hasContent.asStateFlow()

    fun copyFiles(files: List<File>) {
        clipboardData = ClipboardData(files, ClipboardOperationType.COPY)
        _hasContent.value = true
    }

    fun cutFiles(files: List<File>) {
        clipboardData = ClipboardData(files, ClipboardOperationType.CUT)
        _hasContent.value = true
    }

    fun getClipboardData(): ClipboardData? = clipboardData

    fun hasClipboardContent(): Boolean =
        clipboardData != null && clipboardData!!.files.isNotEmpty()

    fun isCutOperation(): Boolean =
        clipboardData?.operationType == ClipboardOperationType.CUT

    fun clear() {
        clipboardData = null
        _hasContent.value = false
    }
}
