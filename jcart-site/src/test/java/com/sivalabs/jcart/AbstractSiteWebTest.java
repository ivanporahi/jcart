package com.sivalabs.jcart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base for JCart Site web/security baseline tests (MockMvc + real security
 * filter chain + real form login against seed customers).
 */
@SpringBootTest(classes = JCartSiteApplication.class)
@TestPropertySource(properties = {
		"spring.sql.init.mode=always",
		"spring.jpa.defer-datasource-initialization=true",
		"spring.mail.host=localhost",
		"spring.mail.port=2525",
		"spring.mail.properties.mail.smtp.connectiontimeout=500",
		"spring.mail.properties.mail.smtp.timeout=500"
})
public abstract class AbstractSiteWebTest
{
	// Seed customers from jcart-core data.sql (dev data only)
	protected static final String CUSTOMER_SIVA = "sivaprasadreddy.k@gmail.com";
	protected static final String CUSTOMER_SIVA_PWD = "siva";
	protected static final String CUSTOMER_RAMU = "ramu@gmail.com";
	protected static final String CUSTOMER_RAMU_PWD = "ramu";

	@Autowired protected WebApplicationContext wac;
	@Autowired protected Filter springSecurityFilterChain;

	protected MockMvc mockMvc;

	@BeforeEach
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
