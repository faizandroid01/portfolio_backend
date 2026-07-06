#!/bin/bash
# Run this ONCE on a fresh Amazon Linux 2023 EC2 instance.
# SSH in as ec2-user and execute: bash ec2-setup.sh
set -e

echo "=== Installing Docker ==="
sudo yum update -y
sudo yum install -y docker git
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ec2-user

echo "=== Creating directory structure ==="
sudo mkdir -p /opt/blog-api
sudo chown ec2-user:ec2-user /opt/blog-api

echo "=== Cloning backend repo ==="
git clone https://github.com/faizandroid01/portfolio_backend.git /opt/blog-api/repo

echo "=== Creating .env file ==="
# EDIT THESE VALUES before running, or fill them in after
cat > /opt/blog-api/.env <<'EOF'
DB_URL=jdbc:postgresql://ep-fancy-fog-and51rpk-pooler.c-6.us-east-1.aws.neon.tech/blogdb?sslmode=require
DB_USERNAME=root
DB_PASSWORD=REPLACE_WITH_NEW_NEON_PASSWORD
PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:4000,https://mdfaizahmed.in,https://www.mdfaizahmed.in
EOF
chmod 600 /opt/blog-api/.env

echo "=== Creating deploy script ==="
cp /opt/blog-api/repo/ec2-deploy.sh /opt/blog-api/deploy.sh
chmod +x /opt/blog-api/deploy.sh

echo ""
echo "=== Setup complete ==="
echo "NEXT: Log out and back in (so docker group takes effect), then:"
echo "  /opt/blog-api/deploy.sh"
