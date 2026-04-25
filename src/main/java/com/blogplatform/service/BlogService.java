package com.blogplatform.service;

import com.blogplatform.dto.*;
import com.blogplatform.model.*;
import com.blogplatform.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogPostRepository postRepo;
    private final AuthorRepository authorRepo;
    private final HashtagRepository hashtagRepo;
    private final BlogCommentRepository commentRepo;

    // ─── List posts ──────────────────────────────────────────

    public Page<PostSummaryResponse> listPosts(int page, int size, String tag, String search) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<BlogPost> posts;
        if (tag != null && !tag.isBlank()) {
            posts = postRepo.findByHashtag(tag, pageable);
        } else if (search != null && !search.isBlank()) {
            posts = postRepo.searchPosts(search, pageable);
        } else {
            posts = postRepo.findByIsPublishedTrueOrderByDateDesc(pageable);
        }

        return posts.map(this::toSummary);
    }

    // ─── Get post by slug ────────────────────────────────────

    public PostDetailResponse getBySlug(String slug) {
        BlogPost post = postRepo.findBySlugAndIsPublishedTrue(slug)
                .orElseThrow(() -> new NoSuchElementException("Post not found: " + slug));
        return toDetail(post);
    }

    // ─── Create post ─────────────────────────────────────────

    @Transactional
    public PostDetailResponse createPost(CreatePostRequest req) {
        req.setAuthorId(UUID.fromString("4cf643e7-5f73-4c29-97e8-41aa711b458f"));

        Author author = authorRepo.findById(req.getAuthorId())
                .orElseThrow(() -> new NoSuchElementException("Author not found")); // Change this later to include
                                                                                    // different author. For now its
                                                                                    // always faiz

        if (postRepo.existsBySlug(req.getSlug())) {
            throw new IllegalArgumentException("Slug already exists: " + req.getSlug());
        }

        BlogPost post = BlogPost.builder()
                .title(req.getTitle())
                .slug(req.getSlug())
                .author(author)
                .date(LocalDate.now())
                .readingTime(req.getReadingTime() != null ? req.getReadingTime() : "1 min read")
                .thumbnail(req.getThumbnail())
                .description(req.getDescription())
                .likes(0)
                .isPublished(true)
                .hashtags(new HashSet<>())
                .sections(new ArrayList<>())
                .comments(new ArrayList<>())
                .build();

        // Hashtags
        if (req.getHashtags() != null) {
            for (String tagName : req.getHashtags()) {
                Hashtag ht = hashtagRepo.findByName(tagName)
                        .orElseGet(() -> hashtagRepo.save(Hashtag.builder().name(tagName).build()));
                post.getHashtags().add(ht);
            }
        }

        // Sections
        if (req.getSections() != null) {
            for (int i = 0; i < req.getSections().size(); i++) {
                CreatePostRequest.SectionInput si = req.getSections().get(i);
                BlogSection section = BlogSection.builder()
                        .post(post)
                        .sortOrder(i)
                        .type(BlogSection.SectionType.valueOf(si.getType()))
                        .content(si.getContent())
                        .build();
                post.getSections().add(section);
            }
        }

        postRepo.save(post);
        return toDetail(post);
    }

    // ─── Update post ─────────────────────────────────────────

    @Transactional
    public PostDetailResponse updatePost(UUID id, UpdatePostRequest req) {
        BlogPost post = postRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        if (req.getTitle() != null)
            post.setTitle(req.getTitle());
        if (req.getDescription() != null)
            post.setDescription(req.getDescription());
        if (req.getThumbnail() != null)
            post.setThumbnail(req.getThumbnail());
        if (req.getReadingTime() != null)
            post.setReadingTime(req.getReadingTime());
        if (req.getIsPublished() != null)
            post.setIsPublished(req.getIsPublished());

        // Replace hashtags
        if (req.getHashtags() != null) {
            post.getHashtags().clear();
            for (String tagName : req.getHashtags()) {
                Hashtag ht = hashtagRepo.findByName(tagName)
                        .orElseGet(() -> hashtagRepo.save(Hashtag.builder().name(tagName).build()));
                post.getHashtags().add(ht);
            }
        }

        // Replace sections
        if (req.getSections() != null) {
            post.getSections().clear();
            for (int i = 0; i < req.getSections().size(); i++) {
                CreatePostRequest.SectionInput si = req.getSections().get(i);
                BlogSection section = BlogSection.builder()
                        .post(post)
                        .sortOrder(i)
                        .type(BlogSection.SectionType.valueOf(si.getType()))
                        .content(si.getContent())
                        .build();
                post.getSections().add(section);
            }
        }

        postRepo.save(post);
        return toDetail(post);
    }

    // ─── Delete post ─────────────────────────────────────────

    @Transactional
    public void deletePost(UUID id) {
        postRepo.deleteById(id);
    }

    // ─── Like post ───────────────────────────────────────────

    @Transactional
    public int likePost(UUID id) {
        postRepo.incrementLikes(id);
        return postRepo.findById(id).map(BlogPost::getLikes).orElse(0);
    }

    // ─── Comments ────────────────────────────────────────────

    @Transactional
    public PostDetailResponse.CommentResponse addComment(UUID postId, CreateCommentRequest req) {
        BlogPost post = postRepo.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found"));

        BlogComment comment = BlogComment.builder()
                .post(post)
                .username(req.getUsername())
                .avatar(req.getAvatar())
                .text(req.getText())
                .likes(0)
                .build();

        comment = commentRepo.save(comment);
        return toCommentResponse(comment);
    }

    @Transactional
    public void deleteComment(UUID postId, UUID commentId) {
        commentRepo.deleteById(commentId);
    }

    @Transactional
    public int likeComment(UUID commentId) {
        commentRepo.incrementLikes(commentId);
        return commentRepo.findById(commentId).map(BlogComment::getLikes).orElse(0);
    }

    // ─── Tags ────────────────────────────────────────────────

    public List<String> getAllTags() {
        return hashtagRepo.findAll().stream()
                .map(Hashtag::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    // ─── Mappers ─────────────────────────────────────────────

    private PostSummaryResponse toSummary(BlogPost p) {
        return PostSummaryResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .slug(p.getSlug())
                .authorName(p.getAuthor().getName())
                .authorAvatar(p.getAuthor().getAvatar())
                .date(p.getDate())
                .readingTime(p.getReadingTime())
                .thumbnail(p.getThumbnail())
                .description(p.getDescription())
                .likes(p.getLikes())
                .commentsCount(commentRepo.countByPostId(p.getId()))
                .hashtags(p.getHashtags().stream().map(Hashtag::getName).sorted().toList())
                .build();
    }

    private PostDetailResponse toDetail(BlogPost p) {
        return PostDetailResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .slug(p.getSlug())
                .authorName(p.getAuthor().getName())
                .authorAvatar(p.getAuthor().getAvatar())
                .date(p.getDate())
                .readingTime(p.getReadingTime())
                .thumbnail(p.getThumbnail())
                .description(p.getDescription())
                .likes(p.getLikes())
                .commentsCount(commentRepo.countByPostId(p.getId()))
                .hashtags(p.getHashtags().stream().map(Hashtag::getName).sorted().toList())
                .sections(p.getSections().stream().map(s -> PostDetailResponse.SectionResponse.builder()
                        .id(s.getId())
                        .sortOrder(s.getSortOrder())
                        .type(s.getType().name())
                        .content(s.getContent())
                        .build()).toList())
                .comments(p.getComments().stream().map(this::toCommentResponse).toList())
                .build();
    }

    private PostDetailResponse.CommentResponse toCommentResponse(BlogComment c) {
        return PostDetailResponse.CommentResponse.builder()
                .id(c.getId())
                .username(c.getUsername())
                .avatar(c.getAvatar())
                .text(c.getText())
                .likes(c.getLikes())
                .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : null)
                .build();
    }
}
