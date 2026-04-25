package com.blogplatform.repository;

import com.blogplatform.model.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

public interface AuthorRepository extends JpaRepository<Author, UUID> {
}
