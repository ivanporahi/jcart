package com.sivalabs.jcarttests;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StreamUtils;

import com.sivalabs.jcart.JCartAdminApplication;
import com.sivalabs.jcart.catalog.CatalogService;
import com.sivalabs.jcart.entities.Role;
import com.sivalabs.jcart.entities.User;
import com.sivalabs.jcart.orders.OrderService;
import com.sivalabs.jcart.security.SecurityService;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {JCartAdminApplication.class, AdminCsrfIntegrationTest.TestConfig.class})
@WebIntegrationTest(value = {"server.ssl.enabled=false", "spring.jpa.show-sql=false",
        "spring.datasource.url=jdbc:h2:mem:admin-csrf;DB_CLOSE_DELAY=-1"}, randomPort = true)
public class AdminCsrfIntegrationTest {
    private static final String EMAIL = "csrf-admin@example.test";
    private static final String PASSWORD = "csrf-test-password";
    private static final Pattern FORMS = Pattern.compile("<form\\b([^>]*)>(.*?)</form>", Pattern.DOTALL);
    private static final Pattern INPUTS = Pattern.compile("<input\\b[^>]*>");

    @Value("${local.server.port}") private int port;
    @Autowired private SecurityService securityService;
    @Autowired private CatalogService catalogService;
    @Autowired private OrderService orderService;
    @Autowired private PasswordEncoder passwordEncoder;
    private Client admin;

    @Configuration
    public static class TestConfig {
        @Bean
        public EmbeddedServletContainerFactory servletContainer() {
            return new TomcatEmbeddedServletContainerFactory();
        }
    }

    @Before
    public void login() throws Exception {
        if (securityService.findUserByEmail(EMAIL) == null) {
            User user = new User();
            user.setName("CSRF test administrator");
            user.setEmail(EMAIL);
            user.setPassword(passwordEncoder.encode(PASSWORD));
            user.setRoles(Arrays.asList(securityService.getRoleByName("ROLE_SUPER_ADMIN")));
            securityService.createUser(user);
        }
        admin = new Client();
        Response login = admin.get("/login");
        assertEquals(200, login.status);
        String token = tokenFor(login.body, "/login");
        assertEquals(302, admin.post("/login", "username=" + EMAIL + "&password=" + PASSWORD + "&_csrf=" + token).status);
        assertEquals(200, admin.get("/home").status);
    }

    @Test
    public void rejectsMissingAndInvalidTokensOnEveryAdminMutation() throws Exception {
        String orderNumber = orderService.getAllOrders().get(0).getOrderNumber();
        String[] paths = {"/users", "/users/1", "/roles", "/roles/1", "/orders/" + orderNumber,
                "/products", "/products/1", "/categories", "/categories/1"};
        for (String path : paths) {
            assertEquals(path, 403, admin.post(path, "name=forged").status);
            assertEquals(path, 403, admin.post(path, "name=forged&_csrf=invalid").status);
        }
    }

