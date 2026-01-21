package com.example.fileexplorerjc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fileexplorerjc.ui.FileExplorerScreen
import com.example.fileexplorerjc.ui.TextEditorScreen
import com.example.fileexplorerjc.ui.theme.FileExplorerJCTheme
import kotlinx.coroutines.launch
import java.io.File

private const val PREFS_NAME = "file_explorer_prefs"
private const val KEY_TAB_PATH_PREFIX = "tab_path_"
private const val KEY_LAST_TAB = "last_tab_index"

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            setMainContent()
        }
    }

    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            setMainContent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    setMainContent()
                } else {
                    showPermissionRequest()
                }
            }
            hasLegacyStoragePermissions() -> {
                setMainContent()
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
        showPermissionRequest()
    }

    private fun showPermissionRequest() {
        setContent {
            FileExplorerJCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "需要存储权限",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "文件管理器需要访问存储空间才能正常工作",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
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
                                } else {
                                    requestLegacyPermissions()
                                }
                            }
                        ) {
                            Text("授予权限")
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun setMainContent() {
        setContent {
            FileExplorerJCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    var editingFile by remember { mutableStateOf<File?>(null) }

                    // 3 个独立的 ViewModel
                    val viewModel0: FileExplorerViewModel = viewModel(key = "tab0")
                    val viewModel1: FileExplorerViewModel = viewModel(key = "tab1")
                    val viewModel2: FileExplorerViewModel = viewModel(key = "tab2")
                    val viewModels = listOf(viewModel0, viewModel1, viewModel2)

                    // 从 SharedPreferences 恢复路径
                    LaunchedEffect(Unit) {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        viewModels.forEachIndexed { index, vm ->
                            val savedPath = prefs.getString("$KEY_TAB_PATH_PREFIX$index", null)
                            vm.loadDirectory(savedPath)
                        }
                    }

                    // 标签页标题
                    val tabTitles = remember {
                        mutableStateListOf("标签1", "标签2", "标签3")
                    }

                    // 更新标签标题并保存路径
                    viewModels.forEachIndexed { index, vm ->
                        val state by vm.state.collectAsState()
                        LaunchedEffect(state.currentDirectory) {
                            state.currentDirectory?.let { dir ->
                                tabTitles[index] = if (dir.parentFile == null) "根目录" else dir.name
                                // 保存路径到 SharedPreferences
                                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                prefs.edit().putString("$KEY_TAB_PATH_PREFIX$index", dir.absolutePath).apply()
                            }
                        }
                    }

                    // 恢复上次的标签页
                    val savedTabIndex = remember {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.getInt(KEY_LAST_TAB, 0).coerceIn(0, 2)
                    }
                    val pagerState = rememberPagerState(initialPage = savedTabIndex, pageCount = { 3 })
                    val coroutineScope = rememberCoroutineScope()

                    // 保存当前标签页
                    LaunchedEffect(pagerState.currentPage) {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putInt(KEY_LAST_TAB, pagerState.currentPage).apply()
                    }

                    if (editingFile != null) {
                        TextEditorScreen(
                            file = editingFile!!,
                            onBack = { editingFile = null }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 标签栏
                            TabRow(
                                selectedTabIndex = pagerState.currentPage,
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                tabTitles.forEachIndexed { index, title ->
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        },
                                        text = { Text(title, maxLines = 1) }
                                    )
                                }
                            }

                            // 页面内容
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                FileExplorerScreen(
                                    viewModel = viewModels[page],
                                    onOpenTextFile = { file -> editingFile = file }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
