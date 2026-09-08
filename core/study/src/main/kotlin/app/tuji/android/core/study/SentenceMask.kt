package app.tuji.android.core.study

/**
 * An unreadable stand-in for a sentence that keeps its shape.
 *
 * 聽句 blurs its sentence so the eye can see *that* there is one — line count,
 * where it breaks — without reading a letter of it. That is what makes the 顯示
 * example button mean something, and it is why the blur is 12dp rather than
 * something gentler.
 *
 * **But `Modifier.blur` needs `RenderEffect`, which is API 31, and `minSdk` is
 * 29.** On 29 and 30 it does not throw or warn: it silently draws the text
 * unblurred. A listening question that prints its own answer is not a degraded
 * question, it is a broken one, and it would have looked completely normal to
 * anyone testing on a current device. So those two API levels get this instead,
 * and the invariant — nothing legible before the reveal — holds on every
 * device the app installs on.
 *
 * Whitespace is preserved so the line breaks land where the real sentence's do;
 * everything else becomes a block. Punctuation goes too: 「。」 at the end of one
 * of two candidate sentences is a tell.
 */
fun maskedSentence(text: String): String =
    text.map { if (it.isWhitespace()) it else MASK }.joinToString("")

/** U+2588 FULL BLOCK — no ascender, no descender, no shape to read. */
private const val MASK = '█'
