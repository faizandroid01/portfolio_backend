# Full Migration Guide
## Moving Backend + Frontend + DB to New AWS Accounts from Scratch

Use this when free tier expires, switching accounts, or disaster recovery.

---

## PHASE 0 - Pre-Migration Checklist

```
[ ] New AWS Account A ready (frontend - S3/CloudFront)
[ ] New AWS Account B ready (backend - EC2/Docker)
[ ] GitHub repos accessible (portfolio_backend + prime-engineer-hub)
[ ] portfolio-key.pem on Mac at ~/.ssh/portfolio-key.pem
[ ] Neon console access (neon.tech)
[ ] GoDaddy access (DNS)
[ ] CloudFormation templates downloaded:
      portfolio-backend-infra-final.yml
      portfolio-frontend-infra.yml
[ ] Note current values before migrating:
      Old EC2 IP:               35.173.52.239
      Old Backend CloudFront:   d2odapf8ux3pv4.cloudfront.net
      Old Frontend CloudFront:  d3ituubffxq42v.cloudfront.net
      Old S3 Bucket:            mdfaizahmed-portfolio
```

---

## PHASE 1 - Database (Neon)

Neon is external - not tied to any AWS account. No data migration needed.
Only thing that changes is the password.

### 1A - Backup existing data

```bash
pg_dump "postgresql://root:CURRENT_PASSWORD@ep-fancy-fog-and51rpk-pooler.c-6.us-east-1.aws.neon.tech/blogdb?sslmode=require" \
  > blogdb_backup_$(date +%Y%m%d).sql
```

### 1B - Rotate Neon password

1. Log into neon.tech -> your project -> Settings -> Reset password
2. Copy the new password - you will use it in Phase 2

### 1C - Verify new credentials work

```bash
psql "postgresql://root:NEW_PASSWORD@ep-fancy-fog-and51rpk-pooler.c-6.us-east-1.aws.neon.tech/blogdb?sslmode=require" \
  -c "SELECT COUNT(*) FROM posts;"
```

Should return your post count. DB phase done.

---

## PHASE 2 - Backend (AWS Account B)

### 2A - Import key pair into new Account B

1. AWS Console (new Account B) -> EC2 -> Key Pairs -> Actions -> Import key pair
2. Name: portfolio-key
3. Get public key from Mac:

```bash
ssh-keygen -y -f ~/.ssh/portfolio-key.pem
```

4. Paste the output (starts with ssh-rsa AAAA...) -> Import key pair

### 2B - Deploy backend CloudFormation stack

1. AWS Console (new Account B) -> CloudFormation -> Create stack -> With new resources
2. Upload: portfolio-backend-infra-final.yml
3. Stack name: portfolio-blog-backend
4. Fill parameters:

```
GitHubRepo:         https://github.com/faizandroid01/portfolio_backend.git
DBUrl:              jdbc:postgresql://ep-fancy-fog-and51rpk-pooler.c-6.us-east-1.aws.neon.tech/blogdb?sslmode=require
DBUsername:         root
DBPassword:         NEW_NEON_PASSWORD (from Phase 1B)
CorsAllowedOrigins: http://localhost:4000,https://mdfaizahmed.in,https://www.mdfaizahmed.in
                    (you will add new frontend CloudFront URL after Phase 3)
```

5. Check "I acknowledge that AWS CloudFormation might create IAM resources"
6. Submit -> wait 8-10 minutes for CREATE_COMPLETE
7. Then wait another 5-8 minutes for bootstrap (Docker install + Maven build) to finish

### 2C - Save new Outputs

From CloudFormation Outputs tab:

```
New EC2 Elastic IP:           _______________________
New Backend CloudFront URL:   _______________________
New Health Check HTTPS URL:   _______________________
New SSH Command:              _______________________
```

### 2D - Verify backend is live

```bash
# Direct EC2 check
curl http://NEW_EC2_IP/api/health

# HTTPS via CloudFront (wait 5-8 mins after CREATE_COMPLETE)
curl https://NEW_BACKEND_CLOUDFRONT/api/health

# Posts API
curl "https://NEW_BACKEND_CLOUDFRONT/api/posts?page=0&size=3"
```

All should return data. If CloudFront times out, wait 5 more minutes and retry.

### 2E - Update GitHub Secrets in portfolio_backend

Go to: github.com/faizandroid01/portfolio_backend -> Settings -> Secrets -> Actions

