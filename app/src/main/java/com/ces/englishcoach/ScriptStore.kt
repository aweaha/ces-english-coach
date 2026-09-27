package com.ces.englishcoach

import android.content.Context
import java.io.File
import java.security.MessageDigest

object ScriptStore {
    @JvmStatic fun keyFor(uri: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
        return d.take(12).joinToString("") { "%02x".format(it) }
    }

    @JvmStatic fun scriptsDir(context: Context): File = File(context.filesDir, "generated_scripts").apply { mkdirs() }
    @JvmStatic fun fileFor(context: Context, key: String): File = File(scriptsDir(context), "$key.json")
    @JvmStatic fun has(context: Context, key: String): Boolean = fileFor(context, key).let { it.exists() && it.length() > 20 }
}
