# Por qué JCart "compila pero falla al arrancar" en JDK 21 — diagnóstico y plan

> Documento de análisis. **No se ha aplicado ningún cambio productivo.**

## 1. Síntoma reproducido

```bash
export JAVA_HOME=<jdk-21>
mvn -pl jcart-core test -Dtest=SeedDataTest
```

```
Tests run: 4, Failures: 0, Errors: 4
Caused by: java.lang.IllegalStateException: Cannot load configuration class: com.sivalabs.jcart.JCartCoreApplication
Caused by: java.lang.NoClassDefFoundError: Could not initialize class org.springframework.cglib.proxy.Enhancer
Caused by: java.lang.ExceptionInInitializerError
Caused by: java.lang.reflect.InaccessibleObjectException:
    Unable to make protected final java.lang.Class java.lang.ClassLoader.defineClass(...) accessible:
    module java.base does not "opens java.lang" to unnamed module
```

Lo mismo ocurre al hacer `java -jar jcart-admin-1.0.jar`: la JVM arranca, Spring empieza a
crear el contexto y muere en el primer `@Configuration`.

## 2. Por qué compila

`maven.compiler.source/target = 1.8`. El `javac` de JDK 21 sigue aceptando `-source 8 -target 8`
(con warning de deprecación) y el código no usa nada eliminado del JDK a nivel de *compilación*
(los paquetes `javax.*` que usa vienen de jars de terceros, no del JDK). Por eso `mvn compile`
pasa: la compilación no ejecuta Spring.

## 3. Por qué falla en tiempo de ejecución

Cadena causal, de arriba abajo:

1. Spring Boot 1.3.0 → Spring Framework **4.2.3** (nov. 2015). Anterior a Java 9 (2017).
2. Spring 4.2 crea proxies de clases `@Configuration` con **CGLIB repaquetizado**
   (`org.springframework.cglib.proxy.Enhancer`).
3. Ese CGLIB define clases en runtime llamando por reflexión a
   `ClassLoader.defineClass(...)`, que es `protected`. Para eso hace `setAccessible(true)`.
4. Desde Java 9 (JPMS, JEP 261) el paquete `java.lang` de `java.base` **no está abierto**
   a la reflexión profunda. En Java 9–15 solo era un warning ("illegal reflective access");
   desde **Java 16** (JEP 396) el acceso se deniega por defecto y en **Java 17+** (JEP 403)
   ya no existe la opción `--illegal-access=permit` para relajarlo.
5. `setAccessible` lanza `InaccessibleObjectException` dentro del inicializador estático de
   `Enhancer` → `ExceptionInInitializerError` → la clase queda inutilizable
   (`NoClassDefFoundError: Could not initialize class`) → Spring no puede cargar
   `JCartCoreApplication` → el contexto no arranca.

En resumen: **no es un bug de JCart; es que Spring 4.2 es anterior al sistema de módulos.**
Spring Framework añadió soporte a Java 9+ en 5.0/5.1 (CGLIB usa `MethodHandles.Lookup.defineClass`
cuando está disponible).

## 4. ¿Vale el workaround `--add-opens`? Verificado: NO

```bash
mvn -pl jcart-core test -Dtest=SeedDataTest \
    -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED"
```

```
Caused by: javax.validation.ValidationException: Unable to instantiate Configuration.
Caused by: java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1
```

El `--add-opens` supera CGLIB pero la siguiente capa cae: **Hibernate Validator 5.2.2** parsea
`java.version` esperando `1.x.y` y explota con `"21"`. Detrás vendrían, por experiencia con
este stack: Hibernate ORM 4.3 (javassist / `sun.misc.Unsafe`), Tomcat 8.0 (deprecaciones de
`java.security`), Jackson 2.6, DevTools con classloader propio, etc. Es un juego de golpear
topos: cada flag abre otro fallo, y aunque se consiguiera arrancar, seguiríamos en un stack
sin soporte desde 2016 corriendo en modo "acceso ilegal tolerado". **Descartado como solución;**
solo sirve como demostración diagnóstica.

## 5. Conclusión

Subir el JDK **obliga** a subir Spring Boot. No hay un camino "solo JDK". Las dos rutas
razonables:

| Ruta | Destino | Pros | Contras |
|---|---|---|---|
| A. Directo | JDK 21 + **Spring Boot 3.3/3.4** (Spring 6, Security 6, Hibernate 6, `jakarta.*`, Thymeleaf 3) | Un solo salto; stack soportado; compatible con JDK 25 después | Mayor diff (javax→jakarta, Security DSL, JUnit 5) |
| B. Escalonado | 1.3 → 2.7 (JDK 8/11) → 3.x (JDK 17/21) | Errores más acotados en cada paso | Doble trabajo; Boot 2.x ya está EOL; el paso 2.7→3 sigue siendo el grande |

**Recomendación: Ruta A**, en una rama, validando con la suite de línea base en cada hito.
El proyecto es pequeño (73 clases, ~4.800 líneas) y la Ruta B no reduce el riesgo real, solo
lo reparte. Java 25 (LTS, sept. 2025) es viable como destino final con Spring Boot ≥ 3.4;
recomiendo migrar a **21** primero y saltar a 25 como paso separado y trivial una vez verde.

