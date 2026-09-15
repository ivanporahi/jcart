package com.sivalabs.jcart.customers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sivalabs.jcart.AbstractCoreIntegrationTest;
import com.sivalabs.jcart.entities.Customer;
import com.sivalabs.jcart.entities.Order;

@Transactional
public class CustomerServiceTest extends AbstractCoreIntegrationTest
{
	@Autowired CustomerService customerService;

	@Test
	public void seedCustomers()
	{
		assertEquals(2, customerService.getAllCustomers().size());
		Customer siva = customerService.getCustomerByEmail("sivaprasadreddy.k@gmail.com");
		assertNotNull(siva);
		assertEquals("Siva", siva.getFirstName());
		assertEquals("K", siva.getLastName());
		assertEquals("999999999", siva.getPhone());
		assertEquals(siva.getId(), customerService.getCustomerById(siva.getId()).getId());
		assertNull(customerService.getCustomerByEmail("nobody@gmail.com"));
	}

	@Test
	public void customerOrders()
	{
		List<Order> orders = customerService.getCustomerOrders("sivaprasadreddy.k@gmail.com");
		assertEquals(1, orders.size());
		assertEquals("1447737431927", orders.get(0).getOrderNumber());
		assertEquals(0, customerService.getCustomerOrders("ramu@gmail.com").size());
	}

	@Test
	public void createCustomer()
	{
		Customer c = new Customer();
		c.setFirstName("New");
		c.setLastName("Customer");
		c.setEmail("new.customer@gmail.com");
		c.setPassword("encoded");
		c.setPhone("123");
		Customer saved = customerService.createCustomer(c);
		assertNotNull(saved.getId());
		assertEquals(3, customerService.getAllCustomers().size());
		assertEquals("New", customerService.getCustomerByEmail("new.customer@gmail.com").getFirstName());
	}
}
