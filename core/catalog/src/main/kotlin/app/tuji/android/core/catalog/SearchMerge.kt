package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word

/**
 * Local hits, then whatever the server saw that they could not.
 *
 * The order is the whole rule. [WordSearch] has already ranked the local rows
 * by how well they answer the query; the server returns its own set with no
 * ranking this client can compare against, so appending is the only honest
 * thing to do with it. Interleaving would mean inventing a comparison between
 * "starts with the query" and "the word this row's definition mentions", and
 * the user would watch the row they were reaching for move.
 *
 * Dedupe by id, and **local wins** a tie: the two describe the same word, but
 * the local copy is the one already on screen, and replacing it would make the
 * list flicker at the moment the network answers.
 */
object SearchMerge {

    fun merge(local: List<Word>, remote: List<Word>): List<Word> {
        if (remote.isEmpty()) return local
        val seen = HashSet<String>(local.size + remote.size)
        return (local + remote).filter { seen.add(it.id) }
    }
}
