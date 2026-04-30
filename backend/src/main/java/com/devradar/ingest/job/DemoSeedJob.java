package com.devradar.ingest.job;

import com.devradar.domain.Source;
import com.devradar.domain.SourceItem;
import com.devradar.domain.SourceItemTag;
import com.devradar.repository.InterestTagRepository;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import com.devradar.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class DemoSeedJob {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSeedJob.class);

    private final SourceItemRepository itemRepo;
    private final SourceItemTagRepository tagRepo;
    private final InterestTagRepository interestTagRepo;
    private final SourceRepository sourceRepo;

    public DemoSeedJob(SourceItemRepository itemRepo, SourceItemTagRepository tagRepo,
                       InterestTagRepository interestTagRepo, SourceRepository sourceRepo) {
        this.itemRepo = itemRepo;
        this.tagRepo = tagRepo;
        this.interestTagRepo = interestTagRepo;
        this.sourceRepo = sourceRepo;
    }

    public int run() {
        Map<String, Long> tagIndex = new HashMap<>();
        interestTagRepo.findAll().forEach(t -> tagIndex.put(t.getSlug(), t.getId()));

        Source hnSource = sourceRepo.findByCode("HN").orElse(null);
        Source ghsaSource = sourceRepo.findByCode("GHSA").orElse(null);
        Source ghReleasesSource = sourceRepo.findByCode("GH_RELEASES").orElse(null);
        if (hnSource == null) {
            LOG.warn("demo-seed: HN source not found, aborting");
            return 0;
        }

        Instant now = Instant.now();
        int inserted = 0;

        record SeedItem(String externalId, String title, String description, String url,
                        String author, int hoursAgo, String sourceCode, List<String> tagSlugs) {}

        List<SeedItem> seeds = List.of(
            new SeedItem("demo-hn-001",
                "Java 24 Officially Released with Structured Concurrency and Scoped Values",
                "Oracle has released Java 24 with major improvements to virtual threads, structured concurrency as a final feature, scoped values, and the new Stream Gatherers API. Performance benchmarks show 40% improvement in high-concurrency workloads.",
                "https://openjdk.org/projects/jdk/24", "openjdk", 6, "HN",
                List.of("java", "performance", "backend")),

            new SeedItem("demo-hn-002",
                "Spring Boot 3.5 Brings Native GraalVM Support and Improved Observability",
                "Spring Boot 3.5 released with first-class GraalVM native image support, OpenTelemetry auto-configuration, and a new declarative HTTP client. Startup time reduced to under 100ms for native builds.",
                "https://spring.io/blog/2026/04/spring-boot-3-5", "spring_team", 12, "HN",
                List.of("spring_boot", "java", "observability", "backend")),

            new SeedItem("demo-hn-003",
                "React 20 Introduces Server Actions and Improved Suspense Boundaries",
                "React 20 ships with stable Server Actions, improved Suspense boundaries for streaming SSR, and a new useFormState hook. The React compiler now handles 95% of memoization automatically.",
                "https://react.dev/blog/2026/04/react-20", "react_team", 18, "HN",
                List.of("react", "javascript", "typescript", "frontend")),

            new SeedItem("demo-hn-004",
                "PostgreSQL 18 Released with Built-in Vector Search and JSON Schema Validation",
                "PostgreSQL 18 adds native vector similarity search (pgvector built-in), JSON schema validation constraints, and parallel VACUUM improvements. Migration guide available for existing pgvector users.",
                "https://www.postgresql.org/about/news/postgresql-18-released/", "postgresql", 24, "HN",
                List.of("postgres", "database", "ai_tooling")),

            new SeedItem("demo-hn-005",
                "TypeScript 5.8 Adds Pattern Matching and Improved Type Inference",
                "TypeScript 5.8 introduces match expressions (pattern matching), improved generic inference, and 30% faster type checking. The new satisfies operator gets additional overload support.",
                "https://devblogs.microsoft.com/typescript/announcing-typescript-5-8/", "typescript_team", 30, "HN",
                List.of("typescript", "javascript", "frontend")),

            new SeedItem("demo-hn-006",
                "MySQL 9.2 Introduces Transparent Read Replicas and Improved JSON Performance",
                "MySQL 9.2 adds transparent read replica routing, 3x faster JSON operations, and automatic index recommendations. InnoDB now supports instant column drops without table rebuild.",
                "https://dev.mysql.com/blog-archive/mysql-9-2/", "mysql_team", 36, "HN",
                List.of("mysql", "database", "performance")),

            new SeedItem("demo-hn-007",
                "Show HN: Building a Real-Time Dashboard with React and Spring Boot WebSockets",
                "Built a real-time monitoring dashboard using React 20 with Server Components, Spring Boot WebSocket STOMP, and MySQL for persistence. Open sourced with full Docker Compose setup. 850 points.",
                "https://github.com/example/realtime-dashboard", "developer42", 42, "HN",
                List.of("react", "spring_boot", "mysql", "docker", "frontend", "backend")),

            new SeedItem("demo-hn-008",
                "Python 3.14 Performance: 2x Faster with the New JIT Compiler",
                "Python 3.14 ships with an experimental JIT compiler that doubles execution speed for CPU-bound workloads. The free-threaded build (no GIL) is now production-ready for multicore applications.",
                "https://www.python.org/downloads/release/python-3140/", "python_dev", 48, "HN",
                List.of("python", "performance")),

            new SeedItem("demo-hn-009",
                "Migrating from REST to GraphQL: Lessons from a Large-Scale Java Backend",
                "How we migrated our Java Spring Boot backend serving 50M requests/day from REST to GraphQL. Covers schema design, N+1 prevention with DataLoader, and performance tuning with query complexity analysis.",
                "https://engineering.example.com/rest-to-graphql", "techblog", 54, "HN",
                List.of("java", "spring_boot", "graphql", "api_design", "backend")),

            new SeedItem("demo-hn-010",
                "State of JavaScript 2026: TypeScript Adoption Reaches 89%, React Still Dominates",
                "Annual survey results: TypeScript used by 89% of respondents, React at 62% framework share, Bun overtakes npm in new projects. Server Components adoption jumped from 12% to 45% year over year.",
                "https://stateofjs.com/2026", "stateofjs", 60, "HN",
                List.of("javascript", "typescript", "react", "frontend")),

            new SeedItem("demo-hn-011",
                "How We Reduced Our PostgreSQL Query Latency by 90% with Proper Indexing",
                "Deep dive into PostgreSQL query optimization: partial indexes, covering indexes, and BRIN indexes for time-series data. Reduced P99 latency from 800ms to 80ms on our MySQL-to-PostgreSQL migration.",
                "https://engineering.example.com/pg-optimization", "dbteam", 72, "HN",
                List.of("postgres", "mysql", "database", "performance", "backend")),

            new SeedItem("demo-hn-012",
                "Docker Desktop 5.0 Released with Built-in Kubernetes and AI Assistant",
                "Docker Desktop 5.0 ships with integrated Kubernetes cluster management, an AI-powered Dockerfile generator, and 50% reduced memory footprint. Free tier now includes 5 environments.",
                "https://www.docker.com/blog/docker-desktop-5-0/", "docker", 78, "HN",
                List.of("docker", "kubernetes", "devops", "ai_tooling")),

            new SeedItem("demo-hn-013",
                "AWS Lambda Now Supports Java 24 Runtime with SnapStart Improvements",
                "AWS Lambda adds Java 24 managed runtime with improved SnapStart reducing cold starts to under 200ms. New features include response streaming for Java and automatic CRaC checkpoint support.",
                "https://aws.amazon.com/blogs/compute/java-24-lambda/", "aws_compute", 84, "HN",
                List.of("java", "aws", "backend", "performance")),

            new SeedItem("demo-hn-014",
                "Tailwind CSS v4.2: Container Queries and Dynamic Color Schemes",
                "Tailwind CSS v4.2 adds first-class container query support, dynamic P3 color scheme generation, and a new grid subgrid utility. Build times improved by 40% with the new Rust-based compiler.",
                "https://tailwindcss.com/blog/tailwindcss-v4-2", "tailwind", 90, "HN",
                List.of("tailwind", "frontend", "rust")),

            new SeedItem("demo-hn-015",
                "Next.js 16 Ships with Built-in Edge Database and Improved ISR",
                "Next.js 16 introduces a built-in edge-compatible database (powered by SQLite + libSQL), improved Incremental Static Regeneration, and on-demand ISR with webhooks. Vercel deployment is optional.",
                "https://nextjs.org/blog/next-16", "vercel_team", 96, "HN",
                List.of("next_js", "react", "typescript", "vercel", "frontend")),

            new SeedItem("demo-hn-016",
                "Show HN: Open Source LLM-Powered Code Review for Java and Python",
                "Built an open-source code review tool that uses local LLMs to analyze Java and Python code for security vulnerabilities, performance issues, and best practice violations. Integrates with GitHub Actions.",
                "https://github.com/example/llm-review", "oss_dev", 102, "HN",
                List.of("java", "python", "ai_tooling", "llm", "security", "github_actions")),

            new SeedItem("demo-hn-017",
                "Redis 8.0 Released with Native Search, JSON, and Time Series Built-in",
                "Redis 8.0 merges Redis Stack modules into core: RediSearch, RedisJSON, and RedisTimeSeries are now built-in. New cluster auto-scaling and memory-optimized data structures included.",
                "https://redis.io/blog/redis-8-0/", "redis_team", 108, "HN",
                List.of("redis", "database", "performance")),

            new SeedItem("demo-hn-018",
                "Kubernetes 1.33 Makes Service Mesh Optional with Built-in mTLS",
                "Kubernetes 1.33 adds native mutual TLS between pods, built-in traffic management policies, and gateway API goes GA. Service meshes like Istio become optional for basic security patterns.",
                "https://kubernetes.io/blog/2026/04/kubernetes-1-33/", "k8s_team", 114, "HN",
                List.of("kubernetes", "security", "devops", "microservices")),

            new SeedItem("demo-hn-019",
                "GraalVM 24 Achieves Java-to-Native Performance Parity for Spring Boot Applications",
                "GraalVM 24 reaches performance parity with JIT for Spring Boot workloads. Native images now support full reflection, dynamic proxies, and JMX monitoring. Memory usage reduced by 60% vs JVM.",
                "https://www.graalvm.org/release-notes/24/", "graalvm", 120, "HN",
                List.of("java", "spring_boot", "performance", "backend")),

            new SeedItem("demo-hn-020",
                "The Hidden Cost of Microservices: When a Monolith is the Right Choice",
                "Analysis of 50 companies that moved from microservices back to monoliths. Key finding: teams under 20 developers see 3x higher velocity with well-structured monoliths. Spring Boot modulith pattern recommended.",
                "https://blog.example.com/microservices-cost", "architect", 126, "HN",
                List.of("microservices", "spring_boot", "backend")),

            new SeedItem("demo-ghsa-001",
                "CVE-2026-32145: Critical RCE in Spring Framework Expression Language",
                "A critical remote code execution vulnerability in Spring Expression Language (SpEL) allows unauthenticated attackers to execute arbitrary code via crafted HTTP requests. Affects Spring Framework 6.0-6.2.3. Patch available in 6.2.4. CVSS 9.8.",
                "https://github.com/advisories/GHSA-xxxx-xxxx-0001", "ghsa_bot", 8, "GHSA",
                List.of("spring_boot", "spring", "java", "security")),

            new SeedItem("demo-ghsa-002",
                "CVE-2026-28910: SQL Injection in MySQL Connector/J PreparedStatement",
                "MySQL Connector/J versions 8.0.0-8.4.0 contain a SQL injection vulnerability when using allowMultiQueries=true with PreparedStatement. Attackers can escape parameter boundaries. Upgrade to 8.4.1. CVSS 8.1.",
                "https://github.com/advisories/GHSA-xxxx-xxxx-0002", "ghsa_bot", 20, "GHSA",
                List.of("mysql", "java", "security", "database")),

            new SeedItem("demo-ghsa-003",
                "CVE-2026-31002: Prototype Pollution in Popular React State Management Library",
                "A prototype pollution vulnerability in zustand v4.x allows attackers to modify Object.prototype through crafted state updates. Affects all React applications using zustand < 4.5.6. CVSS 7.5.",
                "https://github.com/advisories/GHSA-xxxx-xxxx-0003", "ghsa_bot", 32, "GHSA",
                List.of("react", "javascript", "security", "frontend")),

            new SeedItem("demo-ghsa-004",
                "CVE-2026-29877: Path Traversal in Python FastAPI File Upload Handler",
                "FastAPI's UploadFile handler allows path traversal when filename is used directly for storage. Affects FastAPI < 0.115.3. Attackers can overwrite arbitrary files. CVSS 8.8.",
                "https://github.com/advisories/GHSA-xxxx-xxxx-0004", "ghsa_bot", 44, "GHSA",
                List.of("python", "fastapi", "security", "backend")),

            new SeedItem("demo-ghsa-005",
                "CVE-2026-33210: Authentication Bypass in PostgreSQL pg_hba.conf Parsing",
                "PostgreSQL 15.x-17.x contains an authentication bypass when certain pg_hba.conf patterns are used with SCRAM-SHA-256. Malformed connection strings can bypass auth checks. CVSS 9.1.",
                "https://github.com/advisories/GHSA-xxxx-xxxx-0005", "ghsa_bot", 56, "GHSA",
                List.of("postgres", "security", "database")),

            new SeedItem("demo-ghsa-006",
                "CVE-2026-30456: XSS via TypeScript Template Literal Types in Build Output",
                "TypeScript compiler versions 5.4-5.7 can produce JavaScript output containing unescaped HTML when processing certain template literal types with string interpolation. CVSS 6.1.",
                "https://github.com/advisories/GHSA-xxxx-xxxx-0006", "ghsa_bot", 68, "GHSA",
                List.of("typescript", "javascript", "security", "frontend")),

            new SeedItem("demo-rel-001",
                "Spring Boot 3.4.5 Released — Security Patches and Dependency Updates",
                "Spring Boot 3.4.5 maintenance release includes fixes for 12 CVEs, upgraded Jackson to 2.18.2, and improved Docker image layering. Recommended upgrade for all 3.4.x users.",
                "https://github.com/spring-projects/spring-boot/releases/tag/v3.4.5", "spring_releases", 16, "GH_RELEASES",
                List.of("spring_boot", "java", "security", "docker")),

            new SeedItem("demo-rel-002",
                "React 19.1.0: Concurrent Features Stabilized",
                "React 19.1 stabilizes use() hook, async Server Components, and the React compiler. Includes performance fixes for Suspense hydration and improved error boundaries.",
                "https://github.com/facebook/react/releases/tag/v19.1.0", "react_releases", 40, "GH_RELEASES",
                List.of("react", "javascript", "typescript", "frontend")),

            new SeedItem("demo-rel-003",
                "MySQL 8.4.3 LTS: Critical Security Fixes",
                "MySQL 8.4.3 LTS release addresses 8 security vulnerabilities including the Connector/J SQL injection fix. InnoDB performance improvements for large table operations included.",
                "https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-3.html", "mysql_releases", 64, "GH_RELEASES",
                List.of("mysql", "database", "security")),

            new SeedItem("demo-hn-021",
                "Show HN: Full-Stack Type Safety from Database to React with TypeScript and Prisma",
                "Demonstrating end-to-end type safety: PostgreSQL schema → Prisma generates types → tRPC procedures → React Query hooks. Zero runtime type errors in production for 6 months.",
                "https://github.com/example/typesafe-stack", "fullstack_dev", 132, "HN",
                List.of("typescript", "react", "postgres", "frontend", "backend")),

            new SeedItem("demo-hn-022",
                "GitHub Copilot Workspace Now Supports Java Spring Boot Project Generation",
                "GitHub Copilot Workspace adds Spring Boot project scaffolding: generates entities, repositories, REST controllers, and tests from natural language descriptions. Supports Java 21+ and Kotlin.",
                "https://github.blog/copilot-workspace-spring-boot", "github", 138, "HN",
                List.of("java", "spring_boot", "ai_tooling", "llm", "testing")),

            new SeedItem("demo-hn-023",
                "Bun 2.0 Released: Full Node.js Compatibility and Built-in SQLite",
                "Bun 2.0 achieves 100% Node.js API compatibility, adds built-in SQLite database, and introduces Bun.serve() with HTTP/3 support. Package install times now 10x faster than npm.",
                "https://bun.sh/blog/bun-2.0", "bun_team", 144, "HN",
                List.of("javascript", "typescript", "sqlite", "performance")),

            new SeedItem("demo-hn-024",
                "How Cloudflare Replaced Their Java Microservices with Rust and Saved 80% on Compute",
                "Cloudflare engineering shares their migration story: Java Spring Boot microservices replaced with Rust + Actix Web. Memory usage dropped from 2GB to 400MB per service, latency improved by 5x.",
                "https://blog.cloudflare.com/java-to-rust", "cloudflare_eng", 150, "HN",
                List.of("java", "rust", "spring_boot", "actix", "microservices", "cloudflare", "performance")),

            new SeedItem("demo-hn-025",
                "The Complete Guide to React Server Components with Spring Boot Backend",
                "Tutorial: building a full-stack app with React Server Components fetching data directly from a Spring Boot API. Covers authentication, data fetching patterns, and deployment on AWS.",
                "https://tutorial.example.com/rsc-spring", "educator", 156, "HN",
                List.of("react", "spring_boot", "java", "aws", "frontend", "backend")),

            new SeedItem("demo-hn-026",
                "Python Overtakes JavaScript as Most Used Language on GitHub in 2026",
                "GitHub's annual Octoverse report shows Python surpassing JavaScript for the first time, driven by AI/ML adoption. TypeScript grows 40% YoY, Rust enters top 10.",
                "https://github.blog/octoverse-2026", "github", 162, "HN",
                List.of("python", "javascript", "typescript", "rust", "ai_tooling")),

            new SeedItem("demo-hn-027",
                "Terraform 2.0 Preview: Native Kubernetes Provider and Drift Detection",
                "HashiCorp previews Terraform 2.0 with a native Kubernetes provider (replacing the community one), continuous drift detection, and import blocks for existing infrastructure.",
                "https://www.hashicorp.com/blog/terraform-2-0-preview", "hashicorp", 168, "HN",
                List.of("terraform", "kubernetes", "devops", "aws", "gcp"))
        );

        for (SeedItem seed : seeds) {
            Source source = switch (seed.sourceCode) {
                case "HN" -> hnSource;
                case "GHSA" -> ghsaSource;
                case "GH_RELEASES" -> ghReleasesSource;
                default -> hnSource;
            };
            if (source == null) source = hnSource;

            if (itemRepo.existsBySourceIdAndExternalId(source.getId(), seed.externalId)) {
                LOG.debug("demo-seed: skip existing {}", seed.externalId);
                continue;
            }

            SourceItem si = new SourceItem();
            si.setSourceId(source.getId());
            si.setExternalId(seed.externalId);
            si.setUrl(seed.url);
            si.setTitle(seed.title);
            si.setDescription(seed.description);
            si.setAuthor(seed.author);
            si.setPostedAt(now.minus(seed.hoursAgo, ChronoUnit.HOURS));
            si.setRawPayload("{}");
            SourceItem saved = itemRepo.save(si);

            for (String slug : seed.tagSlugs) {
                Long tagId = tagIndex.get(slug);
                if (tagId != null) {
                    tagRepo.save(new SourceItemTag(saved.getId(), tagId));
                }
            }
            inserted++;
        }

        LOG.info("demo-seed: inserted {} items", inserted);
        return inserted;
    }
}
