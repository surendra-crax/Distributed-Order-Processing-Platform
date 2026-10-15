# 100% Free Cloud Deployment Guide

This guide explains how to deploy the **Distributed Order Processing Platform** (including PostgreSQL, Apache Kafka, Redis, and the Spring Boot application + Web UI) to the cloud using **100% free tiers** with **zero infrastructure cost**.

---

## 🏗️ Free Deployment Architecture Overview

```mermaid
graph TD
    User([User Browser]) -->|HTTPS| RenderApp[Render.com Free Web Service<br/>Spring Boot 3 + Live Dashboard]
    
    subgraph "Free Serverless Cloud Providers"
        RenderApp -->|JDBC SSL| NeonDB[(Neon.tech Serverless PostgreSQL<br/>0.5 GB Free ACID Storage)]
        RenderApp -->|SASL_SSL SCRAM-SHA-256| UpstashKafka[(Upstash Serverless Kafka<br/>10,000 Free Msgs/Day)]
        RenderApp -.->|TLS Fail-Open Cache| UpstashRedis[(Upstash Serverless Redis<br/>10,000 Free Commands/Day)]
    end
```

| Component | Free Provider | Free Tier Limits | Cost |
| :--- | :--- | :--- | :--- |
| **Relational DB** | [Neon.tech](https://neon.tech) | 0.5 GB storage, pooled SSL connections, serverless compute | **$0.00** |
| **Event Broker** | [Upstash Kafka](https://upstash.com) | 10,000 messages/day, multi-zone replication, Web UI | **$0.00** |
| **Distributed Cache**| [Upstash Redis](https://upstash.com) | 10,000 commands/day, TLS encryption | **$0.00** |
| **Application Hosting**| [Render.com](https://render.com) or [Railway.app](https://railway.app) | 750 free instance hours/month, automatic git-push deploy, public HTTPS | **$0.00** |

---

## Step 1: Create Free PostgreSQL Database on Neon (2 minutes)

1. Go to [https://neon.tech](https://neon.tech) and sign up using your GitHub account.
2. Click **Create Project**:
   - Project name: `order-processing-db`
   - Region: Select closest to you (e.g., `US East (Ohio)` or `Europe (Frankfurt)`)
3. Neon immediately displays your connection details. Copy the **Pooled connection string**. It looks like:
   ```text
   postgres://username:password@ep-cool-butterfly-123456-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
4. For Spring Boot JDBC format, convert it to:
   ```text
   jdbc:postgresql://ep-cool-butterfly-123456-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
   *Note: Flyway will automatically run `V1__init_schema.sql` and `V2__sample_seed_data.sql` on first boot!*

---

## Step 2: Create Free Serverless Kafka on Upstash (3 minutes)

1. Go to [https://upstash.com](https://upstash.com) and sign up with GitHub.
2. Select **Kafka** in the top navigation and click **Create Cluster**:
   - Cluster Name: `order-platform-kafka`
   - Region: Pick the same cloud region as Neon (e.g. `us-east-1` or `eu-central-1`)
   - Type: **Serverless (Single Zone)** (Free tier)
3. Once created, click on the **Topics** tab and create two topics:
   - `order.events` (Partitions: 3)
   - `order.events.DLT` (Partitions: 3)
4. Go to the **Details** tab and scroll to **Connection Configuration**:
   - Select the **Java** tab to see your credentials:
     - **Bootstrap Servers**: `flowing-ant-12345-us1-kafka.upstash.io:9092`
     - **Username**: `Zmxvd2luZy1hbnQt...`
     - **Password**: `OGQzYjU5M2...`

---

## Step 3: Create Free Serverless Redis on Upstash (1 minute)

1. In the same [Upstash Console](https://upstash.com), click **Redis** in the top navigation.
2. Click **Create Database**:
   - Database Name: `order-platform-redis`
   - Region: Match the same region as your Kafka cluster.
   - Primary: Free
3. Copy the **Endpoint** and **Password**:
   - Host: `flowing-ant-12345.upstash.io`
   - Port: `6379`
   - Password: `your-upstash-redis-password`
   - SSL: `true`

---

## Step 4: Deploy the Application on Render (4 minutes)

1. Go to [https://render.com](https://render.com) and log in with your GitHub account.
2. Click **New +** and select **Web Service**.
3. Select your repository: `surendra-crax/Distributed-Order-Processing-Platform`.
4. Configure service settings:
   - **Name**: `distributed-order-platform`
   - **Region**: Match your database region (e.g., `Ohio (US East)` or `Frankfurt (EU Central)`)
   - **Runtime**: **Docker** (Render will automatically detect your `Dockerfile`)
   - **Instance Type**: **Free**
5. Scroll down to **Environment Variables** and add the following keys:

| Environment Variable | Value Example | Explanation |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates production cloud profile |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-cool-123.neon.tech/neondb?sslmode=require` | Neon JDBC connection string |
| `SPRING_DATASOURCE_USERNAME`| `neondb_owner` | Neon database user |
| `SPRING_DATASOURCE_PASSWORD`| `your-neon-password` | Neon database password |
| `KAFKA_BOOTSTRAP_SERVERS` | `flowing-ant-12345-us1-kafka.upstash.io:9092` | Upstash Kafka broker endpoint |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` | Required for Upstash Kafka |
| `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-256` | SCRAM authentication mechanism |
| `KAFKA_SASL_JAAS_CONFIG` | `org.apache.kafka.common.security.scram.ScramLoginModule required username="UPSTASH_USER" password="UPSTASH_PASSWORD";` | Upstash JAAS credentials |
| `REDIS_HOST` | `flowing-ant-12345.upstash.io` | Upstash Redis hostname |
| `REDIS_PORT` | `6379` | Upstash Redis port |
| `REDIS_PASSWORD` | `your-upstash-redis-password` | Upstash Redis password |
| `REDIS_SSL_ENABLED` | `true` | Enables TLS encryption for Redis |

6. Click **Create Web Service**.
7. Render will pull your GitHub repository, build the Docker image, run database migrations, and boot the Spring Boot application!

---

## Step 5: Verification & Live Dashboard

Once Render finishes deploying (usually takes ~2-3 minutes), you will receive a public HTTPS URL:
`https://distributed-order-platform.onrender.com`

1. **Open the Live Web Dashboard**:
   - Navigate to `https://your-service.onrender.com/` in any browser.
   - You will see the cluster health pill: **Cluster UP (PostgreSQL)**.
   - The live product catalog will load all seeded inventory items.
2. **Open the OpenAPI / Swagger UI**:
   - Navigate to `https://your-service.onrender.com/swagger-ui.html`.
3. **Check Actuator Health Probes**:
   - `https://your-service.onrender.com/actuator/health`
4. **Place a Live Order**:
   - Go to the **Order Terminal & Idempotency Lab** tab on your live dashboard.
   - Submit an order. Watch the real-time order state transition from `PENDING` to `CONFIRMED` as Upstash Kafka consumes the outbox event!
   - Open your [Upstash Console](https://console.upstash.com/kafka) to view the live JSON message recorded on the `order.events` Kafka topic.
