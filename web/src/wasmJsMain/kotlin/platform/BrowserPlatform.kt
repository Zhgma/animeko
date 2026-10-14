package me.him188.ani.utils.platform

import kotlin.time.Clock
import kotlinx.browser.window

object BrowserPlatform
fun BrowserPlatform.isDesktop() = window.matchMedia("(pointer: fine)").matches
fun BrowserPlatform.hasScrollingBug() = false
fun currentTimeMillis() = Clock.System.now().toEpochMilliseconds()
