"""PDF text extraction service powered by PyMuPDF (fitz).

Provides a single public function that accepts raw PDF bytes and
returns extracted text, page count, and document metadata.  All
PyMuPDF resources are explicitly closed to prevent file-descriptor
leaks in long-running server processes.
"""

from __future__ import annotations

import fitz  # PyMuPDF
from loguru import logger


def extract_text_from_pdf(pdf_bytes: bytes) -> tuple[str, int, dict]:
    """Extract text content and metadata from a PDF byte stream.

    Opens the PDF in memory via *fitz.open*, iterates over every page,
    and concatenates the text blocks.  Document-level metadata (title,
    author, etc.) is harvested from the PDF info dictionary.

    Args:
        pdf_bytes: Raw bytes of the PDF file.

    Returns:
        A 3-tuple of ``(full_text, page_count, metadata)`` where
        *metadata* contains keys such as ``title``, ``author``,
        ``subject``, ``page_count``, and ``total_chars``.

    Raises:
        ValueError: If the byte stream cannot be parsed as a valid PDF
            or the document contains zero pages.
    """
    if not pdf_bytes:
        raise ValueError("Received empty PDF byte stream.")

    try:
        doc: fitz.Document = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as exc:
        logger.error("Failed to open PDF stream: {}", exc)
        raise ValueError(f"Unable to parse the provided file as a valid PDF: {exc}") from exc

    try:
        page_count: int = len(doc)
        if page_count == 0:
            raise ValueError("The PDF document contains zero pages.")

        logger.info("Opened PDF with {} page(s). Beginning extraction…", page_count)

        pages_text: list[str] = []
        for page_num in range(page_count):
            page = doc[page_num]
            page_text: str = page.get_text("text")
            pages_text.append(page_text)

            if (page_num + 1) % 50 == 0 or page_num == page_count - 1:
                logger.debug(
                    "Extracted page {}/{} ({} chars)",
                    page_num + 1,
                    page_count,
                    len(page_text),
                )

        full_text: str = "\n".join(pages_text)

        # Harvest metadata from the PDF info dictionary.
        raw_meta: dict = doc.metadata or {}
        metadata: dict = {
            "title": raw_meta.get("title", ""),
            "author": raw_meta.get("author", ""),
            "subject": raw_meta.get("subject", ""),
            "page_count": page_count,
            "total_chars": len(full_text),
        }

        logger.info(
            "Extraction complete — {} page(s), {} total characters.",
            page_count,
            len(full_text),
        )

        return full_text, page_count, metadata

    finally:
        doc.close()
