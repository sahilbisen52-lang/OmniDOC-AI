"""DocAssistant PDF Processing Service — FastAPI application entry-point.

A self-contained microservice that exposes REST endpoints for:
- PDF text extraction (``POST /extract``)
- Configurable text chunking (``POST /chunk``)
- Full document preprocessing pipeline (``POST /preprocess``)
- Health / liveness checks (``GET /health``)

Run locally with::

    uvicorn main:app --reload
"""

from __future__ import annotations

import sys
import time
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, File, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from loguru import logger

from models.schemas import (
    ChunkRequest,
    ChunkResponse,
    ErrorResponse,
    ExtractionResponse,
    HealthResponse,
    PreprocessResponse,
)
from services.preprocessor import preprocess_document
from services.pdf_extractor import extract_text_from_pdf
from services.text_chunker import chunk_text

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
SERVICE_NAME: str = "DocAssistant PDF Processing Service"
SERVICE_VERSION: str = "1.0.0"
MAX_FILE_SIZE_BYTES: int = 50 * 1024 * 1024  # 50 MB
ALLOWED_CONTENT_TYPES: set[str] = {"application/pdf"}

# ---------------------------------------------------------------------------
# Logging configuration — loguru replaces the default uvicorn logger.
# ---------------------------------------------------------------------------
logger.remove()  # Remove default stderr handler
logger.add(
    sys.stderr,
    format=(
        "<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | "
        "<level>{level: <8}</level> | "
        "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> — "
        "<level>{message}</level>"
    ),
    level="DEBUG",
    colorize=True,
)

# ---------------------------------------------------------------------------
# Application start time (set once at module import).
# ---------------------------------------------------------------------------
_start_time: float = time.time()


# ---------------------------------------------------------------------------
# Lifespan context manager
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    """Application lifespan handler — log startup and shutdown events."""
    logger.info("🚀  {} v{} starting up…", SERVICE_NAME, SERVICE_VERSION)
    logger.info("Max upload size: {} MB", MAX_FILE_SIZE_BYTES // (1024 * 1024))
    yield
    logger.info("👋  {} shutting down.", SERVICE_NAME)


# ---------------------------------------------------------------------------
# FastAPI application instance
# ---------------------------------------------------------------------------
app = FastAPI(
    title=SERVICE_NAME,
    version=SERVICE_VERSION,
    description=(
        "A lightweight microservice for extracting text from PDF documents, "
        "chunking large texts for RAG pipelines, and performing basic NLP "
        "preprocessing including keyword extraction."
    ),
    lifespan=lifespan,
    responses={
        400: {"model": ErrorResponse, "description": "Validation / client error"},
        500: {"model": ErrorResponse, "description": "Internal server error"},
    },
)

# ---------------------------------------------------------------------------
# CORS — permissive for local / dev use.
# ---------------------------------------------------------------------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------------------------
# Exception handlers
# ---------------------------------------------------------------------------
@app.exception_handler(ValueError)
async def value_error_handler(_request: Request, exc: ValueError) -> JSONResponse:
    """Return a 400 response for all ``ValueError`` exceptions.

    This covers input validation failures raised by the service layer
    (e.g. corrupt PDF, empty uploads).
    """
    logger.warning("ValueError: {}", exc)
    return JSONResponse(
        status_code=400,
        content=ErrorResponse(
            error="validation_error",
            detail=str(exc),
            status_code=400,
        ).model_dump(),
    )


@app.exception_handler(Exception)
async def general_exception_handler(_request: Request, exc: Exception) -> JSONResponse:
    """Catch-all handler that returns a 500 response for unhandled exceptions.

    Logs the full traceback server-side while returning a safe generic
    message to the client.
    """
    logger.exception("Unhandled exception: {}", exc)
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(
            error="internal_server_error",
            detail="An unexpected error occurred. Please try again later.",
            status_code=500,
        ).model_dump(),
    )


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
async def _read_validated_pdf(file: UploadFile) -> bytes:
    """Read and validate an uploaded file, returning its raw bytes.

    Validates:
    - MIME content-type is ``application/pdf``.
    - File extension ends with ``.pdf``.
    - File size does not exceed ``MAX_FILE_SIZE_BYTES``.

    Args:
        file: The uploaded file from the request.

    Returns:
        The raw bytes of the PDF.

    Raises:
        ValueError: If any validation check fails.
    """
    # --- Content-type check ---
    content_type: str | None = file.content_type
    if content_type not in ALLOWED_CONTENT_TYPES:
        raise ValueError(
            f"Invalid content type '{content_type}'. Only PDF files are accepted."
        )

    # --- Extension check ---
    filename: str = file.filename or ""
    if not filename.lower().endswith(".pdf"):
        raise ValueError(
            f"Invalid file extension for '{filename}'. Only .pdf files are accepted."
        )

    # --- Read bytes and size check ---
    pdf_bytes: bytes = await file.read()
    if len(pdf_bytes) == 0:
        raise ValueError("Uploaded file is empty.")
    if len(pdf_bytes) > MAX_FILE_SIZE_BYTES:
        size_mb = len(pdf_bytes) / (1024 * 1024)
        raise ValueError(
            f"File size ({size_mb:.1f} MB) exceeds the maximum allowed "
            f"size of {MAX_FILE_SIZE_BYTES // (1024 * 1024)} MB."
        )

    return pdf_bytes


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.post(
    "/extract",
    response_model=ExtractionResponse,
    summary="Extract text from a PDF",
    tags=["Extraction"],
)
async def extract_endpoint(file: UploadFile = File(...)) -> ExtractionResponse:
    """Accept a PDF upload and return extracted text with metadata.

    The file is validated for type, extension, and size before being
    passed to the PyMuPDF extraction layer.
    """
    pdf_bytes: bytes = await _read_validated_pdf(file)

    start: float = time.perf_counter()
    full_text, page_count, metadata = extract_text_from_pdf(pdf_bytes)
    elapsed_ms: float = (time.perf_counter() - start) * 1000

    logger.info(
        "POST /extract — {} page(s), {} chars, {:.1f} ms",
        page_count,
        len(full_text),
        elapsed_ms,
    )

    return ExtractionResponse(
        text=full_text,
        page_count=page_count,
        metadata=metadata,
        processing_time_ms=round(elapsed_ms, 2),
    )


