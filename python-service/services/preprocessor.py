"""Document preprocessing pipeline — extraction, chunking, and keyword analysis.

Orchestrates the individual services (pdf_extractor, text_chunker) and
adds lightweight keyword extraction based on term-frequency analysis with
a hardcoded English stop-word list.  No NLTK data downloads are required
at runtime.
"""

from __future__ import annotations

import re
import time
from collections import Counter

from loguru import logger

from services.pdf_extractor import extract_text_from_pdf
from services.text_chunker import chunk_text

# ---------------------------------------------------------------------------
# Hardcoded English stop words — avoids any nltk.download() at runtime.
# ---------------------------------------------------------------------------
STOP_WORDS: frozenset[str] = frozenset(
    {
        "a", "about", "above", "after", "again", "against", "all", "am", "an",
        "and", "any", "are", "aren't", "as", "at", "be", "because", "been",
        "before", "being", "below", "between", "both", "but", "by", "can",
        "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does",
        "doesn't", "doing", "don't", "down", "during", "each", "few", "for",
        "from", "further", "get", "got", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her",
        "here", "here's", "hers", "herself", "him", "himself", "his", "how",
        "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
        "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most",
        "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on",
        "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
        "out", "over", "own", "same", "shan't", "she", "she'd", "she'll",
        "she's", "should", "shouldn't", "so", "some", "such", "than", "that",
        "that's", "the", "their", "theirs", "them", "themselves", "then",
        "there", "there's", "these", "they", "they'd", "they'll", "they're",
        "they've", "this", "those", "through", "to", "too", "under", "until",
        "up", "upon", "us", "very", "was", "wasn't", "we", "we'd", "we'll",
        "we're", "we've", "were", "weren't", "what", "what's", "when",
        "when's", "where", "where's", "which", "while", "who", "who's",
        "whom", "why", "why's", "will", "with", "won't", "would", "wouldn't",
        "you", "you'd", "you'll", "you're", "you've", "your", "yours",
        "yourself", "yourselves", "also", "just", "like", "well", "back",
        "even", "still", "way", "take", "since", "another", "however",
        "two", "three", "four", "five", "new", "one", "may", "much", "many",
        "make", "made", "use", "used", "using", "first", "also", "know",
    }
)

_WORD_PATTERN: re.Pattern[str] = re.compile(r"[a-zA-Z]{3,}")


def extract_keywords(text: str, top_n: int = 20) -> list[str]:
    """Extract the most frequent meaningful words from *text*.

    Uses a simple bag-of-words approach:
    1. Tokenise with a regex that captures alphabetic tokens ≥ 3 chars.
    2. Lower-case all tokens and discard stop words.
    3. Return the *top_n* tokens by raw frequency.

    Args:
        text: The input document text.
        top_n: Maximum number of keywords to return.

    Returns:
        A list of keywords sorted by descending frequency.
    """
    if not text or not text.strip():
        return []

    tokens: list[str] = _WORD_PATTERN.findall(text.lower())
    filtered: list[str] = [t for t in tokens if t not in STOP_WORDS]

    if not filtered:
        return []

    counter: Counter[str] = Counter(filtered)
    keywords: list[str] = [word for word, _ in counter.most_common(top_n)]

    logger.debug("Extracted {} keyword(s) from {} token(s).", len(keywords), len(filtered))
    return keywords


def preprocess_document(
    pdf_bytes: bytes,
    chunk_size: int = 512,
    overlap: int = 50,
    top_n_keywords: int = 20,
) -> dict:
    """Run the full document preprocessing pipeline.

    Steps executed in order:
    1. PDF text extraction via PyMuPDF.
    2. Smart text chunking with sentence-boundary awareness.
    3. Keyword extraction via term-frequency analysis.

    Args:
        pdf_bytes: Raw bytes of the uploaded PDF file.
        chunk_size: Target characters per chunk.
        overlap: Character overlap between consecutive chunks.
        top_n_keywords: Number of keywords to extract.

    Returns:
        A dictionary matching the shape of
        :class:`models.schemas.PreprocessResponse`.
    """
    start_time: float = time.perf_counter()

    # Step 1 — Extract
    logger.info("Pipeline step 1/3: extracting text from PDF…")
    full_text, page_count, metadata = extract_text_from_pdf(pdf_bytes)

    # Step 2 — Chunk
    logger.info("Pipeline step 2/3: chunking text…")
    chunks: list[str] = chunk_text(full_text, chunk_size=chunk_size, overlap=overlap)

    # Step 3 — Keywords
    logger.info("Pipeline step 3/3: extracting keywords…")
    keywords: list[str] = extract_keywords(full_text, top_n=top_n_keywords)

    elapsed_ms: float = (time.perf_counter() - start_time) * 1000

    logger.info("Pipeline complete in {:.1f} ms.", elapsed_ms)

    return {
        "text": full_text,
        "chunks": chunks,
        "page_count": page_count,
        "chunk_count": len(chunks),
        "metadata": metadata,
        "keywords": keywords,
        "processing_time_ms": round(elapsed_ms, 2),
    }
