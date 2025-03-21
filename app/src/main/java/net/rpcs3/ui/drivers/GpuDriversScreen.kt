package net.rpcs3.ui.drivers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.rpcs3.RPCS3
import net.rpcs3.utils.GpuDriverHelper
import net.rpcs3.utils.GpuDriverInstallResult
import net.rpcs3.utils.GpuDriverMetadata
import net.rpcs3.utils.DriversFetcher
import net.rpcs3.utils.DriversFetcher.FetchResult
import net.rpcs3.utils.DriversFetcher.FetchResultOutput
import net.rpcs3.utils.DriversFetcher.DownloadResult
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuDriversScreen(navigateBack: () -> Unit) {
    val context = LocalContext.current
    var drivers by remember { mutableStateOf(GpuDriverHelper.getInstalledDrivers(context)) }
    var selectedDriver by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isInstalling by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showDriverDialog by remember { mutableStateOf(false) }
    var shouldHandleGpuDriverImport by remember { mutableStateOf(false) }
    var shouldFetchAndShowDrivers by remember { mutableStateOf(false) }
    var repoUrl by remember { mutableStateOf<String?>(null) }

    val driverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isInstalling = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val result = GpuDriverHelper.installDriver(context, stream)
                        if (result == GpuDriverInstallResult.Success) {
                            val updatedDrivers = GpuDriverHelper.getInstalledDrivers(context)
                            withContext(Dispatchers.Main) {
                                drivers = updatedDrivers
                            }
                        }
                        withContext(Dispatchers.Main) {
                            isInstalling = false
                            snackbarHostState.showSnackbar(
                                message = GpuDriverHelper.resolveInstallResultToString(result),
                                actionLabel = "Dismiss",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GpuDriver", "Error installing driver: ${e.message}")
                }
            }
        }
    }

    selectedDriver = prefs.getString("selected_gpu_driver", "Default")

    if (showDriverDialog) {
        DriverDialog(
            onDismiss = { showDriverDialog = false },
            onInstallClick = {
                driverPickerLauncher.launch("application/zip")
            },
            onImportClick = {
                shouldHandleGpuDriverImport = true
            }
        )
    }

    if (shouldHandleGpuDriverImport) {
        handleGpuDriverImport(
            onDismiss = { shouldHandleGpuDriverImport = false },
            onFetchClick = { url ->
                shouldFetchAndShowDrivers = true
            }
        )
    }

    if (shouldFetchAndShowDrivers) {
        fetchAndShowDrivers(
            repoUrl = repoUrl!!,
            bypassValidation = false,
            onDismiss = { shouldFetchAndShowDrivers = false },
            onDownloadDriver = { url, name ->
                //downloadDriver(url, name)
            }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, topBar = {
        TopAppBar(
            title = { Text(text = "GPU Drivers", fontWeight = FontWeight.Medium) },
            scrollBehavior = topBarScrollBehavior,
            navigationIcon = {
                IconButton(
                    onClick = navigateBack
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft, null)
                }
            })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Select a GPU Driver",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyColumn {
                items(drivers.entries.toList()) { (file, metadata) ->
                    DriverItem(
                        file = file,
                        metadata = metadata,
                        isSelected = metadata.label == selectedDriver,
                        onSelect = {
                            selectedDriver = metadata.label
                            prefs.edit {
                                putString(
                                    "selected_gpu_driver", selectedDriver ?: ""
                                )
                            }

                            val path = if (metadata.name == "Default") "" else file.path
                            val field =
                                ApplicationInfo::class.java.getField("nativeLibraryDir")
                            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0);
                            val nativeLibraryDir = field.get(appInfo) as String

                            Log.e("Driver", "path $path, internal data dir ${context.filesDir}")
                            RPCS3.instance.settingsSet("Video@@Vulkan@@Custom Driver@@Path", "\"" + path + "\"")
                            RPCS3.instance.settingsSet("Video@@Vulkan@@Custom Driver@@Internal Data Directory", "\"" + context.filesDir + "\"")
                            RPCS3.instance.settingsSet("Video@@Vulkan@@Custom Driver@@Hook Directory", "\"" + nativeLibraryDir + "\"")
                        },
                        onDelete = if (metadata.name == "Default") null else { driverFile ->
                            coroutineScope.launch {
                                if (driverFile.deleteRecursively()) {
                                    drivers = GpuDriverHelper.getInstalledDrivers(context)
                                }
                            }
                        })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showDriverDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInstalling) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = !isInstalling
            ) {
                if (isInstalling) {
                    Text("Installing...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Add, contentDescription = "Install Driver"
                    )
                }
            }

        }
    }
}

