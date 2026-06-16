# ── Outputs ──

output "alb_dns_name" {
  description = "DNS name of the ALB (use this for DNS CNAME)"
  value       = aws_lb.app.dns_name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = aws_db_instance.main.endpoint
}

output "redis_endpoint" {
  description = "ElastiCache Serverless endpoint"
  value       = aws_elasticache_serverless_cache.main.endpoint[0].address
}

output "redis_port" {
  description = "ElastiCache Serverless port"
  value       = aws_elasticache_serverless_cache.main.endpoint[0].port
}

output "ecr_repository_url" {
  description = "ECR repository URL (used in CI/CD)"
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = aws_ecs_service.app.name
}
