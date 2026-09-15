/**
 * 
 */
package com.sivalabs.jcart.admin.config;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.sivalabs.jcart.admin.security.PostAuthorizationFilter;

/**
 * @author Siva
 *
 */
@Configuration
public class WebConfig implements WebMvcConfigurer
{   
	@Value("${server.port:9443}") private int serverPort;
	
	@Autowired 
	private PostAuthorizationFilter postAuthorizationFilter;
	
	@Autowired
    private MessageSource messageSource;

    @Override
    public Validator getValidator() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }
    
	@Bean
	public FilterRegistrationBean<PostAuthorizationFilter> postAuthorizationFilterRegistrationBean() {
	    FilterRegistrationBean<PostAuthorizationFilter> registrationBean = new FilterRegistrationBean<>();
	    registrationBean.setFilter(postAuthorizationFilter);
	    registrationBean.setOrder(Integer.MAX_VALUE);
	    return registrationBean;
	}
	
	@Override
	public void addViewControllers(ViewControllerRegistry registry)
	{
        registry.addViewController("/login").setViewName("public/login");
		registry.addRedirectViewController("/", "/home");
	}
	
	@Bean 
	public ClassLoaderTemplateResolver emailTemplateResolver(){ 
		ClassLoaderTemplateResolver emailTemplateResolver = new ClassLoaderTemplateResolver(); 
		emailTemplateResolver.setPrefix("email-templates/"); 
		emailTemplateResolver.setSuffix(".html"); 
		emailTemplateResolver.setTemplateMode(TemplateMode.HTML); 
		emailTemplateResolver.setCharacterEncoding("UTF-8"); 
		emailTemplateResolver.setOrder(2);
		emailTemplateResolver.setCheckExistence(true);
		return emailTemplateResolver; 
	}
	
	@Bean
	public ServletWebServerFactory servletContainer() {
		TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
			@Override
			protected void postProcessContext(Context context) {
				SecurityConstraint securityConstraint = new SecurityConstraint();
				securityConstraint.setUserConstraint("CONFIDENTIAL");
				SecurityCollection collection = new SecurityCollection();
				collection.addPattern("/*");
				securityConstraint.addCollection(collection);
				context.addConstraint(securityConstraint);
			}
		};
		tomcat.addAdditionalConnectors(initiateHttpConnector());
		return tomcat;
	}

	private Connector initiateHttpConnector() {
		Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
		connector.setScheme("http");
		connector.setPort(9090);
		connector.setSecure(false);
		connector.setRedirectPort(serverPort);
		return connector;
	}
}
