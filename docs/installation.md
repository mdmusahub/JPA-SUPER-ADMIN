# Installation

## Requirements

- **Java 17 or newer.**
- **Spring Boot 4.1.x.** Spring Boot 3.x is not supported.
- **Spring Data JPA** (any JPA provider that Spring Boot 4.1 supports; the project is tested with Hibernate on H2, PostgreSQL and MySQL).
- A **servlet web application** (Spring MVC). WebFlux is not supported.

## Add the starter

The starter is on Maven Central. Add it to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.mdmusahub</groupId>
    <artifactId>springboot-super-admin</artifactId>
    <version>0.1.0</version>
    <type>pom</type>
</dependency>
```

!!! warning "Do not leave out `<type>pom</type>`"
    The starter is a **pom-only** artifact: it carries the list of libraries the console needs and no code of its own. Without `<type>pom</type>` Maven looks for a jar that does not exist and stops with *Could not find artifact io.github.mdmusahub:springboot-super-admin:jar:0.1.0*.

The starter brings the console's own libraries: the web stack, the two Spring Security libraries it signs in with, and the Thymeleaf library it renders its pages with. It does **not** bring JPA: your application already has it.

### What the starter does not do to your application

It does not set Thymeleaf up for your application. Your own views, template engine, view resolvers and `spring.thymeleaf.*` settings (or JSP, FreeMarker, Mustache) are neither used nor changed by the console, and if you have no Thymeleaf you do not get one. It also does not switch on Spring Boot's own security auto-configuration: a host that wants Spring Security for its own routes adds `spring-boot-starter-security` itself, and the console then joins the host's setup without changing any of its rules.

## Add the two required properties

```yaml
jpa:
  admin:
    username: admin
    password: ${ADMIN_PASSWORD}
```

Take the password from the environment or a secret store, and use at least 12 characters. If either property is missing, blank, or an environment variable that is not set, the application **stops at startup** and says which property to add. Under the `prod` profile a password shorter than 12 characters, or a very common one, also stops startup; under other profiles it logs a warning.

## Start the application

Start your application and open `http://localhost:8080/admin`. Sign in with the username and password you set. Every JPA entity of your application is in the sidebar.

Next: [configure](configuration.md) the console, learn [what it can do](using-the-console.md), and read [how it is protected](security.md) before you put it on a network.
