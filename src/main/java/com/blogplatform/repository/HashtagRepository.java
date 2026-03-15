package com.blogplatform.repository;

import com.blogplatform.model.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HashtagRepository extends JpaRepository<Hashtag, Integer> {
    Optional<Hashtag> findByName(String name);
}
