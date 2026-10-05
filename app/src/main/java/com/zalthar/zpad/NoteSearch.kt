package com.zalthar.zpad

import java.io.Reader

/** All query words must appear; scan bounded chunks, including word boundaries. */
internal fun matchesNote(reader: Reader, terms: List<String>, checkCancelled: () -> Unit = {}): Boolean {
    if (terms.isEmpty()) return true
    val remaining = terms.toMutableSet()
    val overlap = terms.maxOf { it.length } - 1
    val buffer = CharArray(8192)
    var tail = ""
    while (true) {
        checkCancelled()
        val count = reader.read(buffer)
        if (count < 0) return false
        val chunk = tail + String(buffer, 0, count)
        remaining.removeAll { chunk.contains(it, ignoreCase = true) }
        if (remaining.isEmpty()) return true
        tail = chunk.takeLast(overlap)
    }
}

internal fun searchTerms(query: String): List<String> = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()
