package com.sivalabs.jcart.admin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.sivalabs.jcart.AbstractAdminWebTest;
import com.sivalabs.jcart.security.SecurityService;

public class AdminSecurityTest extends AbstractAdminWebTest
{
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired SecurityService securityService;

	// ---------- authentication ----------

	@Test
	public void loginPageIsPublic() throws Exception
	{
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(view().name("public/login"));
	}

	@Test
	public void forgotAndResetPasswordPagesArePublic() throws Exception
	{
		mockMvc.perform(get("/forgotPwd")).andExpect(status().isOk());
		// invalid token -> redirect to login (not 403/401)
		mockMvc.perform(get("/resetPwd").param("email", ADMIN).param("token", "bogus"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	public void protectedPagesRedirectAnonymousToLogin() throws Exception
	{
		String[] urls = { "/home", "/categories", "/products", "/orders", "/customers", "/users", "/roles", "/permissions" };
		for (String url : urls)
		{
			mockMvc.perform(get(url))
					.andExpect(status().isFound())
					.andExpect(redirectedUrlPattern("**/login"));
		}
	}

	@Test
	public void validLoginRedirectsToHomeAndCreatesAuthenticatedSession() throws Exception
	{
		MockHttpSession session = login(SUPER_ADMIN, SUPER_ADMIN_PWD);
		assertNotNull(session);
		assertNotNull(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
		mockMvc.perform(get("/home").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("home"))
				.andExpect(content().string(Matchers.containsString("Super Admin")));
	}

	@Test
	public void invalidPasswordRedirectsToLoginError() throws Exception
	{
		mockMvc.perform(post("/login").param("username", SUPER_ADMIN).param("password", "wrong"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	public void unknownUserRedirectsToLoginError() throws Exception
	{
		mockMvc.perform(post("/login").param("username", "nobody@gmail.com").param("password", "x"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	public void logoutInvalidatesSession() throws Exception
	{
		MockHttpSession session = login(SUPER_ADMIN, SUPER_ADMIN_PWD);
		mockMvc.perform(post("/logout").session(session))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
		// GET /logout also works because logoutRequestMatcher is an AntPathRequestMatcher without method
		MockHttpSession session2 = login(SUPER_ADMIN, SUPER_ADMIN_PWD);
		mockMvc.perform(get("/logout").session(session2))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
		mockMvc.perform(get("/home").session(session2))
				.andExpect(status().isFound())
				.andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	public void csrfIsDisabledPostWithoutTokenSucceeds() throws Exception
	{
		// Characterization: csrf().disable() in WebSecurityConfig. If the upgrade
		// enables CSRF this test must fail (403) and be revisited deliberately.
		mockMvc.perform(post("/login").param("username", SUPER_ADMIN).param("password", SUPER_ADMIN_PWD))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/home"));
	}

	@Test
	public void seedPasswordsMatchBCryptEncoder()
	{
		assertNotNull(passwordEncoder);
		assertTrue(passwordEncoder.matches("superadmin", securityService.findUserByEmail(SUPER_ADMIN).getPassword()));
		assertTrue(passwordEncoder.matches("admin", securityService.findUserByEmail(ADMIN).getPassword()));
		assertTrue(passwordEncoder.matches("user", securityService.findUserByEmail(PLAIN_USER).getPassword()));
		assertFalse(passwordEncoder.matches("wrong", securityService.findUserByEmail(ADMIN).getPassword()));
		assertTrue(passwordEncoder.encode("abc").startsWith("$2a$"));
	}

	// ---------- authorization (@Secured on controllers, permission -> ROLE_ prefix) ----------

	@Test
	public void superAdminCanAccessEverything() throws Exception
	{
		MockHttpSession session = login(SUPER_ADMIN, SUPER_ADMIN_PWD);
		String[] urls = { "/home", "/categories", "/products", "/orders", "/customers", "/users", "/roles", "/permissions" };
		for (String url : urls)
		{
			mockMvc.perform(get(url).session(session)).andExpect(status().isOk());
		}
	}

	@Test
	public void adminWithoutUserManagementPermissionsIsForbidden() throws Exception
	{
		// admin@gmail.com -> ROLE_ADMIN: categories, products, orders, customers, payment systems, settings
		MockHttpSession session = login(ADMIN, ADMIN_PWD);
		mockMvc.perform(get("/categories").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/products").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/orders").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/customers").session(session)).andExpect(status().isOk());

		mockMvc.perform(get("/users").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(get("/roles").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(get("/permissions").session(session)).andExpect(status().isForbidden());
	}

	@Test
	public void plainUserCanLoginButHasNoManagementAccess() throws Exception
	{
		// user@gmail.com -> ROLE_USER, no permissions
		MockHttpSession session = login(PLAIN_USER, PLAIN_USER_PWD);
		mockMvc.perform(get("/home").session(session)).andExpect(status().isOk());
		String[] urls = { "/categories", "/products", "/orders", "/customers", "/users", "/roles", "/permissions" };
		for (String url : urls)
		{
			mockMvc.perform(get(url).session(session)).andExpect(status().isForbidden());
		}
	}

	@Test
	public void forbiddenPostIsAlsoRejected() throws Exception
	{
		MockHttpSession session = login(PLAIN_USER, PLAIN_USER_PWD);
		mockMvc.perform(post("/categories").session(session).param("name", "Hacked"))
				.andExpect(status().isForbidden());
	}

	@Test
	public void error403PageRenders() throws Exception
	{
		MockHttpSession session = login(PLAIN_USER, PLAIN_USER_PWD);
		mockMvc.perform(get("/403").session(session))
				.andExpect(status().isOk())
				.andExpect(view().name("error/accessDenied"));
	}

	@Test
	public void staticResourcesArePublic() throws Exception
	{
		// permitAll for /assets/** ; existence of a specific asset is not asserted,
		// only that security does not redirect to login.
		mockMvc.perform(get("/assets/css/does-not-need-to-exist.css"))
				.andExpect(status().is(Matchers.not(302)));
	}
}
