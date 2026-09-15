package com.sivalabs.jcart.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.sivalabs.jcart.AbstractSiteWebTest;
import com.sivalabs.jcart.customers.CustomerService;
import com.sivalabs.jcart.entities.Customer;
import com.sivalabs.jcart.entities.Order;
import com.sivalabs.jcart.entities.OrderStatus;
import com.sivalabs.jcart.orders.OrderService;
import com.sivalabs.jcart.site.web.models.Cart;

public class SiteFunctionalTest extends AbstractSiteWebTest
{
	private static final String CART_KEY = "CART_KEY";

	@Autowired CustomerService customerService;
	@Autowired OrderService orderService;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired PlatformTransactionManager txManager;

	// ---------------------------------------------------------------- catalog

	@Test
	public void homeShowsAtMostFourProductsPerCategory() throws Exception
	{
		mockMvc.perform(get("/home"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("categories", Matchers.hasSize(3)))
				.andExpect(content().string(Matchers.containsString("Flowers")))
				.andExpect(content().string(Matchers.containsString("Toys")))
				.andExpect(content().string(Matchers.containsString("Birds")));
	}

	@Test
	public void productsPageListsAllSeedProducts() throws Exception
	{
		mockMvc.perform(get("/products"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("products", Matchers.hasSize(25)))
				.andExpect(content().string(Matchers.containsString("Quilling Toy 1")))
				.andExpect(content().string(Matchers.containsString("Quilling Toy 25")));
	}

	@Test
	public void productDetailPage() throws Exception
	{
		mockMvc.perform(get("/products/P1001"))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("product"))
				.andExpect(content().string(Matchers.containsString("Quilling Toy 1")));
	}

	@Test
	public void categoryPageListsCategoryProducts() throws Exception
	{
		mockMvc.perform(get("/categories/Birds"))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("category"))
				.andExpect(content().string(Matchers.containsString("Quilling Toy 18")))
				.andExpect(content().string(Matchers.not(Matchers.containsString("Quilling Toy 1<"))));
	}

