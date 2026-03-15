package com.blogplatform.repository;

import com.blogplatform.model.BlogPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    Optional<BlogPost> findBySlugAndIsPublishedTrue(String slug);

    Page<BlogPost> findByIsPublishedTrueOrderByDateDesc(Pageable pageable);

    @Query("SELECT bp FROM BlogPost bp JOIN bp.hashtags h " +
           "WHERE bp.isPublished = true AND h.name = :tag ORDER BY bp.date DESC")
    Page<BlogPost> findByHashtag(@Param("tag") String tag, Pageable pageable);

    @Query("SELECT bp FROM BlogPost bp WHERE bp.isPublished = true " +
           "AND (LOWER(bp.title) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(bp.description) LIKE LOWER(CONCAT('%',:q,'%'))) " +
           "ORDER BY bp.date DESC")
    Page<BlogPost> searchPosts(@Param("q") String query, Pageable pageable);

    @Modifying
    @Query("UPDATE BlogPost bp SET bp.likes = bp.likes + 1 WHERE bp.id = :id")
    void incrementLikes(@Param("id") UUID id);

    boolean existsBySlug(String slug);
}
