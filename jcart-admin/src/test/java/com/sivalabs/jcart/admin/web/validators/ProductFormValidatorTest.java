package com.sivalabs.jcart.admin.web.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import com.sivalabs.jcart.admin.web.models.ProductForm;
import com.sivalabs.jcart.catalog.CatalogService;
import com.sivalabs.jcart.entities.Product;

public class ProductFormValidatorTest
{
	private ProductFormValidator validator;
	private CatalogService catalogService;

	@Before
	public void setUp()
	{
		catalogService = mock(CatalogService.class);
		validator = new ProductFormValidator();
		validator.catalogService = catalogService;
		when(catalogService.getProductBySku("P1001")).thenReturn(product(1, "P1001"));
		when(catalogService.getProductBySku("P1002")).thenReturn(product(2, "P1002"));
	}

	@Test
	public void createWithExistingSkuIsRejected()
	{
		Errors errors = validate(form(null, "P1001"));
		assertTrue(errors.hasFieldErrors("sku"));
		assertEquals("error.exists", errors.getFieldError("sku").getCode());
	}

	@Test
	public void createWithNewSkuIsAccepted()
	{
		assertFalse(validate(form(null, "P9999")).hasErrors());
	}

	@Test
	public void updateKeepingOwnSkuIsAccepted()
	{
		assertFalse(validate(form(1, "P1001")).hasErrors());
	}

	@Test
	public void updateToAnotherProductsSkuIsRejected()
	{
		Errors errors = validate(form(1, "P1002"));
		assertTrue(errors.hasFieldErrors("sku"));
		assertEquals("error.exists", errors.getFieldError("sku").getCode());
	}

	private Errors validate(ProductForm form)
	{
		Errors errors = new BeanPropertyBindingResult(form, "product");
		validator.validate(form, errors);
		return errors;
	}

	private static ProductForm form(Integer id, String sku)
	{
		ProductForm form = new ProductForm();
		form.setId(id);
		form.setSku(sku);
		return form;
	}

	private static Product product(Integer id, String sku)
	{
		Product p = new Product();
		p.setId(id);
		p.setSku(sku);
		return p;
	}
}
