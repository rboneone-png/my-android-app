package com.rboneone.stockrecord.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class AuthMethod(val label: String) {
    PASSWORD("密碼"),
    FINGERPRINT("指紋"),
    FACE("臉部"),
}

data class Account(
    val id: String,
    val username: String,
    val salt: String,
    val passwordHash: String,
    /** 使用者偏好的登入方式；選指紋或臉部時仍保留密碼作為備用 */
    val method: AuthMethod,
)

object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verify(password: String, account: Account): Boolean =
        MessageDigest.isEqual(hash(password, account.salt).toByteArray(), account.passwordHash.toByteArray())
}

/** 帳號資料存在手機裡，密碼只存加鹽雜湊值 */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)
    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    val lastUserId: String? get() = prefs.getString(KEY_LAST_USER, null)

    fun setLastUser(id: String) = prefs.edit().putString(KEY_LAST_USER, id).apply()

    fun find(id: String): Account? = _accounts.value.firstOrNull { it.id == id }

    fun usernameTaken(username: String) = _accounts.value.any { it.username.equals(username.trim(), ignoreCase = true) }

    fun create(username: String, password: String, method: AuthMethod): Account {
        val salt = PasswordHasher.newSalt()
        val account = Account(UUID.randomUUID().toString(), username.trim(), salt, PasswordHasher.hash(password, salt), method)
        save(_accounts.value + account)
        return account
    }

    fun update(account: Account) = save(_accounts.value.map { if (it.id == account.id) account else it })

    private fun load(): List<Account> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Account(
                id = o.getString("id"),
                username = o.getString("username"),
                salt = o.getString("salt"),
                passwordHash = o.getString("hash"),
                method = runCatching { AuthMethod.valueOf(o.getString("method")) }.getOrDefault(AuthMethod.PASSWORD),
            )
        }
    }

    private fun save(list: List<Account>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("username", it.username)
                    .put("salt", it.salt)
                    .put("hash", it.passwordHash)
                    .put("method", it.method.name)
            )
        }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
        _accounts.value = list
    }

    private companion object {
        const val KEY_LIST = "list"
        const val KEY_LAST_USER = "last_user"
    }
}
