package com.sivalabs.jcart.site.web.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.sivalabs.jcart.JCartSiteApplication;
import com.sivalabs.jcart.catalog.CategoryRepository;
import com.sivalabs.jcart.catalog.ProductRepository;
import com.sivalabs.jcart.customers.CustomerRepository;
import com.sivalabs.jcart.entities.Product;
import com.sivalabs.jcart.orders.OrderRepository;
import com.sivalabs.jcart.site.security.AuthenticatedUser;
import com.sivalabs.jcart.site.web.models.Cart;
import com.sivalabs.jcart.site.web.models.CategoryView;
import com.sivalabs.jcart.site.web.models.LineItem;

/**
 * Uses the seed data (data.sql): category "Birds" and product P1001 (category "Flowers")
 * are disabled inside each test transaction, which is rolled back afterwards.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JCartSiteApplication.class)
@WebAppConfiguration
@Transactional
public class StorefrontDisabledCatalogTest
{
	private static final String DISABLED_SKU = "P1001";
	private static final String SKU_IN_DISABLED_CATEGORY = "P1003";
	private static final String ENABLED_SKU = "P1004";

	@Autowired WebApplicationContext context;
	@Autowired CategoryRepository categoryRepository;
	@Autowired ProductRepository productRepository;
	@Autowired CustomerRepository customerRepository;
	@Autowired OrderRepository orderRepository;

	private MockMvc mockMvc;

	@Before
	public void setUp()
	{
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		categoryRepository.getByName("Birds").setDisabled(true);
		productRepository.findBySku(DISABLED_SKU).setDisabled(true);
		productRepository.flush();
	}

	@After
	public void clearSecurityContext()
	{
		SecurityContextHolder.clearContext();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void homeExcludesDisabledCategoriesAndProducts() throws Exception
	{
		MvcResult result = mockMvc.perform(get("/home"))
				.andExpect(status().isOk())
				.andExpect(view().name("home"))
				.andReturn();
		List<CategoryView> categories = (List<CategoryView>) result.getModelAndView().getModel().get("categories");
		List<String> names = new ArrayList<>();
		for (CategoryView category : categories) {
			names.add(category.getName());
			assertTrue(category.getProducts().size() <= 4);
			assertFalse(skus(category.getProducts()).contains(DISABLED_SKU));
		}
		assertTrue(names.contains("Flowers"));
		assertFalse(names.contains("Birds"));
		assertFalse(result.getResponse().getContentAsString().contains("/products/" + DISABLED_SKU + "\""));
	}

	@Test
	public void disabledCategoryPageReturns404() throws Exception
	{
		mockMvc.perform(get("/categories/Birds")).andExpect(status().isNotFound());
		mockMvc.perform(get("/categories/Unknown")).andExpect(status().isNotFound());
	}

	@Test
	public void enabledCategoryPageShowsOnlyEnabledProducts() throws Exception
	{
		MvcResult result = mockMvc.perform(get("/categories/Flowers"))
				.andExpect(status().isOk())
				.andExpect(view().name("category"))
				.andReturn();
		CategoryView category = (CategoryView) result.getModelAndView().getModel().get("category");
		List<String> skus = skus(category.getProducts());
		assertFalse(skus.contains(DISABLED_SKU));
		assertTrue(skus.contains(ENABLED_SKU));
		for (Product product : category.getProducts()) {
			assertFalse(product.isDisabled());
		}
	}

	@Test
	public void productPageReturns404ForDisabledProductOrCategory() throws Exception
	{
		mockMvc.perform(get("/products/" + DISABLED_SKU)).andExpect(status().isNotFound());
		mockMvc.perform(get("/products/" + SKU_IN_DISABLED_CATEGORY)).andExpect(status().isNotFound());
		mockMvc.perform(get("/products/" + ENABLED_SKU)).andExpect(status().isOk()).andExpect(view().name("product"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void searchExcludesDisabledProductsAndDisabledCategories() throws Exception
	{
		MvcResult result = mockMvc.perform(get("/products").param("q", "Quilling"))
				.andExpect(status().isOk())
				.andReturn();
		List<Product> products = (List<Product>) result.getModelAndView().getModel().get("products");
		List<String> skus = skus(products);
		assertTrue(skus.contains(ENABLED_SKU));
		assertFalse(skus.contains(DISABLED_SKU));
		assertFalse(skus.contains(SKU_IN_DISABLED_CATEGORY));
		for (Product product : products) {
			assertFalse(product.isDisabled());
			assertFalse(product.getCategory().isDisabled());
		}
	}

	@Test
	public void addToCartRefusesDisabledAndUnknownProducts() throws Exception
	{
		MockHttpSession session = new MockHttpSession();
		addToCart(session, DISABLED_SKU).andExpect(status().isNotFound());
		addToCart(session, SKU_IN_DISABLED_CATEGORY).andExpect(status().isNotFound());
		addToCart(session, "UNKNOWN").andExpect(status().isNotFound());
		addToCart(session, ENABLED_SKU).andExpect(status().isOk());

		Cart cart = (Cart) session.getAttribute("CART_KEY");
		assertEquals(1, cart.getItems().size());
		assertEquals(ENABLED_SKU, cart.getItems().get(0).getProduct().getSku());
	}

	@Test
	public void placeOrderDropsItemsDisabledAfterBeingAddedAndDoesNotCreateOrder() throws Exception
	{
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new AuthenticatedUser(customerRepository.findByEmail("ramu@gmail.com")), null));
		Cart cart = new Cart();
		cart.getItems().add(new LineItem(productRepository.findBySku(ENABLED_SKU), 1));
		cart.getItems().add(new LineItem(productRepository.findBySku(DISABLED_SKU), 2));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute("CART_KEY", cart);
		long ordersBefore = orderRepository.count();

		mockMvc.perform(post("/orders").session(session)
				.param("firstName", "Ramu").param("lastName", "P").param("emailId", "ramu@gmail.com")
				.param("phone", "8888888888").param("addressLine1", "Street 1").param("city", "Hyderabad")
				.param("state", "TS").param("zipCode", "500088").param("country", "IN")
				.param("billingFirstName", "Ramu").param("billingLastName", "P").param("billingAddressLine1", "Street 1")
				.param("billingCity", "Hyderabad").param("billingState", "TS").param("billingZipCode", "500088")
				.param("billingCountry", "IN").param("ccNumber", "1111111111111111").param("cvv", "123"))
				.andExpect(status().isOk())
				.andExpect(view().name("checkout"))
				.andExpect(model().attribute("unavailableProducts", java.util.Collections.singletonList("Quilling Toy 1")));

		assertEquals(ordersBefore, orderRepository.count());
		assertEquals(1, cart.getItems().size());
		assertEquals(ENABLED_SKU, cart.getItems().get(0).getProduct().getSku());
	}

	private org.springframework.test.web.servlet.ResultActions addToCart(MockHttpSession session, String sku) throws Exception
	{
		return mockMvc.perform(post("/cart/items").session(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"sku\":\"" + sku + "\"}"));
	}

	private static List<String> skus(List<Product> products)
	{
		List<String> skus = new ArrayList<>();
		for (Product product : products) {
			skus.add(product.getSku());
		}
		return skus;
	}
}
