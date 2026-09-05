"""Smart text chunking with configurable size and overlap.

Splits a large body of text into approximately equal-sized chunks while
trying to honour sentence boundaries so that downstream consumers (e.g.
embedding models, RAG retrieval) receive coherent passages.
"""

from __future__ import annotations

from loguru import logger


def chunk_text(
    text: str,
    chunk_size: int = 512,
    overlap: int = 50,
) -> list[str]:
    """Split *text* into overlapping chunks of roughly *chunk_size* characters.

    The splitter walks through the text and attempts to break at the
    nearest sentence boundary (period followed by whitespace, or a
    newline) that falls within the *chunk_size* window.  When no
    suitable boundary is found the text is split at exactly
    *chunk_size* characters to guarantee forward progress.

    Args:
        text: The input text to chunk.
        chunk_size: Target number of characters per chunk (minimum 50).
        overlap: Number of characters to repeat between consecutive
            chunks so that context is preserved across boundaries.

    Returns:
        A list of non-empty text chunks.
    """
    if not text or not text.strip():
        logger.warning("chunk_text called with empty or whitespace-only text.")
        return []

    # Normalise parameters to sane minimums.
    chunk_size = max(chunk_size, 50)
    overlap = max(overlap, 0)
    overlap = min(overlap, chunk_size // 2)  # overlap must not exceed half the chunk

    text_length: int = len(text)
    chunks: list[str] = []
    start: int = 0

    while start < text_length:
        end: int = start + chunk_size

        if end >= text_length:
            # Last chunk — take everything remaining.
            chunk = text[start:].strip()
            if chunk:
                chunks.append(chunk)
            break

        # Try to find a sentence boundary near the end of the window.
        # Search backwards from *end* for a period or newline.
        split_pos: int = _find_sentence_boundary(text, start, end)
        chunk = text[start:split_pos].strip()

        if chunk:
            chunks.append(chunk)

        # Advance the cursor, stepping back by *overlap* characters.
        start = max(split_pos - overlap, start + 1)

    logger.info(
        "Chunked {} chars into {} chunk(s) (target_size={}, overlap={}).",
        text_length,
        len(chunks),
        chunk_size,
        overlap,
    )

    return chunks


def _find_sentence_boundary(text: str, start: int, end: int) -> int:
    """Return the best split position between *start* and *end*.

    Looks backwards from *end* for a period followed by whitespace, or
    a newline character.  Falls back to *end* if nothing suitable is
    found (hard split).

    Args:
        text: The full text being chunked.
        start: Current chunk start index.
        end: Current chunk end index (exclusive upper bound).

    Returns:
        The character index at which to split.
    """
    # Walk backwards looking for a sentence-ending period or newline.
    search_start = max(start + (end - start) // 2, start)  # only look in the second half
    best: int = -1

    for i in range(end, search_start, -1):
        char = text[i - 1]
        if char == "\n":
            best = i
            break
        if char == "." and i < len(text) and (i == len(text) or text[i] in (" ", "\n", "\t")):
            best = i
            break

    return best if best > start else end
