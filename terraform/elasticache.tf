# ── ElastiCache Serverless (Redis) ──

resource "aws_security_group" "redis" {
  name        = "${var.app_name}-redis-sg"
  description = "Allow Redis from ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  tags = {
    Name = "${var.app_name}-redis-sg"
  }
}

resource "aws_elasticache_serverless_cache" "main" {
  engine = "redis"
  name   = "${var.app_name}-redis"

  cache_usage_limits {
    data_storage {
      maximum = 1
      unit    = "GB"
    }
    ecpu_per_second {
      maximum = 1000
    }
  }

  daily_snapshot_time      = "05:00"
  major_engine_version     = "7"
  snapshot_retention_limit = 1
  subnet_ids               = aws_subnet.private[*].id
  security_group_ids       = [aws_security_group.redis.id]
}
