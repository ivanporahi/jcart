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
jcart-core : Tests run: 42, Failures: 0, Errors: 0, Skipped: 1
jcart-admin: Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
jcart-site : Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
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

---

## Hito 1 — POMs, namespace y APIs de framework (commit `c6918cf`)

Objetivo: que `mvn compile` pase con JDK 21 y Spring Boot 4.1.1. Solo código productivo.

### Cambios y por qué

| Cambio | Por qué era necesario |
|---|---|
| `pom.xml`: `java.version=21`, `maven.compiler.release=21`; BOM `spring-boot-dependencies:4.1.1`; plugins `compiler 3.15.0`, `surefire 3.5.6`, `jar 3.5.1`, `resources 3.5.0`; `pluginManagement` de `spring-boot-maven-plugin` con `repackage`. | Boot 1.3 + Spring 4.2 no arrancan en JDK ≥ 9 (CGLIB vs JPMS). Surefire 2.x no ejecuta JUnit 5. El plugin de Boot sin versión en `jcart-site` resolvía un milestone incompatible y el jar de site no era ejecutable. |
| `jcart-core/pom.xml`: `+spring-boot-starter-validation`; `mysql-connector-java` → `com.mysql:mysql-connector-j` (runtime); `-hibernate-validator` explícito, `-javax.el-api`. | En Boot 2+ la validación ya no viene en `starter-web`. Connector/J cambió de coordenadas (8.0.31+). Validator y EL los gestiona el BOM (`hibernate-validator 9.1.3`, `expressly`). |
| `jcart-admin/site/pom.xml`: `starter-web` → `starter-webmvc`; `thymeleaf-extras-springsecurity4` → `springsecurity6`; `+starter-webmvc-test`, `+spring-security-test`; `-commons-io.version` local. | Boot 4 renombró los starters web y de test. La extra de Thymeleaf para Security 6/7 es `springsecurity6`. `commons-io` se centraliza en el padre (Boot no lo gestiona). |
| `javax.persistence/validation/servlet/mail.*` → `jakarta.*`; `org.hibernate.validator.constraints.{NotEmpty,Email}` → `jakarta.validation.constraints.*`. | Spring 6+/Boot 3+ solo soportan Jakarta EE 9+. Las constraints de Hibernate fueron eliminadas en Validator 7+. |
| Spring Data: `findOne(id)` → `findById(id).orElse(null)`; `new Sort(DESC, "createdOn")` → `Sort.by(...)`. | Eliminados en Spring Data 2.0 (`CrudRepository` devuelve `Optional`). Se conserva la semántica "null si no existe" que consumen servicios/controladores/tests. |
| `WebSecurityConfigurerAdapter` → beans `SecurityFilterChain`, `AuthenticationManager` (`ProviderManager` + `DaoAuthenticationProvider(userDetailsService)`), `PasswordEncoder`; `authorizeRequests/antMatchers` → `authorizeHttpRequests/requestMatchers`; `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity(securedEnabled=true)`. | Adapter eliminado en Security 6. Las reglas de rutas, `csrf().disable()`, login/logout y `accessDeniedPage` se copiaron 1:1 (verificado por los tests de seguridad de baseline). |
| `EmbeddedServletContainerFactory` → `ServletWebServerFactory`, `TomcatEmbeddedServletContainerFactory` → `TomcatServletWebServerFactory` (`org.springframework.boot.tomcat.servlet`), `FilterRegistrationBean` de `org.springframework.boot.web.servlet`; `WebMvcConfigurerAdapter` → implementar `WebMvcConfigurer`. `SecurityConstraint/Collection` desde `org.apache.tomcat.util.descriptor.web`. | Renombres de Boot 2 y re-empaquetado de Boot 4 (módulo `spring-boot-tomcat`). Se mantienen puertos: admin 9443/9090, site 8443/8080, con `CONFIDENTIAL` en `/*`. |
| Thymeleaf: `SpringSecurityDialect` de `springsecurity6`; `setTemplateMode("HTML")` (antes `HTML5`). | Thymeleaf 3 eliminó `HTML5`/`LEGACYHTML5`. |
| Plantillas (33 archivos): `layout:decorator="x"` → `layout:decorate="~{x}"`; `$DECORATOR_TITLE` → `$LAYOUT_TITLE`. | layout-dialect 3+ eliminó `decorator`; el token de título cambió de nombre. Comprobado en arranque real: títulos `JCart Admin - Products`, `QuilCart - Home`. |
| Propiedades: `spring.datasource.initialize=true/false` → `spring.sql.init.mode=always/never` + `spring.jpa.defer-datasource-initialization=true`; `server.ssl.keyStoreType/keyAlias` → `key-store-type/key-alias`; `com.mysql.jdbc.Driver` → `com.mysql.cj.jdbc.Driver`. | Renombres de Boot 2.5 (inicialización SQL) y Connector/J 8. `defer-datasource-initialization` hace que `data.sql` corra **después** de que Hibernate cree el esquema, como en Boot 1.3. |
| Entidades `User`, `Role`, `Category`, `Permission`: `GenerationType.AUTO` → `IDENTITY`. | Hibernate 5+ mapea `AUTO` a `SEQUENCE` (`hibernate_sequence`/`*_SEQ`), mientras el esquema/seed y MySQL usaban autoincrement. Con `IDENTITY` se preserva el esquema del "antes" (el resto de entidades ya usaban `IDENTITY`). |

