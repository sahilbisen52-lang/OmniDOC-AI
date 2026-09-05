package com.docassistant.repository;

import com.docassistant.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Document} entities.
 *
 * <p>Provides standard CRUD operations plus a custom finder that
 * returns documents sorted by upload date (newest first), which
 * maps directly to the frontend's document list view.</p>
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

    /**
     * Retrieves all documents ordered by upload timestamp descending
     * (most recently uploaded first).
     *
     * @return ordered list of documents
     */
    List<Document> findAllByOrderByUploadedAtDesc();
    List<Document> findAllByUserIdOrderByUploadedAtDesc(String userId);
}
