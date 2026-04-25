#!/bin/bash

# Configuration
BASE_URL="http://localhost:8080/api"

# Generates consistent UUIDs or use fixed ones if uuidgen is not available
AUTHOR_ID=$(uuidgen 2>/dev/null || echo "11111111-1111-1111-1111-111111111111")
POST_ID=$(uuidgen 2>/dev/null || echo "22222222-2222-2222-2222-222222222222")
COMMENT_ID=$(uuidgen 2>/dev/null || echo "33333333-3333-3333-3333-333333333333")
SLUG="test-slug-api-script"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo "Starting API endpoint tests against $BASE_URL..."
echo ""

# Create table header
printf "%-20s | %-7s | %-45s | %-6s | %-6s\n" "Controller" "Method" "Endpoint" "Status" "Result"
printf "%s\n" "---------------------------------------------------------------------------------------------------"

# test_endpoint function will make the request, grab the status, and print a formatted row
test_endpoint() {
    local controller="$1"
    local method="$2"
    local endpoint="$3"
    shift 3
    # The rest of arguments ($@) are passed directly to curl
    
    local url="${BASE_URL}${endpoint}"
    local status_code
    
    # Run curl silently, only capturing the HTTP response code
    status_code=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$@" "$url")
    
    local result="FAIL"
    local color=$RED
    
    # Treat 2xx status codes as PASS
    if [[ "$status_code" =~ ^2 ]]; then
        result="PASS"
        color=$GREEN
    fi
    
    # In case there's a connection refused, curl might return 000
    if [ "$status_code" = "000" ]; then
        status_code="ERR"
    fi
    
    printf "%-20s | %-7s | %-45s | %-6s | ${color}%-6s${NC}\n" "$controller" "$method" "$endpoint" "$status_code" "$result"
}

# ==========================================
# BlogController Endpoints
# ==========================================

# 1. List Posts
test_endpoint "BlogController" "GET" "/posts?page=0&size=10"

# 2. List Authors
test_endpoint "BlogController" "GET" "/authors"

# 3. Create Post
test_endpoint "BlogController" "POST" "/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "API Test Post",
    "slug": "'${SLUG}'",
    "authorId": "'${AUTHOR_ID}'",
    "thumbnail": "https://example.com/thumb.jpg",
    "description": "Created from bash script",
    "readingTime": "2 mins",
    "hashtags": ["script", "automation"],
    "sections": [
      {
        "type": "paragraph",
        "content": {"text": "This is a test post body."}
      }
    ]
  }'

# 4. Get Post by Slug
test_endpoint "BlogController" "GET" "/posts/${SLUG}"

# 5. Update Post
test_endpoint "BlogController" "PUT" "/posts/${POST_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "API Test Post (Updated)",
    "description": "Updated from bash script",
    "isPublished": true
  }'

# 6. Like Post
test_endpoint "BlogController" "POST" "/posts/${POST_ID}/like"

# 7. Add Comment
test_endpoint "BlogController" "POST" "/posts/${POST_ID}/comments" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "api_tester",
    "avatar": "https://example.com/avatar.jpg",
    "text": "This is a test comment!"
  }'

# 8. Like Comment
test_endpoint "BlogController" "POST" "/comments/${COMMENT_ID}/like"

# 9. Get All Tags
test_endpoint "BlogController" "GET" "/tags"

# 10. Delete Comment
test_endpoint "BlogController" "DELETE" "/posts/${POST_ID}/comments/${COMMENT_ID}"

# 11. Delete Post
test_endpoint "BlogController" "DELETE" "/posts/${POST_ID}"

printf "%s\n" "---------------------------------------------------------------------------------------------------"
echo "API testing completed."