### Fallos durante el hito y resolución

1. `package org.springframework.boot.web.servlet.server does not exist` — en Boot 4 `ServletWebServerFactory` vive en `org.springframework.boot.web.server.servlet`. Corregido el import.
2. `cannot find symbol findOne` en `SecurityService` (llamadas anidadas `permissionRepository.findOne(permission.getId())`) — el reemplazo masivo no las cubrió; corregidas a mano.
3. `mvn -o` fallaba por `maven-surefire-plugin:3.5.6` no cacheado — descarga puntual online (Maven Central directo responde 429; se usa el mirror `maven-central.storage-download.googleapis.com`).

Resultado: `mvn compile` **BUILD SUCCESS** en JDK 21.

---

## Hitos 2–5 — Persistencia, configuración, tests y Thymeleaf (commit `8d67879`)

### Tests → JUnit 5 / Spring Boot Test

- `org.junit.{Test,Before,Ignore,Assert}` → `org.junit.jupiter.api.{Test,BeforeEach,Disabled,Assertions}`; `@RunWith(SpringJUnit4ClassRunner)` + `@SpringApplicationConfiguration` → `@SpringBootTest(classes=...)`; `org.mockito.Matchers` → `ArgumentMatchers`.
- `@Test(expected=JCartException.class)` → `assertThrows(JCartException.class, () -> ...)`.
- Orden de argumentos de `assertTrue(mensaje, cond)` → `assertTrue(cond, mensaje)` (JUnit 5 invirtió la firma; con el orden antiguo no compila).
- **Ningún test se eliminó ni se relajó**: mismos 107 casos, mismas aserciones. Los únicos ajustes de aserción son de forma (ver abajo).

### Fallos, causa raíz y resolución (en orden cronológico)

