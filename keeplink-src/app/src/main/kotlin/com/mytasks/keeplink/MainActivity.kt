package com.mytasks.keeplink

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Two network endpoints, both Google's own, and nothing else. The email + one-time
// oauth_token cookie are read from THIS app's WebView cookie jar (an HttpOnly cookie is
// invisible to page JavaScript but readable by CookieManager, which is a native API) and
// exchanged for a master token at Google's device-auth endpoint. The result is shown for
// the person to copy. It is never written to disk, never logged, never sent anywhere but
// Google. Forked from github.com/Villoh/goopdl-auth (MIT); the protocol call is unchanged.
private const val EMBEDDED_SETUP_URL = "https://accounts.google.com/EmbeddedSetup"
private const val AUTH_URL = "https://android.clients.google.com/auth"
private const val POLL_TIMEOUT_MS = 300_000L
private const val POLL_INTERVAL_MS = 700L
private const val EMAIL_JS =
    "document.querySelector('[data-profile-identifier][data-email]')" +
        "?.getAttribute('data-email') || ''"

private sealed class Screen {
    data object Onboarding : Screen()

    data object SigningIn : Screen()

    data class Success(
        val email: String,
        val token: String,
    ) : Screen()

    data class Failure(
        val message: String,
    ) : Screen()
}

/**
 * Single-purpose flow: sign in interactively at EmbeddedSetup, pull the
 * email + one-time oauth_token cookie it drops, exchange those for a
 * master token, show it for the person to copy into the MyTasks bot.
 */
