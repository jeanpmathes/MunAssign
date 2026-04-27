package org.munika.assignment

/**
 * JVM runtime information exposed to the desktop app.
 */
class JVMPlatform {
    val name: String = "Java ${System.getProperty("java.version")}"
}

fun getPlatform(): JVMPlatform = JVMPlatform()
