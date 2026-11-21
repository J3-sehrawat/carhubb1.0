package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExcelReportExporter
import com.example.ui.theme.Danger600
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald600
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.launch
import java.io.File

/**
 * Compact dark-header button matching AutomotiveHeader trailing controls, accented in Excel emerald.
 */
@Composable
fun ExcelExportHeaderChip(
    enabled: Boolean,
    testTagName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (enabled) Emerald600 else Slate800,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.TableView,
                contentDescription = stringResource(R.string.export_to_excel),
                tint = if (enabled) PureWhite else Slate600,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.export_excel),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) PureWhite else Slate600
            )
        }
    }
}

/**
 * Reusable strip below filter chips showing the filtered count and a one-tap Export to Excel CTA.
 */
@Composable
fun FilteredExcelExportStrip(
    summaryText: String,
    hasRows: Boolean,
    testTagName: String,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Slate100,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelMedium,
                color = Slate700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (hasRows) Emerald100 else Slate200,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .border(
                        1.dp,
                        if (hasRows) Emerald600.copy(alpha = 0.35f) else Slate200,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = hasRows, onClick = onExportClick)
                    .testTag(testTagName)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.export_to_excel),
                        tint = if (hasRows) Emerald600 else Slate600,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Export .xlsx",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasRows) Emerald600 else Slate600
                    )
                }
            }
        }
    }
}

/**
 * Reusable Export-to-Excel (.xlsx) confirmation & download dialog supporting:
 * - Direct one-tap download to Downloads/AutoTrade on Android 10+ (zero permissions)
 * - Storage Access Framework "Save As..." folder picker on all Android versions
 * - Direct Open in Excel / Google Sheets & Share via FileProvider
 */
@Composable
fun ExcelExportActionDialog(
    title: String,
    filterSummary: String,
    sheetsIncluded: List<String>,
    generateFile: suspend () -> Result<File>,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var generatedFile by remember { mutableStateOf<File?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var savedLocation by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExcelReportExporter.XLSX_MIME_TYPE)
    ) { uri ->
        val file = generatedFile ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        isSaving = true
        scope.launch {
            ExcelReportExporter.copyToDocumentUri(context, file, uri)
                .onSuccess {
                    savedLocation = "Saved to chosen folder (${file.name})"
                    onNotify("Excel report saved: ${file.name}")
                }
                .onFailure { err ->
                    onNotify("Save failed: ${err.localizedMessage ?: "error"}")
                }
            isSaving = false
        }
    }

    LaunchedEffect(Unit) {
        generateFile()
            .onSuccess { file -> generatedFile = file }
            .onFailure { err -> errorMsg = err.localizedMessage ?: "Failed to build Excel workbook." }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.TableView,
                    contentDescription = null,
                    tint = Emerald600,
                    modifier = Modifier.size(22.dp)
                )
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = filterSummary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900
                        )
                        Text(
                            text = "Worksheets: ${sheetsIncluded.joinToString(" • ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                }

                when {
                    errorMsg != null -> {
                        Text(
                            text = errorMsg!!,
                            color = Danger600,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    generatedFile == null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Emerald600,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Building styled Excel (.xlsx) workbook…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate800
                            )
                        }
                    }
                    else -> {
                        val file = generatedFile!!
                        Text(
                            text = "File: ${file.name} (${(file.length() / 1024L).coerceAtLeast(1L)} KB)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate600,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (savedLocation != null) {
                            Surface(
                                color = Emerald100,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Emerald600,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = savedLocation!!,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Emerald600
                                    )
                                }
                            }
                        }

                        // Primary CTA: Direct Download to Downloads/AutoTrade (API 29+) or SAF Save As (API < 29)
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    isSaving = true
                                    scope.launch {
                                        ExcelReportExporter.saveToDownloads(context, file)
                                            .onSuccess { path ->
                                                savedLocation = "Downloaded to $path"
                                                onNotify("Saved to $path")
                                            }
                                            .onFailure {
                                                saveAsLauncher.launch(file.name)
                                            }
                                        isSaving = false
                                    }
                                } else {
                                    saveAsLauncher.launch(file.name)
                                }
                            },
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("excel_download_confirm_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSaving) "Saving…" else stringResource(R.string.download_xlsx),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = { saveAsLauncher.launch(file.name) },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("excel_save_as_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save_as_xlsx), style = MaterialTheme.typography.labelMedium)
                        }

                        HorizontalDivider(color = Slate200)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { ExcelReportExporter.openXlsxFile(context, file) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("excel_open_button")
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Open", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { ExcelReportExporter.shareXlsxFile(context, file, title) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("excel_share_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Share", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
