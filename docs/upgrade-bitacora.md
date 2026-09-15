# Bitácora del upgrade: Java 8 / Spring Boot 1.3 → Java 21 / Spring Boot 4.1

Registro cronológico del upgrade, pensado para poder reproducir cada paso o
volver atrás si algo falla. Cada hito termina con un comando de verificación
y su resultado real.

Convenciones:

- `JDK8`  = `/usr/lib/jvm/java-8-openjdk-amd64` (OpenJDK 1.8.0_502)
- `JDK21` = `/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/21.0.12-101.0.LTS/x64` (Temurin 21.0.12.1 LTS)
- Maven 3.6.3 (el del sistema; el wrapper `./mvnw` se añade en el Hito 1)
- Punto de partida: commit `87a6ce3` (PR #1, suite baseline de 107 tests).

---

## Decisión de versión objetivo

**Java 21 LTS + Spring Boot 4.1.1.**

- Java 21: LTS con el mayor rodaje en todo el ecosistema (Spring, Hibernate,
  Tomcat, Mockito/ByteBuddy, herramientas). Java 25 es LTS pero lleva pocos
  meses; el salto 21→25 después es cambiar `maven.compiler.release`.
- Spring Boot 4.1.1 (no 3.5.x): verificado en la API pública de soporte de
  Spring (`https://api.spring.io/projects/spring-boot/generations`):

  | Línea | GA | Fin soporte OSS | Fin soporte comercial |
  |---|---|---|---|
  | 3.5.x | 2025-05-31 | **2026-06-30 (vencido)** | 2032-06-30 |
  | 4.0.x | 2025-11-30 | 2026-12-31 | 2027-12-31 |
  | 4.1.x | 2026-06-30 | **2027-07-31** | 2028-07-31 |

  Boot 4.1.1 se publicó el 2026-08-20 (>3 semanas antes de este upgrade).

---

## Hito 0 — Estado inicial ("antes")

Fecha: 2026-09-15. Rama: `devin/1789488413-java21-upgrade` creada desde `87a6ce3`.

### Toolchain

```
$ /usr/lib/jvm/java-8-openjdk-amd64/bin/java -version
openjdk version "1.8.0_502"
$ mvn -v
Apache Maven 3.6.3
```

`pom.xml`: `maven.compiler.source/target = 1.8`, BOM `spring-boot-dependencies:1.3.0.RELEASE`.

### Dependencias efectivas (extracto de `mvn dependency:list`, 78 artefactos compile/runtime)

| Componente | Versión "antes" |
|---|---|
| Spring Boot | 1.3.0.RELEASE |
| Spring Framework | 4.2.3.RELEASE |
| Spring Security | 4.0.3.RELEASE |
| Spring Data JPA | 1.9.1.RELEASE |
| Hibernate ORM | 4.3.11.Final |
| Hibernate Validator | 5.2.2.Final |
| Bean Validation API | javax.validation 1.1 |
| JPA API | javax.persistence (hibernate-jpa-2.1-api) |
| Servlet API | javax.servlet 3.1 (Tomcat 8.0.28) |
| Thymeleaf | 2.1.4.RELEASE (`thymeleaf-spring4`) |
| Thymeleaf extras Spring Security | `thymeleaf-extras-springsecurity4` 2.1.2.RELEASE |
| Thymeleaf layout dialect | 1.3.1 |
| Tomcat embebido | 8.0.28 |
| H2 | 1.4.190 |
| MySQL driver | `mysql:mysql-connector-java` 5.1.37 |
| JavaMail | `com.sun.mail:javax.mail` 1.5.4 |
| Jackson | 2.6.3 |
| commons-io | 2.3 |
| javax.el-api | 2.2.4 (optional) |
| JUnit | 4.12 |
| Mockito | 1.10.19 |
| spring-boot-maven-plugin | 1.3.0.RELEASE (admin) / **sin versión** (site → resolvía 4.2.0-M1 y fallaba con JDK 8) |
| maven-compiler-plugin / surefire | por defecto de Maven 3.6.3 (3.1 / 2.12.4) |

Lista completa: `docs/upgrade-dependencias-antes.txt`.

### Tests (JDK 8)

```
$ export JAVA_HOME=$JDK8 PATH=$JAVA_HOME/bin:$PATH
$ mvn clean install
jcart-core : Tests run: 43, Failures: 0, Errors: 0, Skipped: 1
jcart-admin: Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
jcart-site : Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS   (107 tests, 0 fallos, 1 ignorado preexistente)
```

Baseline de rendimiento (JDK 8), líneas `PERF` de la suite:

```
PERF core.findUserByEmail avg_ms=0.158 iterations=200
PERF core.getAllCategories avg_ms=0.486 iterations=200
PERF core.getAllProducts avg_ms=0.824 iterations=200
PERF core.getOrder avg_ms=0.230 iterations=200
PERF core.getProductBySku avg_ms=0.724 iterations=200
PERF core.searchProducts avg_ms=0.754 iterations=200
PERF site.GET /home avg_ms=5.947 iterations=100
PERF site.GET /myAccount (authenticated) avg_ms=5.029 iterations=100
PERF site.GET /products avg_ms=3.501 iterations=100
PERF site.GET /products/{sku} avg_ms=5.911 iterations=100
PERF site.POST /cart/items + GET /cart/items/count avg_ms=2.067 iterations=100
PERF site.POST /login (BCrypt) avg_ms=62.020 iterations=100
```

### Arranque (JDK 8)

- admin: `cd jcart-admin && mvn spring-boot:run` → `https://localhost:9443` (HTTP 9090 redirige a 9443).
- site: `cd jcart-site && mvn org.springframework.boot:spring-boot-maven-plugin:1.3.0.RELEASE:run` → `https://localhost:8443`.
- Con JDK 21 sin cambios: compila, **no arranca** (`InaccessibleObjectException` en CGLIB de Spring 4.2; con `--add-opens` falla después Hibernate Validator 5.2). Detalle en `docs/java21-diagnostico-y-plan.md`.

### Inventario de APIs legadas en `src/main` (lo que el upgrade debe tocar)

- `javax.persistence.*` (10 entidades), `javax.validation.*`, `javax.servlet.*`, `javax.mail.*`.
- `org.hibernate.validator.constraints.NotEmpty/Email` (deprecados → `jakarta.validation.constraints`).
- `WebSecurityConfigurerAdapter`, `authorizeRequests()/antMatchers()`, `@EnableGlobalMethodSecurity` (admin y site).
- `WebMvcConfigurerAdapter`, `EmbeddedServletContainerFactory`, `TomcatEmbeddedServletContainerFactory`,
  `org.springframework.boot.context.embedded.FilterRegistrationBean` (admin y site `WebConfig`).
- `org.thymeleaf.extras.springsecurity4.dialect.SpringSecurityDialect`, `ClassLoaderTemplateResolver.setTemplateMode("HTML5")`.
- Spring Data: `repository.findOne(id)`, `delete(id)`.
- Plantillas: 33 `layout:decorator`, 40 `layout:fragment`, 3 `layout:title-pattern` (sintaxis layout-dialect 1.x).
- Propiedades: `spring.datasource.initialize`, `server.ssl.keyStoreType`, `server.ssl.keyAlias`, driver `com.mysql.jdbc.Driver`.
- `GenerationType.AUTO` en `User`, `Role`, `Category`, `Permission` (Hibernate 4 lo resolvía como IDENTITY en H2/MySQL).
