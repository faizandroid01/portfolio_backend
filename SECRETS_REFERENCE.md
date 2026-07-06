# Secrets & Commands Quick Reference

## GitHub Secrets — portfolio_backend repo

| Secret | Where to get it |
|--------|----------------|
| `EC2_HOST` | EC2 console → Public IPv4 address |
| `EC2_USER` | Always `ec2-user` for Amazon Linux |
| `EC2_SSH_KEY` | Full contents of `portfolio-key.pem` |

## GitHub Secrets — prime-engineer-hub repo

| Secret | Where to get it |
|--------|----------------|
| `AWS_ACCESS_KEY_ID` | IAM user `github-actions-frontend` → Access keys |
| `AWS_SECRET_ACCESS_KEY` | Same IAM user |
| `VITE_API_BASE_URL` | `http://YOUR_EC2_IP:8080` |
| `AWS_S3_BUCKET` | The S3 bucket name you created |
| `AWS_CLOUDFRONT_ID` | CloudFront console → Distribution ID |

---

## Key Commands

### SSH into EC2
```bash
ssh -i portfolio-key.pem ec2-user@YOUR_EC2_IP
```

### Check container is running
```bash
docker ps
docker logs blog-api
```

### Manual redeploy (if needed)
```bash
/opt/blog-api/deploy.sh
```

### Health check
```bash
curl http://YOUR_EC2_IP:8080/api/health
```

### Update CORS or secrets on EC2
```bash
nano /opt/blog-api/.env
docker restart blog-api
```

### View live container logs
```bash
docker logs -f blog-api
```

### After rotating Neon password
```bash
# 1. Update the password in Neon console
# 2. SSH into EC2
nano /opt/blog-api/.env   # update DB_PASSWORD
docker restart blog-api
curl http://localhost:8080/api/health
```

---

## CloudFront custom error pages (React Router fix)

In CloudFront console → your distribution → Error pages:

| HTTP error code | Response page | Response code |
|----------------|--------------|---------------|
| 403 | /index.html | 200 |
| 404 | /index.html | 200 |

Both rules are required. Without them, refreshing any React Router URL returns a 403/404.

---

## Deploy order (first time only)

1. Fix `application.yml` → commit → push backend
2. Add `Dockerfile` + `.dockerignore` → commit → push backend
3. Set up EC2 → install Docker → clone repo → run `ec2-setup.sh`
4. Add secrets to backend repo → GitHub Actions deploys automatically
5. Test: `curl http://EC2_IP:8080/api/health`
6. Add `fetch-static-posts.mjs` + update `useBlogApi.ts` → commit → push frontend
7. Create S3 bucket + CloudFront distribution (Account A)
8. Create IAM user → add secrets to frontend repo
9. GitHub Actions builds + deploys frontend automatically
10. Update CORS in `/opt/blog-api/.env` to include CloudFront URL → `docker restart blog-api`
11. Rotate Neon password
