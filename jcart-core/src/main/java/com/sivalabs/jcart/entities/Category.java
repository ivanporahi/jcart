/**
 * 
 */
package com.sivalabs.jcart.entities;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import jakarta.validation.constraints.NotEmpty;

/**
 * @author Siva
 *
 */
@Entity
@Table(name="categories")
public class Category
{
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	private Integer id;
	@Column(nullable=false, unique=true)
	@NotEmpty
	private String name;
	@Column(length=1024)
	private String description;
	@Column(name="disp_order")
	private Integer displayOrder;
	private boolean disabled;
	@OneToMany(mappedBy="category")
	private Set<Product> products;
	
	public Integer getId()
	{
		return id;
	}
	public void setId(Integer id)
	{
		this.id = id;
	}
	public String getName()
	{
		return name;
	}
	public void setName(String name)
	{
		this.name = name;
	}
	public String getDescription()
	{
		return description;
	}
	public void setDescription(String description)
	{
		this.description = description;
	}
	public Integer getDisplayOrder()
	{
		return displayOrder;
	}
	public void setDisplayOrder(Integer displayOrder)
	{
		this.displayOrder = displayOrder;
	}
	
	public boolean isDisabled()
	{
		return disabled;
	}
	public void setDisabled(boolean disabled)
	{
		this.disabled = disabled;
	}
	public Set<Product> getProducts()
	{
		return products;
	}
	public void setProducts(Set<Product> products)
	{
		this.products = products;
	}
	
}
