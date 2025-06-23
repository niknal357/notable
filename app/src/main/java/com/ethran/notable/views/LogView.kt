package com.ethran.notable.views

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.ethran.notable.BuildConfig
import com.onyx.android.sdk.utils.ClipboardUtils.copyToClipboard
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

object BugReportHelper {
    private const val MAX_LOG_LINES = 200

    fun getRecentLogs(): String {
        return try {
            // Get all logs and keep only the newest MAX_LOG_LINES
            val process = Runtime.getRuntime().exec("logcat -d ")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val allLines = LinkedList<String>()

            reader.use { r ->
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    allLines.add(line!!)
                    if (allLines.size > MAX_LOG_LINES) {
                        allLines.removeFirst() // Keep only the newest entries
                    }
                }
            }

            if (allLines.isEmpty()) {
                "No recent logs found"
            } else {
                allLines.joinToString("\n")
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    fun formatLogsForDisplay(rawLogs: String): String {
        return rawLogs.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { line ->
                when {
                    line.contains(" E ") -> "🔴 $line"  // Error
                    line.contains(" W ") -> "🟠 $line"  // Warning
                    line.contains(" I ") -> "🔵 $line"  // Info
                    line.contains(" D ") -> "🟢 $line"  // Debug
                    line.contains(" V ") -> "⚪ $line"  // Verbose
                    else -> line
                }
            }
    }
}

@Composable
fun BugReportScreen(navController: NavController) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    // Generate the report content
    val reportData = remember(description, includeLogs) {
        val deviceInfo = buildDeviceInfo()
        val descriptionSection = description.ifBlank { "_No description provided_" }
        val logs = if (includeLogs) BugReportHelper.getRecentLogs() else null

        ReportData(
            description = descriptionSection,
            deviceInfo = deviceInfo,
            logs = logs
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Report an Issue", style = MaterialTheme.typography.h6)
        }

        Spacer(Modifier.height(16.dp))

        // Description field
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Issue description") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(Modifier.height(16.dp))

        // Report Preview Card
        ReportPreviewCard(reportData)

        Spacer(Modifier.height(16.dp))

        // Include logs toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = includeLogs,
                onCheckedChange = { includeLogs = it }
            )
            Spacer(Modifier.width(8.dp))
            Text("Include diagnostic logs")
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = { copyReportToClipboard(context, reportData.rapportMarkdown()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy Report")
            }

            Spacer(Modifier.width(16.dp))

            Button(
                onClick = {
                    submitBugReport(
                        context,
                        reportData.rapportMarkdown(),
                        reportData.getTitle()
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = description.isNotBlank()
            ) {
                Text("Submit via GitHub")
            }
        }
    }
}

@Composable
private fun ReportPreviewCard(reportData: ReportData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("📋 Report Preview", style = MaterialTheme.typography.subtitle1)
            Spacer(Modifier.height(8.dp))

            // Description
            Text(
                "📝 Description:",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
            Text(
                reportData.description,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Device Info
            Text(
                "📱 Device Info:",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
            Text(
                buildString {
                    append("• Model: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("• Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                    append("• App Version: ${BuildConfig.VERSION_NAME}")
                },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Logs (if included)
            reportData.logs?.let { logs ->
                Text(
                    "📋 Logs:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    BugReportHelper.formatLogsForDisplay(logs),
                    style = MaterialTheme.typography.body2.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

private fun buildDeviceInfo(): String {
    return """
        |• Model: ${Build.MANUFACTURER} ${Build.MODEL}
        |• Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
        |• App Version: ${BuildConfig.VERSION_NAME}
        |• Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}
    """.trimMargin()
}


private fun copyReportToClipboard(context: Context, reportString: String) {
    copyToClipboard(context, reportString)
    Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun submitBugReport(context: Context, reportString: String, title: String? = null) {
    try {
        val url = "https://github.com/ethran/notable/issues/new?" +
                "title=${URLEncoder.encode("Bug: $title", "UTF-8")}" +
                "&body=${URLEncoder.encode(reportString, "UTF-8")}"

        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to submit report", Toast.LENGTH_LONG).show()
    }
}

data class ReportData(
    val description: String,
    val deviceInfo: String,
    val logs: String?
) {
    fun rapportMarkdown(): String {
        val formatedLogs = logs?.let { BugReportHelper.formatLogsForDisplay(logs) }
        return buildString {
            append("### Description\n")
            append(description).append("\n\n")
            append("### Device Info\n")
            append(deviceInfo.replace("•", "-")).append("\n")
            append("\n### Diagnostic Logs\n```\n")
            append(formatedLogs)
            append("\n```")
        }
    }

    fun getTitle(): String {
        return description.take(40)
    }
}