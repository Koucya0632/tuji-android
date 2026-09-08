package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyOptionStateTest {

    private fun option(
        label: String,
        picked: String? = null,
        revealed: Boolean = false,
        wrongPicks: Set<String> = emptySet(),
    ) = StudyOptionState.forOption(label, answer = "cat", picked, revealed, wrongPicks)

    @Test
    fun `before the reveal every option is idle`() {
        for (label in listOf("cat", "dog", "bird", "fish")) {
            assertEquals(StudyOptionState.Idle, option(label))
        }
    }

    @Test
    fun `the reveal classifies each option`() {
        assertEquals(StudyOptionState.Right, option("cat", picked = "cat", revealed = true))
        assertEquals(StudyOptionState.Wrong, option("dog", picked = "dog", revealed = true))
        assertEquals(StudyOptionState.Answer, option("cat", picked = "dog", revealed = true))
        assertEquals(StudyOptionState.Dim, option("bird", picked = "dog", revealed = true))
    }

    @Test
    fun `a ruled-out option stays wrong before and after the reveal`() {
        // One of them is still the option the user got wrong; letting it fall
        // to Dim beside the answer would erase the only trace of the attempt.
        val ruled = setOf("dog")
        assertEquals(StudyOptionState.Wrong, option("dog", wrongPicks = ruled))
        assertEquals(
            StudyOptionState.Wrong,
            option("dog", picked = "cat", revealed = true, wrongPicks = ruled),
        )
        // …and the one that landed is still Right.
        assertEquals(
            StudyOptionState.Right,
            option("cat", picked = "cat", revealed = true, wrongPicks = ruled),
        )
    }

    @Test
    fun `a picture is judged by id even when the labels match`() {
        // Two catalogue words can print the same string; they cannot share an id.
        assertEquals(
            StudyOptionState.Wrong,
            StudyOptionState.forPicture(
                optionId = "impostor", answerId = "real", pickedId = "impostor", revealed = true,
            ),
        )
        assertEquals(
            StudyOptionState.Answer,
            StudyOptionState.forPicture(
                optionId = "real", answerId = "real", pickedId = "impostor", revealed = true,
            ),
        )
    }

    @Test
    fun `a correct picture is right and the other recedes`() {
        assertEquals(
            StudyOptionState.Right,
            StudyOptionState.forPicture("a", "a", pickedId = "a", revealed = true),
        )
        assertEquals(
            StudyOptionState.Dim,
            StudyOptionState.forPicture("b", "a", pickedId = "a", revealed = true),
        )
    }

    @Test
    fun `pictures carry no mark before the reveal`() {
        // Ruling out one of two pictures is the same act as answering, so a
        // picture is never marked while the question is open.
        assertEquals(
            StudyOptionState.Idle,
            StudyOptionState.forPicture("a", "a", pickedId = null, revealed = false),
        )
        assertEquals(
            StudyOptionState.Idle,
            StudyOptionState.forPicture("a", "a", pickedId = "a", revealed = false),
        )
    }
}