@app.post(
    "/chunk",
    response_model=ChunkResponse,
    summary="Chunk text into overlapping segments",
    tags=["Chunking"],
)
async def chunk_endpoint(body: ChunkRequest) -> ChunkResponse:
    """Accept a JSON body containing text and optional chunking parameters.

    Returns a list of text chunks suitable for embedding or retrieval
    pipelines.
    """
    chunks: list[str] = chunk_text(
        body.text,
        chunk_size=body.chunk_size,
        overlap=body.overlap,
    )

    avg_size: int = (
        round(sum(len(c) for c in chunks) / len(chunks)) if chunks else 0
    )

    logger.info(
        "POST /chunk — {} chunk(s), avg size {} chars",
        len(chunks),
        avg_size,
    )

    return ChunkResponse(
        chunks=chunks,
        chunk_count=len(chunks),
        avg_chunk_size=avg_size,
    )


@app.post(
    "/preprocess",
    response_model=PreprocessResponse,
    summary="Full preprocessing pipeline",
    tags=["Pipeline"],
)
async def preprocess_endpoint(file: UploadFile = File(...)) -> PreprocessResponse:
    """Accept a PDF upload and run the full preprocessing pipeline.

    Steps: text extraction → chunking → keyword extraction.
    """
    pdf_bytes: bytes = await _read_validated_pdf(file)
    result: dict = preprocess_document(pdf_bytes)

    logger.info(
        "POST /preprocess — {} page(s), {} chunk(s), {:.1f} ms",
        result["page_count"],
        result["chunk_count"],
        result["processing_time_ms"],
    )

    return PreprocessResponse(**result)


@app.get(
    "/health",
    response_model=HealthResponse,
    summary="Service health check",
    tags=["Health"],
)
async def health_endpoint() -> HealthResponse:
    """Return service liveness information including uptime."""
    uptime: float = round(time.time() - _start_time, 2)
    return HealthResponse(
        status="healthy",
        service=SERVICE_NAME,
        version=SERVICE_VERSION,
        uptime_seconds=uptime,
    )



