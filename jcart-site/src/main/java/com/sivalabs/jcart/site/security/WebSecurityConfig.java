/**
 * 
 */
package com.sivalabs.jcart.site.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, proxyTargetClass = true)
public class WebSecurityConfig {

	@Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService customUserDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
        	.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
            	.requestMatchers("/resources/**", "/webjars/**","/assets/**").permitAll()
                .requestMatchers("/", "/register", "/forgotPwd","/resetPwd").permitAll()
                .requestMatchers("/myAccount","/checkout","/orders").authenticated()
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/home")
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
            	.logoutUrl("/logout")
            	.permitAll())
            .exceptionHandling(handling -> handling.accessDeniedPage("/403"));
        return http.build();
    }
}
