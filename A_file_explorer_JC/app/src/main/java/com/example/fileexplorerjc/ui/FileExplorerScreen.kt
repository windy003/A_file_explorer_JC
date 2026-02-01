package com.example.fileexplorerjc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
import com.example.fileexplorerjc.DefaultAppManager
import com.example.fileexplorerjc.FavoritesManager
import com.example.fileexplorerjc.FileExplorerViewModel
import com.example.fileexplorerjc.FileItem
import com.example.fileexplorerjc.ui.theme.FileGray
import com.example.fileexplorerjc.ui.theme.FolderYellow
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
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
    var showOpenWithDialog by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        FavoritesManager.init(context)
        DefaultAppManager.init(context)
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

    LaunchedEffect(state.refreshSuccess) {
        if (state.refreshSuccess == true) {
            Toast.makeText(context, "刷新成功", Toast.LENGTH_SHORT).show()
            viewModel.clearRefreshSuccess()
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

                // 文件列表（支持下拉刷新）
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    SwipeRefresh(
                        state = rememberSwipeRefreshState(isRefreshing = state.isRefreshing),
                        onRefresh = { viewModel.onPullRefresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
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
                                                openFileWithApp(
                                                    context = context,
                                                    file = fileItem.file,
                                                    packageName = DefaultAppManager.getDefaultApp(ext)
                                                )
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
            },
            onOpenWith = {
                showOpenWithDialog = fileItem.file
                showFileMenu = null
            }
        )
    }

    // 打开方式对话框
    showOpenWithDialog?.let { file ->
        OpenWithDialog(
            file = file,
            onDismiss = { showOpenWithDialog = null },
            onAppSelected = { packageName, setAsDefault, mimeType ->
                val ext = file.extension.lowercase()
                if (setAsDefault) {
                    DefaultAppManager.setDefaultApp(ext, packageName)
                    Toast.makeText(context, "已设为默认打开方式", Toast.LENGTH_SHORT).show()
                }
                openFileWithApp(context, file, packageName, mimeType)
                showOpenWithDialog = null
            },
            onClearDefault = {
                val ext = file.extension.lowercase()
                DefaultAppManager.clearDefaultApp(ext)
                Toast.makeText(context, "已清除默认设置", Toast.LENGTH_SHORT).show()
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

// 图片文件扩展名列表
private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")

/**
 * 判断文件是否是图片
 */
private fun isImageFile(file: File): Boolean {
    return file.extension.lowercase() in imageExtensions
}

/**
 * 加载图片缩略图
 */
private fun loadImageThumbnail(file: File, size: Int): ImageBitmap? {
    return try {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)

        // 计算缩放比例
        val scaleFactor = maxOf(
            options.outWidth / size,
            options.outHeight / size,
            1
        )

        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = scaleFactor
        }

        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
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

    // 异步加载图片缩略图
    var imageThumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    val isImage = remember(fileItem.file.absolutePath) { isImageFile(fileItem.file) }

    LaunchedEffect(fileItem.file.absolutePath) {
        if (isImage && imageThumbnail == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                imageThumbnail = loadImageThumbnail(fileItem.file, 96)
            }
        }
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

            // 图标/缩略图
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                when {
                    // 显示图片缩略图
                    isImage && imageThumbnail != null -> {
                        Image(
                            bitmap = imageThumbnail!!,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    // 图片加载中显示占位图标
                    isImage -> {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                    // 显示 APK 图标
                    apkIconBitmap != null -> {
                        Image(
                            bitmap = apkIconBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    // 默认图标
                    else -> {
                        Icon(
                            imageVector = if (fileItem.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (fileItem.isDirectory) FolderYellow else FileGray,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
    onExtract: () -> Unit,
    onOpenWith: () -> Unit
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

            // 文件才显示"打开方式"选项
            if (!fileItem.isDirectory) {
                ListItem(
                    headlineContent = { Text("打开方式") },
                    leadingContent = { Icon(Icons.Default.Launch, contentDescription = null) },
                    modifier = Modifier.combinedClickable(onClick = onOpenWith)
                )
            }

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

/**
 * 文件类型枚举
 */
enum class FileOpenType(val label: String, val mimeType: String, val icon: @Composable () -> Unit) {
    TEXT("文本", "text/*", { Icon(Icons.Default.Description, contentDescription = null) }),
    IMAGE("图片", "image/*", { Icon(Icons.Default.Image, contentDescription = null) }),
    VIDEO("视频", "video/*", { Icon(Icons.Default.Movie, contentDescription = null) }),
    AUDIO("音频", "audio/*", { Icon(Icons.Default.MusicNote, contentDescription = null) }),
    APPLICATION("应用/其他", "application/*", { Icon(Icons.Default.Apps, contentDescription = null) }),
    ALL("全部类型", "*/*", { Icon(Icons.Default.MoreHoriz, contentDescription = null) })
}

/**
 * 获取可以打开指定 MIME 类型的应用列表
 */
private fun getAppsForMimeType(context: android.content.Context, file: File, mimeType: String): List<ResolveInfo> {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    // 使用 MATCH_ALL 获取所有可以处理此 Intent 的应用，而不仅仅是默认应用
    val apps = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    // 去重（同一个包名可能有多个 Activity）
    return apps.distinctBy { it.activityInfo.packageName }
}

/**
 * 使用指定应用和 MIME 类型打开文件
 */
private fun openFileWithApp(context: android.content.Context, file: File, packageName: String?, mimeType: String? = null) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val actualMimeType = mimeType ?: getMimeType(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, actualMimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (packageName != null) {
                setPackage(packageName)
            }
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val ext = file.extension.lowercase()
        if (packageName != null && DefaultAppManager.hasDefaultApp(ext)) {
            DefaultAppManager.clearDefaultApp(ext)
            Toast.makeText(context, "默认应用无法打开，已清除设置", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "无法打开文件", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 打开方式全屏对话框 - 两步选择：先选类型，再选应用
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OpenWithDialog(
    file: File,
    onDismiss: () -> Unit,
    onAppSelected: (packageName: String, setAsDefault: Boolean, mimeType: String) -> Unit,
    onClearDefault: () -> Unit
) {
    val context = LocalContext.current
    val ext = file.extension.lowercase()
    val currentDefault = remember(ext) { DefaultAppManager.getDefaultApp(ext) }

    // 当前选择的文件类型，null 表示还在选择类型阶段
    var selectedType by remember { mutableStateOf<FileOpenType?>(null) }
    var setAsDefault by remember { mutableStateOf(false) }

    // 根据选择的类型获取应用列表
    val apps = remember(selectedType) {
        selectedType?.let { getAppsForMimeType(context, file, it.mimeType) } ?: emptyList()
    }

    // 全屏对话框
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (selectedType == null) "选择打开类型" else "选择应用")
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedType != null) {
                                selectedType = null
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(
                                if (selectedType != null) Icons.Default.ArrowBack else Icons.Default.Close,
                                contentDescription = if (selectedType != null) "返回" else "关闭"
                            )
                        }
                    },
                    actions = {
                        if (currentDefault != null && selectedType == null) {
                            TextButton(onClick = { onClearDefault() }) {
                                Text("清除默认")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 文件名显示
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = FileGray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "扩展名: ${if (ext.isNotEmpty()) ext else "无"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 当前默认应用提示
                if (currentDefault != null && selectedType == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "已设置默认打开应用",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (selectedType == null) {
                    // 第一步：选择文件类型
                    Text(
                        "以什么类型打开此文件？",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(FileOpenType.entries.toList()) { type ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        type.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        type.mimeType,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            type.icon()
                                        }
                                    }
                                },
                                modifier = Modifier.combinedClickable(
                                    onClick = { selectedType = type }
                                )
                            )
                            Divider(modifier = Modifier.padding(start = 80.dp))
                        }
                    }
                } else {
                    // 第二步：选择应用
                    if (apps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "没有可以打开此类型的应用",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // 设为默认选项
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { setAsDefault = !setAsDefault })
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = setAsDefault,
                                onCheckedChange = { setAsDefault = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "设为此类型文件的默认打开方式",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Divider()

                        Text(
                            "共 ${apps.size} 个可用应用",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(apps) { resolveInfo ->
                                val packageName = resolveInfo.activityInfo.packageName
                                val appName = resolveInfo.loadLabel(context.packageManager).toString()
                                val appIcon = remember(packageName) {
                                    try {
                                        resolveInfo.loadIcon(context.packageManager)
                                            .toBitmap(96, 96, Bitmap.Config.ARGB_8888)
                                            .asImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                val isCurrentDefault = packageName == currentDefault

                                ListItem(
                                    headlineContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                appName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            if (isCurrentDefault) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = MaterialTheme.shapes.small,
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        "默认",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    supportingContent = {
                                        Text(
                                            packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingContent = {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Apps,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.combinedClickable(
                                        onClick = { onAppSelected(packageName, setAsDefault, selectedType!!.mimeType) }
                                    )
                                )
                                Divider(modifier = Modifier.padding(start = 80.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
