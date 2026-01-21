package com.example.fileexplorerjc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fileexplorerjc.ClipboardManager
import com.example.fileexplorerjc.FavoritesManager
import com.example.fileexplorerjc.FileExplorerViewModel
import com.example.fileexplorerjc.FileItem
import com.example.fileexplorerjc.ui.theme.FileGray
import com.example.fileexplorerjc.ui.theme.FolderYellow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: FileExplorerViewModel,
    onOpenTextFile: (File) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var showFavoritesDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var showFileMenu by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(Unit) {
        FavoritesManager.init(context)
        // 路径恢复在 MainActivity 中处理，这里只在没有初始化时才加载
        if (state.currentDirectory == null) {
            viewModel.loadInitialDirectory()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isMultiSelectMode) "已选 ${state.selectedFiles.size} 项"
                        else "文件管理器"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isMultiSelectMode) viewModel.exitMultiSelectMode()
                        else viewModel.navigateUp()
                    }) {
                        Icon(
                            if (state.isMultiSelectMode) Icons.Default.Close
                            else Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (!state.isMultiSelectMode) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { showFavoritesDialog = true }) {
                            Icon(Icons.Default.Star, contentDescription = "收藏夹")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = state.isMultiSelectMode) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = {
                            viewModel.copySelectedFiles()
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                                Text("复制", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = {
                            viewModel.cutSelectedFiles()
                            Toast.makeText(context, "已剪切", Toast.LENGTH_SHORT).show()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ContentCut, contentDescription = "剪切")
                                Text("剪切", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = {
                            showDeleteDialog = state.selectedFiles.firstOrNull()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = {
                            if (state.selectedFiles.size == state.files.size) viewModel.deselectAll()
                            else viewModel.selectAll()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SelectAll, contentDescription = "全选")
                                Text("全选", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!state.isMultiSelectMode) {
                Column {
                    if (state.hasClipboardContent) {
                        SmallFloatingActionButton(
                            onClick = {
                                val result = viewModel.pasteFiles()
                                val msg = if (ClipboardManager.isCutOperation()) "移动" else "粘贴"
                                result.onSuccess { Toast.makeText(context, "${msg}成功: $it 项", Toast.LENGTH_SHORT).show() }
                                result.onFailure { Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show() }
                            },
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴")
                        }
                    }
                    FloatingActionButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
                // 存储信息
                if (state.storageInfo.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = state.storageInfo,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // 面包屑导航
                BreadcrumbRow(
                    breadcrumbs = state.breadcrumbs,
                    onBreadcrumbClick = { viewModel.navigateToDirectory(it) }
                )

                // 当前路径
                Text(
                    text = state.currentDirectory?.absolutePath ?: "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 文件列表
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(state.files, key = { it.file.absolutePath }) { fileItem ->
                            FileItemCard(
                                fileItem = fileItem,
                                isSelected = state.selectedFiles.contains(fileItem.file),
                                isMultiSelectMode = state.isMultiSelectMode,
                                onClick = {
                                    if (state.isMultiSelectMode) {
                                        viewModel.toggleFileSelection(fileItem.file)
                                    } else if (fileItem.isDirectory) {
                                        viewModel.navigateToDirectory(fileItem.file)
                                    } else {
                                        // 打开文件
                                        val ext = fileItem.file.extension.lowercase()
                                        if (ext in listOf("txt", "log", "json", "xml", "md", "csv", "ini", "cfg", "conf", "properties", "yaml", "yml")) {
                                            // 使用内置编辑器打开文本文件
                                            onOpenTextFile(fileItem.file)
                                        } else {
                                            // 使用外部应用打开
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    fileItem.file
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, getMimeType(fileItem.file))
                                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "无法打开文件", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!state.isMultiSelectMode) {
                                        viewModel.enterMultiSelectMode(fileItem.file)
                                    }
                                },
                                onMoreClick = { showFileMenu = fileItem }
                            )
                        }
                    }
                }
            }
        }

    // 新建对话框
    if (showCreateDialog) {
        CreateDialog(
            onDismiss = { showCreateDialog = false },
            onCreateFolder = { name ->
                if (viewModel.createFolder(name)) {
                    Toast.makeText(context, "文件夹创建成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                }
                showCreateDialog = false
            },
            onCreateFile = { name ->
                if (viewModel.createFile(name)) {
                    Toast.makeText(context, "文件创建成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "创建失败", Toast.LENGTH_SHORT).show()
                }
                showCreateDialog = false
            }
        )
    }

    // 收藏夹对话框
    if (showFavoritesDialog) {
        FavoritesDialog(
            onDismiss = { showFavoritesDialog = false },
            onFavoriteClick = {
                viewModel.navigateToDirectory(it)
                showFavoritesDialog = false
            }
        )
    }

    // 重命名对话框
    showRenameDialog?.let { file ->
        RenameDialog(
            currentName = file.name,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                if (viewModel.renameFile(file, newName)) {
                    Toast.makeText(context, "重命名成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
                }
                showRenameDialog = null
            }
        )
    }

    // 删除确认对话框
    showDeleteDialog?.let {
        DeleteConfirmDialog(
            count = if (state.isMultiSelectMode) state.selectedFiles.size else 1,
            onDismiss = { showDeleteDialog = null },
            onConfirm = {
                if (state.isMultiSelectMode) {
                    val result = viewModel.deleteSelectedFiles()
                    result.onSuccess { Toast.makeText(context, "已删除 $it 项", Toast.LENGTH_SHORT).show() }
                } else {
                    if (viewModel.deleteFile(it)) {
                        Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                    }
                }
                showDeleteDialog = null
            }
        )
    }

    // 文件操作菜单
    showFileMenu?.let { fileItem ->
        FileOptionsBottomSheet(
            fileItem = fileItem,
            onDismiss = { showFileMenu = null },
            onRename = { showRenameDialog = fileItem.file; showFileMenu = null },
            onDelete = { showDeleteDialog = fileItem.file; showFileMenu = null },
            onCopy = {
                ClipboardManager.copyFiles(listOf(fileItem.file))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                viewModel.refresh()
                showFileMenu = null
            },
            onToggleFavorite = {
                if (fileItem.isDirectory) {
                    val isFav = FavoritesManager.toggleFavorite(fileItem.file.absolutePath)
                    Toast.makeText(context, if (isFav) "已添加收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
                }
                showFileMenu = null
            },
            onCompress = {
                if (fileItem.isDirectory) {
                    Toast.makeText(context, "正在压缩...", Toast.LENGTH_SHORT).show()
                    viewModel.compressToZip(fileItem.file) { success ->
                        viewModel.refresh()
                        Toast.makeText(context, if (success) "压缩成功" else "压缩失败", Toast.LENGTH_SHORT).show()
                    }
                }
                showFileMenu = null
            },
            onExtract = {
                if (fileItem.file.extension.lowercase() == "zip") {
                    Toast.makeText(context, "正在解压...", Toast.LENGTH_SHORT).show()
                    viewModel.extractZip(fileItem.file) { success ->
                        viewModel.refresh()
                        Toast.makeText(context, if (success) "解压成功" else "解压失败", Toast.LENGTH_SHORT).show()
                    }
                }
                showFileMenu = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BreadcrumbRow(
    breadcrumbs: List<File>,
    onBreadcrumbClick: (File) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        breadcrumbs.forEachIndexed { index, file ->
            val isLast = index == breadcrumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { onBreadcrumbClick(file) },
                    label = {
                        Text(
                            if (file.parentFile == null) "根目录" else file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isLast) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                if (!isLast) {
                    Text(">", modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemCard(
    fileItem: FileItem,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current

    // 获取 APK 图标 (缩放到合适大小)
    val apkIconBitmap = remember(fileItem.file.absolutePath) {
        if (fileItem.file.extension.lowercase() == "apk") {
            getApkIconBitmap(context.packageManager, fileItem.file.absolutePath, 96)
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // 图标
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                if (apkIconBitmap != null) {
                    // 显示 APK 图标
                    Image(
                        bitmap = apkIconBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (fileItem.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = if (fileItem.isDirectory) FolderYellow else FileGray,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 文件名和信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (fileItem.isDirectory) fileItem.getDisplayDate()
                    else "${fileItem.getDisplaySize()} • ${fileItem.getDisplayDate()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isMultiSelectMode) {
                IconButton(onClick = onMoreClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDialog(
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit
) {
    var isFolder by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建") },
        text = {
            Column {
                Row {
                    FilterChip(
                        selected = isFolder,
                        onClick = { isFolder = true },
                        label = { Text("文件夹") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = !isFolder,
                        onClick = { isFolder = false },
                        label = { Text("文件") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isFolder) "文件夹名称" else "文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        if (isFolder) onCreateFolder(name) else onCreateFile(name)
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesDialog(
    onDismiss: () -> Unit,
    onFavoriteClick: (File) -> Unit
) {
    val favorites = FavoritesManager.getValidFavorites()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏夹") },
        text = {
            if (favorites.isEmpty()) {
                Text("暂无收藏")
            } else {
                LazyColumn {
                    items(favorites) { file ->
                        ListItem(
                            headlineContent = { Text(file.name) },
                            supportingContent = {
                                Text(
                                    text = file.absolutePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = FolderYellow) },
                            modifier = Modifier.combinedClickable(onClick = { onFavoriteClick(file) })
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("新名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank() && name != currentName
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除 $count 个项目吗？此操作不可撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileOptionsBottomSheet(
    fileItem: FileItem,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit
) {
    val isFavorite = if (fileItem.isDirectory) FavoritesManager.isFavorite(fileItem.file.absolutePath) else false

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            ListItem(
                headlineContent = { Text(fileItem.name) },
                supportingContent = { Text(if (fileItem.isDirectory) "文件夹" else fileItem.getDisplaySize()) },
                leadingContent = {
                    Icon(
                        if (fileItem.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = if (fileItem.isDirectory) FolderYellow else FileGray
                    )
                }
            )
            Divider()

            ListItem(
                headlineContent = { Text("复制") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier.combinedClickable(onClick = onCopy)
            )
            ListItem(
                headlineContent = { Text("重命名") },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.combinedClickable(onClick = onRename)
            )

            if (fileItem.isDirectory) {
                ListItem(
                    headlineContent = { Text(if (isFavorite) "取消收藏" else "添加收藏") },
                    leadingContent = { Icon(if (isFavorite) Icons.Default.StarOutline else Icons.Default.Star, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = onToggleFavorite)
                )
                ListItem(
                    headlineContent = { Text("压缩为ZIP") },
                    leadingContent = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = onCompress)
                )
            }

            if (!fileItem.isDirectory && fileItem.file.extension.lowercase() == "zip") {
                ListItem(
                    headlineContent = { Text("解压") },
                    leadingContent = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = onExtract)
                )
            }

            ListItem(
                headlineContent = { Text("删除", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.combinedClickable(onClick = onDelete)
            )
        }
    }
}

private fun getMimeType(file: File): String {
    return when (file.extension.lowercase()) {
        "txt", "log" -> "text/plain"
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        else -> "*/*"
    }
}

private fun getApkIconBitmap(packageManager: PackageManager, apkPath: String, size: Int): ImageBitmap? {
    return try {
        val packageInfo = packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
        packageInfo?.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
            val drawable = appInfo.loadIcon(packageManager)
            // 缩放到指定大小
            val bitmap = drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
}
