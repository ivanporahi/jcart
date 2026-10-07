package com.sivalabs.jcart.admin.web.models;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.Test;

public class ProductFormTest
{
	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private boolean hasSkuViolation(String sku)
	{
		ProductForm form = new ProductForm();
		form.setSku(sku);
		form.setName("Name");
		form.setPrice(new BigDecimal("10.00"));
		form.setCategoryId(1);
		Set<ConstraintViolation<ProductForm>> violations = validator.validateProperty(form, "sku");
		return !violations.isEmpty();
	}

	@Test
	public void acceptsAlphanumericSkus()
	{
		assertFalse(hasSkuViolation("P1001"));
		assertFalse(hasSkuViolation("abc-123_X"));
	}

	@Test
	public void rejectsSkusWithScriptCharacters()
	{
		assertTrue(hasSkuViolation("x');alert(document.cookie);//"));
		assertTrue(hasSkuViolation("x\"><script>alert(1)</script>"));
		assertTrue(hasSkuViolation("a b"));
		assertTrue(hasSkuViolation("a/b"));
	}
}
