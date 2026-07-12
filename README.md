# portfolio_backend
Spring boot app for portfolio backend


# Operations CheatSheet
# Portfolio Blog — Operations Cheat Sheet

## SSH Access
```bash
ssh -i ~/.ssh/portfolio-key.pem ec2-user@35.173.52.239
```

---

## Health Checks

```bash
# Direct EC2 health check (HTTP)
curl http://35.173.52.239/api/health

# Backend via CloudFront (HTTPS)
curl https://d2odapf8ux3pv4.cloudfront.net/api/health

# Frontend site
curl -I https://d3ituubffxq42v.cloudfront.net
```

---

## API Verification

```bash
# Get posts
curl "https://d2odapf8ux3pv4.cloudfront.net/api/posts?page=0&size=5"

# Get all tags
curl "https://d2odapf8ux3pv4.cloudfront.net/api/tags"

# Get single post by slug
curl "https://d2odapf8ux3pv4.cloudfront.net/api/posts/YOUR_SLUG"

# CORS preflight check
curl -X OPTIONS \
     -H "Origin: https://d3ituubffxq42v.cloudfront.net" \
     -H "Access-Control-Request-Method: GET" \
     "https://d2odapf8ux3pv4.cloudfront.net/api/posts" -I
```

---

## Docker Commands (run inside SSH session)

```bash
# Check running containers
docker ps

# Check all containers including stopped
docker ps -a

# Live logs
docker logs -f blog-api

# Last 50 lines of logs
docker logs --tail 50 blog-api

# Last 100 lines with timestamps
docker logs --tail 100 -t blog-api

# Check env vars inside container
docker exec blog-api env

# Check CORS and PORT specifically
docker exec blog-api env | grep -E "PORT|CORS"

# Open shell inside container
docker exec -it blog-api sh

# Restart container
docker restart blog-api

# Stop and remove container
docker stop blog-api && docker rm blog-api

# Start container fresh from existing image
docker run -d \
  --name blog-api \
  --env-file /opt/blog-api/.env \
  --restart unless-stopped \
  -p 80:80 \
  portfolio-blog-api

# Check disk usage by images
docker images

# Remove dangling images
docker image prune -f
```

---

## App Config (run inside SSH session)

```bash
# View secrets file
cat /opt/blog-api/.env

# Edit secrets file
nano /opt/blog-api/.env

# View bootstrap log (check if first deploy succeeded)
sudo cat /var/log/user-data.log

# View deploy log
cat /var/log/user-data.log | tail -50

# Run manual redeploy
/opt/blog-api/deploy.sh

# View deploy script
cat /opt/blog-api/deploy.sh
```

---

## After Editing .env (run inside SSH session)

```bash
# Stop and restart with new env vars
docker stop blog-api && docker rm blog-api

docker run -d \
  --name blog-api \
  --env-file /opt/blog-api/.env \
  --restart unless-stopped \
  -p 80:80 \
  portfolio-blog-api

# Wait and verify
sleep 15
curl http://localhost/api/health
docker exec blog-api env | grep -E "PORT|CORS"
```

---

## GitHub Actions — Trigger Manual Deploy

```bash
# Backend
cd portfolio_backend
git commit --allow-empty -m "chore: trigger deploy"
git push origin main

# Frontend
cd prime-engineer-hub
git commit --allow-empty -m "chore: trigger deploy"
git push origin main
```

---

## Key URLs

| What | URL |
|------|-----|
| Frontend (CloudFront) | https://d3ituubffxq42v.cloudfront.net |
| Backend API (CloudFront HTTPS) | https://d2odapf8ux3pv4.cloudfront.net |
| Backend API (EC2 direct HTTP) | http://35.173.52.239 |
| Health check (HTTPS) | https://d2odapf8ux3pv4.cloudfront.net/api/health |
| Health check (HTTP) | http://35.173.52.239/api/health |

---

## Key Info

| What | Value |
|------|-------|
| EC2 IP (Elastic) | 35.173.52.239 |
| EC2 SSH user | ec2-user |
| SSH key | ~/.ssh/portfolio-key.pem |
| App directory | /opt/blog-api/ |
| Container name | blog-api |
| Docker image | portfolio-blog-api |
| Spring Boot port | 80 |
| AWS Account A | stepaheadwithvision@gmail.com (Frontend) |
| AWS Account B | stepaheadwithvision2@gmail.com (Backend) |
| Backend repo | faizandroid01/portfolio_backend |
| Frontend repo | faizandroid01/prime-engineer-hub |
| Neon DB | ep-fancy-fog-and51rpk-pooler (blogdb) |