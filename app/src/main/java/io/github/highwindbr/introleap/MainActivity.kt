package io.github.highwindbr.introleap

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private lateinit var preferences: SharedPreferences
    private val serviceEnabled: MutableState<Boolean> = mutableStateOf(false)
    private val disneyAvailable: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences(SkipService.PREFS, MODE_PRIVATE)

        setContent {
            IntroLeapTheme {
                IntroLeapScreen(
                    preferences = preferences,
                    serviceEnabled = serviceEnabled.value,
                    disneyAvailable = disneyAvailable.value,
                    openAccessibilitySettings = ::openAccessibilitySettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = isAccessibilityServiceEnabled()
        disneyAvailable.value = isPackageAvailable(SkipService.DISNEY_PACKAGE)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, SkipService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        return enabled.split(':').any { flattened ->
            ComponentName.unflattenFromString(flattened) == expected
        }
    }

    private fun isPackageAvailable(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun openAccessibilitySettings() {
        val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        try {
            if (accessibilityIntent.resolveActivity(packageManager) != null) {
                startActivity(accessibilityIntent)
                return
            }
        } catch (_: RuntimeException) {
            // Some TV firmwares advertise the activity but reject the launch.
        }
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

@Composable
private fun IntroLeapTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF9FC9FF),
        onPrimary = Color(0xFF00325A),
        primaryContainer = Color(0xFF174A75),
        onPrimaryContainer = Color(0xFFD2E4FF),
        secondary = Color(0xFFB7C8DD),
        onSecondary = Color(0xFF213240),
        background = Color(0xFF0B0F16),
        onBackground = Color(0xFFE5EAF2),
        surface = Color(0xFF171C24),
        onSurface = Color(0xFFE5EAF2),
        surfaceVariant = Color(0xFF252C37),
        onSurfaceVariant = Color(0xFFC1C7D0),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun IntroLeapScreen(
    preferences: SharedPreferences,
    serviceEnabled: Boolean,
    disneyAvailable: Boolean,
    openAccessibilitySettings: () -> Unit,
) {
    var disneyEnabled by remember {
        mutableStateOf(preferences.getBoolean(SkipService.PREF_DISNEY, false))
    }
    var skipIntros by remember {
        mutableStateOf(preferences.getBoolean(SkipService.PREF_DISNEY_INTRO, true))
    }
    var showConfirmation by remember {
        mutableStateOf(preferences.getBoolean(SkipService.PREF_SHOW_TOAST, false))
    }
    val settingsFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        settingsFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF111A27), MaterialTheme.colorScheme.background),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 1120.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 64.dp, vertical = 42.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Header()
            }
            item {
                ServiceCard(
                    enabled = serviceEnabled,
                    onOpenSettings = openAccessibilitySettings,
                    focusRequester = settingsFocus,
                )
            }
            item {
                SectionTitle(stringResource(R.string.apps_and_skip_types))
            }
            item {
                DisneyCard(
                    available = disneyAvailable,
                    appEnabled = disneyEnabled,
                    skipIntros = skipIntros,
                    onAppEnabledChange = {
                        disneyEnabled = it
                        preferences.edit().putBoolean(SkipService.PREF_DISNEY, it).apply()
                    },
                    onSkipIntrosChange = {
                        skipIntros = it
                        preferences.edit().putBoolean(SkipService.PREF_DISNEY_INTRO, it).apply()
                    },
                )
            }
            item {
                SectionTitle(stringResource(R.string.confirmations))
            }
            item {
                SettingsCard {
                    TvToggleRow(
                        title = stringResource(R.string.show_skip_confirmation),
                        description = stringResource(R.string.skip_confirmation_description),
                        checked = showConfirmation,
                        enabled = true,
                        onCheckedChange = {
                            showConfirmation = it
                            preferences.edit().putBoolean(SkipService.PREF_SHOW_TOAST, it).apply()
                        },
                    )
                }
            }
            item {
                PrivacyCard()
            }
        }
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = stringResource(R.string.eyebrow),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 42.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 19.sp,
            lineHeight = 26.sp,
        )
    }
}

@Composable
private fun ServiceCard(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
    focusRequester: FocusRequester,
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.accessibility_service_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(
                        if (enabled) R.string.service_enabled else R.string.service_disabled,
                    ),
                    color = if (enabled) Color(0xFF9DDBA5) else MaterialTheme.colorScheme.error,
                    fontSize = 16.sp,
                )
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                Text(
                    text = stringResource(R.string.open_accessibility_settings),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DisneyCard(
    available: Boolean,
    appEnabled: Boolean,
    skipIntros: Boolean,
    onAppEnabledChange: (Boolean) -> Unit,
    onSkipIntrosChange: (Boolean) -> Unit,
) {
    SettingsCard(modifier = Modifier.alpha(if (available) 1f else 0.46f)) {
        TvToggleRow(
            title = "Disney+",
            description = stringResource(
                if (available) R.string.intros else R.string.not_available,
            ),
            checked = appEnabled,
            enabled = available,
            onCheckedChange = onAppEnabledChange,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        )
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.alpha(if (available && appEnabled) 1f else 0.42f)) {
            TvToggleRow(
                title = stringResource(R.string.skip_intros),
                description = null,
                checked = skipIntros,
                enabled = available && appEnabled,
                onCheckedChange = onSkipIntrosChange,
            )
        }
    }
}

@Composable
private fun TvToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (description != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PrivacyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 26.dp, vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.privacy_title),
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = stringResource(R.string.privacy_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
    }
}
