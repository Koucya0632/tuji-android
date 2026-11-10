package app.tuji.android.core.scenes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped scenes, decoded strictly from the APK's assets: every file the
 * index names loads, and what the screens rely on is there.
 */
class SceneContentTest {
    private val dir = File("../../app/src/main/assets/scenes")
    private val read: (String) -> String = { File(dir, it).readText() }
    private val catalog by lazy { SceneCatalog.loadAll(read) }

    @Test fun `every scene in the index decodes, and every scene file is in the index`() {
        val files = dir.listFiles { f -> f.extension == "json" && f.name != "index.json" }!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(files, catalog.scenes.map { it.id.raw }.toSet())
        assertEquals(22, catalog.scenes.size)
    }

    @Test fun `every scene has five to seven 問句`() {
        catalog.scenes.forEach { assertTrue(it.id.raw, it.questions.size in 5..7) }
    }

    @Test fun `released scenes are in the index and the free ones are released`() {
        val index = SceneCatalog.index(read)
        assertTrue(index.released.all { it in index.ids })
        catalog.scenes.filter { it.access == SceneAccess.Free }.forEach { assertTrue(it.id.raw, it.id in index.released) }
    }

    @Test fun `a staged scene stages every 問句 and every route names only its own`() {
        catalog.scenes.forEach { scene ->
            val stage = scene.stage ?: return@forEach
            val ids = scene.questions.map { it.id }.toSet()
            assertEquals(scene.id.raw, ids.map { it.raw }.toSet(), stage.steps.keys)
            assertEquals(scene.id.raw, ids, stage.routes.flatMap { it.questions }.toSet())
            assertFalse(scene.id.raw, stage.routes.first().isOptional)
        }
    }

    @Test fun `every clip a scene names is in the APK`() {
        val audio = File(dir, "audio")
        catalog.scenes.forEach { scene ->
            scene.questions.forEach { q ->
                q.phrasings.mapNotNull { it.spoken?.audio }.forEach { assertTrue(it, File(audio, it).exists()) }
                q.response.clip?.let { assertTrue(it.audio, File(audio, it.audio).exists()) }
            }
            scene.stage?.steps?.values?.flatMap { it.replies.orEmpty() }?.filter { it.isSpoken }?.forEach {
                assertTrue("${it.ja} has no ${it.clip}", File(audio, it.clip).exists())
            }
        }
    }

    @Test fun `every stage image is in the APK`() {
        val images = File(dir, "images").list()!!.map { it.substringBeforeLast('.') }.toSet()
        catalog.scenes.forEach { scene ->
            scene.stage?.steps?.values?.forEach { step ->
                assertTrue(step.background, step.background in images)
                step.props.orEmpty().forEach { assertTrue(it, it in images) }
            }
        }
    }

    @Test fun `every scene glosses every Japanese line it says`() {
        catalog.scenes.forEach { scene ->
            assertNotNull(scene.id.raw, scene.glossTable)
            val lines = scene.questions.flatMap { q -> q.phrasings.mapNotNull { it.spoken?.text } + q.response.ja } +
                scene.stage?.steps?.values?.flatMap { it.replies.orEmpty() }?.filter { it.isSpoken }?.map { it.ja }.orEmpty()
            lines.forEach { assertNotNull("${scene.id.raw}: $it", scene.gloss(it)) }
        }
    }

    @Test fun `a screen's buttons answer its question's choices`() {
        catalog.scenes.flatMap { it.questions }.forEach { q ->
            q.phrasings.mapNotNull { it.screen }.forEach { screen ->
                assertTrue(q.id.raw, screen.buttons.all { b -> q.choices.any { it.id == b.choiceId } })
            }
        }
    }

    /** The clip name is the Japanese's SHA-256, as tuji-ios and the audio pipeline name it. */
    @Test fun `a reply's clip is named like iOS names it`() {
        val reply = StageReply("x", "はい", LocalizedText("", "", ""), true, LocalizedText("", "", ""))
        assertEquals("reply-8f90d85ec13a.mp3", reply.clip)
    }

    /** Same order as tuji-ios for the same phrasing, so both apps show one order. */
    @Test fun `choices are mixed by phrasing the way iOS mixes them`() {
        val question = catalog.scene(SceneId("konbini-checkout"))!!.questions.first()
        assertEquals(listOf("b", "c", "a"), question.choices(question.phrasings.first().id).map { it.id })
    }
}