## 6. Plan de modernización (a ejecutar SOLO tras aprobación)

Cada hito termina con `mvn clean install` verde (o con los fallos esperados documentados) y
un diff del `grep '^PERF'` contra `baseline-java8.txt`.

### Hito 0 – Preparación (sin tocar el stack)
- [ ] Rama `modernization/java21`.
- [ ] Guardar `baseline-java8.txt` (salida `PERF` y conteo de tests: 107/0/0).
- [ ] Añadir Maven Wrapper (`mvn wrapper:wrapper -Dmaven=3.9.x`) para fijar Maven ≥ 3.6.3 (Boot 3 lo exige).
- [ ] Fijar versión del `spring-boot-maven-plugin` en `jcart-site` (hoy sin versión → resuelve 4.x y falla con JDK 8) y añadir `repackage` (hoy el jar del site no es ejecutable).

### Hito 1 – Build: JDK 21 + Spring Boot 3.x parent
- [ ] `pom.xml` raíz: `<parent>` `spring-boot-starter-parent` 3.3.x/3.4.x (o BOM), `java.version=21`, quitar `maven.compiler.source/target=1.8`.
- [ ] Borrar versiones explícitas que ahora gestiona el BOM (`javax.el-api`, Thymeleaf extras, etc.).
- [ ] Sustituir dependencias renombradas:
  - `spring-boot-starter-thymeleaf` mantiene nombre; añadir `thymeleaf-extras-springsecurity6` y `thymeleaf-layout-dialect` 3.x.
  - `mysql:mysql-connector-java` → `com.mysql:mysql-connector-j`.
  - `com.sun.mail:javax.mail` → `spring-boot-starter-mail` (Jakarta Mail 2 / `org.eclipse.angus`).
  - `spring-boot-starter-validation` explícito (ya no viene con `starter-web`).
  - `commons-io` a 2.16+.
  - H2 → 2.2/2.3 (gestionado por BOM).
- [ ] `javax.*` → `jakarta.*` en todo `src/main` y `src/test`: `javax.persistence`, `javax.validation`, `javax.servlet`, `javax.mail`. (OpenRewrite `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3` hace la mayor parte de este hito automáticamente y es lo que sugiero usar.)
- [ ] `mvn clean compile` verde. Criterio de salida: compila. Los tests aún no.

### Hito 2 – Persistencia (Spring Data 3 + Hibernate 6 + H2 2)
- [ ] Repositorios: `findOne(id)` → `findById(id).orElse(null)` (9 usos en core); revisar `@Query` con JPQL.
- [ ] `spring.jpa.hibernate.ddl-auto=update` + `data.sql`: en Boot ≥ 2.5 `data.sql` corre **antes** que Hibernate cree el esquema. Poner `spring.jpa.defer-datasource-initialization=true` y `spring.sql.init.mode=always` (sustituye a `spring.datasource.initialize`, que también usan las clases base de test).
- [ ] H2 2.x: palabras reservadas (`VALUE`, `KEY`, `USER`…) en entidades/`data.sql`; `now()` sigue válido. `CURRENT_TIMESTAMP` si hace falta.
- [ ] Hibernate 6: `@Type`, `@GenericGenerator` si hubiera; tipos numéricos en `Order.getTotalAmount()`; `LazyInitializationException` puede aparecer donde antes no (Open-Session-In-View sigue activo por defecto en Boot, pero **avisa**: `spring.jpa.open-in-view=true` explícito).
- [ ] MySQL: `spring.jpa.database-platform` ya no necesario; revisar `application-prod.properties`.
- [ ] Criterio: `mvn -pl jcart-core test` verde (42 tests) tras adaptar tests a JUnit 5 (ver Hito 5).

### Hito 3 – Seguridad (Spring Security 6)
- [ ] Eliminar `WebSecurityConfigurerAdapter` en admin y site → `@Bean SecurityFilterChain`.
- [ ] `antMatchers` → `requestMatchers`; `authorizeRequests()` → `authorizeHttpRequests()`; lambdas DSL.
- [ ] `@EnableGlobalMethodSecurity(securedEnabled=true)` → `@EnableMethodSecurity(securedEnabled=true)`.
- [ ] `configureGlobal(AuthenticationManagerBuilder)` → exponer `UserDetailsService` + `PasswordEncoder` como beans (Boot arma el `DaoAuthenticationProvider`).
- [ ] **Decisión CSRF**: hoy `csrf().disable()` en ambas apps. Opción conservadora para el upgrade: mantener deshabilitado (tests `csrfIsDisabled*` siguen verdes) y activar en un PR posterior con `CookieCsrfTokenRepository` + token en los `fetch`/jQuery del carrito. Recomiendo separar.
- [ ] `BCryptPasswordEncoder`: compatible con los hashes `$2a$10$` de `data.sql`; nada que migrar.
- [ ] `accessDeniedPage("/403")`, `loginProcessingUrl`, `logoutRequestMatcher` → equivalentes; `AntPathRequestMatcher` sigue existiendo (deprecado en 6.x, ok).
- [ ] **Hallazgo de seguridad** (`SiteSecurityTest.orderDetailPagesAreNotProtected_characterization`): `/orders/{n}` y `/orderconfirmation` anónimos y sin chequeo de propietario. Decidir si se corrige en este PR (`.requestMatchers("/orders/**","/orderconfirmation").authenticated()` + filtro por cliente en `OrderController`) o en uno aparte. Recomiendo aparte, para que el upgrade sea comportamiento-neutral.
- [ ] Criterio: `AdminSecurityTest` + `SiteSecurityTest` verdes (o con cambios de test **justificados** en el commit).

