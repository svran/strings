package air.svran.app.xp.strings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var xposedService: XposedService? by mutableStateOf(null)
    private var scopeList by mutableStateOf<List<String>>(emptyList())
    private var appsList by mutableStateOf<List<ApplicationInfo>>(emptyList())
    private var isAppsLoaded by mutableStateOf(false)

    private var currentPkgForFile: String? = null
    
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleFileSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                refreshScope(service)
            }

            override fun onServiceDied(service: XposedService) {
                xposedService = null
            }
        })

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    private fun loadApps() {
        if (isAppsLoaded) return
        lifecycleScope.launch(Dispatchers.IO) {
            val list = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != packageName }
                .sortedBy { it.loadLabel(packageManager).toString() }
            withContext(Dispatchers.Main) {
                appsList = list
                isAppsLoaded = true
            }
        }
    }

    private fun refreshScope(service: XposedService) {
        try {
            scopeList = service.scope
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting scope", e)
        }
    }

    private fun handleFileSelected(uri: Uri) {
        val pkg = currentPkgForFile ?: return
        val service = xposedService ?: return
        
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val pfd = service.openRemoteFile("${pkg}_strings.xml")
                FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                pfd.close()
            }
            Toast.makeText(this, "配置文件已保存: $pkg", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save file", e)
            Toast.makeText(this, "保存文件失败", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun AppNavigation() {
        var showAppList by remember { mutableStateOf(false) }

        if (showAppList && xposedService != null) {
            AppListScreen(onBack = { showAppList = false })
        } else {
            StatusScreen(onContinue = { showAppList = true })
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun StatusScreen(onContinue: () -> Unit) {
        val service = xposedService
        val isActive = service != null

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Strings 模块状态") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Status Icon",
                    tint = if (isActive) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isActive) "模块已激活" else "模块未激活",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isActive) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("框架信息", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (service != null) {
                            Text("框架名称: ${service.frameworkName}", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("框架版本: ${service.frameworkVersion} (${service.frameworkVersionCode})", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("API 版本: ${service.apiVersion}", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text("未检测到 LSPosed 框架服务。\n请确认模块已在 LSPosed 中勾选，并重启应用。", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Button(
                    onClick = onContinue,
                    enabled = isActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("管理目标应用", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppListScreen(onBack: () -> Unit) {
        val service = xposedService ?: return
        
        LaunchedEffect(Unit) {
            loadApps()
        }
        
        BackHandler {
            onBack()
        }

        val prefs = remember { getPreferences(Context.MODE_PRIVATE) }
        var showMenu by remember { mutableStateOf(false) }
        var isSearching by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var hideSystemApps by remember { mutableStateOf(prefs.getBoolean("hideSystemApps", false)) }
        var sortEnabledFirst by remember { mutableStateOf(prefs.getBoolean("sortEnabledFirst", false)) }

        val displayedApps = remember(appsList, scopeList, hideSystemApps, sortEnabledFirst, searchQuery) {
            var list = appsList
            if (hideSystemApps) {
                list = list.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            }
            if (searchQuery.isNotEmpty()) {
                val query = searchQuery.lowercase()
                list = list.filter { 
                    it.packageName.lowercase().contains(query) || 
                    it.loadLabel(packageManager).toString().lowercase().contains(query) 
                }
            }
            if (sortEnabledFirst) {
                list = list.sortedByDescending { scopeList.contains(it.packageName) }
            }
            list
        }

        Scaffold(
            topBar = {
                if (isSearching) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("搜索应用名或包名") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                isSearching = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text("选择目标应用") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("隐藏系统应用") },
                                    trailingIcon = {
                                        if (hideSystemApps) {
                                            Icon(Icons.Default.Check, contentDescription = "Checked")
                                        }
                                    },
                                    onClick = {
                                        val newValue = !hideSystemApps
                                        hideSystemApps = newValue
                                        prefs.edit().putBoolean("hideSystemApps", newValue).apply()
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("已启用置顶") },
                                    trailingIcon = {
                                        if (sortEnabledFirst) {
                                            Icon(Icons.Default.Check, contentDescription = "Checked")
                                        }
                                    },
                                    onClick = {
                                        val newValue = !sortEnabledFirst
                                        sortEnabledFirst = newValue
                                        prefs.edit().putBoolean("sortEnabledFirst", newValue).apply()
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    )
                }
            }
        ) { padding ->
            if (!isAppsLoaded) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    items(displayedApps, key = { it.packageName }) { app ->
                        val isEnabled = scopeList.contains(app.packageName)
                        AppItem(
                            appInfo = app,
                            isEnabled = isEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    service.requestScope(listOf(app.packageName), object : XposedService.OnScopeEventListener {
                                        override fun onScopeRequestApproved(approved: List<String>) {
                                            runOnUiThread { refreshScope(service) }
                                        }
                                        override fun onScopeRequestFailed(message: String) {
                                            runOnUiThread {
                                                Toast.makeText(this@MainActivity, "授权失败: $message", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    })
                                } else {
                                    service.removeScope(listOf(app.packageName))
                                    refreshScope(service)
                                }
                            },
                            onConfigureClick = {
                                currentPkgForFile = app.packageName
                                filePickerLauncher.launch("text/xml")
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun AppItem(
        appInfo: ApplicationInfo,
        isEnabled: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        onConfigureClick: () -> Unit
    ) {
        val appName = remember { appInfo.loadLabel(packageManager).toString() }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!isEnabled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = appName, style = MaterialTheme.typography.titleMedium)
                Text(text = appInfo.packageName, style = MaterialTheme.typography.bodySmall)
            }
            
            if (isEnabled) {
                Button(
                    onClick = onConfigureClick,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text("选择 XML")
                }
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
