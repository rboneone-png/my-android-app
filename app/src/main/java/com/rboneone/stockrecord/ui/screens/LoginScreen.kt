package com.rboneone.stockrecord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rboneone.stockrecord.StockApp
import com.rboneone.stockrecord.auth.Account
import com.rboneone.stockrecord.auth.AuthMethod
import com.rboneone.stockrecord.auth.Biometric
import com.rboneone.stockrecord.auth.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_PASSWORD = 4

@Composable
fun LoginScreen(app: StockApp) {
    val accounts by app.accountStore.accounts.collectAsStateWithLifecycle()
    // 沒有帳號時只能新增
    var tab by rememberSaveable { mutableIntStateOf(if (accounts.isEmpty()) 1 else 0) }
    LaunchedEffect(accounts.isEmpty()) { if (accounts.isEmpty()) tab = 1 }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp),
            )
            Text("股票記帳", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { if (accounts.isNotEmpty()) tab = 0 },
                    enabled = accounts.isNotEmpty(),
                    text = { Text("登入") },
                )
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("新增") })
            }
            Column(Modifier.padding(24.dp)) {
                if (tab == 0) LoginForm(app, accounts) else CreateForm(app, isFirst = accounts.isEmpty())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoginForm(app: StockApp, accounts: List<Account>) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable {
        mutableStateOf(app.accountStore.lastUserId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.first().id)
    }
    val selected = accounts.firstOrNull { it.id == selectedId } ?: accounts.first()
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val canBiometric = selected.method != AuthMethod.PASSWORD && Biometric.isAvailable(context)

    fun biometricLogin() {
        Biometric.authenticate(activity, "登入 ${selected.username}", selected.method) { ok, err ->
            if (ok) app.login(selected) else error = err
        }
    }

    // 選到使用指紋 / 臉部的帳號時自動跳出辨識
    LaunchedEffect(selected.id) {
        error = null
        password = ""
        if (canBiometric) biometricLogin()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("選擇帳號", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { a ->
                FilterChip(selected = a.id == selected.id, onClick = { selectedId = a.id }, label = { Text(a.username) })
            }
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("密碼") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { PasswordHasher.verify(password, selected) }
                    busy = false
                    if (ok) app.login(selected) else error = "密碼錯誤"
                }
            },
            enabled = !busy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "驗證中…" else "登入") }
        if (canBiometric) {
            OutlinedButton(onClick = ::biometricLogin, modifier = Modifier.fillMaxWidth()) {
                Text("使用${selected.method.label}登入")
            }
        }
    }
}

@Composable
private fun CreateForm(app: StockApp, isFirst: Boolean) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val biometricAvailable = remember { Biometric.isAvailable(context) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(AuthMethod.PASSWORD) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun finish() {
        busy = true
        scope.launch {
            val account = withContext(Dispatchers.Default) { app.createAccount(username, password, method) }
            busy = false
            app.login(account)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isFirst) {
            Text(
                "第一次使用，請先建立帳號。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text("帳號名稱") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("密碼（至少 $MIN_PASSWORD 碼）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = { Text("再次輸入密碼") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("登入方式", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthMethod.entries.forEach { m ->
                FilterChip(
                    selected = method == m,
                    onClick = { method = m },
                    enabled = m == AuthMethod.PASSWORD || biometricAvailable,
                    label = { Text(m.label) },
                )
            }
        }
        Text(
            if (biometricAvailable) {
                "選擇指紋或臉部時，密碼仍可作為備用登入方式。實際使用哪一種辨識，由手機已設定的方式決定。"
            } else {
                "這支手機尚未設定指紋或臉部辨識，目前只能使用密碼。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                error = when {
                    username.isBlank() -> "請輸入帳號名稱"
                    app.accountStore.usernameTaken(username) -> "這個帳號名稱已經有人使用"
                    password.length < MIN_PASSWORD -> "密碼至少需要 $MIN_PASSWORD 碼"
                    password != confirm -> "兩次輸入的密碼不一樣"
                    else -> null
                }
                if (error != null) return@Button
                if (method == AuthMethod.PASSWORD) {
                    finish()
                } else {
                    // 先確認辨識可以使用，再建立帳號
                    Biometric.authenticate(activity, "設定${method.label}登入", method) { ok, err ->
                        if (ok) finish() else error = err ?: "已取消${method.label}設定"
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "建立中…" else "建立帳號") }
    }
}