### Hito 4 – Web / Thymeleaf 3 / Tomcat 10
- [ ] `WebMvcConfigurerAdapter` → `implements WebMvcConfigurer`.
- [ ] Thymeleaf 3 + layout-dialect 3: `layout:decorator` → `layout:decorate="~{layout}"` (66 ocurrencias en 33 plantillas); `th:inline="text"` y expresiones `#httpServletRequest` obsoletas; `LEGACYHTML5` no existe (verificar HTML bien formado o usar `spring.thymeleaf.mode=HTML`).
- [ ] `thymeleaf-extras-springsecurity4` → `springsecurity6` (`sec:authorize` igual).
- [ ] Tomcat 10 (`jakarta.servlet`): `HttpServletRequest` en controladores; `NestedServletException` desaparece de Spring 6 (afecta a `AdminFunctionalTest.unknownOrderEditPageBlowsUpInTemplate`).
- [ ] Jackson 2.17: `@RequestBody Product` / `LineItem` en `CartController` — verificar que los DTOs deserializan igual (entidades JPA como body es frágil; funcionará, pero anotar como deuda).
- [ ] `spring.mvc.pathmatch` – en Boot 3 el matcher es `PathPatternParser`; `/products/images/{productId}` y `/orders/{orderNumber}` no usan sufijos, sin impacto esperado.
- [ ] `server.ssl.*` con el keystore existente; `server.port` igual.
- [ ] Devtools: mantener, es compatible.
- [ ] Criterio: `AdminFunctionalTest` + `SiteFunctionalTest` verdes; arranque manual `java -jar` de admin y site con navegación básica.

### Hito 5 – Tests (JUnit 5 + Boot Test 3)
- [ ] `junit-vintage` **no**: migrar directo a Jupiter (la tabla de equivalencias está en `docs/baseline-tests.md`). Cambios concentrados en las 3 clases base.
- [ ] Mockito 1.10 → 5.x (BOM): `EmailServiceTest` usa API básica, sin cambios previstos.
- [ ] Umbrales `MAX_AVG_MS` intactos; comparar `PERF` contra baseline.
- [ ] `spring.datasource.initialize=true` → `spring.sql.init.mode=always` en las clases base.

### Hito 6 – Verificación y cierre
- [ ] `mvn clean install` verde: **107 tests / 0 fallos** (o lista explícita de tests modificados con motivo).
- [ ] `diff baseline-java8.txt baseline-java21.txt`: ningún endpoint > 2× salvo justificación (esperado: BCrypt ~igual, arranque algo más lento en frío, endpoints iguales o mejores).
- [ ] Prueba manual: login admin, crear categoría, listar productos; site: registrar, carrito, checkout, ver orden.
- [ ] Actualizar README (JDK 21, `./mvnw`, comandos de arranque).
- [ ] (Opcional, PR separado) JDK 25: cambiar `java.version=25`, rerun. Con Boot 3.4+ no se esperan cambios de código.
- [ ] (PRs separados, post-upgrade) corregir los bugs caracterizados: edición de producto, 404 de orden, duplicado por nombre, IDOR de órdenes, activar CSRF.

### Riesgos principales y mitigación
| Riesgo | Prob. | Mitigación |
|---|---|---|
| Thymeleaf 2→3 rompe plantillas silenciosamente (render 200 con HTML incompleto) | Alta | `SiteFunctionalTest`/`AdminFunctionalTest` afirman contenido concreto de cada vista |
| `data.sql` no carga o carga antes del DDL | Alta | `SeedDataTest` cuenta filas exactas; `defer-datasource-initialization` |
| Security 6 cambia rutas públicas/privadas o el nombre de vista de login | Media | `*SecurityTest` |
| `LazyInitializationException` por cambios en OSIV/Hibernate 6 | Media | Tests web recorren relaciones en la vista |
| Regresión de rendimiento por Hibernate 6 (N+1 en `home`) | Baja | `PERF` diff |
| Emails: Jakarta Mail cambia `MimeMessageHelper` | Baja | `EmailServiceTest` |

### Estimación
Una sesión de trabajo para Hitos 0–5 (OpenRewrite acelera 1 y 2), más media sesión para
Hito 6 y ajustes de plantillas. JDK 25 y los bugs caracterizados: PRs cortos posteriores.
