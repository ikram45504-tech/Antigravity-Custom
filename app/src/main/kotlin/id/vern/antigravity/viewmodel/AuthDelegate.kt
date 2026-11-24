package id.vern.antigravity.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import id.vern.antigravity.R
import id.vern.antigravity.oauth.GitHubAccount
import id.vern.antigravity.oauth.GitHubAuthManager
import id.vern.antigravity.oauth.GitHubDeviceCode
import id.vern.antigravity.oauth.GitHubPollResult
import id.vern.antigravity.oauth.StoredAccount
import id.vern.antigravity.oauth.AccountManager
import id.vern.antigravity.oauth.OAuthManager
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.service.CloudCodeQuotaService
import id.vern.antigravity.service.AgyModelsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthDelegate {
    private val mutableUserEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = mutableUserEmail.asStateFlow()

    private val mutableUserName = MutableStateFlow("")
    val userName: StateFlow<String> = mutableUserName.asStateFlow()

    private val mutableUserPicture = MutableStateFlow<String?>(null)
    val userPicture: StateFlow<String?> = mutableUserPicture.asStateFlow()

    private val mutableAccounts = MutableStateFlow<List<StoredAccount>>(emptyList())
    val accounts: StateFlow<List<StoredAccount>> = mutableAccounts.asStateFlow()

    private val mutableIsAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = mutableIsAuthenticating.asStateFlow()

    private val mutableAuthError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = mutableAuthError.asStateFlow()

    private val mutableShowGoogleSignInDialog = MutableStateFlow(false)
    val showGoogleSignInDialog: StateFlow<Boolean> = mutableShowGoogleSignInDialog.asStateFlow()

    private val mutablePendingAuthUrl = MutableStateFlow<String?>(null)
    val pendingAuthUrl: StateFlow<String?> = mutablePendingAuthUrl.asStateFlow()

    private val mutableGithubAccount = MutableStateFlow<GitHubAccount?>(null)
    val githubAccount: StateFlow<GitHubAccount?> = mutableGithubAccount.asStateFlow()

    private val mutableGithubDeviceCode = MutableStateFlow<GitHubDeviceCode?>(null)
    val githubDeviceCode: StateFlow<GitHubDeviceCode?> = mutableGithubDeviceCode.asStateFlow()

    private val mutableIsGithubAuthenticating = MutableStateFlow(false)
    val isGithubAuthenticating: StateFlow<Boolean> = mutableIsGithubAuthenticating.asStateFlow()

    private val mutableGithubAuthError = MutableStateFlow<String?>(null)
    val githubAuthError: StateFlow<String?> = mutableGithubAuthError.asStateFlow()

    private var githubPollJob: Job? = null

    fun showGoogleSignInDialog() {
        mutableShowGoogleSignInDialog.value = true
    }

    fun dismissGoogleSignInDialog() {
        mutableShowGoogleSignInDialog.value = false
    }

    fun setPendingAuthUrl(url: String?) {
        mutablePendingAuthUrl.value = url
    }

    fun refreshAuthState(
        context: Context,
        scope: CoroutineScope,
        onRefreshUserQuota: (Context) -> Unit
    ) {
        OAuthManager.checkAndImportExistingToken(context)
        mutableGithubAccount.value = GitHubAuthManager.getAccount(context)
        val allAccounts = AccountManager.getAccounts(context)
        mutableAccounts.value = allAccounts
        val email = OAuthManager.getUserEmail(context)
        mutableUserEmail.value = email
        val name = OAuthManager.getUserName(context) ?: if (email != null) {
            email.substringBefore("@").replace(".", " ").split(" ")
                .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
        } else ""
        mutableUserName.value = name
        val pic = OAuthManager.getUserPicture(context)
        if (!pic.isNullOrBlank()) {
            mutableUserPicture.value = pic
        }

        onRefreshUserQuota(context)

        scope.launch {
            if (OAuthManager.refreshTokenIfNeeded(context)) {
                val newEmail = OAuthManager.getUserEmail(context)
                mutableUserEmail.value = newEmail
                val newName = OAuthManager.getUserName(context) ?: if (newEmail != null) {
                    newEmail.substringBefore("@").replace(".", " ").split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                } else ""
                mutableUserName.value = newName
                val refreshedPic = OAuthManager.getUserPicture(context)
                if (!refreshedPic.isNullOrBlank()) {
                    mutableUserPicture.value = refreshedPic
                }
                if (newEmail != null) {
                    onRefreshUserQuota(context)
                }
            }
            val userInfo = OAuthManager.fetchUserInfo(context)
            if (userInfo != null) {
                if (!userInfo.name.isNullOrBlank()) {
                    mutableUserName.value = userInfo.name
                }
                if (!userInfo.picture.isNullOrBlank()) {
                    mutableUserPicture.value = userInfo.picture
                }
                if (!userInfo.email.isNullOrBlank()) {
                    mutableUserEmail.value = userInfo.email
                }
            }
            mutableAccounts.value = AccountManager.getAccounts(context)
        }
    }

    fun switchAccount(
        context: Context,
        accountId: String,
        scope: CoroutineScope,
        onAccountSwitched: () -> Unit
    ) {
        scope.launch {
            val success = AccountManager.switchAccount(context, accountId)
            if (success) {
                AgyModelsService.invalidateCache()
                val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .remove(AntigravityViewModel.KEY_CLI_MODELS_FETCHED_AT)
                    .remove(AntigravityViewModel.KEY_CLI_MODELS_JSON)
                    .remove(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID)
                    .apply()
                onAccountSwitched()
            }
        }
    }

    fun removeAccount(
        context: Context,
        accountId: String,
        scope: CoroutineScope,
        onAccountRemoved: () -> Unit
    ) {
        scope.launch {
            val success = AccountManager.removeAccount(context, accountId)
            if (success) {
                val currentActive = AccountManager.getActiveAccount(context)
                if (currentActive != null) {
                    AgyModelsService.invalidateCache()
                    val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .remove(AntigravityViewModel.KEY_CLI_MODELS_FETCHED_AT)
                        .remove(AntigravityViewModel.KEY_CLI_MODELS_JSON)
                        .remove(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID)
                        .apply()
                }
                onAccountRemoved()
            }
        }
    }

    fun startGoogleSignIn(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (String?) -> Unit,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val pendingUrl = mutablePendingAuthUrl.value
        if (!pendingUrl.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onLog("Auth", "Opening official engine PKCE auth URL in browser...", false)
                mutableShowGoogleSignInDialog.value = true
                return
            } catch (e: Exception) {
                onLog("Auth", "Failed to open auth URL: ${e.message}", true)
            }
        }

        if (mutableIsAuthenticating.value) {
            mutableShowGoogleSignInDialog.value = true
            return
        }

        scope.launch {
            mutableIsAuthenticating.value = true
            mutableAuthError.value = null
            mutableShowGoogleSignInDialog.value = true
            onLog("Auth", "Initiating Google OAuth flow on port 8085...", false)

            OAuthManager.startBrowserAuth(
                context = context,
                onSuccess = { email ->
                    mutableUserEmail.value = email
                    mutableIsAuthenticating.value = false
                    mutableShowGoogleSignInDialog.value = false
                    mutablePendingAuthUrl.value = null
                    onSuccess(email)
                },
                onError = { err ->
                    mutableAuthError.value = err
                    mutableIsAuthenticating.value = false
                    onLog("Auth", "Google sign-in error: $err", true)
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun onActivityResume(
        context: Context,
        scope: CoroutineScope,
        onCheckUpdates: (Context) -> Unit,
        onAccountChangeDetected: (String) -> Unit
    ) {
        onCheckUpdates(context)
        OAuthManager.checkAndImportExistingToken(context)
        val email = OAuthManager.getUserEmail(context)
        if (email != null && email != mutableUserEmail.value) {
            mutableUserEmail.value = email
            onAccountChangeDetected(email)
        } else if (email != null) {
            if (mutableUserPicture.value.isNullOrBlank()) {
                val pic = OAuthManager.getUserPicture(context)
                if (!pic.isNullOrBlank()) {
                    mutableUserPicture.value = pic
                } else {
                    scope.launch {
                        val userInfo = OAuthManager.fetchUserInfo(context)
                        if (!userInfo?.picture.isNullOrBlank()) {
                            mutableUserPicture.value = userInfo?.picture
                        }
                    }
                }
            }
        }
    }

    fun exchangeManualAuthCode(
        context: Context,
        rawCodeOrToken: String,
        scope: CoroutineScope,
        onStdinSent: () -> Unit,
        onSuccess: (String?) -> Unit,
        onLog: (String, String, Boolean) -> Unit
    ) {
        var trimmed = rawCodeOrToken.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                val uri = Uri.parse(trimmed)
                val codeParam = uri.getQueryParameter("code")
                if (!codeParam.isNullOrBlank()) {
                    trimmed = codeParam
                }
            } catch (_: Exception) {}
        }
        if (trimmed.contains("%")) {
            try {
                trimmed = java.net.URLDecoder.decode(trimmed, "UTF-8")
            } catch (_: Exception) {}
        }

        val sent = EngineExecutor.writeToAgyStdin(trimmed)
        if (sent) {
            mutableShowGoogleSignInDialog.value = false
            mutablePendingAuthUrl.value = null
            onLog("Auth", "Submitted authorization code directly to active engine stdin", false)
            Toast.makeText(context, context.getString(R.string.auth_verifying_code), Toast.LENGTH_SHORT).show()
            onStdinSent()
            return
        }

        scope.launch {
            mutableIsAuthenticating.value = true
            mutableAuthError.value = null
            onLog("Auth", "Processing authentication code/token...", false)

            OAuthManager.exchangeCodeDirectly(
                context = context,
                rawInput = trimmed,
                onSuccess = { email ->
                    mutableUserEmail.value = email
                    mutableIsAuthenticating.value = false
                    mutableShowGoogleSignInDialog.value = false
                    mutablePendingAuthUrl.value = null
                    onSuccess(email)
                },
                onError = { err ->
                    mutableAuthError.value = err
                    mutableIsAuthenticating.value = false
                    onLog("Auth", "Google sign-in error: $err", true)
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun signOutGoogle(
        context: Context,
        onSignedOut: () -> Unit,
        onLog: (String, String) -> Unit
    ) {
        OAuthManager.clearToken(context)
        mutableUserEmail.value = null
        mutableUserName.value = ""
        mutableUserPicture.value = null

        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(AntigravityViewModel.KEY_SELECTED_MODEL)
            .remove(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID)
            .remove(AntigravityViewModel.KEY_THINKING_BUDGET)
            .remove(AntigravityViewModel.KEY_CLI_MODELS_JSON)
            .remove(AntigravityViewModel.KEY_CLI_MODELS_FETCHED_AT)
            .remove(OAuthManager.KEY_USER_NAME)
            .remove(OAuthManager.KEY_USER_PICTURE)
            .remove(OAuthManager.KEY_USER_EMAIL)
            .apply()

        onSignedOut()
        onLog("Auth", "Signed out of Google account")
        Toast.makeText(context, context.getString(R.string.account_toast_signed_out), Toast.LENGTH_SHORT).show()
    }

    fun refreshGitHubState(context: Context) {
        mutableGithubAccount.value = GitHubAuthManager.getAccount(context)
    }

    fun startGitHubDeviceFlow(
        context: Context,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        githubPollJob?.cancel()
        mutableGithubAuthError.value = null
        mutableIsGithubAuthenticating.value = true
        mutableGithubDeviceCode.value = null

        githubPollJob = scope.launch {
            val codeResult = withContext(Dispatchers.IO) {
                GitHubAuthManager.requestDeviceCode()
            }
            val deviceCode = codeResult.getOrElse { err ->
                mutableIsGithubAuthenticating.value = false
                mutableGithubAuthError.value = err.message ?: "Failed to start GitHub login"
                onLog("GitHub", "Device code error: ${err.message}", true)
                return@launch
            }

            mutableGithubDeviceCode.value = deviceCode
            var intervalSec = deviceCode.interval.coerceAtLeast(5)
            val deadlineMs = System.currentTimeMillis() + (deviceCode.expiresIn * 1000L)

            while (System.currentTimeMillis() < deadlineMs) {
                delay(intervalSec * 1000L)
                when (val poll = withContext(Dispatchers.IO) { GitHubAuthManager.pollAccessToken(deviceCode.deviceCode) }) {
                    is GitHubPollResult.Success -> {
                        withContext(Dispatchers.IO) {
                            GitHubAuthManager.saveAccount(context, poll.account)
                        }
                        mutableGithubAccount.value = poll.account
                        mutableGithubDeviceCode.value = null
                        mutableIsGithubAuthenticating.value = false
                        mutableGithubAuthError.value = null
                        onLog("GitHub", "Connected GitHub account @${poll.account.login}", false)
                        Toast.makeText(context, "Connected GitHub as @${poll.account.login}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    is GitHubPollResult.Pending -> {
                        if (poll.slowDown) intervalSec += 5
                    }
                    is GitHubPollResult.Expired -> {
                        mutableGithubDeviceCode.value = null
                        mutableIsGithubAuthenticating.value = false
                        mutableGithubAuthError.value = "Code expired. Please try again."
                        return@launch
                    }
                    is GitHubPollResult.Denied -> {
                        mutableGithubDeviceCode.value = null
                        mutableIsGithubAuthenticating.value = false
                        mutableGithubAuthError.value = "Authorization was cancelled on GitHub."
                        return@launch
                    }
                    is GitHubPollResult.Error -> {
                        mutableGithubDeviceCode.value = null
                        mutableIsGithubAuthenticating.value = false
                        mutableGithubAuthError.value = poll.message
                        onLog("GitHub", "OAuth error: ${poll.message}", true)
                        return@launch
                    }
                }
            }

            mutableGithubDeviceCode.value = null
            mutableIsGithubAuthenticating.value = false
            mutableGithubAuthError.value = "Authorization timed out."
        }
    }

    fun cancelGitHubDeviceFlow() {
        githubPollJob?.cancel()
        githubPollJob = null
        mutableGithubDeviceCode.value = null
        mutableIsGithubAuthenticating.value = false
        mutableGithubAuthError.value = null
    }

    fun connectGitHubWithPat(
        context: Context,
        token: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            mutableGithubAuthError.value = "Token cannot be empty"
            return
        }
        mutableGithubAuthError.value = null
        mutableIsGithubAuthenticating.value = true
        scope.launch {
            val account = withContext(Dispatchers.IO) {
                GitHubAuthManager.fetchUserProfile(trimmed)
            }
            if (account != null) {
                withContext(Dispatchers.IO) {
                    GitHubAuthManager.saveAccount(context, account)
                }
                mutableGithubAccount.value = account
                mutableIsGithubAuthenticating.value = false
                onLog("GitHub", "Connected GitHub account @${account.login} via PAT", false)
                Toast.makeText(context, "Connected GitHub as @${account.login}", Toast.LENGTH_SHORT).show()
            } else {
                mutableIsGithubAuthenticating.value = false
                mutableGithubAuthError.value = "Invalid token or failed to reach GitHub."
            }
        }
    }

    fun disconnectGitHub(
        context: Context,
        onLog: (String, String) -> Unit
    ) {
        cancelGitHubDeviceFlow()
        GitHubAuthManager.signOut(context)
        mutableGithubAccount.value = null
        onLog("Auth", "Disconnected GitHub account")
        Toast.makeText(context, "Disconnected GitHub account", Toast.LENGTH_SHORT).show()
    }
}
