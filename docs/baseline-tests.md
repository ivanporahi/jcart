# Suite de línea base (pre-modernización)

Suite de caracterización del comportamiento **actual** de JCart (Java 8 / Spring Boot 1.3.0).
Se ejecuta antes del upgrade, se vuelve a ejecutar después con el stack nuevo, y se comparan
resultados. **No modifica código productivo**: donde el comportamiento actual es un bug, el
test lo documenta tal cual (sufijo `_knownBug` / `_characterization`) para que el upgrade lo
preserve o lo cambie de forma deliberada, nunca por accidente.

## Cómo ejecutar

```bash
# JDK 8 (estado actual)
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
mvn clean install                       # suite completa, ~30 s
mvn -pl jcart-core  test                # solo un módulo
mvn -pl jcart-site  test -Dtest=SiteFunctionalTest

# Extraer la baseline de rendimiento para diff
mvn clean install | grep '^PERF' | sort > baseline-java8.txt
```

No requiere MySQL ni SMTP: los tests usan el perfil `dev` (H2 en memoria + `data.sql`) y apuntan
el correo a `localhost:2525` con timeout corto (el envío falla y la app lo tolera).

## Resultado con JDK 8 (2026-09-15, OpenJDK 1.8.0_502, Maven 3.6.3)

| Módulo | Tests | Fallos | Errores | Skipped |
|---|---|---|---|---|
| jcart-core | 42 | 0 | 0 | 1 (`@Ignore` preexistente) |
| jcart-admin | 31 | 0 | 0 | 0 |
| jcart-site | 34 | 0 | 0 | 0 |
| **Total** | **107** | **0** | **0** | **1** |

Antes de esta suite había 4 tests (3 activos), todos smoke de arranque de contexto.

## Qué cubre

### jcart-core (servicios + datos)
| Clase | Cobertura |
|---|---|
| `SeedDataTest` | `data.sql` cargado: 9 permisos, 4 roles, 5 usuarios, 3 categorías, 25 productos, 2 clientes, 1 orden; tablas join; hashes BCrypt `$2a$10$` |
| `CatalogServiceTest` | categorías/productos, búsqueda (nombre/SKU/descr.), crear/actualizar, duplicados. Bug documentado: `createProduct` valida duplicado por **nombre**, no por SKU |
| `SecurityServiceTest` | usuarios/roles/permisos, autoridades, ciclo reset de contraseña (token válido/inválido/email desconocido), crear/actualizar rol y usuario |
| `CustomerServiceTest` | clientes seed, órdenes por cliente, alta de cliente |
| `OrderServiceTest` | orden seed con dirección/pago/items, `createOrder` genera número y cascada, `updateOrder` solo cambia estado |
| `EmailServiceTest` | `JavaMailSender` mockeado: cabeceras, HTML, `MailException` → `JCartException` |
| `CorePerformanceBaselineTest` | 6 micro-benchmarks de servicio (200 iteraciones, umbral 50 ms) |

### jcart-admin (MockMvc + filtro real de Spring Security)
| Clase | Cobertura |
|---|---|
| `AdminSecurityTest` | rutas públicas (`/login` → vista `public/login`, forgot/reset), redirección a login, login OK/KO, logout, **CSRF deshabilitado**, BCrypt, superadmin, admin sin permisos → 403, usuario sin permisos, POST prohibido, página 403, estáticos |
| `AdminFunctionalTest` | CRUD categorías/productos/roles/usuarios con validaciones, órdenes (listar/actualizar estado), clientes, permisos, forgot-password. Bugs documentados: **editar producto nunca persiste** (`ProductFormValidator` rechaza el propio SKU como duplicado); `/orders/{n}` desconocido → excepción de plantilla (500) |

