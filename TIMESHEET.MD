#  Engineering Log & Technical Timesheet

## Developer: Tue Tran

## Project: Jenny's Nail Studio💅 Full-Stack Application

### Architecture: Hybrid Development Model (Local Spring Boot + Remote Dockerized PostgreSQL on Linux VPS)

#### Phase 1: Project Scaffolding & Core Architecture Design

**Frontend & Static Asset Development:**

* Created the initial layout and structure using HTML, CSS, and client-side scripts to serve as the user-facing interface for Jenny's Nail Studio booking and service catalog.

* Organized static resources (src/main/resources/static and templates) to handle rendering, styling, and client-side interactions.

**Backend Java Initialization:**

* Initialized the core Java Spring Boot backend structure.

* Designed domain models, REST controllers, and service layers to handle business logic, data transfer objects (DTOs), and repository interfaces.

**Version Control Setup:**

* Initialized local Git repository, created an enterprise-standard .gitignore file to safely exclude build artifacts (target/), local IDE configurations (.idea/, .vscode/), and sensitive application secrets.

#### Phase 2: Domain Management, Hosting Exploration, & Network Security

**GoDaddy & Web Services Integration:**

* Investigated external web hosting and domain registration workflows via GoDaddy to plan public-facing deployment routes.

* Evaluated cloud provider options, DNS configurations, and SSL certificate requirements for secure HTTP traffic (HTTPS).

**Port Management & Inbound/Outbound Networking:**

* Configured VPS firewall rules and security groups to manage inbound traffic across essential ports (e.g., HTTP/HTTPS traffic, SSH management, and application-specific ports like 8080 and 8081).

* Mapped out networking paths for internal container communication versus external client access.

#### Phase 3: Containerization & Infrastructure Setup (Docker)

**Dockerfile Implementation:**

* Developed a multi-stage Dockerfile leveraging Eclipse Temurin / OpenJDK base images to compile, package, and containerize the Java Spring Boot application into a lightweight, isolated image (app-demo).

**Docker Compose Orchestration (docker-compose.yml):**

* Structured a robust docker-compose.yml file to manage multi-container deployments on the remote Linux VPS.

* Defined isolated service blocks, environment variables, restart policies (unless-stopped), and persistent named volumes (postgres_data) to prevent data loss on container termination.

**Ecosystem Management Tools:**

* Deployed and configured Portainer (portainer/portainer-ce) for visual container monitoring and lifecycle management.

* Deployed pgAdmin (dpage/pgadmin4) behind a web proxy port to provide a browser-based graphical user interface for relational database management and query execution.

#### Phase 4: CI/CD Pipeline Automation (Jenkinsfile)

**Jenkins Automation Server Setup:**

* Integrated Jenkins (jenkins/jenkins:lts) into the remote VPS infrastructure to manage continuous integration and deployment pipelines.

**Pipeline Scripting (Jenkinsfile):**

* Wrote a declarative Jenkinsfile to automate the software delivery lifecycle:

* Checkout: Pulling the latest source code revisions from the remote Git repository.

* Build: Executing Maven wrapper compilation (./mvnw clean package) to generate production-ready JAR artifacts.

* Test: Running automated unit and integration test suites.

* Containerization & Deploy: Triggering Docker image builds and updating running service containers automatically.

#### Phase 5: Dependency Management & Build Configuration (pom.xml)

**Maven Project Object Model Configuration:**

* Configured pom.xml utilizing the Spring Boot parent starter (version 3.2.5) with Java 17 compatibility.

**Dependency Conflict Resolution:**

* Identified and pruned redundant, duplicate starter declarations (spring-boot-starter-web and spring-boot-starter-data-jpa) that were inadvertently pasted during rapid scaffolding, eliminating Maven build warnings and classloader ambiguity.

**Database Driver Integration:**

* Added the runtime PostgreSQL JDBC driver (org.postgresql:postgresql) for production connectivity and retained H2 database support (com.h2database:h2) configured under runtime scope for lightweight local unit testing.

**Security & Authentication Dependencies:**

* Integrated Spring Security (spring-boot-starter-security) and JJWT libraries (jjwt-api, jjwt-impl, jjwt-jackson version 0.11.5) to establish stateless token-based authentication.

#### Phase 6: Hybrid Development Architecture & Database Tunneling

**Hybrid Workflow Engineering:**

* Designed a high-efficiency development workflow where active coding and rapid trial-and-error debugging occur locally on the developer's machine using IntelliJ, while persistence is maintained against a robust, production-mirroring PostgreSQL container hosted on the remote Linux VPS.

**Secure Connectivity & Configuration:**

* Reconfigured the VPS PostgreSQL container to explicitly bind and expose port 5432 (-p 5432:5432) to accommodate external database tooling and secure SSH tunneling.

* Updated local application.properties to target the remote database connection string, credentials, and connection pooling parameters (HikariCP).

#### Phase 7: Automated Deployment Scripting & Container Lifecycle Management

**Custom Deployment Script (deploy.sh):**

* Authored a dedicated bash deployment script on the VPS to streamline updates:

* Automatically pulls the latest commits from the remote GitHub main branch (git pull origin main).

* Builds the fresh Docker image tagged as latest.

* Prunes dangling, unused Docker build cache and images to conserve VPS disk space (docker image prune -f).

**Container Lifecycle & Conflict Mitigation:**

* Resolved common Docker runtime errors (such as Conflict: The container name is already in use).

* Upgraded the deploy.sh cleanup logic from a basic conditional check to robust force-removal handling (docker rm -f app-demo 2>/dev/null || true), ensuring seamless container replacement regardless of whether previous instances were running, stopped, or crashed.

**Shell Aliasing (deploy):**

* Registered a custom bash alias (alias deploy="...") in the VPS environment to reduce the full deployment pipeline execution down to a single keyword command (deploy).