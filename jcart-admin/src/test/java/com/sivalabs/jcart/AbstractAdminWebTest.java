package com.sivalabs.jcart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.Filter;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base for JCart Admin web/security baseline tests. Uses MockMvc with the real
 * Spring Security filter chain and real form login (BCrypt against seed data),
 * so the same tests are valid after the Spring Boot / Security upgrade.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JCartAdminApplication.class)
@WebAppConfiguration
@TestPropertySource(properties = {
		"spring.datasource.initialize=true",
		"spring.mail.host=localhost",
		"spring.mail.port=2525",
		"spring.mail.properties.mail.smtp.connectiontimeout=500",
		"spring.mail.properties.mail.smtp.timeout=500"
})
public abstract class AbstractAdminWebTest
{
	// Seed credentials from jcart-core data.sql (dev data only)
	protected static final String SUPER_ADMIN = "superadmin@gmail.com";
	protected static final String SUPER_ADMIN_PWD = "superadmin";
	protected static final String ADMIN = "admin@gmail.com";
	protected static final String ADMIN_PWD = "admin";
	protected static final String PLAIN_USER = "user@gmail.com";
	protected static final String PLAIN_USER_PWD = "user";

	@Autowired protected WebApplicationContext wac;
	@Autowired protected Filter springSecurityFilterChain;

	protected MockMvc mockMvc;

	@Before
	public void setUpMockMvc()
	{
		mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(springSecurityFilterChain).build();
	}

	protected MockHttpSession login(String username, String password) throws Exception
	{
		MvcResult result = mockMvc.perform(post("/login").param("username", username).param("password", password))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/home"))
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
