package com.blogplatform.repository;

import com.blogplatform.model.BlogComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface BlogCommentRepository extends JpaRepository<BlogComment, UUID> {

    List<BlogComment> findByPostIdOrderByCreatedAtDesc(UUID postId);

    long countByPostId(UUID postId);

    @Modifying
    @Query("UPDATE BlogComment c SET c.likes = c.likes + 1 WHERE c.id = :id")
    void incrementLikes(@Param("id") UUID id);
}
