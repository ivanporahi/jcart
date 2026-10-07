package com.sivalabs.jcart.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.sivalabs.jcart.JCartCoreApplication;
import com.sivalabs.jcart.entities.Category;
import com.sivalabs.jcart.entities.Product;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = JCartCoreApplication.class)
@Transactional
public class CatalogServiceStorefrontTest
{
	@Autowired CatalogService catalogService;
	@Autowired CategoryRepository categoryRepository;
	@Autowired ProductRepository productRepository;

	private Category enabledCategory;

	@Before
	public void setUp()
	{
		enabledCategory = saveCategory("TstEnabledCat", false);
		Category disabledCategory = saveCategory("TstDisabledCat", true);
		saveProduct("TST-ACTIVE", enabledCategory, false);
		saveProduct("TST-DISABLED", enabledCategory, true);
		saveProduct("TST-IN-DISABLED-CAT", disabledCategory, false);
		saveProduct("TST-NO-CAT", null, false);
	}

	@Test
	public void activeCategoriesExcludeDisabledOnes()
	{
		List<String> names = categoryNames(catalogService.getActiveCategories());
		assertTrue(names.contains("TstEnabledCat"));
		assertFalse(names.contains("TstDisabledCat"));
		assertNotNull(catalogService.getActiveCategoryByName("TstEnabledCat"));
		assertNull(catalogService.getActiveCategoryByName("TstDisabledCat"));
	}

	@Test
	public void activeProductBySkuExcludesDisabledProductsAndDisabledCategories()
	{
		assertNotNull(catalogService.getActiveProductBySku("TST-ACTIVE"));
		assertNotNull(catalogService.getActiveProductBySku("TST-NO-CAT"));
		assertNull(catalogService.getActiveProductBySku("TST-DISABLED"));
		assertNull(catalogService.getActiveProductBySku("TST-IN-DISABLED-CAT"));
		assertNull(catalogService.getActiveProductBySku("TST-UNKNOWN"));
	}

	@Test
	public void activeProductsByCategoryExcludeDisabledProducts()
	{
		List<String> skus = skus(catalogService.getActiveProductsByCategory(enabledCategory));
		assertEquals(1, skus.size());
		assertEquals("TST-ACTIVE", skus.get(0));
	}

	@Test
	public void activeSearchExcludesDisabledProductsAndDisabledCategories()
	{
		List<String> skus = skus(catalogService.searchActiveProducts("TST-"));
		assertEquals(2, skus.size());
		assertTrue(skus.contains("TST-ACTIVE"));
		assertTrue(skus.contains("TST-NO-CAT"));
	}

	@Test
	public void adminFacingMethodsStillReturnDisabledItems()
	{
		List<String> allSkus = skus(catalogService.getAllProducts());
		assertTrue(allSkus.contains("TST-DISABLED"));
		assertTrue(allSkus.contains("TST-IN-DISABLED-CAT"));
		assertNotNull(catalogService.getProductBySku("TST-DISABLED"));
		assertNotNull(catalogService.getProductBySku("TST-IN-DISABLED-CAT"));
		assertEquals(4, catalogService.searchProducts("TST-").size());
		assertTrue(categoryNames(catalogService.getAllCategories()).contains("TstDisabledCat"));
		assertNotNull(catalogService.getCategoryByName("TstDisabledCat"));
	}

	private Category saveCategory(String name, boolean disabled)
	{
		Category category = new Category();
		category.setName(name);
		category.setDisabled(disabled);
		return categoryRepository.save(category);
	}

	private Product saveProduct(String sku, Category category, boolean disabled)
	{
		Product product = new Product();
		product.setSku(sku);
		product.setName(sku);
		product.setPrice(new BigDecimal("10.00"));
		product.setCategory(category);
		product.setDisabled(disabled);
		return productRepository.save(product);
	}

	private static List<String> skus(List<Product> products)
	{
		List<String> skus = new ArrayList<>();
		for (Product product : products) {
			skus.add(product.getSku());
		}
		return skus;
	}

	private static List<String> categoryNames(List<Category> categories)
	{
		List<String> names = new ArrayList<>();
		for (Category category : categories) {
			names.add(category.getName());
		}
		return names;
	}
}