### jcart-site (MockMvc + filtro real)
| Clase | Cobertura |
|---|---|
| `CartTest` | unitario puro del carrito: add/incrementar, update, remove, clear, count, total, subtotal |
| `SiteSecurityTest` | páginas públicas, `/myAccount` `/checkout` `POST /orders` exigen login, login OK/KO, un usuario admin no puede entrar al site, logout, CSRF deshabilitado. **Hallazgo de seguridad documentado:** `/orders/{n}` y `/orderconfirmation` son anónimos y exponen cualquier orden por número (sin comprobación de propietario) |
| `SiteFunctionalTest` | home (3 categorías), listado (25), detalle, categoría, imágenes; REST de carrito completo y aislamiento por sesión; registro (encode BCrypt, duplicados, validación); checkout: validaciones conservan carrito, orden creada con items/direcciones/pago/estado NEW, **carrito eliminado de sesión**, confirmación, detalle y `myAccount` |
| `SitePerformanceBaselineTest` | 6 endpoints (100 iteraciones, umbral 200 ms) |

## Baseline de rendimiento (JDK 8, misma máquina; comparar orden de magnitud, no ms exactos)

```
PERF core.findUserByEmail          avg_ms=0.158  iterations=200
PERF core.getOrder                 avg_ms=0.230  iterations=200
PERF core.getAllCategories         avg_ms=0.486  iterations=200
PERF core.getProductBySku          avg_ms=0.724  iterations=200
PERF core.searchProducts           avg_ms=0.754  iterations=200
PERF core.getAllProducts           avg_ms=0.824  iterations=200
PERF site.POST /cart/items + GET /cart/items/count avg_ms=2.067 iterations=100
PERF site.GET /products            avg_ms=3.501  iterations=100
PERF site.GET /myAccount (auth)    avg_ms=5.029  iterations=100
PERF site.GET /products/{sku}      avg_ms=5.911  iterations=100
PERF site.GET /home                avg_ms=5.947  iterations=100
PERF site.POST /login (BCrypt)     avg_ms=62.020 iterations=100   # dominado por BCrypt strength 10
```

Arranque de contexto (log `Started ... in`): core ≈ 0.3–1.9 s, admin ≈ 3.4 s, site ≈ 2.7 s.
Build completo `mvn clean install`: ≈ 31 s.

## Comportamientos que el upgrade DEBE decidir explícitamente

Estos tests pasan hoy porque describen el estado actual; al migrar hay que elegir entre
conservar el comportamiento (test intacto) o corregirlo (cambiar test + código, en commit aparte):

1. `AdminFunctionalTest.updateProductViaWebIsRejectedBySkuValidator_knownBug` – edición de producto rota.
2. `AdminFunctionalTest.unknownOrderEditPageBlowsUpInTemplate` – 500 en vez de 404.
3. `CatalogServiceTest.createProductDuplicateCheckUsesNameNotSku` – duplicado por nombre.
4. `SiteSecurityTest.orderDetailPagesAreNotProtected_characterization` – IDOR en órdenes.
5. `*SecurityTest.csrfIsDisabled*` – CSRF apagado en admin y site (Spring Security 6 lo activa por defecto).
6. `AdminSecurityTest.loginPageIsPublic` – nombre de vista `public/login`.

## Cómo replicar tras el upgrade

Los tests usan API de test de Spring Boot 1.3 / JUnit 4. Con Spring Boot 3 la traducción es mecánica:

| Hoy | Después |
|---|---|
| `@RunWith(SpringJUnit4ClassRunner.class)` + `@SpringApplicationConfiguration(classes=X)` + `@WebAppConfiguration` | `@SpringBootTest(classes=X)` (+`@AutoConfigureMockMvc` opcional) |
| `@Test` `org.junit` | `org.junit.jupiter.api.Test`; `@Before` → `@BeforeEach` |
| `javax.servlet.Filter` | `jakarta.servlet.Filter` |
| `MockMvcBuilders.webAppContextSetup(wac).addFilters(springSecurityFilterChain)` | igual, o `.apply(springSecurity())` |
| `NestedServletException` (test 500) | `jakarta.servlet.ServletException` |
| `org.hibernate.validator.constraints.NotEmpty/Email` (si tests los usan) | `jakarta.validation.constraints.*` |

Las clases base (`AbstractCoreIntegrationTest`, `AbstractAdminWebTest`, `AbstractSiteWebTest`)
concentran toda la configuración; el resto de tests no debería necesitar más cambios que imports.
