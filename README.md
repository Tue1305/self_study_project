
# Spring Boot JWT Authentication API

A full-stack RESTful application built with Spring Boot, Spring Security, JSON Web Tokens (JWT), and Docker. The project features a secure authentication backend and a static single-page frontend interface.

---

## Features

* **JWT Authentication:** Generates and validates signed Bearer tokens using `io.jsonwebtoken` (JJWT).
* **Spring Security 6 Integration:** Secures endpoints with stateless token authorization using a custom filter (`JwtAuthenticationFilter`).
* **Built-in Frontend:** Hosts a clean single-page web dashboard directly via Spring Boot static resources.
* **Docker Support:** Fully containerized setup for consistent deployment environments.

---

## Tech Stack

* **Backend:** Java 17, Spring Boot 3.2.5, Spring Security, Maven
* **Security:** JJWT 0.11.5
* **Frontend:** HTML5, CSS3, JavaScript (Fetch API)
* **DevOps:** Docker

---

## Project Structure

```text
demo/
 ├── Dockerfile
 ├── pom.xml
 ├── README.md
 └── src/
      └── main/
           ├── java/com/example/demo/
           │    ├── AuthController.java
           │    ├── JwtAuthenticationFilter.java
           │    ├── JwtAuthResponse.java
           │    ├── JwtTokenProvider.java
           │    └── SecurityConfig.java
           └── resources/
                └── static/
                     └── index.html