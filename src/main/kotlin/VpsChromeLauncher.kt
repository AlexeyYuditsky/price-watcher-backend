package alexeyyuditsky

import java.io.File

class VpsChromeLauncher : ChromeLauncher() {
    override val profileDirectory = File(System.getProperty("user.home"), "ozon-cdp-profile")

    override fun findChrome(): File? = sequenceOf(
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable"
    ).map(::File).firstOrNull { it.isFile && it.canExecute() }
}