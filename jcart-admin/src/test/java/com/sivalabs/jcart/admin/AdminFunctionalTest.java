package com.sivalabs.jcart.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.sivalabs.jcart.AbstractAdminWebTest;
import com.sivalabs.jcart.catalog.CatalogService;
import com.sivalabs.jcart.entities.Category;
import com.sivalabs.jcart.entities.OrderStatus;
import com.sivalabs.jcart.entities.Product;
import com.sivalabs.jcart.entities.Role;
import com.sivalabs.jcart.entities.User;
import com.sivalabs.jcart.orders.OrderService;
import com.sivalabs.jcart.security.SecurityService;

/**
 * End-to-end (controller + validator + service + Thymeleaf view) baseline for
 * the admin back-office, executed as super admin. Tests mutate the shared H2
 * database, so they use unique names and assert relative to what they created.
 */
public class AdminFunctionalTest extends AbstractAdminWebTest
{
	@Autowired CatalogService catalogService;
	@Autowired SecurityService securityService;
	@Autowired OrderService orderService;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired PlatformTransactionManager txManager;

	MockHttpSession session;

	private <T> T inTx(TransactionCallback<T> callback)
	{
		return new TransactionTemplate(txManager).execute(callback);
	}

	@BeforeEach
	public void loginAsSuperAdmin() throws Exception
	{
		session = login(SUPER_ADMIN, SUPER_ADMIN_PWD);
	}

	// ---------- categories ----------