    @Test
    public void forgedSuperAdminCreationCannotPersistAUser() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String body = userFields(email);
        assertEquals(403, admin.post("/users", body).status);
        assertEquals(403, admin.post("/users", body + "&_csrf=invalid").status);
        Client otherSession = new Client();
        String otherToken = tokenFor(otherSession.get("/login").body, "/login");
        assertEquals(403, admin.post("/users", body + "&_csrf=" + otherToken).status);
        assertNull(securityService.findUserByEmail(email));
    }

    @Test
    public void legitimateUserCreationWithTheRenderedTokenStillWorks() throws Exception {
        String token = tokenFor(admin.get("/users/new").body, "/users");
        String email = UUID.randomUUID() + "@example.test";
        Response response = admin.post("/users", userFields(email) + "&_csrf=" + token);
        assertEquals(302, response.status);
        assertTrue(response.location.endsWith("/users"));
        User created = securityService.findUserByEmail(email);
        assertNotNull(created);
        assertEquals("ROLE_SUPER_ADMIN", created.getRoles().get(0).getName());
        assertTrue(passwordEncoder.matches(PASSWORD, created.getPassword()));
    }

    @Test
    public void thymeleafAddsOneTokenToEveryAdministrativePostForm() throws Exception {
        String orderNumber = orderService.getAllOrders().get(0).getOrderNumber();
        String[] pages = {"/users/new", "/users/1", "/roles/new", "/roles/1",
                "/orders/" + orderNumber, "/products/new", "/products/1", "/categories/new", "/categories/1"};
        for (String page : pages) {
            Response response = admin.get(page);
            assertEquals(page, 200, response.status);
            Matcher forms = FORMS.matcher(response.body);
            int postForms = 0;
            while (forms.find()) {
                if ("post".equalsIgnoreCase(attribute(forms.group(1), "method"))) {
                    assertNotNull(page, tokenIn(forms.group(2)));
                    postForms++;
                }
            }
            assertEquals(page + " must render its mutation and logout forms", 2, postForms);
            assertNotNull(tokenFor(response.body, "/logout"));
            assertFalse(response.body.contains("href=\"/logout\""));
        }
    }

    @Test
    public void publicAuthenticationFormsAlsoRenderAndRequireTokens() throws Exception {
        Client anonymous = new Client();
        for (String path : new String[]{"/login", "/forgotPwd"}) {
            Response response = anonymous.get(path);
            assertEquals(200, response.status);
            assertNotNull(tokenFor(response.body, path));
            assertEquals(403, anonymous.post(path, "email=" + EMAIL).status);
        }
        String resetToken = securityService.resetPassword(EMAIL);
        Response reset = anonymous.get("/resetPwd?email=" + EMAIL + "&token=" + resetToken);
        assertEquals(200, reset.status);
        assertNotNull(tokenFor(reset.body, "/resetPwd"));
        assertEquals(403, anonymous.post("/resetPwd", "email=" + EMAIL).status);
    }

    @Test
    public void logoutRequiresPostAndAValidToken() throws Exception {
        String token = tokenFor(admin.get("/home").body, "/logout");
        admin.get("/logout");
        assertEquals(200, admin.get("/home").status);
        assertEquals(403, admin.post("/logout", "").status);
        assertEquals(403, admin.post("/logout", "_csrf=invalid").status);
        assertEquals(200, admin.get("/home").status);
        Response logout = admin.post("/logout", "_csrf=" + token);
        assertEquals(302, logout.status);
        assertTrue(logout.location.endsWith("/login?logout"));
        Response home = admin.get("/home");
        assertEquals(302, home.status);
        assertTrue(home.location.endsWith("/login"));
    }

    @Test
    public void realMultipartProductRequestsRequireTheTokenInTheBody() throws Exception {
        String token = tokenFor(admin.get("/products/new").body, "/products");
        String sku = "csrf-" + UUID.randomUUID();
        for (String path : new String[]{"/products", "/products/1"}) {
            assertEquals(403, admin.multipart(path, sku, null).status);
            assertEquals(403, admin.multipart(path, sku, "invalid").status);
        }
        assertNull(catalogService.getProductBySku(sku));
        Response created = admin.multipart("/products", sku, token);
        assertEquals(302, created.status);
        assertTrue(created.location.endsWith("/products"));
        assertNotNull(catalogService.getProductBySku(sku));

        Response edit = admin.multipart("/products/1", "", token);
        assertEquals(200, edit.status);
        assertNotNull(tokenFor(edit.body, "/products/1"));
    }

    private String userFields(String email) {
        Role role = securityService.getRoleByName("ROLE_SUPER_ADMIN");
        return "name=CSRF+test&email=" + email + "&password=" + PASSWORD + "&roles[0].id=" + role.getId();
    }

    private static String attribute(String html, String name) {
        Matcher matcher = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String tokenFor(String html, String action) {
        Matcher forms = FORMS.matcher(html);
        while (forms.find()) {
            if (action.equals(attribute(forms.group(1), "action"))) {
                String token = tokenIn(forms.group(2));
                assertNotNull("Missing CSRF token for " + action, token);
                return token;
            }
        }
        throw new AssertionError("Missing form for " + action);
    }

    private static String tokenIn(String form) {
        Matcher inputs = INPUTS.matcher(form);
        String token = null;
        while (inputs.find()) {
            if ("_csrf".equals(attribute(inputs.group(), "name"))) {
                assertNull("Duplicate CSRF input", token);
                assertEquals("hidden", attribute(inputs.group(), "type"));
                token = attribute(inputs.group(), "value");
                assertNotNull(token);
                assertFalse(token.isEmpty());
            }
        }
        return token;
    }

    private class Client {
        private String cookie;

        Response get(String path) throws Exception {
            return request("GET", path, null, null);
        }

        Response post(String path, String body) throws Exception {
            return request("POST", path, "application/x-www-form-urlencoded", body);
        }

        Response multipart(String path, String sku, String token) throws Exception {
            String boundary = "CsrfBoundary" + UUID.randomUUID();
            StringBuilder body = new StringBuilder();
            String categoryId = catalogService.getAllCategories().get(0).getId().toString();
            String[][] fields = {{"name", "CSRF product"}, {"sku", sku}, {"price", "10.00"}, {"categoryId", categoryId}};
            for (String[] field : fields) {
                part(body, boundary, field[0], field[1]);
            }
            if (token != null) {
                part(body, boundary, "_csrf", token);
            }
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"image\"; filename=\"\"\r\n")
                    .append("Content-Type: image/jpeg\r\n\r\n\r\n--").append(boundary).append("--\r\n");
            return request("POST", path, "multipart/form-data; boundary=" + boundary, body.toString());
        }

        private void part(StringBuilder body, String boundary, String name, String value) {
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"")
                    .append(name).append("\"\r\n\r\n").append(value).append("\r\n");
        }

        private Response request(String method, String path, String contentType, String body) throws Exception {
            HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod(method);
                if (cookie != null) {
                    connection.setRequestProperty("Cookie", cookie);
                }
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", contentType);
                    byte[] bytes = body.getBytes("UTF-8");
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try (java.io.OutputStream output = connection.getOutputStream()) {
                        output.write(bytes);
                    }
                }
                int status = connection.getResponseCode();
                List<String> cookies = connection.getHeaderFields().get("Set-Cookie");
                if (cookies != null) {
                    for (String value : cookies) {
                        if (value.startsWith("JSESSIONID=")) {
                            cookie = value.split(";", 2)[0];
                        }
                    }
                }
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String responseBody = "";
                if (stream != null) {
                    try (InputStream input = stream) {
                        responseBody = new String(StreamUtils.copyToByteArray(input), "UTF-8");
                    }
                }
                return new Response(status, responseBody, connection.getHeaderField("Location"));
            } finally {
                connection.disconnect();
            }
        }
    }

    private static class Response {
        final int status;
        final String body;
        final String location;

        Response(int status, String body, String location) {
            this.status = status;
            this.body = body;
            this.location = location;
        }
    }
}
