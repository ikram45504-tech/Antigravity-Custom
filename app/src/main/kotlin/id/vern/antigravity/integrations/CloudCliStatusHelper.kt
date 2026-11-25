package id.vern.antigravity.integrations

import android.content.Context
import java.io.File

data class CloudCliStatus(
    val hasNode: Boolean,
    val hasNpm: Boolean,
    val hasVercel: Boolean,
    val hasSupabase: Boolean,
    val hasWrangler: Boolean
)

object CloudCliStatusHelper {
    private fun findBinary(context: Context, name: String): Boolean {
        val dirs = listOf(
            File(context.filesDir, "usr/bin"),
            File(context.filesDir, "engine/bin"),
            File(context.filesDir, "runtime/bin"),
            File(context.filesDir, "home/.npm-global/bin")
        )
        return dirs.any { dir ->
            val f = File(dir, name)
            f.exists() && f.isFile
        }
    }

    fun inspect(context: Context): CloudCliStatus = CloudCliStatus(
        hasNode = findBinary(context, "node"),
        hasNpm = findBinary(context, "npm"),
        hasVercel = findBinary(context, "vercel"),
        hasSupabase = findBinary(context, "supabase"),
        hasWrangler = findBinary(context, "wrangler")
    )

    fun installCommandFor(provider: CloudProvider): String = when (provider) {
        CloudProvider.VERCEL -> "pkg install -y nodejs && npm install -g vercel"
        CloudProvider.SUPABASE -> "pkg install -y nodejs && npm install -g supabase"
        CloudProvider.CLOUDFLARE -> "pkg install -y nodejs && npm install -g wrangler"
    }
}
