package com.sivalabs.jcart.admin.web.controllers;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sivalabs.jcart.JCartAdminApplication;
import com.sivalabs.jcart.admin.security.SecurityUtil;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JCartAdminApplication.class)
@WebAppConfiguration
public class ProductControllerUpdateTest
{
	private static final int PRODUCT_ID = 1;

	@Autowired private WebApplicationContext context;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private UserDetailsService userDetailsService;

	private MockMvc mockMvc;
	private Map<String, Object> original;

	@Before
	public void setUp()
	{
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		UserDetails admin = userDetailsService.loadUserByUsername("admin@gmail.com");
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				admin, "N/A", AuthorityUtils.createAuthorityList(SecurityUtil.MANAGE_PRODUCTS)));
		original = jdbcTemplate.queryForMap("select sku, description, price, cat_id from products where id=?", PRODUCT_ID);
	}

	@After
	public void tearDown()
	{
		jdbcTemplate.update("update products set description=?, price=?, cat_id=? where id=?",
				original.get("description"), original.get("price"), original.get("cat_id"), PRODUCT_ID);
		SecurityContextHolder.clearContext();
	}

	@Test
	public void updateKeepingOwnSkuPersistsChanges() throws Exception
	{
		mockMvc.perform(post("/products/" + PRODUCT_ID)
				.param("id", String.valueOf(PRODUCT_ID))
				.param("sku", (String) original.get("sku"))
				.param("name", "Quilling Toy 1")
				.param("price", "999.50")
				.param("description", "Edited description")
				.param("categoryId", "2"))
			.andExpect(model().hasNoErrors())
			.andExpect(redirectedUrl("/products"))
			.andExpect(flash().attribute("info", "Product updated successfully"));

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"select sku, description, price, cat_id from products where id=?", PRODUCT_ID);
		assertEquals(original.get("sku"), row.get("sku"));
		assertEquals("Edited description", row.get("description"));
		assertEquals(0, new BigDecimal("999.50").compareTo((BigDecimal) row.get("price")));
		assertEquals(2, ((Number) row.get("cat_id")).intValue());
	}

	@Test
	public void updateToAnotherProductsSkuIsRejected() throws Exception
	{
		mockMvc.perform(post("/products/" + PRODUCT_ID)
				.param("id", String.valueOf(PRODUCT_ID))
				.param("sku", "P1002")
				.param("name", "Quilling Toy 1")
				.param("price", "999.50")
				.param("description", "Should not be saved")
				.param("categoryId", "2"))
			.andExpect(model().attributeHasFieldErrorCode("product", "sku", "error.exists"))
			.andExpect(view().name("products/edit_product"));

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"select description from products where id=?", PRODUCT_ID);
		assertEquals(original.get("description"), row.get("description"));
	}
}
