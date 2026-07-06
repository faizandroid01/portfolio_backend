# Current Stats
https://docs.google.com/spreadsheets/d/1AqDU1gaFYlDN1DrnC3ZmlsDbt7ISSfGYJ8McFJNUaVA/edit?gid=740338436#gid=740338436

# Portfolio Blog — Complete AWS Deployment Guide

**Frontend:** S3 + CloudFront (Account A — stepaheadwithvision@gmail.com)  
**Backend:** EC2 + Docker (Account B — stepaheadwithvision2@gmail.com)  
**Database:** Neon PostgreSQL (external, already configured)

---

## Table of Contents

1. [Pre-flight checklist](#1-pre-flight-checklist)
2. [Fix backend config before anything](#2-fix-backend-config-before-anything)
3. [Write the Dockerfile (backend)](#3-write-the-dockerfile-backend)
4. [Set up EC2 (Account B)](#4-set-up-ec2-account-b)
5. [Backend GitHub Actions — build & deploy](#5-backend-github-actions)
6. [Write the static-fetch script (frontend)](#6-write-the-static-fetch-script-frontend)
7. [Set up S3 + CloudFront (Account A)](#7-set-up-s3--cloudfront-account-a)
8. [Frontend GitHub Actions — build & deploy](#8-frontend-github-actions)
9. [Update CORS after both are live](#9-update-cors)
10. [Rotate the Neon password](#10-rotate-the-neon-password)
11. [Verification checklist](#11-verification-checklist)
12. [Cost summary](#12-cost-summary)

---

## 1. Pre-flight Checklist (Done)

Things you need before starting:

- [ ] AWS Account A console access (stepaheadwithvision)
- [ ] AWS Account B console access (stepaheadwithvision2)
- [ ] GitHub repos:
  - `faizandroid01/prime-engineer-hub` (frontend)
  - `faizandroid01/portfolio_backend` (backend)
- [ ] Neon console access (to rotate the password at the end)
- [ ] Your domain: `mdfaizahmed.in` (to point at CloudFront)

---

## 2. Fix Backend Config Before Anything (Done)

Your `application.yml` has the Neon password in plaintext in git history.  
Fix the file first, then rotate the password (step 10) after deploy.

### `src/main/resources/application.yml` — replace entirely with:

```yaml
spring:
  application:
    name: blog-api

  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 3
      minimum-idle: 1
      idle-timeout: 10000
      max-lifetime: 30000
      connection-timeout: 20000

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  jackson:
    serialization:
      write-dates-as-timestamps: false

server:
  port: ${PORT:8080}

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

### Add a health endpoint to `BlogController.java` (Done)

Add this method — the EC2 load balancer and your own scripts will call it:

```java
@GetMapping("/health")
public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of("status", "UP"));
}
```

Commit and push both changes to `main`.

---

## 3. Write the Dockerfile (backend) (Done)

Create this file at the root of `portfolio_backend`:

```dockerfile
# ── Stage 1: build ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: run ────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/blog-api-1.0.0.jar app.jar

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Also create `.dockerignore` at the repo root:

```
target/
.git/
.idea/
*.md
```

Commit and push both files.

---

## 4. Set up EC2 (Account B) 

### 4a. Launch the instance

1. Go to **EC2 → Launch Instance** in Account B
2. Settings:
   - Name: `portfolio-blog-api`
   - AMI: **Amazon Linux 2023** (free tier eligible)
   - Instance type: **t3.micro** (free tier)
   - Key pair: create new → name it `portfolio-key` → download the `.pem` file → **keep it safe**
   - Security group: create new, add these inbound rules:

| Type | Port | Source | Purpose |
|------|------|--------|---------|
| SSH | 22 | Your IP only | You SSH in |
| Custom TCP | 8080 | 0.0.0.0/0 | Spring Boot API |

3. Storage: 8 GB gp3 (default, free tier)
4. Launch

### 4b. Note the public IP

After launch, go to the instance → copy the **Public IPv4 address**.  
(Optionally: allocate an Elastic IP so the address never changes — EC2 → Elastic IPs → Allocate → Associate.)

### 4c. Install Docker on the instance

SSH in:
```bash
ssh -i portfolio-key.pem ec2-user@YOUR_EC2_IP
```

Then install Docker:
```bash
sudo yum update -y
sudo yum install -y docker git
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user
# Log out and back in so the group takes effect
exit
```

SSH back in and verify:
```bash
docker run hello-world
```

### 4d. Create the environment file on EC2

This is where your secrets live — only on the server, never in git:

```bash
sudo mkdir -p /opt/blog-api
sudo tee /opt/blog-api/.env > /dev/null <<'EOF'
DB_URL=jdbc:postgresql://ep-fancy-fog-and51rpk-pooler.c-6.us-east-1.aws.neon.tech/blogdb?sslmode=require
DB_USERNAME=root
DB_PASSWORD=npg_5tPgLc6FTdyv
PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:4000,https://mdfaizahmed.in,https://www.mdfaizahmed.in
EOF
sudo chmod 600 /opt/blog-api/.env
```

> **Note:** After step 10 (rotating the Neon password), update `DB_PASSWORD` in this file.

### 4e. Create the deploy script on EC2

```bash
sudo tee /opt/blog-api/deploy.sh > /dev/null <<'EOF'
#!/bin/bash
set -e

IMAGE="portfolio-blog-api"
CONTAINER="blog-api"

echo "Pulling latest code..."
cd /opt/blog-api/repo
git pull origin main

echo "Building Docker image..."
docker build -t $IMAGE .

echo "Stopping old container..."
docker stop $CONTAINER 2>/dev/null || true
docker rm $CONTAINER 2>/dev/null || true

echo "Starting new container..."
docker run -d \
  --name $CONTAINER \
  --env-file /opt/blog-api/.env \
  --restart unless-stopped \
  -p 8080:8080 \
  $IMAGE

echo "Waiting for health check..."
sleep 10
curl -sf http://localhost:8080/api/health && echo "✅ Deploy successful" || echo "❌ Health check failed"
EOF
sudo chmod +x /opt/blog-api/deploy.sh
```

### 4f. Clone the repo on EC2 (first time only)

```bash
sudo mkdir -p /opt/blog-api/repo
sudo chown ec2-user:ec2-user /opt/blog-api/repo
git clone https://github.com/faizandroid01/portfolio_backend.git /opt/blog-api/repo
```

Run your first deploy manually to confirm it works:
```bash
/opt/blog-api/deploy.sh
```

Check it's running:
```bash
curl http://localhost:8080/api/health
# Should return: {"status":"UP"}
curl http://localhost:8080/api/posts?page=0&size=3
```

---

## 5. Backend GitHub Actions

### 5a. Add secrets to the backend repo

In GitHub → `portfolio_backend` → Settings → Secrets and variables → Actions → New secret:

| Secret name | Value |
|-------------|-------|
| `EC2_HOST` | Your EC2 public IP |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | The full contents of `portfolio-key.pem` |

### 5b. Create the workflow

Create `.github/workflows/deploy.yml` in `portfolio_backend`:

```yaml
name: Build and Deploy to EC2

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'

      - name: Build JAR
        run: ./mvnw package -DskipTests -q

      - name: Deploy to EC2
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            /opt/blog-api/deploy.sh
```

Commit and push. Watch the Actions tab — it should SSH into your EC2 and redeploy on every push to `main`.

---

## 6. Write the Static-Fetch Script (frontend)

This runs at build time in GitHub Actions. It calls your live backend, grabs the top 5 posts, and writes them into the bundle so users see them instantly with no API call.

### 6a. Create `scripts/fetch-static-posts.mjs`

In the `prime-engineer-hub` repo root:

```javascript
// scripts/fetch-static-posts.mjs
// Runs before `vite build`. Fetches the top posts from the live API
// and writes them as a TypeScript file baked into the bundle.

import { writeFileSync, mkdirSync } from "fs";

const API_URL = (process.env.VITE_API_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const OUTPUT  = "src/data/generatedStaticPosts.ts";
const COUNT   = 5;

async function main() {
  let posts = [];

  try {
    console.log(`[static-fetch] Fetching ${COUNT} posts from ${API_URL} ...`);
    const res = await fetch(`${API_URL}/api/posts?page=0&size=${COUNT}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    posts = data.content ?? [];
    console.log(`[static-fetch] ✅ Got ${posts.length} posts`);
  } catch (err) {
    // Build still succeeds — runtime fetch handles it
    console.warn(`[static-fetch] ⚠️  API unreachable (${err.message}). Baking empty array.`);
  }

  // Map backend DTO shape → frontend BlogPost shape
  const mapped = posts.map((p) => ({
    id:           p.id,
    title:        p.title,
    slug:         p.slug,
    author:       p.authorName,
    authorAvatar: p.authorAvatar,
    date:         p.date,
    readingTime:  p.readingTime,
    thumbnail:    p.thumbnail,
    description:  p.description,
    hashtags:     p.hashtags ?? [],
    likes:        p.likes ?? 0,
    commentsCount: p.commentsCount ?? 0,
    content:      [],
    comments:     [],
  }));

  const ts = `\
// AUTO-GENERATED — do not edit manually.
// Regenerated on every CI build by scripts/fetch-static-posts.mjs
import type { BlogPost } from "./blogPosts";

export const generatedStaticPosts: BlogPost[] = ${JSON.stringify(mapped, null, 2)};
`;

  writeFileSync(OUTPUT, ts, "utf-8");
  console.log(`[static-fetch] Written → ${OUTPUT}`);
}

main();
```

### 6b. Create the initial empty file (so TypeScript doesn't break locally)

Create `src/data/generatedStaticPosts.ts`:

```typescript
// AUTO-GENERATED — do not edit manually.
// Regenerated on every CI build by scripts/fetch-static-posts.mjs
import type { BlogPost } from "./blogPosts";

export const generatedStaticPosts: BlogPost[] = [];
```

Add it to `.gitignore` so local runs don't pollute the repo:

```
# Generated at build time by CI
src/data/generatedStaticPosts.ts
```

### 6c. Update `package.json` scripts

```json
"scripts": {
  "prebuild": "node scripts/fetch-static-posts.mjs",
  "build": "vite build",
  "build:dev": "vite build --mode development",
  "dev": "vite",
  "lint": "eslint .",
  "preview": "vite preview",
  "test": "vitest run",
  "test:watch": "vitest"
}
```

### 6d. Update `src/hooks/useBlogApi.ts`

Add this import near the top (after the existing `blogPosts` import):

```typescript
import { generatedStaticPosts } from "@/data/generatedStaticPosts";
```

Then replace the `staticSlugs` and `useStaticPosts` section:

```typescript
// Merge hand-crafted posts + CI-fetched posts, de-duped by slug
// Hand-crafted posts take priority (they have full content + images)
const allStaticPosts = [
  ...blogPosts,
  ...generatedStaticPosts.filter(
    (p) => !blogPosts.some((s) => s.slug === p.slug)
  ),
];

const staticSlugs = new Set(allStaticPosts.map((p) => p.slug));

export function isStaticSlug(slug: string): boolean {
  return staticSlugs.has(slug);
}

export function useStaticPosts(tag?: string | null) {
  return tag
    ? allStaticPosts.filter((p) => p.hashtags.includes(tag))
    : allStaticPosts;
}
```

Commit all of the above.

---

## 7. Set up S3 + CloudFront (Account A)

### 7a. Create the S3 bucket

1. Go to **S3 → Create bucket** in Account A
2. Settings:
   - Bucket name: `mdfaizahmed-portfolio` (must be globally unique)
   - Region: `us-east-1`
   - **Block all public access: ON** (CloudFront will be the only accessor)
   - Everything else: defaults
3. Create bucket

### 7b. Create a CloudFront distribution

1. Go to **CloudFront → Create distribution**
2. **Origin domain:** select your S3 bucket from the dropdown
3. **Origin access:** choose **Origin access control (OAC)** → Create new OAC (defaults are fine)
4. CloudFront will prompt you to update the S3 bucket policy — click **Copy policy**, you'll use it in 7c
5. **Viewer protocol policy:** Redirect HTTP to HTTPS
6. **Allowed HTTP methods:** GET, HEAD
7. **Cache policy:** CachingOptimized
8. **Default root object:** `index.html`
9. **Custom error pages:** Add one:
   - HTTP error code: `403`
   - Response page path: `/index.html`
   - HTTP response code: `200`
   
   Add another:
   - HTTP error code: `404`
   - Response page path: `/index.html`
   - HTTP response code: `200`
   
   (These two rules make React Router work — all paths serve `index.html`)

10. If you have a custom domain (`mdfaizahmed.in`):
    - Alternate domain names (CNAMEs): add `mdfaizahmed.in` and `www.mdfaizahmed.in`
    - Custom SSL certificate: request one via ACM (us-east-1) for `mdfaizahmed.in`

11. Create distribution — note the **CloudFront domain name** (e.g. `d1abc123.cloudfront.net`)

### 7c. Attach the S3 bucket policy

Go to **S3 → your bucket → Permissions → Bucket policy** → paste the policy CloudFront gave you. It looks like:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontServicePrincipal",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::mdfaizahmed-portfolio/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "arn:aws:cloudfront::YOUR_ACCOUNT_ID:distribution/YOUR_DIST_ID"
        }
      }
    }
  ]
}
```

### 7d. Create an IAM user for GitHub Actions (Account A)

1. Go to **IAM → Users → Create user**
2. Name: `github-actions-frontend`
3. Attach policies directly:
   - `AmazonS3FullAccess`
   - `CloudFrontFullAccess`
4. Create user → **Security credentials → Create access key** → CLI use → download

---

## 8. Frontend GitHub Actions

### 8a. Add secrets to the frontend repo

In GitHub → `prime-engineer-hub` → Settings → Secrets → Actions:

| Secret name | Value |
|-------------|-------|
| `AWS_ACCESS_KEY_ID` | From the IAM user in step 7d |
| `AWS_SECRET_ACCESS_KEY` | From the IAM user in step 7d |
| `VITE_API_BASE_URL` | `http://YOUR_EC2_IP:8080` |
| `AWS_S3_BUCKET` | `mdfaizahmed-portfolio` |
| `AWS_CLOUDFRONT_ID` | Your CloudFront distribution ID |

### 8b. Create the workflow

Create `.github/workflows/deploy.yml` in `prime-engineer-hub`:

```yaml
name: Build and Deploy to S3

on:
  push:
    branches: [main]

env:
  AWS_REGION: us-east-1

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - name: Install dependencies
        run: npm ci

      - name: Build (fetches static posts + vite build)
        env:
          VITE_API_BASE_URL: ${{ secrets.VITE_API_BASE_URL }}
        run: npm run build
        # This runs: node scripts/fetch-static-posts.mjs && vite build

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}

      - name: Sync dist/ to S3
        run: |
          aws s3 sync dist/ s3://${{ secrets.AWS_S3_BUCKET }} \
            --delete \
            --cache-control "public,max-age=31536000,immutable" \
            --exclude "index.html"

          # index.html gets short cache so new deploys propagate fast
          aws s3 cp dist/index.html s3://${{ secrets.AWS_S3_BUCKET }}/index.html \
            --cache-control "public,max-age=0,must-revalidate"

      - name: Invalidate CloudFront cache
        run: |
          aws cloudfront create-invalidation \
            --distribution-id ${{ secrets.AWS_CLOUDFRONT_ID }} \
            --paths "/*"
```

Commit and push. The Actions tab will show the build — watch the `[static-fetch]` log line to confirm it fetches from your EC2 backend.

---

## 9. Update CORS

Once both are deployed, update the CORS allowed origins on EC2:

```bash
ssh -i portfolio-key.pem ec2-user@YOUR_EC2_IP
sudo nano /opt/blog-api/.env
```

Update `CORS_ALLOWED_ORIGINS`:
```
CORS_ALLOWED_ORIGINS=http://localhost:4000,https://mdfaizahmed.in,https://www.mdfaizahmed.in,https://YOUR_CLOUDFRONT_DOMAIN.cloudfront.net
```

Restart the container:
```bash
docker restart blog-api
```

---

## 10. Rotate the Neon Password

The old password is in your git history. Do this after everything is deployed and working:

1. Log into [neon.tech](https://neon.tech) → your project → **Settings → Reset password**
2. Copy the new password
3. Update `/opt/blog-api/.env` on EC2 with the new `DB_PASSWORD`
4. Restart: `docker restart blog-api`
5. Verify: `curl http://localhost:8080/api/health`

To clean the old password from git history (optional but thorough):
```bash
# Install git-filter-repo: pip install git-filter-repo
git filter-repo --path src/main/resources/application.yml --invert-paths
# Then force-push — coordinate with any collaborators first
git push --force-with-lease
```

---

## 11. Verification Checklist

After everything is deployed, run through these:

### Backend (Account B)
```bash
# Health check
curl http://YOUR_EC2_IP:8080/api/health
# → {"status":"UP"}

# Posts endpoint
curl "http://YOUR_EC2_IP:8080/api/posts?page=0&size=3"
# → JSON with paginated posts

# CORS header check (replace ORIGIN with your CloudFront URL)
curl -H "Origin: https://mdfaizahmed.in" \
     -I http://YOUR_EC2_IP:8080/api/posts
# → Should include: Access-Control-Allow-Origin: https://mdfaizahmed.in
```

### Frontend (Account A)
- Open `https://YOUR_CLOUDFRONT_DOMAIN.cloudfront.net`
- Blog section should load instantly (static posts from bundle)
- Dynamic posts load below them via API
- Open DevTools → Network → verify static posts have no API call
- Check that `/blog/some-slug` works when you paste the URL directly (React Router via 403→200 rule)

### GitHub Actions
- Push a dummy commit to each repo
- Watch both Actions pipelines go green
- Backend: SSH deploy logs should appear
- Frontend: `[static-fetch] ✅ Got N posts` should appear in the build log

---

## 12. Cost Summary

| Service | Account | Free Tier | Notes |
|---------|---------|-----------|-------|
| EC2 t3.micro | B | 750 hrs/month (12 months) | ~$8/mo after year 1 |
| S3 storage | A | 5 GB free | Your `dist/` is ~5MB |
| S3 requests | A | 20,000 GET/month free | CloudFront minimises direct S3 hits |
| CloudFront | A | 1 TB transfer + 10M requests/month free | Essentially free for a portfolio |
| Neon PostgreSQL | External | 0.5 GB, 1 project | Free forever tier |
| GitHub Actions | Both | 2,000 min/month free | Each deploy is ~2–3 min |

**Total cost while in free tier: $0**  
**Total cost after 12 months: ~$8/month (just the EC2)**
