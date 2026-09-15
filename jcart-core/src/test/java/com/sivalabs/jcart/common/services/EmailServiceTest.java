package com.sivalabs.jcart.common.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.sivalabs.jcart.JCartException;

public class EmailServiceTest
{
	JavaMailSender mailSender;
	EmailService emailService;
	MimeMessage mimeMessage;

	@BeforeEach
	public void setUp()
	{
		mailSender = mock(JavaMailSender.class);
		mimeMessage = new MimeMessage((Session) null);
		when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
		emailService = new EmailService();
		emailService.javaMailSender = mailSender;
		emailService.supportEmail = "support@jcart.test";
	}

	@Test
	public void sendEmailBuildsHtmlMessageAndSends() throws Exception
	{
		emailService.sendEmail("to@jcart.test", "Subject", "<b>Hi</b>");
		verify(mailSender).send(mimeMessage);
		assertEquals("Subject", mimeMessage.getSubject());
		assertEquals("support@jcart.test", mimeMessage.getFrom()[0].toString());
		assertEquals("to@jcart.test", mimeMessage.getAllRecipients()[0].toString());
		assertEquals("<b>Hi</b>", mimeMessage.getContent());
	}

	@Test
	public void mailFailureIsWrappedInJCartException()
	{
		doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
		try
		{
			emailService.sendEmail("to@jcart.test", "Subject", "body");
			fail();
		}
		catch (JCartException e)
		{
			assertEquals("Unable to send email", e.getMessage());
		}
	}
}