```
EC2_HOST     ->  NEW_EC2_IP
EC2_USER     ->  ec2-user  (unchanged)
EC2_SSH_KEY  ->  unchanged (same .pem file)
```

### 2F - Test auto-deploy

```bash
cd portfolio_backend
git commit --allow-empty -m "chore: test deploy after migration"
git push origin main
```

Watch Actions tab -> should go green in 4-5 minutes.

---

## PHASE 3 - Frontend (AWS Account A)

### 3A - Deploy frontend CloudFormation stack

1. AWS Console (new Account A) -> CloudFormation -> Create stack -> With new resources
2. Upload: portfolio-frontend-infra.yml
3. Stack name: portfolio-blog-frontend
4. Fill parameters:

```
BucketName:    mdfaizahmed-portfolio
               (if name taken, try mdfaizahmed-portfolio-2025 or similar)
BackendAPIURL: https://NEW_BACKEND_CLOUDFRONT (from Phase 2C)
```

5. Check "I acknowledge that AWS CloudFormation might create IAM resources"
6. Submit -> wait 5-8 minutes

### 3B - Save new Outputs

From CloudFormation Outputs tab:

```
New S3 Bucket:               _______________________
New Frontend CloudFront ID:  _______________________
New Frontend CloudFront URL: _______________________
New CloudFront Domain:       _______________________
AWS_ACCESS_KEY_ID:           _______________________
AWS_SECRET_ACCESS_KEY:       _______________________
```

### 3C - Update GitHub Secrets in prime-engineer-hub

Go to: github.com/faizandroid01/prime-engineer-hub -> Settings -> Secrets -> Actions

Update all 5:

```
AWS_ACCESS_KEY_ID:     NEW VALUE from Outputs
AWS_SECRET_ACCESS_KEY: NEW VALUE from Outputs
AWS_S3_BUCKET:         NEW BUCKET NAME from Outputs
AWS_CLOUDFRONT_ID:     NEW CLOUDFRONT ID from Outputs
VITE_API_BASE_URL:     https://NEW_BACKEND_CLOUDFRONT
```

### 3D - Trigger frontend deploy

```bash
cd prime-engineer-hub
git commit --allow-empty -m "chore: trigger deploy after migration"
git push origin main
```

Build log should show:
```
[static-fetch] Fetching top 5 posts from https://NEW_BACKEND_CLOUDFRONT
[static-fetch] Fetched N posts
```

### 3E - Verify frontend is live

Open in browser: https://NEW_FRONTEND_CLOUDFRONT_URL

---

## PHASE 4 - Wire CORS (connect frontend to backend)

### 4A - SSH into new EC2

```bash
ssh -i ~/.ssh/portfolio-key.pem ec2-user@NEW_EC2_IP
```

### 4B - Update .env with new frontend CloudFront URL

```bash
cat > /opt/blog-api/.env << 'EOF'
DB_URL=jdbc:postgresql://ep-fancy-fog-and51rpk-pooler.c-6.us-east-1.aws.neon.tech/blogdb?sslmode=require
DB_USERNAME=root
DB_PASSWORD=NEW_NEON_PASSWORD
PORT=80
CORS_ALLOWED_ORIGINS=http://localhost:4000,https://mdfaizahmed.in,https://www.mdfaizahmed.in,https://NEW_FRONTEND_CLOUDFRONT_DOMAIN
EOF
```

### 4C - Restart container

```bash
docker stop blog-api && docker rm blog-api

docker run -d \
  --name blog-api \
  --env-file /opt/blog-api/.env \
  --restart unless-stopped \
  -p 80:80 \
  portfolio-blog-api

sleep 15
docker exec blog-api env | grep -E "PORT|CORS"
```

### 4D - Test CORS

```bash
curl -X OPTIONS \
     -H "Origin: https://NEW_FRONTEND_CLOUDFRONT_DOMAIN" \
     -H "Access-Control-Request-Method: GET" \
     "https://NEW_BACKEND_CLOUDFRONT/api/posts" -I
```

Should return:
```
access-control-allow-origin: https://NEW_FRONTEND_CLOUDFRONT_DOMAIN
```

---

## PHASE 5 - Custom Domain (GoDaddy + ACM)

### 5A - Request new ACM certificate (new Account A, us-east-1 region only)

1. AWS Console (new Account A) -> Certificate Manager -> MUST be us-east-1
2. Request a certificate -> Request a public certificate
3. Add domain names:
    - mdfaizahmed.in
    - www.mdfaizahmed.in
