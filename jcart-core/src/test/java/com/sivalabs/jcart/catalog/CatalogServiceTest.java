package com.sivalabs.jcart.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sivalabs.jcart.AbstractCoreIntegrationTest;
import com.sivalabs.jcart.JCartException;
import com.sivalabs.jcart.entities.Category;
import com.sivalabs.jcart.entities.Product;

@Transactional
public class CatalogServiceTest extends AbstractCoreIntegrationTest
{
	@Autowired CatalogService catalogService;

	@Test
	public void getAllCategoriesReturnsSeedCategories()
	{
		List<Category> categories = catalogService.getAllCategories();
		assertEquals(3, categories.size());
	}

	@Test
	public void getAllProductsReturnsSeedProducts()
	{
		assertEquals(25, catalogService.getAllProducts().size());
	}

	@Test
	public void getCategoryByNameAndById()
	{
		Category flowers = catalogService.getCategoryByName("Flowers");
		assertNotNull(flowers);
		assertEquals(Integer.valueOf(1), flowers.getDisplayOrder());
		assertFalse(flowers.isDisabled());
		assertEquals(flowers.getId(), catalogService.getCategoryById(flowers.getId()).getId());
		assertNull(catalogService.getCategoryByName("DoesNotExist"));
		assertNull(catalogService.getCategoryById(999999));
	}

	@Test
	public void categoryHasItsProducts()
	{
		// 25 products distributed round-robin over 3 categories: 9/8/8
		Category flowers = catalogService.getCategoryByName("Flowers");
		Category toys = catalogService.getCategoryByName("Toys");
		Category birds = catalogService.getCategoryByName("Birds");
		assertEquals(9, flowers.getProducts().size());
		assertEquals(8, toys.getProducts().size());
		assertEquals(8, birds.getProducts().size());
	}

	@Test
	public void createCategoryPersistsAndRejectsDuplicates()
	{
		Category c = new Category();
		c.setName("Gadgets");
		c.setDescription("Electronic gadgets");
		c.setDisplayOrder(10);
		Category saved = catalogService.createCategory(c);
		assertNotNull(saved.getId());
		assertEquals(4, catalogService.getAllCategories().size());

		Category dup = new Category();
		dup.setName("Gadgets");
		try
		{
			catalogService.createCategory(dup);
			fail("Expected JCartException for duplicate category");
		}
		catch (JCartException e)
		{
			assertEquals("Category Gadgets already exist", e.getMessage());
		}
	}

	@Test
	public void updateCategoryChangesMutableFieldsOnly()
	{
		Category flowers = catalogService.getCategoryByName("Flowers");
		Category update = new Category();
		update.setId(flowers.getId());
		update.setName("RenamedShouldBeIgnored");
		update.setDescription("New description");
		update.setDisplayOrder(42);
		update.setDisabled(true);
		Category updated = catalogService.updateCategory(update);
		assertEquals("Flowers", updated.getName());
		assertEquals("New description", updated.getDescription());
		assertEquals(Integer.valueOf(42), updated.getDisplayOrder());
		assertTrue(updated.isDisabled());
	}

	@Test
	public void updateUnknownCategoryThrows()
	{
		Category update = new Category();
		update.setId(999999);
		assertThrows(JCartException.class, () -> catalogService.updateCategory(update));
	}

	@Test
	public void getProductBySkuAndById()
	{
		Product p = catalogService.getProductBySku("P1001");
		assertNotNull(p);
		assertEquals("Quilling Toy 1", p.getName());
		assertEquals(new BigDecimal("430.00"), p.getPrice());
		assertEquals("Flowers", p.getCategory().getName());
		assertEquals(p.getId(), catalogService.getProductById(p.getId()).getId());
		assertNull(catalogService.getProductBySku("NOPE"));
	}

	@Test
	public void searchProductsMatchesNameSkuOrDescription()
	{
		// "Toy 1" matches Toy 1 and Toy 10..19 -> 11 products
		assertEquals(11, catalogService.searchProducts("Toy 1").size());
		assertEquals(1, catalogService.searchProducts("P1025").size());
		assertEquals(25, catalogService.searchProducts("").size());
		assertEquals(0, catalogService.searchProducts("zzz").size());
	}

	@Test
	public void createProductPersistsWithCategory()
	{
		Product p = new Product();
		p.setSku("P9999");
		p.setName("New Product");
		p.setDescription("desc");
		p.setPrice(new BigDecimal("12.50"));
		p.setCategory(catalogService.getCategoryByName("Toys"));
		Product saved = catalogService.createProduct(p);
		assertNotNull(saved.getId());
		assertEquals(26, catalogService.getAllProducts().size());
		assertEquals("Toys", catalogService.getProductBySku("P9999").getCategory().getName());
	}

	@Test
	public void createProductDuplicateCheckUsesNameNotSku()
	{
		// Characterization of current behaviour: CatalogService.createProduct looks up
		// the existing product by product.getName() (not SKU). A duplicate SKU with a
		// different name is therefore NOT rejected by the service (DB unique constraint
		// catches it on flush instead). Keep this behaviour identical after the upgrade
		// or fix it deliberately in both baselines.
		Product p = new Product();
		p.setSku("P9998");
		p.setName("P1001");
		p.setPrice(BigDecimal.ONE);
		p.setCategory(catalogService.getCategoryByName("Toys"));
		try
		{
			catalogService.createProduct(p);
			fail("Expected JCartException because name equals an existing SKU");
		}
		catch (JCartException e)
		{
			assertEquals("Product SKU P9998 already exist", e.getMessage());
		}
	}

	@Test
	public void updateProductChangesMutableFields()
	{
		Product existing = catalogService.getProductBySku("P1002");
		Product update = new Product();
		update.setId(existing.getId());
		update.setSku("IGNORED");
		update.setName("IGNORED");
		update.setDescription("Updated description");
		update.setPrice(new BigDecimal("99.99"));
		update.setDisabled(true);
		Category cat = new Category();
		cat.setId(catalogService.getCategoryByName("Birds").getId());
		update.setCategory(cat);

		Product updated = catalogService.updateProduct(update);
		assertEquals("P1002", updated.getSku());
		assertEquals("Quilling Toy 2", updated.getName());
		assertEquals("Updated description", updated.getDescription());
		assertEquals(new BigDecimal("99.99"), updated.getPrice());
		assertTrue(updated.isDisabled());
		assertEquals("Birds", updated.getCategory().getName());
	}
}
