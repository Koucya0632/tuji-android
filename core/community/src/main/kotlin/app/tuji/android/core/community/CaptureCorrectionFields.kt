package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.UiLanguage

/**
 * The second field on the correction form — the one that carries the meaning.
 * The first is always 圖片名稱 (`lemma`).
 */
enum class CaptureSecondField {
    /** Chinese interfaces edit `displayZhHant` directly. */
    ChineseName,

    /** A ja/en interface learning the *other* language edits its own-language gloss. */
    Gloss,

    /** UI language == target language: the lemma is already in the user's own language. */
    Hidden,
}

/**
 * 校正欄位 — what the capture form asks for, given the interface language and
 * the language being learned, and everything that follows from that answer.
 *
 * One grid, one home. The chip under a candidate, the name a confirm sends and
 * the line under a card on the shelf are all the same question — "which name
 * does this user read?" — and each used to answer it alone: the chip fell back
 * to Chinese on an English interface, and a renamed card kept the Chinese name
 * of the object the user had just corrected away from.
 */
object CaptureCorrectionFields {
    fun second(ui: UiLanguage, target: TargetLanguage): CaptureSecondField = when (ui) {
        UiLanguage.ZhHant, UiLanguage.ZhHans -> CaptureSecondField.ChineseName
        UiLanguage.Ja -> if (target == TargetLanguage.JA) CaptureSecondField.Hidden else CaptureSecondField.Gloss
        UiLanguage.En -> if (target == TargetLanguage.EN) CaptureSecondField.Hidden else CaptureSecondField.Gloss
    }

    /**
     * The meaning a candidate chip shows under its label: whatever the form's
     * second field would hold. A ja/en interface never falls back to the
     * Chinese name — it is not this user's language, and it is not on the form.
     */
    fun candidateMeaning(second: CaptureSecondField, zhHant: String?, gloss: String?): String? = when (second) {
        CaptureSecondField.ChineseName -> zhHant
        CaptureSecondField.Gloss -> gloss
        CaptureSecondField.Hidden -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The Chinese name a confirm sends, or null to send none.
     *
     * Without the Chinese field on screen the user cannot correct it, so once
     * they rename the card the candidate's Chinese name describes the object
     * they corrected away from. The server regenerates it from the new name.
     */
    fun chineseNameToSend(second: CaptureSecondField, lemma: String, suggestedLemma: String?, zhHant: String): String? {
        if (second == CaptureSecondField.ChineseName || suggestedLemma == null) return zhHant
        return zhHant.takeIf { lemma.trim() == suggestedLemma.trim() }
    }

    /**
     * An item's name in the interface language, for the line under its lemma.
     * A name equal to the lemma is the server's "no Chinese name yet" stand-in.
     */
    fun localName(ui: UiLanguage, item: AtlasItem): String? {
        val target = item.targetLanguage ?: return item.displayZhHant.takeIf { ui == UiLanguage.ZhHant || ui == UiLanguage.ZhHans }.shown(item)
        return when (second(ui, target)) {
            CaptureSecondField.ChineseName -> item.displayZhHant
            CaptureSecondField.Gloss -> if (ui == UiLanguage.Ja) item.displayJa else item.displayEn
            CaptureSecondField.Hidden -> null
        }.shown(item)
    }

    private fun String?.shown(item: AtlasItem): String? = this?.takeIf { it.isNotBlank() && it != item.lemma }
}
