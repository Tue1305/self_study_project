# Jenny's Nail Studio 💅 — Spring Boot JWT Authentication & Multi-Environment Platform

A full-stack RESTful application and DevOps platform built with **Spring Boot 3.2.5**, **Spring Security 6**, and **JSON Web Tokens (JWT)**. The application features stateless Bearer token authorization, an integrated single-page frontend, and an automated multi-environment CI/CD deployment pipeline on Ubuntu 24.04 LTS.

---

## ✨ Features

* **JWT Authentication**: Generates and validates signed Bearer tokens using `io.jsonwebtoken` (JJWT 0.11.5).
* **Spring Security 6 Integration**: Secures endpoints with stateless token authorization via a custom `JwtAuthenticationFilter`.
* **Built-in Dashboard**: Serves a clean single-page web interface directly through Spring Boot static resources (`src/main/resources/static/index.html`).
* **Multi-Environment Orchestration**: Isolated Production and Testing application environments using Docker Compose behind an Nginx reverse proxy.
* **Automated CI/CD**: Jenkins pipeline supporting automated builds, health check verification, and automatic rollback safeguards.
* **Database Management**: PostgreSQL persistent storage with visual management via pgAdmin 4.

---

## 🏗️ System Architecture & Subdomains

The platform is orchestrated using Docker Compose behind an Nginx reverse proxy running on the host VPS (`162.35.172.197`).

| Domain / Subdomain | Target Service | Internal Port | Environment |
| :--- | :--- | :--- | :--- |
| `bepnharua.com` | `app-production` | `8080` | Live Production |
| `test.bepnharua.com` | `app-testing` | `8082` | Staging / Testing |
| `jenkins.bepnharua.com` | `jenkins` | `8081` | CI/CD Automation Engine |
| `pgadmin.bepnharua.com` | `pgadmin` | `5050` | Database Management GUI |
| *N/A* | `postgres` | `5432` | PostgreSQL Database |

---

## 🛠️ Tech Stack

* **Backend**: Java 17, Spring Boot 3.2.5, Spring Security 6, Maven
* **Security**: JJWT (`0.11.5`)
* **Frontend**: HTML5, Bootstrap 5.3, JavaScript (Fetch API)
* **DevOps & Infrastructure**: Ubuntu 24.04 LTS, Docker & Docker Compose, Nginx, Certbot (SSL)
* **CI/CD Pipeline**: Jenkins Multibranch Pipeline

---

## 📂 Project Structure

```text
self_study_project/
├── Dockerfile                  # Container build definition for Spring Boot app
├── docker-compose.yml          # Multi-container orchestration (App, DB, Jenkins, pgAdmin)
├── Jenkinsfile                 # Declarative CI/CD pipeline script
├── pom.xml                     # Maven project dependencies
├── README.md                   # Project documentation
└── src/
    └── main/
        ├── java/com/example/demo/
        │   ├── AuthController.java            # REST endpoint definitions
        │   ├── JwtAuthenticationFilter.java   # Stateless token filter
        │   ├── JwtAuthResponse.java           # JWT DTO response wrapper
        │   ├── JwtTokenProvider.java          # Token generation & verification
        │   └── SecurityConfig.java            # Spring Security 6 bean configs
        └── resources/
            └── static/
                └── index.html                 # Bootstrap 5 Dashboard