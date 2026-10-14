package alexeyyuditsky

import java.io.File

class LocalChromeLauncher : ChromeLauncher() {
    override val profileDirectory = File("files/ozon-cdp-profile")

    override fun findChrome(): File? = sequenceOf(
        System.getenv("PROGRAMFILES"),
        System.getenv("PROGRAMFILES(X86)"),
        System.getenv("LOCALAPPDATA")
    ).filterNotNull()
        .map { File(it, "Google/Chrome/Application/chrome.exe") }
        .firstOrNull(File::isFile)
}