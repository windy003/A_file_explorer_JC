package com.example.fileexplorerjc

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.fileexplorerjc.ui.FavoritesDialog
import com.example.fileexplorerjc.ui.theme.FileExplorerJCTheme
import com.example.fileexplorerjc.ui.theme.FileGray
import com.example.fileexplorerjc.ui.theme.FolderYellow
import java.io.File

class FilePickerActivity : ComponentActivity() {

    private var mimeTypeFilter: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            setPickerContent()
        } else {
            Toast.makeText(this, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            setPickerContent()
        } else {
            Toast.makeText(this, "需要存储权限才能选择文件", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取请求的 MIME 类型
        mimeTypeFilter = intent.type

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    setPickerContent()
                } else {
                    requestManageStoragePermission()
                }
            }
            hasLegacyStoragePermissions() -> {
                setPickerContent()
            }
            else -> {
                requestLegacyPermissions()
            }
        }
    }

    private fun hasLegacyStoragePermissions(): Boolean {
        val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        val write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLegacyPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStoragePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStoragePermissionLauncher.launch(intent)
            }
        }
    }

    private fun setPickerContent() {
        setContent {
            FileExplorerJCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FilePickerScreen(
                        mimeTypeFilter = mimeTypeFilter,
                        onFileSelected = { file ->
                            returnFileResult(file)
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun returnFileResult(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val resultIntent = Intent().apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "无法返回文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    mimeTypeFilter: String?,
    onFileSelected: (File) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var currentDirectory by remember {
        mutableStateOf(Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0"))
    }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var showFavoritesDialog by remember { mutableStateOf(false) }

    // 初始化收藏夹管理器
    LaunchedEffect(Unit) {
        FavoritesManager.init(context)
    }

    // 根据 MIME 类型过滤文件
    fun shouldShowFile(file: File): Boolean {
        if (file.isDirectory) return true
        if (mimeTypeFilter == null || mimeTypeFilter == "*/*") return true

        val extension = file.extension.lowercase()
        return when {
            mimeTypeFilter.startsWith("image/") -> extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
            mimeTypeFilter.startsWith("video/") -> extension in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp")
            mimeTypeFilter.startsWith("audio/") -> extension in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "wma")
            mimeTypeFilter.startsWith("text/") -> extension in listOf("txt", "log", "json", "xml", "md", "csv", "ini", "cfg")
            mimeTypeFilter == "application/pdf" -> extension == "pdf"
            mimeTypeFilter == "application/zip" -> extension == "zip"
            else -> true
        }
    }

    // 加载目录
    fun loadDirectory(dir: File) {
        currentDirectory = dir
        files = dir.listFiles()
            ?.filter { shouldShowFile(it) }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    LaunchedEffect(currentDirectory) {
        loadDirectory(currentDirectory)
    }

    // 返回手势处理
    BackHandler {
        val parent = currentDirectory.parentFile
        if (parent != null && parent.canRead()) {
            loadDirectory(parent)
        } else {
            onCancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择文件")
                        Text(
                            text = currentDirectory.absolutePath,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    // 收藏夹
                    IconButton(onClick = { showFavoritesDialog = true }) {
                        Icon(Icons.Default.Star, contentDescription = "收藏夹")
                    }
                    // 返回上级目录
                    IconButton(
                        onClick = {
                            val parent = currentDirectory.parentFile
                            if (parent != null && parent.canRead()) {
                                loadDirectory(parent)
                            }
                        },
                        enabled = currentDirectory.parentFile?.canRead() == true
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "上级目录")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("没有文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = file.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            if (!file.isDirectory) {
                                Text(
                                    text = formatFileSize(file.length()),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (file.isDirectory) FolderYellow else FileGray,
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        modifier = Modifier.clickable {
                            if (file.isDirectory) {
                                if (file.canRead()) {
                                    loadDirectory(file)
                                } else {
                                    Toast.makeText(context, "无权限访问", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                onFileSelected(file)
                            }
                        }
                    )
                    Divider()
                }
            }
        }
    }

    // 收藏夹对话框
    if (showFavoritesDialog) {
        FavoritesDialog(
            onDismiss = { showFavoritesDialog = false },
            onFavoriteClick = { favoriteDir ->
                if (favoriteDir.canRead()) {
                    loadDirectory(favoriteDir)
                } else {
                    Toast.makeText(context, "无权限访问", Toast.LENGTH_SHORT).show()
                }
                showFavoritesDialog = false
            }
        )
    }
}

private fun formatFileSize(size: Long): String {
    val kb = 1024L
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        size >= gb -> String.format("%.2f GB", size.toDouble() / gb)
        size >= mb -> String.format("%.2f MB", size.toDouble() / mb)
        size >= kb -> String.format("%.2f KB", size.toDouble() / kb)
        else -> "$size B"
    }
}