| # | Módulo | Qué falló | Por qué | Cómo se resolvió | ¿Cambia comportamiento? |
|---|---|---|---|---|---|
| 1 | core | 6 errores `Unique index or primary key violation` al crear categoría/producto/usuario/rol en tests. | `data.sql` inserta IDs explícitos. H2 1.4 avanzaba la identidad al insertar un ID explícito; **H2 2.x no**, así que el siguiente `INSERT` autogenerado colisionaba con el seed (id=1). | `data.sql`: `ALTER TABLE <t> ALTER COLUMN id RESTART WITH <max+1>` para las 10 tablas con seed. | No. Solo garantiza el mismo estado inicial que en H2 1.4. |
| 2 | admin/site | Contexto no arranca: `InvalidConfigDataPropertyException: Property 'spring.profiles.active' imported from 'application-default.properties' is invalid in a profile specific resource`. | Boot 2.4+ prohíbe activar perfiles desde archivos específicos de perfil. Además `jcart-core/application.properties` colisionaba con el `application.properties` de cada app (Boot ya no fusiona dos `application.properties` del classpath de forma fiable). | `application-default.properties` → `application.properties` en admin y site con `spring.profiles.default=dev`; core: `application.properties` → `jcart-core.properties` importado con `spring.config.import=classpath:jcart-core.properties` (en main y test). | No. Perfil efectivo sigue siendo `dev` con H2 en memoria; `prod` sigue apuntando a MySQL. |
| 3 | admin/site | `AdminSecurityTest`/`SiteSecurityTest`: `Redirected URL '/login' does not match the expected URL pattern '**/login'`. | Security 4 redirigía con URL absoluta (`http://localhost/login`); Security 7 usa **URL relativa** (`/login`), y `**/login` no casa con una cadena que empieza en `/`. | Aserción → `header().string("Location", endsWith("/login"))`: valida lo mismo (redirección a login) y acepta ambas formas. | No (solo la forma de la URL de redirección). |
| 4 | admin | `AdminFunctionalTest.unknownOrder...` esperaba `NestedServletException`. | Clase eliminada en Spring 6; MockMvc propaga `jakarta.servlet.ServletException`. | Se desenvuelve la causa y se sigue exigiendo `TemplateProcessingException` (el defecto preexistente “orden desconocida → 500” queda caracterizado igual). | No. |
| 5 | site | 8 errores `TemplateProcessingException: Only variable expressions returning numbers or booleans are allowed in this context` en `home/products/category/product.html` (y `cart.html`, no cubierto por tests). | Thymeleaf 3.1 **prohíbe** concatenar Strings dentro de `th:onclick`/`th:onchange` (mitigación XSS). El código 1.x era `th:onclick="'javascript:addItemToCart(\'' + ${product.sku} + '\');'"`. | Patrón recomendado por Thymeleaf: `th:data-sku="${product.sku}" onclick="addItemToCart(this.getAttribute('data-sku'));"` (ídem `removeItemFromCart`, `updateCartItemQuantity(sku, this.value)`). Las funciones JS no cambian. | No funcional; el HTML generado cambia de `onclick="javascript:addItemToCart('P1001')"` a `data-sku="P1001" onclick="addItemToCart(this.getAttribute('data-sku'))"`. Verificado en arranque real (`POST /cart/items` → `{"count":1}`). |
| 6 | admin/site | En logs de test: `MailConnectException: Couldn't connect to host, port: localhost, 2525`. | No es un fallo de test: el registro de cliente / forgot-password intenta enviar correo real a `localhost:2525` (igual que en el baseline JDK 8, donde salía `javax.mail.MessagingException`). `EmailService` captura y loguea. | Sin cambio; comportamiento idéntico al "antes". | No. |

### Resultado de la suite en JDK 21 (`mvn clean install`)

```
$ java -version  → openjdk version "21.0.12.1" 2026-08-18 LTS
jcart-core : Tests run: 42, Failures: 0, Errors: 0, Skipped: 1   (mismo @Disabled preexistente)
jcart-admin: Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
jcart-site : Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS   (107 tests, 0 fallos)
```

Avisos no bloqueantes que quedan (documentados, no corregidos para no ampliar el alcance):
- `HHH90000033: deprecated annotation @Temporal` (Hibernate 7 recomienda `java.time`; las entidades siguen con `java.util.Date`).
- `spring.jpa.open-in-view is enabled by default` (mismo comportamiento que Boot 1.3, que también lo activaba).

---

## Hito 6 — Arranque real, performance y cierre

### Arranque (JDK 21) — jars ejecutables generados por `mvn clean install`

```
$ java -jar jcart-admin/target/jcart-admin-1.0.jar
Tomcat started on ports 9443 (https), 9090 (http)   Started JCartAdminApplication in 10.5 s
$ java -jar jcart-site/target/jcart-site-1.0.jar
Tomcat started on ports 8443 (https), 8080 (http)   Started JCartSiteApplication in 10.6 s
```

Verificación con `curl` (0 ERROR/Exception en logs):

| Petición | Resultado |
|---|---|
| `GET http://localhost:9090/home` | 302 → `https://localhost:9443/home` |
| `GET https://localhost:9443/home` anónimo | 302 → `/login`; `/login` 200 |
| `POST /login superadmin` → `GET /products` | 302 `/home`; título `JCart Admin - Products` |
| `GET http://localhost:8080/home` | 302 → `https://localhost:8443/home` |
| `GET https://localhost:8443/home` | 200, `QuilCart - Home`, botones con `data-sku="P1001"…` |
| `POST /cart/items {"sku":"P1001"}` → `GET /cart/items/count` | 200 / `{"count":1}` |
| `GET /myAccount` anónimo | 302 → `/login` |
| `GET /orders/{n}` anónimo | 200 (defecto preexistente, **conservado** a propósito; ver `docs/baseline-tests.md`) |

