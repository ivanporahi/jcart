package com.sivalabs.jcart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sivalabs.jcart.catalog.CatalogService;
import com.sivalabs.jcart.orders.OrderService;
import com.sivalabs.jcart.security.SecurityService;

/**
 * Micro-benchmark baseline for the service layer. Prints timings in a
 * machine-readable "PERF <name> <ms>" format so before/after upgrade runs can be
 * diffed (grep PERF target/surefire-reports/*-output.txt). Assertions only guard
 * against gross regressions; the value lies in the printed numbers.
 */
public class CorePerformanceBaselineTest extends AbstractCoreIntegrationTest
{
	private static final int ITERATIONS = 200;
	private static final long MAX_AVG_MS = 50;

	@Autowired CatalogService catalogService;
	@Autowired SecurityService securityService;
	@Autowired OrderService orderService;

	@Test
	public void getAllProducts()
	{
		time("core.getAllProducts", new Runnable() { public void run() { catalogService.getAllProducts(); } });
	}

	@Test
	public void getAllCategoriesWithProducts()
	{
		time("core.getAllCategories", new Runnable() { public void run() { catalogService.getAllCategories(); } });
	}

	@Test
	public void searchProducts()
	{
		time("core.searchProducts", new Runnable() { public void run() { catalogService.searchProducts("Toy"); } });
	}

	@Test
	public void getProductBySku()
	{
		time("core.getProductBySku", new Runnable() { public void run() { catalogService.getProductBySku("P1010"); } });
	}

	@Test
	public void findUserByEmail()
	{
		time("core.findUserByEmail", new Runnable() { public void run() { securityService.findUserByEmail("superadmin@gmail.com"); } });
	}

	@Test
	public void getOrderByNumber()
	{
		time("core.getOrder", new Runnable() { public void run() { orderService.getOrder("1447737431927"); } });
	}

	private void time(String name, Runnable r)
	{
		for (int i = 0; i < 20; i++) r.run(); // warm-up
		long start = System.nanoTime();
		for (int i = 0; i < ITERATIONS; i++) r.run();
		double avgMs = (System.nanoTime() - start) / 1_000_000.0 / ITERATIONS;
		System.out.println(String.format("PERF %s avg_ms=%.3f iterations=%d", name, avgMs, ITERATIONS));
		assertTrue(name + " avg " + avgMs + "ms exceeds " + MAX_AVG_MS + "ms", avgMs < MAX_AVG_MS);
	}
}