	@Test
	public void listCategoriesRendersSeedData() throws Exception
	{
		mockMvc.perform(get("/categories").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/categories"))
				.andExpect(model().attribute("categories", Matchers.hasSize(Matchers.greaterThanOrEqualTo(3))))
				.andExpect(content().string(Matchers.containsString("Flowers")))
				.andExpect(content().string(Matchers.containsString("Toys")))
				.andExpect(content().string(Matchers.containsString("Birds")));
	}

	@Test
	public void createCategoryFlow() throws Exception
	{
		mockMvc.perform(get("/categories/new").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/create_category"));

		mockMvc.perform(post("/categories").session(session)
				.param("name", "WebCat").param("description", "created via mvc").param("displayOrder", "7"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/categories"))
				.andExpect(flash().attribute("info", "Category created successfully"));

		Category created = catalogService.getCategoryByName("WebCat");
		assertNotNull(created);
		assertEquals(Integer.valueOf(7), created.getDisplayOrder());

		// edit form + update
		mockMvc.perform(get("/categories/" + created.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/edit_category"))
				.andExpect(model().attributeExists("category"));

		mockMvc.perform(post("/categories/" + created.getId()).session(session)
				.param("id", created.getId().toString()).param("name", "WebCat")
				.param("description", "updated").param("displayOrder", "8").param("disabled", "true"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/categories"));

		Category updated = catalogService.getCategoryById(created.getId());
		assertEquals("updated", updated.getDescription());
		assertEquals(Integer.valueOf(8), updated.getDisplayOrder());
		assertTrue(updated.isDisabled());
	}

	@Test
	public void createCategoryValidationErrors() throws Exception
	{
		// @NotEmpty name
		mockMvc.perform(post("/categories").session(session).param("name", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/create_category"))
				.andExpect(model().attributeHasFieldErrors("category", "name"));
		// duplicate via CategoryValidator
		mockMvc.perform(post("/categories").session(session).param("name", "Flowers"))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/create_category"))
				.andExpect(model().attributeHasFieldErrorCode("category", "name", "error.exists"));
	}

	// ---------- products ----------

	@Test
	public void listProductsRendersSeedData() throws Exception
	{
		mockMvc.perform(get("/products").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("products/products"))
				.andExpect(model().attribute("products", Matchers.hasSize(Matchers.greaterThanOrEqualTo(25))))
				.andExpect(content().string(Matchers.containsString("Quilling Toy 1")));
	}

	@Test
	public void createProductFlow() throws Exception
	{
		Integer toysId = catalogService.getCategoryByName("Toys").getId();
		mockMvc.perform(get("/products/new").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("products/create_product"))
				.andExpect(model().attributeExists("categoriesList"));

		mockMvc.perform(post("/products").session(session)
				.param("sku", "WEB001").param("name", "Web Product").param("description", "d")
				.param("price", "19.99").param("categoryId", toysId.toString()))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/products"));

		Product created = catalogService.getProductBySku("WEB001");
		assertNotNull(created);
		assertEquals("Web Product", created.getName());
		assertEquals(0, created.getPrice().compareTo(new java.math.BigDecimal("19.99")));
		assertEquals("Toys", created.getCategory().getName());

		mockMvc.perform(get("/products/" + created.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("products/edit_product"));

	}

	@Test
	public void updateProductViaWebIsRejectedBySkuValidator_knownBug() throws Exception
	{
		// Characterization of a pre-existing bug: POST /products/{id} runs
		// ProductFormValidator, which rejects any SKU that already exists - including
		// the product's own SKU (sent read-only by edit_product.html). Editing a
		// product from the admin UI therefore never persists. Omitting the SKU fails
		// @NotEmpty instead. Keep as-is for the baseline; fix deliberately later.
		Product existing = catalogService.getProductBySku("P1005");
		java.math.BigDecimal originalPrice = existing.getPrice();
		Integer birdsId = catalogService.getCategoryByName("Birds").getId();

		mockMvc.perform(post("/products/" + existing.getId()).session(session)
				.param("id", existing.getId().toString()).param("sku", "P1005").param("name", "Quilling Toy 5")
				.param("price", "25.00").param("categoryId", birdsId.toString()))
				.andExpect(status().isOk())
				.andExpect(view().name("products/edit_product"))
				.andExpect(model().attributeHasFieldErrorCode("product", "sku", "error.exists"));

		mockMvc.perform(post("/products/" + existing.getId()).session(session)
				.param("id", existing.getId().toString()).param("name", "Quilling Toy 5")
				.param("price", "25.00").param("categoryId", birdsId.toString()))
				.andExpect(status().isOk())
				.andExpect(view().name("products/edit_product"))
				.andExpect(model().attributeHasFieldErrors("product", "sku"));

		Product unchanged = catalogService.getProductById(existing.getId());
		assertEquals(0, unchanged.getPrice().compareTo(originalPrice));
		assertEquals("Toys", unchanged.getCategory().getName());
	}

	@Test
	public void createProductValidationErrors() throws Exception
	{
		mockMvc.perform(post("/products").session(session).param("sku", "").param("name", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("products/create_product"))
				.andExpect(model().attributeHasFieldErrors("product", "sku", "name", "categoryId"));

		mockMvc.perform(post("/products").session(session)
				.param("sku", "P1001").param("name", "dup").param("price", "1").param("categoryId", "1"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("product", "sku", "error.exists"));
	}

	// ---------- orders ----------

	@Test
	public void listAndUpdateOrder() throws Exception
	{
		mockMvc.perform(get("/orders").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("orders/orders"))
				.andExpect(content().string(Matchers.containsString("1447737431927")));

		mockMvc.perform(get("/orders/1447737431927").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("orders/edit_order"))
				.andExpect(model().attributeExists("order"));

		// Status update also tries to send an email; SMTP is unreachable in tests and
		// the controller swallows JCartException, so the flow must still succeed.
		mockMvc.perform(post("/orders/1447737431927").session(session)
				.param("orderNumber", "1447737431927").param("status", "IN_PROCESS"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/orders"))
				.andExpect(flash().attribute("info", "Order updated successfully"));
		assertEquals(OrderStatus.IN_PROCESS, orderService.getOrder("1447737431927").getStatus());
	}

	@Test
	public void unknownOrderEditPageBlowsUpInTemplate() throws Exception
	{
		// Characterization: no 404 handling; the controller puts null into the model
		// and Thymeleaf fails evaluating "order.orderNumber" (surfaces as a 500 in prod).
		Exception e = assertThrows(Exception.class,
				() -> mockMvc.perform(get("/orders/does-not-exist").session(session)));
		Throwable root = e instanceof jakarta.servlet.ServletException ? e.getCause() : e;
		assertTrue(root instanceof org.thymeleaf.exceptions.TemplateProcessingException,
				"expected TemplateProcessingException but was " + root);
	}

	// ---------- customers ----------

	@Test
	public void listCustomersAndDetail() throws Exception
	{
		mockMvc.perform(get("/customers").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("customers/customers"))
				.andExpect(content().string(Matchers.containsString("sivaprasadreddy.k@gmail.com")));
		mockMvc.perform(get("/customers/1").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("customers/view_customer"));
	}

	// ---------- users / roles / permissions ----------

	@Test
	public void listPermissionsRolesUsers() throws Exception
	{
		mockMvc.perform(get("/permissions").session(session))
				.andExpect(status().isOk())
				.andExpect(model().attribute("permissions", Matchers.hasSize(9)));
		mockMvc.perform(get("/roles").session(session))
				.andExpect(status().isOk())
				.andExpect(model().attribute("roles", Matchers.hasSize(Matchers.greaterThanOrEqualTo(4))));
		mockMvc.perform(get("/users").session(session))
				.andExpect(status().isOk())
				.andExpect(model().attribute("users", Matchers.hasSize(Matchers.greaterThanOrEqualTo(5))));
	}

	@Test
	public void createRoleFlow() throws Exception
	{
		mockMvc.perform(get("/roles/new").session(session))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("permissionsList"));

		mockMvc.perform(post("/roles").session(session)
				.param("name", "ROLE_WEB").param("description", "web role")
				.param("permissions[0].id", "1").param("permissions[1].id", "2"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/roles"));

		final Role role = securityService.getRoleByName("ROLE_WEB");
		assertNotNull(role);
		assertEquals(2, inTx(new TransactionCallback<Integer>() {
			public Integer doInTransaction(TransactionStatus status) {
				return securityService.getRoleById(role.getId()).getPermissions().size();
			}
		}).intValue());

		mockMvc.perform(get("/roles/" + role.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("roles/edit_role"));

		// duplicate role name -> validator error
		mockMvc.perform(post("/roles").session(session).param("name", "ROLE_WEB"))
				.andExpect(status().isOk())
				.andExpect(view().name("roles/create_role"))
				.andExpect(model().attributeHasFieldErrorCode("role", "name", "error.exists"));
	}

	@Test
	public void createUserFlowEncodesPassword() throws Exception
	{
		mockMvc.perform(get("/users/new").session(session))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("rolesList"));

		mockMvc.perform(post("/users").session(session)
				.param("name", "Web User").param("email", "webuser@gmail.com").param("password", "secret1")
				.param("roles[0].id", "2"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/users"));

		final User user = securityService.findUserByEmail("webuser@gmail.com");
		assertNotNull(user);
		assertFalse("secret1".equals(user.getPassword()));
		assertTrue(user.getPassword().startsWith("$2a$"));
		assertTrue(passwordEncoder.matches("secret1", user.getPassword()));
		assertEquals("ROLE_ADMIN", inTx(new TransactionCallback<String>() {
			public String doInTransaction(TransactionStatus status) {
				return securityService.getUserById(user.getId()).getRoles().get(0).getName();
			}
		}));

		// new user can log in to the admin app
		MockHttpSession newSession = login("webuser@gmail.com", "secret1");
		mockMvc.perform(get("/categories").session(newSession)).andExpect(status().isOk());
		mockMvc.perform(get("/users").session(newSession)).andExpect(status().isForbidden());

		mockMvc.perform(get("/users/" + user.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("users/edit_user"));
	}

	@Test
	public void createUserValidationErrors() throws Exception
	{
		// password @Size(min=4), duplicate email
		mockMvc.perform(post("/users").session(session)
				.param("name", "X").param("email", ADMIN).param("password", "abc"))
				.andExpect(status().isOk())
				.andExpect(view().name("users/create_user"))
				.andExpect(model().attributeHasFieldErrors("user", "password", "email"));
	}

	// ---------- forgot / reset password ----------

	@Test
	public void forgotPasswordFlowForKnownAndUnknownEmail() throws Exception
	{
		mockMvc.perform(post("/forgotPwd").param("email", "siva@gmail.com"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/forgotPwd"))
				.andExpect(flash().attributeExists("msg"));
		String token = securityService.findUserByEmail("siva@gmail.com").getPasswordResetToken();
		assertNotNull(token);

		mockMvc.perform(post("/forgotPwd").param("email", "nobody@gmail.com"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/forgotPwd"))
				.andExpect(flash().attribute("msg", "Invalid email address"));

		// valid token renders reset form
		mockMvc.perform(get("/resetPwd").param("email", "siva@gmail.com").param("token", token))
				.andExpect(status().isOk())
				.andExpect(view().name("public/resetPwd"))
				.andExpect(model().attribute("token", token));

		// mismatch -> stays on form
		mockMvc.perform(post("/resetPwd").param("email", "siva@gmail.com").param("token", token)
				.param("password", "newpass1").param("confPassword", "other"))
				.andExpect(status().isOk())
				.andExpect(view().name("public/resetPwd"))
				.andExpect(model().attributeExists("msg"));

		// wrong token -> redirect login, password unchanged
		mockMvc.perform(post("/resetPwd").param("email", "siva@gmail.com").param("token", "bad")
				.param("password", "newpass1").param("confPassword", "newpass1"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"));
		assertTrue(passwordEncoder.matches("siva", securityService.findUserByEmail("siva@gmail.com").getPassword()));

		// success
		mockMvc.perform(post("/resetPwd").param("email", "siva@gmail.com").param("token", token)
				.param("password", "newpass1").param("confPassword", "newpass1"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"));
		User siva = securityService.findUserByEmail("siva@gmail.com");
		assertNull(siva.getPasswordResetToken());
		assertTrue(passwordEncoder.matches("newpass1", siva.getPassword()));
		login("siva@gmail.com", "newpass1");
	}
}
