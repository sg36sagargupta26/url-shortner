# AWS Deployment Guide — URL Shortener

Step-by-step plan to deploy the Shortly URL shortener to AWS ECS Fargate.

---

## Prerequisites

- AWS account with admin access
- GitHub repository: `https://github.com/sg36sagargupta26/url-shortner`
- Domain name (optional, for custom short URL domain)
- Terraform 1.9+ installed locally
- AWS CLI installed and configured

---

## Phase 1: AWS Account Setup (one-time)

### 1.1 Create an IAM admin user (if not using root)
```
AWS Console → IAM → Users → Create user
- Name: shortly-admin
- Attach policy: AdministratorAccess
- Create access key (save Access Key ID + Secret Access Key)
```

### 1.2 Configure AWS CLI locally
```bash
aws configure
# Enter Access Key ID, Secret Access Key, region: us-east-1
```

### 1.3 Create S3 bucket for Terraform state
```bash
aws s3 mb s3://shortly-terraform-state --region us-east-1
aws s3api put-bucket-versioning \
  --bucket shortly-terraform-state \
  --versioning-configuration Status=Enabled
```

### 1.4 Create DynamoDB table for Terraform locks
```bash
aws dynamodb create-table \
  --table-name shortly-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

### 1.5 Create ECR repository
```bash
aws ecr create-repository --repository-name shortly --region us-east-1
```
Note the repository URI: `123456789012.dkr.ecr.us-east-1.amazonaws.com/shortly`

---

## Phase 2: Security & Secrets

### 2.1 Create GitHub OIDC provider (for passwordless CI/CD)
```
AWS Console → IAM → Identity providers → Add provider
- Provider type: OpenID Connect
- Provider URL: https://token.actions.githubusercontent.com
- Audience: sts.amazonaws.com
```

### 2.2 Create GitHub Actions IAM role
```bash
aws iam create-role \
  --role-name github-actions-shortly \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:sg36sagargupta26/url-shortner:*"
        }
      }
    }]
  }'
```

### 2.3 Attach permissions to the role
```bash
aws iam attach-role-policy \
  --role-name github-actions-shortly \
  --policy-arn arn:aws:iam::aws:policy/AmazonECS_FullAccess

aws iam attach-role-policy \
  --role-name github-actions-shortly \
  --policy-arn arn:aws:iam::aws:policy/AmazonECR-FullAccess
```

### 2.4 Update `.github/workflows/deploy.yml`
Replace `123456789012` in the role ARN with your AWS account ID:
```yaml
role-to-assume: arn:aws:iam::YOUR_ACCOUNT_ID:role/github-actions-shortly
```

### 2.5 Download MaxMind GeoLite2 database
1. Register at https://dev.maxmind.com/geoip/geolite2-free-geolocation-data
2. Download `GeoLite2-City.mmdb`
3. Place it in `src/main/resources/GeoLite2-City.mmdb`
4. **Do NOT commit this file** (already in `.gitignore`)

---

## Phase 3: Terraform Deployment

### 3.1 Review variables
```bash
cd terraform
```
Check `variables.tf` — adjust if needed:
- `aws_region` (default: `us-east-1`)
- `db_instance_class` (default: `db.t3.micro` — good for MVP)
- `cpu` / `memory` (default: 256 / 512 — 0.25 vCPU)

### 3.2 Initialize Terraform
```bash
terraform init
```

### 3.3 Preview changes
```bash
terraform plan -out=tfplan
```

Review the output — it will create:
- VPC with public + private subnets
- ECS Fargate cluster + service (2 tasks)
- Application Load Balancer
- RDS PostgreSQL 16 (encrypted, automated backups)
- ElastiCache Serverless Redis
- CloudWatch log group
- SSM Parameter Store secrets

### 3.4 Apply
```bash
terraform apply tfplan
```
This takes ~10-15 minutes (RDS is the slowest resource).

### 3.5 Save outputs
```bash
terraform output
```
Note:
- `alb_dns_name` → use for DNS CNAME
- `ecr_repository_url` → confirm it matches Phase 1.5
- `rds_endpoint` → database hostname

---

## Phase 4: First Deployment

### 4.1 Push code to trigger CI/CD
```bash
git add -A
git commit -m "deploy: ready for AWS deployment"
git push origin main
```

### 4.2 Monitor GitHub Actions
```
GitHub repo → Actions → "Deploy to AWS ECS" workflow
```
Watch for:
1. **Build** job — compiles and tests
2. **Deploy** job — builds Docker image, pushes to ECR, deploys to ECS

### 4.3 Verify deployment
```bash
# Get the ALB DNS from terraform output
ALB_DNS=$(terraform -chdir=terraform output -raw alb_dns_name)