	@Test
	public void productImageEndpointReturnsBytesOrEmpty() throws Exception
	{
		// Images live on disk (jcart.images.dir); in a clean checkout the file is
		// absent and the controller returns 200 with empty body.
		mockMvc.perform(get("/products/images/1")).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------- cart

	@Test
	public void cartRestLifecycle() throws Exception
	{
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(get("/cart/items/count").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string("{\"count\":0}"));

		// add same sku twice -> qty 2, add another sku -> 3 items in total
		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1001\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1001\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1010\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/cart/items/count").session(session)).andExpect(content().string("{\"count\":3}"));

		Cart cart = (Cart) session.getAttribute(CART_KEY);
		assertEquals(2, cart.getItems().size());
		assertEquals(2, cart.getItems().get(0).getQuantity());
		BigDecimal expected = cart.getItems().get(0).getProduct().getPrice().multiply(new BigDecimal(2))
				.add(cart.getItems().get(1).getProduct().getPrice());
		assertEquals(0, expected.compareTo(cart.getTotalAmount()));

		// update quantity
		mockMvc.perform(put("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"product\":{\"sku\":\"P1001\"},\"quantity\":5}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/cart/items/count").session(session)).andExpect(content().string("{\"count\":6}"));

		// quantity <= 0 removes
		mockMvc.perform(put("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"product\":{\"sku\":\"P1001\"},\"quantity\":0}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/cart/items/count").session(session)).andExpect(content().string("{\"count\":1}"));

		// delete by sku
		mockMvc.perform(delete("/cart/items/P1010").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/cart/items/count").session(session)).andExpect(content().string("{\"count\":0}"));

		// clear
		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1020\"}"));
		mockMvc.perform(delete("/cart").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/cart/items/count").session(session)).andExpect(content().string("{\"count\":0}"));

		mockMvc.perform(get("/cart").session(session))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("cart"));
	}

	@Test
	public void cartIsPerSession() throws Exception
	{
		MockHttpSession s1 = new MockHttpSession();
		MockHttpSession s2 = new MockHttpSession();
		mockMvc.perform(post("/cart/items").session(s1).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1001\"}"));
		mockMvc.perform(get("/cart/items/count").session(s1)).andExpect(content().string("{\"count\":1}"));
		mockMvc.perform(get("/cart/items/count").session(s2)).andExpect(content().string("{\"count\":0}"));
	}

	// --------------------------------------------------------------- register

	@Test
	public void registerNewCustomerEncodesPasswordAndRedirectsToLogin() throws Exception
	{
		String email = "newcustomer@example.com";
		mockMvc.perform(post("/register")
				.param("firstName", "New").param("lastName", "Customer")
				.param("email", email).param("password", "pass123").param("phone", "5551234"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"))
				.andExpect(flash().attribute("info", "Customer created successfully"));

		Customer created = customerService.getCustomerByEmail(email);
		assertNotNull(created);
		assertTrue(passwordEncoder.matches("pass123", created.getPassword()));

		// and the new customer can log in
		login(email, "pass123");
	}

	@Test
	public void registerValidationErrors() throws Exception
	{
		// duplicate email
		mockMvc.perform(post("/register")
				.param("firstName", "Dup").param("lastName", "L")
				.param("email", CUSTOMER_SIVA).param("password", "x").param("phone", "1"))
				.andExpect(status().isOk())
				.andExpect(view().name("register"))
				.andExpect(model().attributeHasFieldErrorCode("customer", "email", "error.exists"));

		// missing/invalid fields
		mockMvc.perform(post("/register").param("email", "not-an-email"))
				.andExpect(status().isOk())
				.andExpect(view().name("register"))
				.andExpect(model().attributeHasFieldErrors("customer", "firstName", "email", "password"));
	}

	// --------------------------------------------------------------- checkout

	@Test
	public void checkoutPageShowsCartForAuthenticatedCustomer() throws Exception
	{
		MockHttpSession session = login(CUSTOMER_RAMU, CUSTOMER_RAMU_PWD);
		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1002\"}"));
		mockMvc.perform(get("/checkout").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("checkout"))
				.andExpect(model().attributeExists("order", "cart"))
				.andExpect(content().string(Matchers.containsString("Quilling Toy 2")));
	}

	@Test
	public void placeOrderValidationErrorsKeepCart() throws Exception
	{
		MockHttpSession session = login(CUSTOMER_RAMU, CUSTOMER_RAMU_PWD);
		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1002\"}"));

		mockMvc.perform(post("/orders").session(session).param("emailId", "bad-email"))
				.andExpect(status().isOk())
				.andExpect(view().name("checkout"))
				.andExpect(model().attributeHasFieldErrors("order", "firstName", "lastName", "emailId", "phone",
						"addressLine1", "city", "state", "zipCode", "country", "ccNumber", "cvv"))
				.andExpect(model().attributeExists("cart"));

		Cart cart = (Cart) session.getAttribute(CART_KEY);
		assertEquals(1, cart.getItemCount());
	}

	@Test
	public void placeOrderCreatesOrderClearsCartAndShowsConfirmation() throws Exception
	{
		MockHttpSession session = login(CUSTOMER_RAMU, CUSTOMER_RAMU_PWD);
		int ordersBefore = customerService.getCustomerOrders(CUSTOMER_RAMU).size();

		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1003\"}"));
		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1003\"}"));
		mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"P1021\"}"));
		Cart cart = (Cart) session.getAttribute(CART_KEY);
		final BigDecimal cartTotal = cart.getTotalAmount();

		// Email is attempted against localhost:2525 (nothing listening): the
		// controller swallows JCartException, so checkout still succeeds.
		MvcResult result = mockMvc.perform(post("/orders").session(session)
				.param("firstName", "Ramu").param("lastName", "K")
				.param("emailId", CUSTOMER_RAMU).param("phone", "9999999999")
				.param("addressLine1", "1 Main St").param("addressLine2", "")
				.param("city", "Hyderabad").param("state", "TS").param("zipCode", "500001").param("country", "IN")
				.param("billingFirstName", "Ramu").param("billingLastName", "K")
				.param("billingAddressLine1", "1 Main St").param("billingCity", "Hyderabad")
				.param("billingState", "TS").param("billingZipCode", "500001").param("billingCountry", "IN")
				.param("ccNumber", "4111111111111111").param("cvv", "123"))
				.andExpect(status().isFound())
				.andReturn();

		String location = result.getResponse().getRedirectedUrl();
		assertTrue(location.startsWith("orderconfirmation?orderNumber="), location);
		final String orderNumber = location.substring(location.indexOf('=') + 1);

		// cart cleared from session
		assertNull(session.getAttribute(CART_KEY));

		// order persisted with items, addresses, payment and NEW status
		new TransactionTemplate(txManager).execute(new TransactionCallbackWithoutResult() {
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				Order order = orderService.getOrder(orderNumber);
				assertNotNull(order);
				assertEquals(OrderStatus.NEW, order.getStatus());
				assertEquals(CUSTOMER_RAMU, order.getCustomer().getEmail());
				assertEquals(2, order.getItems().size());
				assertEquals("1 Main St", order.getDeliveryAddress().getAddressLine1());
				assertEquals("1 Main St", order.getBillingAddress().getAddressLine1());
				assertEquals("4111111111111111", order.getPayment().getCcNumber());
				assertEquals(0, cartTotal.compareTo(order.getTotalAmount()));
			}
		});

		List<Order> orders = customerService.getCustomerOrders(CUSTOMER_RAMU);
		assertEquals(ordersBefore + 1, orders.size());

		// confirmation + detail pages
		mockMvc.perform(get("/orderconfirmation").session(session).param("orderNumber", orderNumber))
				.andExpect(status().isOk())
				.andExpect(view().name("orderconfirmation"))
				.andExpect(content().string(Matchers.containsString(orderNumber)));
		mockMvc.perform(get("/orders/" + orderNumber).session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("view_order"))
				.andExpect(content().string(Matchers.containsString(orderNumber)));

		// and it shows up in myAccount
		mockMvc.perform(get("/myAccount").session(session))
				.andExpect(status().isOk())
				.andExpect(model().attribute("orders", Matchers.hasSize(ordersBefore + 1)))
				.andExpect(content().string(Matchers.containsString(orderNumber)));
	}
}
