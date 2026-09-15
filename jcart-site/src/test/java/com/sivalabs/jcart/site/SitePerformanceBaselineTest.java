package com.sivalabs.jcart.site;

import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.sivalabs.jcart.AbstractSiteWebTest;

/**
 * Coarse latency baseline for the main storefront endpoints (MockMvc, full
 * Thymeleaf rendering, real security filter chain). Prints
 * "PERF site.<endpoint> avg_ms=..." so runs can be diffed before/after the
 * JDK/Spring upgrade. Thresholds are deliberately generous: this guards against
 * order-of-magnitude regressions, not micro-optimisations.
 */
public class SitePerformanceBaselineTest extends AbstractSiteWebTest
{
	private static final int WARMUP = 10;
	private static final int ITERATIONS = 100;
	private static final double MAX_AVG_MS = 200;

	private interface Request
	{
		void perform() throws Exception;
	}

	private void time(String name, Request r) throws Exception
	{
		for (int i = 0; i < WARMUP; i++) r.perform();
		long start = System.nanoTime();
		for (int i = 0; i < ITERATIONS; i++) r.perform();
		double avgMs = (System.nanoTime() - start) / 1_000_000.0 / ITERATIONS;
		System.out.println(String.format("PERF %s avg_ms=%.3f iterations=%d", name, avgMs, ITERATIONS));
		assertTrue(name + " avg " + avgMs + "ms exceeds " + MAX_AVG_MS + "ms", avgMs < MAX_AVG_MS);
	}

	@Test
	public void homePage() throws Exception
	{
		time("site.GET /home", new Request() {
			public void perform() throws Exception {
				mockMvc.perform(get("/home")).andExpect(status().isOk());
			}
		});
	}

	@Test
	public void productsPage() throws Exception
	{
		time("site.GET /products", new Request() {
			public void perform() throws Exception {
				mockMvc.perform(get("/products")).andExpect(status().isOk());
			}
		});
	}

	@Test
	public void productDetailPage() throws Exception
	{
		time("site.GET /products/{sku}", new Request() {
			public void perform() throws Exception {
				mockMvc.perform(get("/products/P1001")).andExpect(status().isOk());
			}
		});
	}

	@Test
	public void cartAddAndCount() throws Exception
	{
		final MockHttpSession session = new MockHttpSession();
		time("site.POST /cart/items + GET /cart/items/count", new Request() {
			public void perform() throws Exception {
				mockMvc.perform(post("/cart/items").session(session).contentType(MediaType.APPLICATION_JSON)
						.content("{\"sku\":\"P1001\"}")).andExpect(status().isOk());
				mockMvc.perform(get("/cart/items/count").session(session)).andExpect(status().isOk());
			}
		});
	}

	@Test
	public void authenticatedMyAccount() throws Exception
	{
		final MockHttpSession session = login(CUSTOMER_SIVA, CUSTOMER_SIVA_PWD);
		time("site.GET /myAccount (authenticated)", new Request() {
			public void perform() throws Exception {
				mockMvc.perform(get("/myAccount").session(session)).andExpect(status().isOk());
			}
		});
	}

	@Test
	public void loginRoundTrip() throws Exception
	{
		time("site.POST /login (BCrypt)", new Request() {
			public void perform() throws Exception {
				login(CUSTOMER_RAMU, CUSTOMER_RAMU_PWD);
			}
		});
	}
}
