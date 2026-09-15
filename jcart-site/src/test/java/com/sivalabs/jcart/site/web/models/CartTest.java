package com.sivalabs.jcart.site.web.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.sivalabs.jcart.entities.Product;

/**
 * Pure unit tests (no Spring) for the session cart model.
 */
public class CartTest
{
	private static Product product(String sku, String price)
	{
		Product p = new Product();
		p.setSku(sku);
		p.setName("Product " + sku);
		p.setPrice(new BigDecimal(price));
		return p;
	}

	@Test
	public void newCartIsEmpty()
	{
		Cart cart = new Cart();
		assertTrue(cart.getItems().isEmpty());
		assertEquals(0, cart.getItemCount());
		assertEquals(0, BigDecimal.ZERO.compareTo(cart.getTotalAmount()));
	}

	@Test
	public void addItemAppendsAndIncrementsExistingSku()
	{
		Cart cart = new Cart();
		cart.addItem(product("A", "10.00"));
		cart.addItem(product("B", "2.50"));
		cart.addItem(product("A", "10.00"));
		assertEquals(2, cart.getItems().size());
		assertEquals(3, cart.getItemCount());
		assertEquals(2, cart.getItems().get(0).getQuantity());
		assertEquals(new BigDecimal("22.50"), cart.getTotalAmount());
	}

	@Test
	public void updateItemQuantityReplacesQuantity()
	{
		Cart cart = new Cart();
		cart.addItem(product("A", "10.00"));
		cart.updateItemQuantity(product("A", "10.00"), 5);
		assertEquals(5, cart.getItemCount());
		assertEquals(new BigDecimal("50.00"), cart.getTotalAmount());
		// unknown sku is a no-op
		cart.updateItemQuantity(product("Z", "1"), 3);
		assertEquals(1, cart.getItems().size());
	}

	@Test
	public void removeItemBySku()
	{
		Cart cart = new Cart();
		cart.addItem(product("A", "10.00"));
		cart.addItem(product("B", "2.50"));
		cart.removeItem("A");
		assertEquals(1, cart.getItems().size());
		assertEquals("B", cart.getItems().get(0).getProduct().getSku());
		cart.removeItem("does-not-exist");
		assertEquals(1, cart.getItems().size());
	}

	@Test
	public void clearItems()
	{
		Cart cart = new Cart();
		cart.addItem(product("A", "10.00"));
		cart.clearItems();
		assertEquals(0, cart.getItemCount());
		assertEquals(0, BigDecimal.ZERO.compareTo(cart.getTotalAmount()));
	}

	@Test
	public void lineItemSubTotal()
	{
		LineItem item = new LineItem(product("A", "19.99"), 3);
		assertEquals(new BigDecimal("59.97"), item.getSubTotal());
	}
}
