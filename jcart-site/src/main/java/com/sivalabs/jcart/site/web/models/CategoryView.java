/**
 * 
 */
package com.sivalabs.jcart.site.web.models;

import java.util.List;

import com.sivalabs.jcart.entities.Category;
import com.sivalabs.jcart.entities.Product;

/**
 * Read-only view of a category for the storefront, holding only the products
 * that may be shown. Avoids mutating the managed Category entity.
 */
public class CategoryView
{
	private final String name;
	private final String description;
	private final List<Product> products;
	
	public CategoryView(Category category, List<Product> products)
	{
		this.name = category.getName();
		this.description = category.getDescription();
		this.products = products;
	}
	
	public String getName()
	{
		return name;
	}
	public String getDescription()
	{
		return description;
	}
	public List<Product> getProducts()
	{
		return products;
	}
}