Ahora `cd jcart-site && mvn spring-boot:run` también funciona (antes requería invocar el plugin con versión explícita).

### Performance: antes (JDK 8) vs después (JDK 21) — misma máquina, una corrida cada una

| Métrica | JDK 8 / Boot 1.3 | JDK 21 / Boot 4.1 |
|---|---|---|
| core.findUserByEmail | 0.158 ms | 0.418 ms |
| core.getAllCategories | 0.486 ms | 1.294 ms |
| core.getAllProducts | 0.824 ms | 1.764 ms |
| core.getOrder | 0.230 ms | 0.889 ms |
| core.getProductBySku | 0.724 ms | 1.543 ms |
| core.searchProducts | 0.754 ms | 1.259 ms |
| site GET /home | 5.947 ms | 6.831 ms |
| site GET /myAccount (auth) | 5.029 ms | 7.843 ms |
| site GET /products | 3.501 ms | 5.560 ms |
| site GET /products/{sku} | 5.911 ms | 9.292 ms |
| site POST /cart/items + count | 2.067 ms | 2.574 ms |
| site POST /login (BCrypt) | 62.020 ms | 61.215 ms |

Lectura: todo sigue muy por debajo de los umbrales de la suite. Las operaciones JPA de sub-milisegundo aparecen ~2× más lentas en valor absoluto (0.2–1.8 ms); es el coste esperado de Hibernate 7 + Spring Data 4 (más capas, `show-sql` activo, JIT frío en 200 iteraciones) y no una regresión funcional. BCrypt es idéntico (dominado por el hash). Si se quisiera afinar, los candidatos son `spring.jpa.show-sql=false` y `spring.jpa.open-in-view=false`, fuera del alcance de este upgrade.

### Resumen antes vs después

| | Antes | Después |
|---|---|---|
| Java | 8 (`1.8.0_502`) | 21 LTS (`21.0.12.1`) |
| Spring Boot / Framework / Security / Data JPA | 1.3.0 / 4.2.3 / 4.0.3 / 1.9.1 | 4.1.1 / 7.0.9 / 7.1.1 / 4.1.1 |
| Hibernate ORM / Validator | 4.3.11 / 5.2.2 | 7.4.5 / 9.1.3 |
| Namespace EE | `javax.*` (JPA 2.1, Servlet 3.1, Mail 1.5, Validation 1.1) | `jakarta.*` (Persistence 3.2, Servlet 6.1, Mail 2.1 + Angus 2.0, Validation 3.1) |
| Thymeleaf / layout-dialect / extras security | 2.1.4 / 1.3.1 / springsecurity4 2.1.2 | 3.1.5 / 4.0.1 / springsecurity6 3.1.5 |
| Tomcat embebido | 8.0.28 | 11.0.24 |
| H2 / MySQL driver | 1.4.190 / `mysql-connector-java` 5.1.37 | 2.4.240 / `mysql-connector-j` 9.7.0 |
| Jackson | 2.6.3 (`com.fasterxml`) | 3.1.5 (`tools.jackson`) |
| JUnit / Mockito | 4.12 / 1.10.19 | Jupiter 6.0.3 / 5.23.0 |
| Plugins Maven | compiler 3.x (source/target 1.8), surefire 2.x, boot-plugin sin versión en site | compiler 3.15.0 (`release 21`), surefire 3.5.6, jar 3.5.1, resources 3.5.0, boot-plugin 4.1.1 con `repackage` en admin y site |
| Tests | 107 verdes (JDK 8) | 107 verdes (JDK 21), sin eliminar ni relajar ninguno |

Lista completa de dependencias: `docs/upgrade-dependencias-antes.txt` (78) y `docs/upgrade-dependencias-despues.txt` (140, incluye test scope).

### Cambios de comportamiento intencionales

Ninguno funcional. Los defectos caracterizados en el baseline (`/orders/{n}` anónimo, edición de producto que no persiste, orden desconocida → 500, CSRF desactivado) se **conservan** deliberadamente para que la comparación antes/después sea 1:1; corregirlos es trabajo posterior con sus tests actualizados.

### Cómo reproducir

```
export JAVA_HOME=<jdk21> PATH=$JAVA_HOME/bin:$PATH
mvn clean install                         # 107 tests
java -jar jcart-admin/target/jcart-admin-1.0.jar   # https://localhost:9443
java -jar jcart-site/target/jcart-site-1.0.jar     # https://localhost:8443
```
