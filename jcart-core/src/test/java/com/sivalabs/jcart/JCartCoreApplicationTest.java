/**
 * 
 */
package com.sivalabs.jcart;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sivalabs.jcart.common.services.EmailService;
/**
 * @author Siva
 *
 */
@SpringBootTest(classes = JCartCoreApplication.class)
public class JCartCoreApplicationTest
{
	@Autowired DataSource dataSource;
	@Autowired EmailService emailService;
	
	@Test
	public void testDummy() throws SQLException
	{
		String schema = dataSource.getConnection().getCatalog();
		assertTrue("jcart".equalsIgnoreCase(schema));
	}
	
	@Test
	@Disabled
	public void testSendEmail()
	{
		emailService.sendEmail("admin@gmail.com", "JCart - Test Mail", "This is a test email from JCart");
	}
	
}
