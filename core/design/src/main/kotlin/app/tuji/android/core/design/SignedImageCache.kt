package app.tuji.android.core.design

import coil3.ComponentRegistry
import coil3.Uri
import coil3.intercept.Interceptor
import coil3.key.Keyer
import coil3.request.ImageResult
import coil3.request.Options

/**
 * What a Supabase-signed storage URL is a picture *of*: the URL minus its
 * signature.
 *
 * A private-bucket photo is handed out as `/storage/v1/object/sign/…?token=…`
 * and the server signs afresh on every response, so the same picture arrives
 * under a new URL each time. Coil keys both of its caches on the whole URL —
 * which made every one of the user's own pictures a permanent miss, downloaded
 * again from Supabase on each list refresh. That is transfer the project pays
 * for, and it is what `TujiImagePipeline` on iOS already avoids the same way.
 *
 * Pure string work on purpose: it is the part worth testing, and a JVM test
 * has no `android.net.Uri`.
 */
object SignedImageUrl {

    private const val SIGNED_PATH = "/storage/v1/object/sign/"

    /**
     * The identity of a signed object, or `null` for anything else — which
     * leaves Coil's own key alone, since a public image's URL is already stable.
     *
     * Only the token rotates. Anything else in the query (a transform size,
     * say) still describes which picture you get, so it stays.
     */
    fun objectId(url: String): String? {
        val withoutFragment = url.substringBefore('#')
        val base = withoutFragment.substringBefore('?')
        if (!base.contains(SIGNED_PATH)) return null
        val identifying = withoutFragment.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() && it.substringBefore('=') != "token" }
        return if (identifying.isEmpty()) base else base + "?" + identifying.joinToString("&")
    }
}

/** The memory cache's half. Returning `null` falls through to Coil's default keyer. */
private class SignedImageKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? = SignedImageUrl.objectId(data.toString())
}

/**
 * The disk cache's half. The network fetcher keys the disk on the request's
 * `diskCacheKey` and falls back to the URL, and no keyer is consulted for it,
 * so the request itself has to carry the stable key.
 */
private class SignedImageDiskKey : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = when (val data = request.data) {
            is String -> data
            is Uri -> data.toString()
            else -> null
        }
        val id = url?.let(SignedImageUrl::objectId)
        if (id == null || request.diskCacheKey != null) return chain.proceed()
        return chain.withRequest(request.newBuilder().diskCacheKey(id).build()).proceed()
    }
}

/** Both halves, for the one `ImageLoader` the app builds. */
fun ComponentRegistry.Builder.signedImageCacheKeys(): ComponentRegistry.Builder =
    add(SignedImageDiskKey()).add(SignedImageKeyer(), Uri::class)