class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyTasksTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var screen by remember { mutableStateOf<Screen>(Screen.Onboarding) }
                    when (val current = screen) {
                        is Screen.Onboarding -> {
                            OnboardingScreen(
                                onStart = { screen = Screen.SigningIn },
                            )
                        }

                        is Screen.SigningIn -> {
                            SignInScreen(
                                onSuccess = { email, token -> screen = Screen.Success(email, token) },
                                onFailure = { message -> screen = Screen.Failure(message) },
                            )
                        }

                        is Screen.Success -> {
                            ResultScreen(current.email, current.token)
                        }

                        is Screen.Failure -> {
                            FailureScreen(
                                message = current.message,
                                onRetry = { screen = Screen.Onboarding },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyTasksTheme(content: @Composable () -> Unit) {
    val colorScheme =
        darkColorScheme(
            primary =
                androidx.compose.ui.graphics
                    .Color(0xFFF5F5F5),
            onPrimary =
                androidx.compose.ui.graphics
                    .Color(0xFF0D0D0D),
            background =
                androidx.compose.ui.graphics
                    .Color(0xFF0D0D0D),
            onBackground =
                androidx.compose.ui.graphics
                    .Color(0xFFF5F5F5),
            surface =
                androidx.compose.ui.graphics
                    .Color(0xFF1A1A1A),
            onSurface =
                androidx.compose.ui.graphics
                    .Color(0xFFF5F5F5),
        )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun OnboardingScreen(onStart: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.8f),
        ) {
            Image(
                // Its own drawable rather than either mipmap. @mipmap/ic_launcher resolves
                // to the adaptive-icon XML on API 26+, which painterResource rejects at
                // runtime ("Only VectorDrawables and rasterized asset types are supported"),
                // and the foreground PNG carries the transparent inset a launcher mask
                // needs, which on screen just renders the artwork small inside its box.
                painter = painterResource(id = R.drawable.mytasks_logo),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
        }
        androidx.compose.foundation.layout
            .Spacer(Modifier.size(24.dp))
        Text(
            text = "MyTasks — вход в Keep",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        androidx.compose.foundation.layout
            .Spacer(Modifier.size(12.dp))
        Text(
            text =
                "Войди в свой Google — приложение получит ключ, по которому бот " +
                    "MyTasks сможет работать с твоими списками в Google Keep. " +
                    "Ключ показывается один раз; на телефоне ничего не сохраняется.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        androidx.compose.foundation.layout
            .Spacer(Modifier.size(32.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Войти в Google")
        }
    }
}

@Composable
private fun SignInScreen(
    onSuccess: (String, String) -> Unit,
    onFailure: (String) -> Unit,
) {
    val context = LocalContext.current
    val webView =
        remember {
            // Fresh session per attempt: CookieManager persists to disk across app
            // launches, so a stale oauth_token from a previous try would otherwise
            // get picked up instantly, before the user even finishes signing in.
            CookieManager.getInstance().removeAllCookies(null)
            WebView(context).apply {
                clearCache(true)
                clearHistory()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                loadUrl(EMBEDDED_SETUP_URL)
            }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Войди в аккаунт Google ниже.",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }

    LaunchedEffect(Unit) {
        val credentials =
            pollForCredentials(webView) ?: run {
                onFailure("Вход не завершился вовремя. Попробуй ещё раз.")
                return@LaunchedEffect
            }
        try {
            val token =
                withContext(Dispatchers.IO) {
                    fetchAasToken(credentials.first, credentials.second)
                }
            onSuccess(credentials.first, token)
        } catch (e: Exception) {
            onFailure("Не удалось получить ключ: ${e.message}")
        }
    }
}

@Composable
private fun ResultScreen(
    email: String,
    token: String,
) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Вход выполнен: $email",
            color = MaterialTheme.colorScheme.onBackground,
        )
        androidx.compose.foundation.layout
            .Spacer(Modifier.size(16.dp))
        Text(
            text = "Ключ для бота",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        androidx.compose.foundation.layout
            .Spacer(Modifier.size(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text = token,
                    modifier = Modifier.padding(12.dp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        androidx.compose.foundation.layout
            .Spacer(Modifier.size(16.dp))
        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("mytasks_key", token))
                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
            },
        ) {
            Text("Скопировать")
        }
        androidx.compose.foundation.layout
            .Spacer(Modifier.size(16.dp))
        Text(
            text =
                "Отправь эту строку боту MyTasks в личный чат. Он подключит твой Keep " +
                    "и сразу сотрёт сообщение. Больше её никому не показывай — это ключ " +
                    "к твоему аккаунту.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun FailureScreen(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.onBackground)
        androidx.compose.foundation.layout
            .Spacer(Modifier.size(24.dp))
        OutlinedButton(onClick = onRetry) { Text("Ещё раз") }
    }
}

/**
 * Polls the WebView's cookies + signed-in profile until both email and oauth_token
 * appear, and stay unchanged across two consecutive polls — Google can drop an
 * interim oauth_token right after the email step, before the password/2FA steps
 * finish, so a single sighting isn't enough to trust it's the final one.
 */
private suspend fun pollForCredentials(webView: WebView): Pair<String, String>? {
    val start = System.currentTimeMillis()
    var lastSeen: Pair<String, String>? = null
    while (System.currentTimeMillis() - start < POLL_TIMEOUT_MS) {
        val cookies = CookieManager.getInstance().getCookie(webView.url ?: EMBEDDED_SETUP_URL)
        val token = extractOauthToken(cookies)
        val current = token?.let { t -> evaluateEmail(webView)?.let { email -> email to t } }
        if (current != null && current == lastSeen) return current
        lastSeen = current
        delay(POLL_INTERVAL_MS)
    }
    return null
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private suspend fun evaluateEmail(webView: WebView): String? =
    suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(EMAIL_JS) { raw ->
            cont.resume(raw.trim('"').takeIf { it.contains("@") }, null)
        }
    }

private fun extractOauthToken(cookieHeader: String?): String? =
    cookieHeader
        ?.split(";")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("oauth_token=oauth2_4/") }
        ?.substringAfter("=")

/** The oauth-token exchange: oauth2_4/ cookie -> aas_et/ master token. Google endpoint only. */
private fun fetchAasToken(
    email: String,
    oauthToken: String,
): String {
    val params =
        linkedMapOf(
            "Email" to email,
            "Token" to oauthToken,
            "ACCESS_TOKEN" to "1",
            "add_account" to "1",
            "callerPkg" to "com.google.android.gms",
            "callerSig" to "38918a453d07199354f8b19af05ec6562ced5788",
            "device_country" to "us",
            "droidguard_results" to "null",
            "get_accountid" to "1",
            "google_play_services_version" to "240913000",
            "lang" to "en",
            "sdk_version" to "28",
            "service" to "ac2dm",
        )
    val body =
        params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

    val connection = URL(AUTH_URL).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.connectTimeout = 30_000
    connection.readTimeout = 30_000
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    connection.setRequestProperty("Accept-Encoding", "identity")
    connection.setRequestProperty("app", "com.google.android.gms")
    connection.setRequestProperty("User-Agent", "")

    OutputStreamWriter(connection.outputStream).use { it.write(body) }

    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
    val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }

    val values =
        text
            .lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx < 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }.toMap()

    values["Token"]?.let { return it }
    values["Error"]?.let { throw RuntimeException(it) }
    throw RuntimeException("NoAASToken (HTTP ${connection.responseCode})")
}