# Health check
curl http://$ALB_DNS/health

# Create a short link
curl -X POST http://$ALB_DNS/api/v1/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://aws.amazon.com","ttl":"30d"}'

# Test redirect
curl -v http://$ALB_DNS/<shortCode>
```

---

## Phase 5: Post-Deployment

### 5.1 DNS Setup (optional)
```
Route 53 → Hosted zone → Create record
- Type: A (Alias)
- Alias target: Application Load Balancer
- Name: short.yourdomain.com
```

Then update `CreateLinkResponse.BASE_URL` in `src/main/java/com/shortly/dto/CreateLinkResponse.java`:
```java
private static final String BASE_URL = "https://short.yourdomain.com/";
```

### 5.2 SSL/TLS (optional)
```
AWS Certificate Manager → Request certificate
- Domain: short.yourdomain.com
- Validation: DNS
→ Add HTTPS listener to ALB (port 443)
```

### 5.3 CloudFront CDN (optional)
For caching redirect responses and reducing ALB load:
```
CloudFront → Create distribution
- Origin: ALB DNS
- Cache behavior: /{shortCode} → cache based on path
- Custom domain + ACM certificate
```

### 5.4 Set up monitoring
- **CloudWatch Alarms**: CPU > 80%, memory > 80%, 5xx errors > 0
- **RDS Performance Insights**: enable in RDS console
- **ElastiCache metrics**: CPU, cache hit rate, connections

---

## Phase 6: Ongoing Maintenance

### Monthly
```bash
# Refresh MaxMind GeoLite2 database
# (GitHub Actions cron runs this — see .github/workflows/deploy.yml)
# Or manually: download new .mmdb → rebuild Docker → redeploy
```

### Scaling
When traffic grows:
1. Update `desired_count` in `terraform/variables.tf` → `terraform apply`
2. Increase `cpu` / `memory` for Fargate tasks
3. Upgrade RDS instance class (`db.t3.small` → `db.t3.medium`)

### Cost estimate (monthly)
| Resource | Spec | Est. Cost |
|----------|------|-----------|
| ECS Fargate (2 tasks) | 0.25 vCPU, 0.5 GB | ~$25 |
| ALB | 1 LCU | ~$20 |
| RDS PostgreSQL | db.t3.micro, 20 GB | ~$15 |
| ElastiCache Serverless | 1 GB max | ~$15 |
| CloudWatch Logs | 5 GB | ~$5 |
| Data transfer | — | ~$5 |
| **Total** | | **~$85/month** |

---

## Quick Reference

| What | Where |
|------|-------|
| Terraform | `terraform/` (8 files) |
| CI/CD | `.github/workflows/deploy.yml` |
| Dockerfile | `Dockerfile` |
| App config | `src/main/resources/application-prod.yml` |
| MaxMind DB | `src/main/resources/GeoLite2-City.mmdb` (not committed) |

---

## Rollback

```bash
# If deployment breaks:
# 1. Revert the commit
git revert HEAD
git push origin main

# 2. Or force-deploy a specific image
aws ecs update-service \
  --cluster shortly-cluster \
  --service shortly-service \
  --force-new-deployment

# 3. Destroy everything (⚠️ destructive)
terraform -chdir=terraform destroy
```
