package com.sivalabs.jcart.site;

import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.sivalabs.jcart.AbstractSiteWebTest;

public class SiteSecurityTest extends AbstractSiteWebTest
{
	@Test
	public void publicPagesAreAccessibleAnonymously() throws Exception
	{
		mockMvc.perform(get("/home")).andExpect(status().isOk()).andExpect(view().name("home"));
		mockMvc.perform(get("/products")).andExpect(status().isOk()).andExpect(view().name("products"));
		mockMvc.perform(get("/products/P1001")).andExpect(status().isOk()).andExpect(view().name("product"));
		mockMvc.perform(get("/categories/Flowers")).andExpect(status().isOk()).andExpect(view().name("category"));
		mockMvc.perform(get("/cart")).andExpect(status().isOk()).andExpect(view().name("cart"));
		mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));
		mockMvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("register"));
	}

	@Test
	public void customerPagesRequireAuthentication() throws Exception
	{
		String[] urls = { "/myAccount", "/checkout" };
		for (String url : urls)
		{
			mockMvc.perform(get(url))
					.andExpect(status().isFound())
					.andExpect(redirectedUrlPattern("**/login"));
		}
		mockMvc.perform(post("/orders"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	public void orderDetailPagesAreNotProtected_characterization()  throws Exception
	{
		// Characterization of current behaviour: only /myAccount, /checkout and /orders
		// are matched as authenticated(); /orders/{orderNumber} and /orderconfirmation
		// are reachable anonymously and expose any order by number (no ownership check).
		// Documented as a security finding; must be revisited during modernization.
		mockMvc.perform(get("/orders/1447737431927"))
				.andExpect(status().isOk())
				.andExpect(view().name("view_order"))
				.andExpect(content().string(Matchers.containsString("1447737431927")));
		mockMvc.perform(get("/orderconfirmation").param("orderNumber", "1447737431927"))
				.andExpect(status().isOk())
				.andExpect(view().name("orderconfirmation"));
	}

	@Test
	public void validCustomerLoginRedirectsToHome() throws Exception
	{
		MockHttpSession session = login(CUSTOMER_SIVA, CUSTOMER_SIVA_PWD);
		assertNotNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
		mockMvc.perform(get("/myAccount").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("myAccount"))
				.andExpect(content().string(Matchers.containsString(CUSTOMER_SIVA)));
	}

	@Test
	public void invalidLoginRedirectsToLoginError() throws Exception
	{
		mockMvc.perform(post("/login").param("username", CUSTOMER_SIVA).param("password", "wrong"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?error"));
		mockMvc.perform(post("/login").param("username", "nobody@gmail.com").param("password", "x"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	public void adminUsersCannotLoginToSite() throws Exception
	{
		// Site authenticates against the customers table, not users
		mockMvc.perform(post("/login").param("username", "superadmin@gmail.com").param("password", "superadmin"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	public void logoutEndsSession() throws Exception
	{
		MockHttpSession session = login(CUSTOMER_SIVA, CUSTOMER_SIVA_PWD);
		mockMvc.perform(get("/logout").session(session))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
		mockMvc.perform(get("/myAccount").session(session))
				.andExpect(status().isFound())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	public void csrfIsDisabled() throws Exception
	{
		// JSON POST to cart without any CSRF token succeeds (csrf().disable()).
		mockMvc.perform(post("/cart/items").contentType("application/json").content("{\"sku\":\"P1001\"}"))
				.andExpect(status().isOk());
	}

	@Test
	public void staticAssetsArePublic() throws Exception
	{
		mockMvc.perform(get("/assets/css/nonexistent.css")).andExpect(status().is(Matchers.not(302)));
	}
}
