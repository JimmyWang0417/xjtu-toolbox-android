package com.xjtu.toolbox.cmps.app

import androidx.compose.runtime.Stable
import com.xjtu.toolbox.cmps.data.CampusLocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class AccountType(val displayName: String) {
    Undergraduate("本科生"),
    Postgraduate("研究生"),
}

data class AccountProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val type: AccountType,
    val isActive: Boolean = false,
)

data class SessionState(
    val activeAccount: AccountProfile? = null,
    val accounts: List<AccountProfile> = emptyList(),
    val campusOnline: Boolean = false,
    val restoring: Boolean = false,
) {
    val isLoggedIn: Boolean get() = activeAccount != null
}

@Stable
class AppNavigator {
    private val _stack = MutableStateFlow<List<AppRoute>>(listOf(AppRoute.Main))
    val stack: StateFlow<List<AppRoute>> = _stack

    val current: AppRoute get() = _stack.value.last()

    fun navigate(route: AppRoute) {
        _stack.update { stack ->
            if (route == AppRoute.Main) listOf(AppRoute.Main) else stack + route
        }
    }

    fun replace(route: AppRoute) {
        _stack.update { stack -> stack.dropLast(1).ifEmpty { listOf(AppRoute.Main) } + route }
    }

    fun back(): Boolean {
        var popped = false
        _stack.update { stack ->
            if (stack.size > 1) {
                popped = true
                stack.dropLast(1)
            } else {
                stack
            }
        }
        return popped
    }
}

class AppStore(
    private val localStore: CampusLocalStore = CampusLocalStore(),
) {
    private val _session = MutableStateFlow(restoreSession())
    val session: StateFlow<SessionState> = _session

    fun login(username: String, password: String, accountType: AccountType) {
        val normalizedUsername = username.ifBlank { "local" }
        val account = AccountProfile(
            id = normalizedUsername,
            username = normalizedUsername,
            displayName = username.ifBlank { "西迁人" },
            type = accountType,
            isActive = true,
        )
        localStore.putCredential(account.id, normalizedUsername, password)
        _session.update { state ->
            val inactive = state.accounts.filterNot { it.id == account.id }.map { it.copy(isActive = false) }
            SessionState(activeAccount = account, accounts = inactive + account, campusOnline = true).also(::persistSession)
        }
    }

    fun activateAccount(accountId: String) {
        _session.update { state ->
            val target = state.accounts.firstOrNull { it.id == accountId } ?: return@update state
            val accounts = state.accounts.map { it.copy(isActive = it.id == accountId) }
            state.copy(activeAccount = target.copy(isActive = true), accounts = accounts, campusOnline = true).also(::persistSession)
        }
    }

    fun removeAccount(accountId: String) {
        localStore.removeCredential(accountId)
        _session.update { state ->
            val remaining = state.accounts.filterNot { it.id == accountId }
            val active = remaining.firstOrNull()
            state.copy(
                activeAccount = active?.copy(isActive = true),
                accounts = remaining.map { it.copy(isActive = it.id == active?.id) },
                campusOnline = active != null,
            ).also(::persistSession)
        }
    }

    fun logout() {
        _session.update { state ->
            val accounts = state.accounts.map { it.copy(isActive = false) }
            state.copy(activeAccount = null, accounts = accounts, campusOnline = false).also(::persistSession)
        }
    }

    private fun restoreSession(): SessionState {
        val accounts = localStore.getSetting("accounts", "")
            .lineSequence()
            .mapNotNull(::decodeAccount)
            .toList()
        val activeId = localStore.activeAccountId
        val active = accounts.firstOrNull { it.id == activeId }
        val normalized = accounts.map { it.copy(isActive = it.id == active?.id) }
        return SessionState(
            activeAccount = active?.copy(isActive = true),
            accounts = normalized,
            campusOnline = active != null,
        )
    }

    private fun persistSession(state: SessionState) {
        localStore.activeAccountId = state.activeAccount?.id.orEmpty()
        localStore.putSetting("accounts", state.accounts.joinToString("\n") { encodeAccount(it) })
    }

    private fun encodeAccount(account: AccountProfile): String =
        listOf(account.id, account.username, account.displayName, account.type.name)
            .joinToString("|") { it.escapePart() }

    private fun decodeAccount(raw: String): AccountProfile? {
        val parts = raw.split("|")
        if (parts.size != 4) return null
        val type = runCatching { AccountType.valueOf(parts[3].unescapePart()) }.getOrNull() ?: return null
        val id = parts[0].unescapePart().ifBlank { return null }
        return AccountProfile(
            id = id,
            username = parts[1].unescapePart(),
            displayName = parts[2].unescapePart().ifBlank { id },
            type = type,
            isActive = id == localStore.activeAccountId,
        )
    }
}

private fun String.escapePart(): String =
    replace("%", "%25").replace("|", "%7C").replace("\n", "%0A")

private fun String.unescapePart(): String =
    replace("%0A", "\n").replace("%7C", "|").replace("%25", "%")
