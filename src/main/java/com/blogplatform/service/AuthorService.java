package com.blogplatform.service;

import com.blogplatform.model.Author;
import com.blogplatform.repository.AuthorRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorService {

    private final AuthorRepository authorRepository;

    @Transactional
    public List<Author> getAllAuthors(){
        return authorRepository.findAll();
    }
}