4. Validation method: DNS validation -> Request
5. Click on the pending cert -> note the two CNAME records:
    - Name:  _xxxx.mdfaizahmed.in
    - Value: _xxxx.acm-validations.aws

### 5B - Add ACM validation CNAMEs in GoDaddy

GoDaddy -> DNS -> Add record for each:

```
Type:  CNAME
Name:  _xxxx  (just the part before .mdfaizahmed.in)
Value: _xxxx.acm-validations.aws
TTL:   600
```

Wait 5-30 minutes -> cert status changes to "Issued".

### 5C - Update CloudFront distribution with custom domain + cert

1. CloudFront (new Account A) -> frontend distribution -> General tab -> Edit
2. Alternate domain names (CNAMEs):
    - mdfaizahmed.in
    - www.mdfaizahmed.in
3. Custom SSL certificate: select newly issued cert
4. Save -> wait 5 minutes

### 5D - Update GoDaddy DNS to point to new CloudFront

GoDaddy -> DNS -> update or add:

```
Type:  CNAME
Name:  www
Value: NEW_FRONTEND_CLOUDFRONT_DOMAIN
TTL:   600

Type:  CNAME
Name:  @
Value: NEW_FRONTEND_CLOUDFRONT_DOMAIN
TTL:   600
```

DNS propagates in 5 minutes to 48 hours. Test with:

```bash
curl -I https://mdfaizahmed.in
curl -I https://www.mdfaizahmed.in
```

---

## PHASE 6 - Final Verification Checklist

Run all of these - everything should return 200 or data:

```bash
# 1. Neon DB direct connection
psql "postgresql://root:NEW_PASSWORD@ep-fancy-fog-and51rpk-pooler.c-6.us-east-1.aws.neon.tech/blogdb?sslmode=require" \
  -c "SELECT COUNT(*) FROM posts;"

# 2. Backend health direct
curl http://NEW_EC2_IP/api/health

# 3. Backend health via CloudFront HTTPS
curl https://NEW_BACKEND_CLOUDFRONT/api/health

# 4. Posts API
curl "https://NEW_BACKEND_CLOUDFRONT/api/posts?page=0&size=3"

# 5. CORS preflight
curl -X OPTIONS \
     -H "Origin: https://NEW_FRONTEND_CLOUDFRONT" \
     -H "Access-Control-Request-Method: GET" \
     "https://NEW_BACKEND_CLOUDFRONT/api/posts" -I

# 6. Frontend site
curl -I https://NEW_FRONTEND_CLOUDFRONT

# 7. Custom domain
curl -I https://mdfaizahmed.in
curl -I https://www.mdfaizahmed.in

# 8. Test backend auto-deploy
cd portfolio_backend && git commit --allow-empty -m "test" && git push origin main

# 9. Test frontend auto-deploy
cd prime-engineer-hub && git commit --allow-empty -m "test" && git push origin main
```

---

## Migration Time Estimate

| Phase | Task | Time |
|-------|------|------|
| 0 | Pre-flight checklist | 5 mins |
| 1 | Neon backup + password rotation | 10 mins |
| 2 | Backend stack deploy + verify | 20 mins |
| 3 | Frontend stack deploy + verify | 15 mins |
| 4 | CORS wiring | 5 mins |
| 5 | Custom domain ACM + GoDaddy + CloudFront | 30-60 mins (mostly DNS wait) |
| 6 | Final verification | 10 mins |
| Total | | ~90 mins active + DNS propagation |

---

## What Stays the Same After Migration

```
GitHub repos              no change
Neon database URL         no change (only password rotates)
GoDaddy domain            no change (only DNS records update)
portfolio-key.pem         same key file, re-import into new account
All application code      no change
Docker image              rebuilt from same repo automatically
CloudFormation templates  reuse exactly as-is
```

## What Changes After Migration

```
EC2 IP                    new Elastic IP assigned
Backend CloudFront URL    new distribution = new domain
Frontend CloudFront URL   new distribution = new domain
S3 bucket name            may need suffix if name taken
IAM access keys           new keys generated by CloudFormation
ACM certificate           new cert in new account
GitHub Secrets            update EC2_HOST + all 5 frontend secrets
CORS origins on EC2       update .env with new frontend CloudFront URL
GoDaddy DNS records       update to point to new CloudFront domain
```