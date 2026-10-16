package alexeyyuditsky.core.browser

import java.io.File

class LocalChromeLauncher(profileDirectory: File) : ChromeLauncher(profileDirectory) {

    override fun findChrome(): File? = sequenceOf(
        System.getenv("PROGRAMFILES"),
        System.getenv("PROGRAMFILES(X86)"),
        System.getenv("LOCALAPPDATA")
    ).filterNotNull()
        .map { File(it, "Google/Chrome/Application/chrome.exe") }
        .firstOrNull(File::isFile)
}