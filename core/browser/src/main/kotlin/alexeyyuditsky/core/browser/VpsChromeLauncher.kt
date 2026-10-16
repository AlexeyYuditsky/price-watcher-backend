package alexeyyuditsky.core.browser

import java.io.File

class VpsChromeLauncher(profileDirectory: File) : ChromeLauncher(profileDirectory) {

    override fun findChrome(): File? = sequenceOf(
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable"
    ).map(::File).firstOrNull { it.isFile && it.canExecute() }
}