package com.rboneone.stockrecord

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rboneone.stockrecord.ui.AppViewModel
import com.rboneone.stockrecord.ui.StockRecordApp
import com.rboneone.stockrecord.ui.screens.LoginScreen
import com.rboneone.stockrecord.ui.theme.StockRecordTheme

/** 生物辨識需要 FragmentActivity */
class MainActivity : FragmentActivity() {
    private val app get() = application as StockApp
    private var backgroundSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockRecordTheme {
                val account by app.session.collectAsStateWithLifecycle()
                val current = account
                if (current == null) {
                    LoginScreen(app)
                } else {
                    key(current.id) {
                        StockRecordApp(vm = viewModel<AppViewModel>(key = "user-${current.id}"))
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundSince = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // 離開 App 超過 5 分鐘，回來時需要重新登入
        if (backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince > LOCK_AFTER_MS) {
            app.logout()
        }
        backgroundSince = 0
    }

    private companion object {
        const val LOCK_AFTER_MS = 5 * 60 * 1000L
    }
}
