package io.github.milankablar.carkaraoke

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.milankablar.carkaraoke.ui.CarKaraokeApp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CarKaraokeApp() }
    }
    override fun onStart() { super.onStart(); MediaBridgeSessionManager.acquire(this, "phone") }
    override fun onStop() { MediaBridgeSessionManager.relinquish("phone"); super.onStop() }
}
fun hasNotificationAccess(context: Context): Boolean {
    val component = ComponentName(context, MediaNotificationListener::class.java)
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
}
fun openNotificationAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
fun applyLanguage(context: Context, language: String, country: String): Context {
    if (language.isEmpty()) return context
    val locale = Locale(language, country)
    val configuration = android.content.res.Configuration(context.resources.configuration).apply { setLocale(locale) }
    return context.createConfigurationContext(configuration)
}
