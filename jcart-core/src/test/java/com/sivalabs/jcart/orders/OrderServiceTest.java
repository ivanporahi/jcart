package com.sivalabs.jcart.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sivalabs.jcart.AbstractCoreIntegrationTest;
import com.sivalabs.jcart.catalog.CatalogService;
import com.sivalabs.jcart.customers.CustomerService;
import com.sivalabs.jcart.entities.Address;
import com.sivalabs.jcart.entities.Order;
import com.sivalabs.jcart.entities.OrderItem;
import com.sivalabs.jcart.entities.OrderStatus;
import com.sivalabs.jcart.entities.Payment;
import com.sivalabs.jcart.entities.Product;

@Transactional
public class OrderServiceTest extends AbstractCoreIntegrationTest
{
	@Autowired OrderService orderService;
	@Autowired CatalogService catalogService;
	@Autowired CustomerService customerService;

	@Test
	public void seedOrder()
	{
		assertEquals(1, orderService.getAllOrders().size());
		Order order = orderService.getOrder("1447737431927");
		assertNotNull(order);
		assertEquals(OrderStatus.NEW, order.getStatus());
		assertEquals("sivaprasadreddy.k@gmail.com", order.getCustomer().getEmail());
		assertEquals("Hyderabad", order.getDeliveryAddress().getCity());
		assertEquals("Hitech City", order.getBillingAddress().getAddressLine1());
		assertEquals("1111111111111111", order.getPayment().getCcNumber());
		assertEquals(1, order.getItems().size());
		OrderItem item = order.getItems().iterator().next();
		assertEquals("P1001", item.getProduct().getSku());
		assertEquals(1, item.getQuantity());
		assertEquals(new BigDecimal("430.00"), item.getPrice());
		assertNull(orderService.getOrder("does-not-exist"));
	}

	@Test
	public void createOrderGeneratesNumberAndCascades()
	{
		Order order = new Order();
		order.setCustomer(customerService.getCustomerByEmail("ramu@gmail.com"));
		Address addr = new Address();
		addr.setAddressLine1("Line 1");
		addr.setCity("City");
		addr.setState("State");
		addr.setZipCode("00000");
		addr.setCountry("Country");
		order.setDeliveryAddress(addr);
		Address billing = new Address();
		billing.setAddressLine1("Line 1");
		billing.setCity("City");
		billing.setState("State");
		billing.setZipCode("00000");
		billing.setCountry("Country");
		order.setBillingAddress(billing);
		Payment payment = new Payment();
		payment.setCcNumber("4444333322221111");
		payment.setCvv("123");
		order.setPayment(payment);

		Product p1 = catalogService.getProductBySku("P1003");
		Product p2 = catalogService.getProductBySku("P1004");
		Set<OrderItem> items = new HashSet<OrderItem>();
		items.add(item(order, p1, 2));
		items.add(item(order, p2, 1));
		order.setItems(items);

		long before = System.currentTimeMillis();
		Order saved = orderService.createOrder(order);
		assertNotNull(saved.getId());
		assertNotNull(saved.getOrderNumber());
		long num = Long.parseLong(saved.getOrderNumber());
		assertTrue(num >= before && num <= System.currentTimeMillis());
		assertEquals(OrderStatus.NEW, saved.getStatus());
		assertNotNull(saved.getCreatedOn());

		assertEquals(2, orderService.getAllOrders().size());
		Order loaded = orderService.getOrder(saved.getOrderNumber());
		assertEquals(2, loaded.getItems().size());
		assertNotNull(loaded.getDeliveryAddress().getId());
		assertNotNull(loaded.getPayment().getId());
		assertEquals(1, customerService.getCustomerOrders("ramu@gmail.com").size());
	}

	@Test
	public void updateOrderChangesStatusOnly()
	{
		Order update = new Order();
		update.setOrderNumber("1447737431927");
		update.setStatus(OrderStatus.COMPLETED);
		Order updated = orderService.updateOrder(update);
		assertEquals(OrderStatus.COMPLETED, updated.getStatus());
		assertEquals("sivaprasadreddy.k@gmail.com", updated.getCustomer().getEmail());
		assertEquals(OrderStatus.COMPLETED, orderService.getOrder("1447737431927").getStatus());
	}

	private OrderItem item(Order order, Product product, int qty)
	{
		OrderItem item = new OrderItem();
		item.setOrder(order);
		item.setProduct(product);
		item.setQuantity(qty);
		item.setPrice(product.getPrice());
		return item;
	}
}
