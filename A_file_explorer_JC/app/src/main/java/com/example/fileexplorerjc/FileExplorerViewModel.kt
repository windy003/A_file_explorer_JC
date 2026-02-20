package com.example.fileexplorerjc

import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class FileExplorerState(
    val currentDirectory: File? = null,
    val files: List<FileItem> = emptyList(),
    val breadcrumbs: List<File> = emptyList(),
    val isMultiSelectMode: Boolean = false,
    val selectedFiles: Set<File> = emptySet(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshSuccess: Boolean? = null,
    val storageInfo: String = "",
    val errorMessage: String? = null,
    val hasClipboardContent: Boolean = false
)

class FileExplorerViewModel : ViewModel() {

    private val _state = MutableStateFlow(FileExplorerState())
    val state: StateFlow<FileExplorerState> = _state.asStateFlow()

    private var isInitialized = false

    init {
        ClipboardManager.hasContentFlow
            .onEach { hasContent ->
                _state.value = _state.value.copy(hasClipboardContent = hasContent)
            }
            .launchIn(viewModelScope)
    }

    fun loadInitialDirectory() {
        if (isInitialized) return
        loadDirectory(null)
    }

    fun loadDirectory(savedPath: String?) {
        if (isInitialized) return
        isInitialized = true

        val initialDir = if (savedPath != null) {
            val savedDir = File(savedPath)
            if (savedDir.exists() && savedDir.canRead()) {
                savedDir
            } else {
                getDefaultDirectory()
            }
        } else {
            getDefaultDirectory()
        }
        navigateToDirectory(initialDir)
        updateStorageInfo()
    }

    private fun getDefaultDirectory(): File {
        return try {
            val externalDir = Environment.getExternalStorageDirectory()
            if (externalDir?.exists() == true && externalDir.canRead()) {
                externalDir
            } else {
                File("/storage/emulated/0")
            }
        } catch (e: Exception) {
            File("/storage/emulated/0")
        }
    }

    fun navigateToDirectory(directory: File) {
        if (!directory.canRead()) {
            _state.value = _state.value.copy(errorMessage = "无权限访问此目录")
            return
        }

        _state.value = _state.value.copy(isLoading = true)

        val files = directory.listFiles()?.map { FileItem(it) }
            ?.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()

        val breadcrumbs = mutableListOf<File>()
        var current: File? = directory
        while (current != null) {
            breadcrumbs.add(0, current)
            current = current.parentFile
        }

        _state.value = _state.value.copy(
            currentDirectory = directory,
            files = files,
            breadcrumbs = breadcrumbs,
            isLoading = false,
            isMultiSelectMode = false,
            selectedFiles = emptySet()
        )
    }

    fun navigateUp(): Boolean {
        val parent = _state.value.currentDirectory?.parentFile
        return if (parent != null && parent.canRead()) {
            navigateToDirectory(parent)
            true
        } else false
    }

    fun refresh() {
        _state.value.currentDirectory?.let { navigateToDirectory(it) }
    }

    fun onPullRefresh() {
        _state.value = _state.value.copy(isRefreshing = true)
        _state.value.currentDirectory?.let { dir ->
            if (!dir.canRead()) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    errorMessage = "无权限访问此目录"
                )
                return
            }

            val files = dir.listFiles()?.map { FileItem(it) }
                ?.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?: emptyList()

            val breadcrumbs = mutableListOf<File>()
            var current: File? = dir
            while (current != null) {
                breadcrumbs.add(0, current)
                current = current.parentFile
            }

            _state.value = _state.value.copy(
                files = files,
                breadcrumbs = breadcrumbs,
                isRefreshing = false,
                refreshSuccess = true,
                isMultiSelectMode = false,
                selectedFiles = emptySet()
            )
        } ?: run {
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    fun clearRefreshSuccess() {
        _state.value = _state.value.copy(refreshSuccess = null)
    }

    fun enterMultiSelectMode(file: File) {
        _state.value = _state.value.copy(
            isMultiSelectMode = true,
            selectedFiles = setOf(file)
        )
    }

    fun exitMultiSelectMode() {
        _state.value = _state.value.copy(
            isMultiSelectMode = false,
            selectedFiles = emptySet()
        )
    }

    fun toggleFileSelection(file: File) {
        val currentSelected = _state.value.selectedFiles.toMutableSet()
        if (currentSelected.contains(file)) {
            currentSelected.remove(file)
        } else {
            currentSelected.add(file)
        }
        _state.value = _state.value.copy(selectedFiles = currentSelected)
    }

    fun selectAll() {
        val allFiles = _state.value.files.map { it.file }.toSet()
        _state.value = _state.value.copy(selectedFiles = allFiles)
    }

    fun deselectAll() {
        _state.value = _state.value.copy(selectedFiles = emptySet())
    }

    fun copySelectedFiles() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isNotEmpty()) {
            ClipboardManager.copyFiles(selectedFiles)
            exitMultiSelectMode()
        }
    }

    fun cutSelectedFiles() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isNotEmpty()) {
            ClipboardManager.cutFiles(selectedFiles)
            exitMultiSelectMode()
        }
    }

    fun pasteFiles(): Result<Int> {
        val clipboardData = ClipboardManager.getClipboardData() ?: return Result.failure(Exception("剪贴板为空"))
        val currentDir = _state.value.currentDirectory ?: return Result.failure(Exception("当前目录不可用"))

        var successCount = 0
        val isCut = ClipboardManager.isCutOperation()

        clipboardData.files.forEach { source ->
            try {
                val target = getUniqueFile(currentDir, source.name)
                if (isCut) {
                    if (!source.renameTo(target)) {
                        if (source.isDirectory) copyDirectory(source, target) else copyFile(source, target)
                        deleteRecursive(source)
                    }
                } else {
                    if (source.isDirectory) copyDirectory(source, target) else copyFile(source, target)
                }
                successCount++
            } catch (e: Exception) { }
        }

        if (isCut) ClipboardManager.clear()
        refresh()
        return Result.success(successCount)
    }

    fun deleteSelectedFiles(): Result<Int> {
        var successCount = 0
        _state.value.selectedFiles.forEach { file ->
            if (deleteRecursive(file)) successCount++
        }
        exitMultiSelectMode()
        refresh()
        return Result.success(successCount)
    }

    fun deleteFile(file: File): Boolean {
        val result = deleteRecursive(file)
        refresh()
        return result
    }

    fun renameFile(file: File, newName: String): Boolean {
        val newFile = File(file.parent, newName)
        val result = file.renameTo(newFile)
        if (result) refresh()
        return result
    }

    fun createFolder(name: String): Boolean {
        val currentDir = _state.value.currentDirectory ?: return false
        val newFolder = File(currentDir, name)
        if (newFolder.exists()) return false
        val result = newFolder.mkdir()
        if (result) refresh()
        return result
    }

    fun createFile(name: String): Boolean {
        val currentDir = _state.value.currentDirectory ?: return false
        val newFile = File(currentDir, name)
        if (newFile.exists()) return false
        return try {
            val result = newFile.createNewFile()
            if (result) refresh()
            result
        } catch (e: Exception) { false }
    }

    fun compressToZip(folder: File, onComplete: (Boolean) -> Unit) {
        if (!folder.isDirectory) {
            onComplete(false)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val parentDir = folder.parentFile ?: return@Thread
                val zipFile = getUniqueFile(parentDir, "${folder.name}.zip")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                    zipFolder(folder, folder.name, zos)
                }
                handler.post { onComplete(true) }
            } catch (e: Exception) {
                handler.post { onComplete(false) }
            }
        }.start()
    }

    fun extractZip(zipFile: File, onComplete: (Boolean) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val parentDir = zipFile.parentFile ?: return@Thread
                val targetFolder = getUniqueFile(parentDir, zipFile.nameWithoutExtension)
                targetFolder.mkdirs()
                ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val entryFile = File(targetFolder, entry.name)
                        if (!entryFile.canonicalPath.startsWith(targetFolder.canonicalPath)) {
                            throw SecurityException("ZIP entry outside target")
                        }
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(entryFile)).use { bos -> zis.copyTo(bos) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                handler.post { onComplete(true) }
            } catch (e: Exception) {
                handler.post { onComplete(false) }
            }
        }.start()
    }

    private fun updateStorageInfo() {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            val availableBytes = stat.blockSizeLong * stat.availableBlocksLong
            val usedBytes = totalBytes - availableBytes
            val usedSpace = formatFileSize(usedBytes)
            val totalSpace = formatFileSize(totalBytes)
            _state.value = _state.value.copy(storageInfo = "已用 $usedSpace / 共 $totalSpace")
        } catch (e: Exception) { }
    }

    private fun formatFileSize(size: Long): String {
        val kb = 1024L; val mb = kb * 1024; val gb = mb * 1024
        return when {
            size >= gb -> String.format("%.2f GB", size.toDouble() / gb)
            size >= mb -> String.format("%.2f MB", size.toDouble() / mb)
            size >= kb -> String.format("%.2f KB", size.toDouble() / kb)
            else -> "$size B"
        }
    }

    private fun getUniqueFile(directory: File, originalName: String): File {
        var targetFile = File(directory, originalName)
        if (!targetFile.exists()) return targetFile
        val lastDot = originalName.lastIndexOf('.')
        val nameWithoutExt = if (lastDot > 0) originalName.substring(0, lastDot) else originalName
        val ext = if (lastDot > 0) originalName.substring(lastDot) else ""
        var counter = 1
        while (targetFile.exists() && counter < 1000) {
            targetFile = File(directory, "$nameWithoutExt($counter)$ext")
            counter++
        }
        return targetFile
    }

    private fun copyFile(source: File, target: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } else {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { file ->
            val targetChild = File(target, file.name)
            if (file.isDirectory) copyDirectory(file, targetChild) else copyFile(file, targetChild)
        }
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { if (!deleteRecursive(it)) return false }
        }
        return file.delete()
    }

    private fun zipFolder(folder: File, basePath: String, zos: ZipOutputStream) {
        folder.listFiles()?.forEach { file ->
            val entryPath = "$basePath/${file.name}"
            if (file.isDirectory) {
                zos.putNextEntry(ZipEntry("$entryPath/"))
                zos.closeEntry()
                zipFolder(file, entryPath, zos)
            } else {
                zos.putNextEntry(ZipEntry(entryPath))
                BufferedInputStream(FileInputStream(file)).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
