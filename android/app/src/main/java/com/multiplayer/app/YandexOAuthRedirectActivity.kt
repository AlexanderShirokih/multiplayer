package com.multiplayer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.multiplayer.feature.auth.yamusic.CompleteYandexAuthorizationUseCase
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class YandexOAuthRedirectActivity : ComponentActivity() {
    private val completeAuthorization: CompleteYandexAuthorizationUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callbackUri = intent?.dataString
        if (isTaskRoot) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }

        if (callbackUri == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            runCatching {
                completeAuthorization(callbackUri)
            }
            finish()
        }
    }
}
