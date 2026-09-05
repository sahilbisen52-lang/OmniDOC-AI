"""Pydantic v2 request/response schemas for the DocAssistant PDF Processing Service.

All models use Pydantic v2 with strict type validation and comprehensive
field descriptions suitable for automatic OpenAPI documentation generation.
"""

from pydantic import BaseModel, Field


class ExtractionResponse(BaseModel):
    """Response schema for the PDF text extraction endpoint.

    Contains the raw extracted text along with page count, document
    metadata, and server-side processing time for observability.
    """

    text: str = Field(
        ...,
        description="Full extracted text content from the PDF document.",
    )
    page_count: int = Field(
        ...,
        ge=0,
        description="Total number of pages in the PDF document.",
    )
    metadata: dict = Field(
        default_factory=dict,
        description="Document metadata including title, author, subject, and character counts.",
    )
    processing_time_ms: float = Field(
        ...,
        ge=0,
        description="Server-side processing time in milliseconds.",
    )


class ChunkRequest(BaseModel):
    """Request schema for the text chunking endpoint.

    Accepts raw text and optional chunking parameters to control
    chunk granularity and overlap for downstream RAG pipelines.
    """

    text: str = Field(
        ...,
        min_length=1,
        description="The text content to split into chunks.",
    )
    chunk_size: int = Field(
        default=512,
        ge=50,
        le=8192,
        description="Approximate number of characters per chunk.",
    )
    overlap: int = Field(
        default=50,
        ge=0,
        le=512,
        description="Number of overlapping characters between consecutive chunks.",
    )


class ChunkResponse(BaseModel):
    """Response schema for the text chunking endpoint.

    Returns the generated text chunks with summary statistics
    for downstream pipeline configuration.
    """

    chunks: list[str] = Field(
        ...,
        description="List of text chunks produced by the chunker.",
    )
    chunk_count: int = Field(
        ...,
        ge=0,
        description="Total number of chunks produced.",
    )
    avg_chunk_size: int = Field(
        ...,
        ge=0,
        description="Average character count across all chunks.",
    )


class PreprocessResponse(BaseModel):
    """Response schema for the full preprocessing pipeline endpoint.

    Combines extraction, chunking, and keyword analysis into a single
    response payload for one-shot document ingestion workflows.
    """

    text: str = Field(
        ...,
        description="Full extracted text from the PDF document.",
    )
    chunks: list[str] = Field(
        ...,
        description="Text chunks suitable for embedding or indexing.",
    )
    page_count: int = Field(
        ...,
        ge=0,
        description="Total number of pages in the PDF document.",
    )
    chunk_count: int = Field(
        ...,
        ge=0,
        description="Total number of chunks produced.",
    )
    metadata: dict = Field(
        default_factory=dict,
        description="Document metadata extracted from the PDF.",
    )
    keywords: list[str] = Field(
        ...,
        description="Top keywords extracted via frequency analysis.",
    )
    processing_time_ms: float = Field(
        ...,
        ge=0,
        description="Total server-side processing time in milliseconds.",
    )


class HealthResponse(BaseModel):
    """Response schema for the health-check endpoint.

    Provides basic liveness information, service identity, and
    uptime for orchestration and monitoring tooling.
    """

    status: str = Field(
        ...,
        description="Service health status, e.g. 'healthy'.",
    )
    service: str = Field(
        ...,
        description="Canonical service name.",
    )
    version: str = Field(
        ...,
        description="Semantic version of the running service.",
    )
    uptime_seconds: float = Field(
        ...,
        ge=0,
        description="Seconds elapsed since the service started.",
    )


class ErrorResponse(BaseModel):
    """Standard error response envelope.

    Returned by custom exception handlers to give callers a
    predictable JSON error shape across all failure modes.
    """

    error: str = Field(
        ...,
        description="Short error category, e.g. 'validation_error'.",
    )
    detail: str = Field(
        ...,
        description="Human-readable description of what went wrong.",
    )
    status_code: int = Field(
        ...,
        ge=400,
        le=599,
        description="HTTP status code echoed in the response body.",
    )


class SearchRequest(BaseModel):
    """Request schema for semantic vector search."""

    query: str = Field(
        ...,
        min_length=1,
        description="The natural language query to search for.",
    )
    document_ids: list[str] = Field(
        ...,
        description="List of document IDs to restrict the search to.",
    )
    top_k: int = Field(
        default=5,
        ge=1,
        le=50,
        description="Maximum number of relevant chunks to return.",
    )


class SearchResult(BaseModel):
    """A single semantic search hit."""

    text: str = Field(
        ...,
        description="The text content of the matching chunk.",
    )
    document_id: str = Field(
        ...,
        description="The ID of the document where this chunk was found.",
    )
    score: float = Field(
        ...,
        description="The cosine similarity score (0.0 to 1.0) of the match.",
    )


class SearchResponse(BaseModel):
    """Response envelope for semantic vector search."""

    results: list[SearchResult] = Field(
        ...,
        description="The list of search results sorted by descending score.",
    )
    query: str = Field(
        ...,
        description="The query string that was searched.",
    )
