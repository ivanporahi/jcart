package com.sivalabs.jcart;

import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Base class for jcart-core integration tests.
 *
 * Boots the core context against the in-memory H2 database and forces
 * data.sql to be loaded so tests run against the seed dataset.
 *
 * Framework-specific annotations are intentionally concentrated here so that
 * after the Java/Spring Boot upgrade only this class needs to change
 * (e.g. @SpringBootTest + JUnit 5, spring.sql.init.mode=always).
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JCartCoreApplication.class)
@TestPropertySource(properties = {
		"spring.datasource.initialize=true",
		"spring.mail.host=localhost",
		"spring.mail.port=2525",
		"spring.mail.properties.mail.smtps.connectiontimeout=500",
		"spring.mail.properties.mail.smtps.timeout=500"
})
public abstract class AbstractCoreIntegrationTest
{
}
