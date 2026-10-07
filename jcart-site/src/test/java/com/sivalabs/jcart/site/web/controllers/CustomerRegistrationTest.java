package com.sivalabs.jcart.site.web.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sivalabs.jcart.JCartSiteApplication;
import com.sivalabs.jcart.customers.CustomerService;
import com.sivalabs.jcart.entities.Customer;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JCartSiteApplication.class)
@WebAppConfiguration
public class CustomerRegistrationTest
{
	@Autowired private WebApplicationContext context;
	@Autowired private CustomerService customerService;

	private MockMvc mockMvc;

	@Before
	public void setUp()
	{
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	public void registerIgnoresSuppliedIdAndDoesNotOverwriteExistingCustomer() throws Exception
	{
		Customer victim = customerService.getCustomerById(1);
		String victimEmail = victim.getEmail();
		String victimPassword = victim.getPassword();

		mockMvc.perform(post("/register")
				.param("id", "1")
				.param("firstName", "Attacker")
				.param("lastName", "X")
				.param("email", "attacker-takeover@example.com")
				.param("password", "attackerPwd")
				.param("phone", "123"))
			.andExpect(redirectedUrl("/login"));

		Customer unchanged = customerService.getCustomerById(1);
		assertEquals(victimEmail, unchanged.getEmail());
		assertEquals(victimPassword, unchanged.getPassword());

		Customer created = customerService.getCustomerByEmail("attacker-takeover@example.com");
		assertNotNull(created);
		assertNotEquals(Integer.valueOf(1), created.getId());
	}
}
