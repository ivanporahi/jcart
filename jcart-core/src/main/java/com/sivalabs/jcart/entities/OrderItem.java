/**
 * 
 */
package com.sivalabs.jcart.entities;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * @author Siva
 *
 */
@Entity
@Table(name="order_items")
public class OrderItem implements Serializable
{
	private static final long serialVersionUID = 1L;
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	private Integer id;
	@ManyToOne
	@JoinColumn(name="product_id")
	private Product product;
	private BigDecimal price;
	private int quantity;
	@ManyToOne
	@JoinColumn(name="order_id")
	private Order order;
	
	public Integer getId()
	{
		return id;
	}
	public void setId(Integer id)
	{
		this.id = id;
	}
	public Product getProduct()
	{
		return product;
	}
	public void setProduct(Product product)
	{
		this.product = product;
	}
	
	public BigDecimal getPrice()
	{
		return price;
	}
	public void setPrice(BigDecimal price)
	{
		this.price = price;
	}
	public Order getOrder()
	{
		return order;
	}
	public void setOrder(Order order)
	{
		this.order = order;
	}
	public int getQuantity()
	{
		return quantity;
	}
	public void setQuantity(int quantity)
	{
		this.quantity = quantity;
	}
	
	public BigDecimal getSubTotal()
	{
		return product.getPrice().multiply(new BigDecimal(quantity));
	}
	
}
