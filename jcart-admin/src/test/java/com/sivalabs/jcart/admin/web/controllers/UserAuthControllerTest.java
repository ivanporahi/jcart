package com.sivalabs.jcart.admin.web.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.sivalabs.jcart.entities.User;
import com.sivalabs.jcart.security.SecurityService;

public class UserAuthControllerTest
{
	private String sentTo;
	private String sentURL;
	private UserAuthController controller;

	@Before
	public void setUp()
	{
		controller = new UserAuthController() {
			@Override
			protected void sendForgotPasswordEmail(String email, String resetPwdURL)
			{
				sentTo = email;
				sentURL = resetPwdURL;
			}
		};
		controller.securityService = mock(SecurityService.class);
		controller.messageSource = mock(MessageSource.class);
		controller.setBaseUrl("https://admin.jcart.example/");

		User user = new User();
		user.setEmail("admin@gmail.com");
		when(controller.securityService.resetPassword("admin@gmail.com")).thenReturn("token-123");
		when(controller.securityService.findUserByEmail("admin@gmail.com")).thenReturn(user);
	}

	@Test
	public void resetLinkIgnoresHostHeaderAndUsesConfiguredBaseUrl()
	{
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/forgotPwd");
		request.setScheme("https");
		request.setServerName("attacker.example");
		request.setServerPort(443);
		request.addHeader("Host", "attacker.example");
		request.setParameter("email", "admin@gmail.com");

		controller.handleForgotPwd(request, new RedirectAttributesModelMap());

		assertEquals("https://admin.jcart.example/resetPwd?email=admin%40gmail.com&token=token-123", sentURL);
		assertFalse(sentURL.contains("attacker.example"));
		assertEquals("admin@gmail.com", sentTo);
	}

	@Test
	public void resetLinkEncodesEmailQueryParameter()
	{
		String url = controller.buildResetPwdURL("a+b&x=1@gmail.com", "t");
		assertTrue(url.startsWith("https://admin.jcart.example/resetPwd?email=a%2Bb%26x%3D1%40gmail.com&token=t"));
	}
}
