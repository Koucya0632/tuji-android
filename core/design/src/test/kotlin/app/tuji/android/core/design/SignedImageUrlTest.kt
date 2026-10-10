package app.tuji.android.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignedImageUrlTest {

    private val signed = "https://x.supabase.co/storage/v1/object/sign/user-atlas-images/u1/i1/thumb.webp"

    /** The regression this exists for: a re-signed URL must be the same picture. */
    @Test fun `two signatures of one object share an identity`() {
        assertEquals(signed, SignedImageUrl.objectId("$signed?token=aaa"))
        assertEquals(SignedImageUrl.objectId("$signed?token=aaa"), SignedImageUrl.objectId("$signed?token=bbb"))
    }

    @Test fun `different objects stay different`() {
        val other = signed.replace("thumb.webp", "original.webp")
        assertEquals(other, SignedImageUrl.objectId("$other?token=aaa"))
    }

    @Test fun `a query item that is not the token still identifies the picture`() {
        assertEquals("$signed?width=320", SignedImageUrl.objectId("$signed?width=320&token=aaa"))
        assertEquals("$signed?width=320", SignedImageUrl.objectId("$signed?token=aaa&width=320#frag"))
    }

    @Test fun `a public or foreign url is left to the default key`() {
        assertNull(SignedImageUrl.objectId("https://img.nexflow.team/word-images/cup.webp"))
        assertNull(SignedImageUrl.objectId("https://x.supabase.co/storage/v1/object/public/word-images/cup.webp?token=aaa"))
        // The marker in the query is not a signed path.
        assertNull(SignedImageUrl.objectId("https://example.com/a.webp?next=/storage/v1/object/sign/b"))
    }
}
