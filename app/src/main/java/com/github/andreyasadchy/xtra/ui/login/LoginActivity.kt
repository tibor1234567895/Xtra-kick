package com.github.andreyasadchy.xtra.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.ActivityLoginBinding
import com.github.andreyasadchy.xtra.kick.auth.KickOAuthClient
import com.github.andreyasadchy.xtra.kick.auth.KickTokenRefreshScheduler
import com.github.andreyasadchy.xtra.kick.config.KickEnvironment
import com.github.andreyasadchy.xtra.kick.storage.KickTokenStore
import com.github.andreyasadchy.xtra.util.applyTheme
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.isLightTheme
import com.github.andreyasadchy.xtra.util.shortToast
import com.github.andreyasadchy.xtra.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject
    lateinit var kickOAuthClient: KickOAuthClient

    @Inject
    lateinit var kickTokenStore: KickTokenStore

    @Inject
    lateinit var environment: KickEnvironment

    @Inject
    lateinit var kickTokenRefreshScheduler: KickTokenRefreshScheduler

    private lateinit var binding: ActivityLoginBinding

    private var codeVerifier: String? = null
    private var state: String? = null
    private var authorizationUrl: HttpUrl? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                rightMargin = insets.right
                bottomMargin = insets.bottom
            }
            windowInsets
        }
        with(binding) {
            textZoom.gone()
            webViewContainer.visible()
            configureWebView()
            havingTrouble.setOnClickListener {
                authorizationUrl?.let { url ->
                    val intent = Intent(Intent.ACTION_VIEW, url.toString().toUri())
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else {
                        shortToast(R.string.no_browser_found)
                    }
                } ?: shortToast(R.string.login_error_generic)
            }
        }
        lifecycleScope.launch {
            startAuthorizationFlow()
        }
    }

    private fun ActivityLoginBinding.configureWebView() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = userAgentString + " XtraKick/Android"
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) && !isLightTheme()) {
                WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_ON)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) && isLightTheme()) {
                WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_OFF)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 100) {
                    progressBar.gone()
                } else {
                    progressBar.visible()
                }
            }
        }
        webView.webViewClient = object : WebViewClientCompat() {
            override fun onPageCommitVisible(view: WebView, url: String) {
                progressBar.gone()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return handleRedirect(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleRedirect(Uri.parse(url))
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceErrorCompat
            ) {
                if (request.isForMainFrame) {
                    showError(getString(R.string.login_error_with_code, error.errorCode))
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    showError(getString(R.string.login_error_with_code, errorResponse.statusCode))
                }
            }
        }
    }

    private suspend fun startAuthorizationFlow() {
        showLoading(true)
        try {
            val verifier = KickOAuthClient.generateCodeVerifier()
            val challenge = KickOAuthClient.generateCodeChallenge(verifier)
            val state = UUID.randomUUID().toString()
            val url = withContext(Dispatchers.IO) {
                kickOAuthClient.buildAuthorizationUrl(state, challenge)
            }
            this.codeVerifier = verifier
            this.state = state
            this.authorizationUrl = url
            binding.webView.loadUrl(url.toString())
        } catch (t: Throwable) {
            showError(getString(R.string.login_error_generic), t)
        } finally {
            showLoading(false)
        }
    }

    private fun handleRedirect(uri: Uri): Boolean {
        val redirect = environment.redirectUri.ifBlank { null }
        if (redirect != null && !uri.toString().startsWith(redirect)) {
            return false
        }
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            showError(getString(R.string.login_error_oauth, error))
            return true
        }
        val returnedState = uri.getQueryParameter("state")
        if (state != null && returnedState != state) {
            showError(getString(R.string.login_error_state))
            return true
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            return false
        }
        exchangeAuthorizationCode(code)
        return true
    }

    private fun exchangeAuthorizationCode(code: String) {
        val verifier = codeVerifier
        if (verifier.isNullOrBlank()) {
            showError(getString(R.string.login_error_state))
            return
        }
        lifecycleScope.launch {
            showLoading(true)
            try {
                val response = withContext(Dispatchers.IO) {
                    kickOAuthClient.exchangeAuthorizationCode(code, verifier)
                }
                kickTokenStore.update(response)
                kickTokenRefreshScheduler.schedule()
                setResult(RESULT_OK)
                finish()
            } catch (t: Throwable) {
                showError(getString(R.string.login_error_generic), t)
                startAuthorizationFlow()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        if (loading) {
            binding.progressBar.visible()
        } else {
            binding.progressBar.gone()
        }
    }

    private fun showError(message: String, throwable: Throwable? = null) {
        val display = if (throwable != null) {
            "$message\n${throwable.localizedMessage ?: throwable.javaClass.simpleName}"
        } else {
            message
        }
        shortToast(display)
    }
}
