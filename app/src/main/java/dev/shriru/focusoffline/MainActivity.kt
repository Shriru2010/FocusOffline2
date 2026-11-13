package dev.shriru.focusoffline

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val PREFS = "focus_offline"
private const val KEY_END = "session_end"
private const val KEY_BLOCKED = "blocked_packages"
private const val KEY_TOTAL = "completed_seconds"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FocusApp(this) }
    }
}

@Composable
private fun FocusApp(context: Context) {
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(25) }
    var endAt by remember { mutableLongStateOf(prefs.getLong(KEY_END, 0L)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val remaining = ((endAt - now).coerceAtLeast(0L) / 1000L).toInt()
    val active = endAt > now
    if (endAt != 0L && !active) {
        LaunchedEffect(endAt) {
            val start = prefs.getLong("session_start", endAt)
            val duration = ((endAt - start).coerceAtLeast(0L) / 1000L)
            prefs.edit().putLong(KEY_TOTAL, prefs.getLong(KEY_TOTAL, 0L) + duration).putLong(KEY_END, 0L).apply()
            endAt = 0L
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF5D45A6), background = androidx.compose.ui.graphics.Color(0xFFF7F5FC))) {
        Scaffold(bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Text("◷") }, label = { Text("Focus") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1; refresh++ }, icon = { Text("▦") }, label = { Text("Block apps") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Text("▤") }, label = { Text("Insights") })
            }
        }) { pad ->
            when (selectedTab) {
                0 -> Column(Modifier.fillMaxSize().padding(pad).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("FOCUS OFFLINE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(if (active) "Stay in the zone." else "Make time count.", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (active) "SESSION IN PROGRESS" else "FOCUS TIMER", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(14.dp))
                            Text("%02d:%02d".format(remaining / 60, remaining % 60), fontSize = 58.sp, fontWeight = FontWeight.Light)
                            Text(if (active) "Strict mode is on" else "Choose a duration", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (!active) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { minutes = (minutes - 5).coerceAtLeast(5) }) { Text("−") }
                            Text("$minutes min", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = { minutes = (minutes + 5).coerceAtMost(180) }) { Text("+") }
                        }
                        Button(onClick = {
                            val start = System.currentTimeMillis()
                            val end = start + minutes * 60_000L
                            prefs.edit().putLong("session_start", start).putLong(KEY_END, end).apply()
                            endAt = end
                        }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Start focus") }
                    } else {
                        Text("Your selected apps are blocked until the timer ends.", color = MaterialTheme.colorScheme.secondary)
                        Text("Emergency access and Android system controls remain available.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Everything stays on this device. No account. No ads. No internet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                1 -> BlockAppsScreen(context, prefs, refresh)
                else -> InsightsScreen(context, prefs)
            }
        }
    }
}

private data class AppEntry(val label: String, val pkg: String)

@Composable
private fun BlockAppsScreen(context: Context, prefs: android.content.SharedPreferences, refresh: Int) {
    var chosen by remember { mutableStateOf(prefs.getStringSet(KEY_BLOCKED, emptySet())?.toSet() ?: emptySet()) }
    val apps = remember(refresh) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull {
            val pkg = it.activityInfo.packageName
            if (pkg == context.packageName) null else AppEntry(it.loadLabel(pm).toString(), pkg)
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("App blocker", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Choose apps to block during focus sessions. Accessibility permission is processed locally.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) { Text("Enable Focus Offline accessibility service") }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(apps, key = { it.pkg }) { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = app.pkg in chosen, onCheckedChange = { checked ->
                        chosen = if (checked) chosen + app.pkg else chosen - app.pkg
                        prefs.edit().putStringSet(KEY_BLOCKED, chosen).apply()
                    })
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.Medium)
                        Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsScreen(context: Context, prefs: android.content.SharedPreferences) {
    val allowed = remember { hasUsageAccess(context) }
    var used by remember { mutableLongStateOf(0L) }
    LaunchedEffect(allowed) {
        if (allowed) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }
            used = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis()).sumOf { it.totalTimeInForeground }
        }
    }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Insights", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text("Today's recorded app usage")
            Text(if (allowed) "${used / 3_600_000}h ${(used / 60_000) % 60}m" else "Permission needed", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            if (!allowed) Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Grant usage access") }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
            Text("Completed focus time")
            val seconds = prefs.getLong(KEY_TOTAL, 0L)
            Text("${seconds / 3600}h ${(seconds / 60) % 60}m", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        } }
        Text("Usage totals are supplied by Android and may update with a delay.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}
