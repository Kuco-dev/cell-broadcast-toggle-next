package com.kamilake.cbtoggle.watch

import android.content.Context
import android.os.Build
import com.kamilake.cbtoggle.CommandResult
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask

/** Our adb client identity. The watch remembers it after pairing, like a paired computer. */
class WatchAdb private constructor(
    private val privateKey: PrivateKey,
    private val certificate: Certificate,
) : AbsAdbConnectionManager() {

    init {
        // Newer daemons speak TLS and support every protocol feature we use.
        setApi(Build.VERSION_CODES.R)
        setTimeout(10, TimeUnit.SECONDS)
    }

    override fun getPrivateKey() = privateKey
    override fun getCertificate() = certificate
    override fun getDeviceName() = DEVICE_NAME

    /** Runs `cmd <args>` on the connected watch and returns its exit code and combined output. */
    fun cmd(args: List<String>): CommandResult {
        // Arguments come from our own code, but quote them anyway so a space can never split one.
        val line = (listOf("cmd") + args).joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
        val out = shell("$line 2>&1; echo $EXIT_MARKER\$?")
        val at = out.lastIndexOf(EXIT_MARKER)
        check(at >= 0) { out.trim().ifEmpty { "No response from the watch" } }
        val code = out.substring(at + EXIT_MARKER.length).trim().toIntOrNull() ?: -1
        return CommandResult(code, out.substring(0, at))
    }

    // libadb can block forever on a stream the peer already closed, so stop at our exit line instead.
    private fun shell(command: String): String {
        val stream = openStream("shell:$command")
        val watchdog = Timer(true)
        watchdog.schedule(timerTask { runCatching { stream.close() } }, SHELL_TIMEOUT_MS)
        try {
            val input = stream.openInputStream()
            val buf = ByteArray(8192)
            val out = ByteArrayOutputStream()
            while (!out.toString(Charsets.UTF_8.name()).hasExitLine()) {
                val n = try {
                    input.read(buf, 0, buf.size)
                } catch (_: IOException) {
                    -1
                }
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            watchdog.cancel()
            runCatching { stream.close() }
        }
    }

    private fun String.hasExitLine(): Boolean {
        val at = lastIndexOf(EXIT_MARKER)
        return at >= 0 && indexOf('\n', at) >= 0
    }

    companion object {
        private const val EXIT_MARKER = "__cbtoggle_exit_"
        private const val SHELL_TIMEOUT_MS = 20_000L
        private const val DEVICE_NAME = "CellBroadcastToggle"
        private const val KEY_FILE = "watch_adb.key"
        private const val CERT_FILE = "watch_adb.crt"

        @Volatile
        private var instance: WatchAdb? = null

        fun get(context: Context): WatchAdb = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context): WatchAdb {
            val dir = context.noBackupFilesDir
            val keyFile = File(dir, KEY_FILE)
            val certFile = File(dir, CERT_FILE)
            if (keyFile.exists() && certFile.exists()) {
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
                return WatchAdb(key, cert)
            }
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val name = X500Name("CN=$DEVICE_NAME")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(
                name,
                BigInteger.valueOf(now),
                Date(now - TimeUnit.DAYS.toMillis(1)),
                Date(now + TimeUnit.DAYS.toMillis(365L * 30)),
                name,
                pair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
            val cert = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(pair.private.encoded)
            certFile.writeBytes(cert.encoded)
            return WatchAdb(pair.private, cert)
        }
    }
}
