package me.him188.ani.app.ui.settings.account

private val nicknameMatcher = Regex("^[\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FFa-zA-Z\\d_]+$")

/** Account nickname policy shared by the native and browser profile editors. */
fun isValidProfileNickname(nickname: String): Boolean {
    if (nickname.isEmpty()) return true
    if (nickname.isBlank() || !nicknameMatcher.matches(nickname)) return false
    return nickname.sumOf { if (it.code < 256) 1 else 2 } in 6..20
}