@Composable
fun DriverItemContent(
    metadata: GpuDriverMetadata,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = metadata.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = metadata.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DriverItem(
    file: File,
    metadata: GpuDriverMetadata,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: ((File) -> Unit)?
) {
    if (onDelete == null) {
        DriverItemContent(metadata, isSelected, onSelect)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete(file)
                true
            } else {
                false
            }
        })

    SwipeToDismissBox(
        modifier = Modifier.animateContentSize(),
        state = dismissState,
        backgroundContent = {
            if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(end = 16.dp)
                        .padding(vertical = 4.dp),
                    contentAlignment = androidx.compose.ui.Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White
                    )
                }
            }
        },
        content = { DriverItemContent(metadata, isSelected, onSelect) },
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = false
    )
}

@Composable
fun DriverDialog(
    onDismiss: () -> Unit,
    onInstallClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val items = listOf(
        "Import",
        "Install"
    )
    var selectedItemIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Choose")
        },
        text = {
            Column {
                items.forEachIndexed { index, text ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedItemIndex = index }
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = selectedItemIndex == index,
                            onClick = { selectedItemIndex = index }
                        )
                        Text(text = text, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedItemIndex == 1) {
                    onInstallClick()
                } else {
                    onImportClick()
                }
                onDismiss()
            }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun handleGpuDriverImport(
    onDismiss: () -> Unit,
    onFetchClick: (String) -> Unit
) {
    var textInputValue by remember { mutableStateOf("https://github.com/K11MCH1/AdrenoToolsDrivers") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Enter repo url")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = textInputValue,
                    onValueChange = { textInputValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (textInputValue.isNotEmpty()) {
                    onFetchClick(textInputValue)
                }
                onDismiss()
            }) {
                Text(text = "Fetch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun fetchAndShowDrivers(
    repoUrl: String,
    bypassValidation: Boolean = false,
    onDismiss: () -> Unit,
    onDownloadDriver: (String, String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var fetchResult by remember { mutableStateOf<FetchResult?>(null) }
    var fetchedDrivers by remember { mutableStateOf<List<Pair<String, String?>>>(emptyList()) }
    var chosenIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val fetchOutput = DriversFetcher.fetchReleases(repoUrl, bypassValidation)
        isLoading = false

        if (fetchOutput.result is FetchResult.Error) {
            fetchResult = fetchOutput.result
        } else if (fetchOutput.result is FetchResult.Warning) {
            fetchResult = fetchOutput.result
        } else {
            fetchedDrivers = fetchOutput.fetchedDrivers
        }
    }

    fetchResult?.let {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Error") },
            text = { Text(it.message ?: "Something unexpected occurred while fetching $repoUrl drivers") },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
        return
    }

    if (isLoading) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Fetching") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Please wait")
                }
            },
            confirmButton = {}
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drivers") },
        text = {
            Column {
                fetchedDrivers.forEachIndexed { index, driver ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenIndex = index }
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = chosenIndex == index,
                            onClick = { chosenIndex = index }
                        )
                        Text(text = driver.first, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val chosenDriver = fetchedDrivers[chosenIndex]
                onDownloadDriver(chosenDriver.second!!, chosenDriver.first!!)
                onDismiss()
            }) {
                Text(text = "Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}
