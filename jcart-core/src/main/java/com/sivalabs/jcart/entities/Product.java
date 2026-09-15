/**
 * 
 */
package com.sivalabs.jcart.entities;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * @author Siva
 *
 */
@Entity
@Table(name="products")
public class Product implements Serializable
{
	private static final long serialVersionUID = 1L;
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	private Integer id;
	@Column(nullable=false, unique=true)
	private String sku;
	@Column(nullable=false)
	private String name;
	private String description;
	@Column(nullable=false)
	private BigDecimal price = new BigDecimal("0.0");
	private String imageUrl;
	private boolean disabled;
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name="created_on")
	private Date createdOn = new Date();
	
	@ManyToOne
	@JoinColumn(name="cat_id")
	private Category category;
	
	public Integer getId()
	{
		return id;
	}
	public void setId(Integer id)
	{
		this.id = id;
	}
	
	public String getSku()
	{
		return sku;
	}
	public void setSku(String sku)
	{
		this.sku = sku;
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
	public BigDecimal getPrice()
	{
		return price;
	}
	public void setPrice(BigDecimal price)
	{
		this.price = price;
	}
	public String getImageUrl()
	{
		return imageUrl;
	}
	public void setImageUrl(String imageUrl)
	{
		this.imageUrl = imageUrl;
	}

	public boolean isDisabled()
	{
		return disabled;
	}
	public void setDisabled(boolean disabled)
	{
		this.disabled = disabled;
	}
	public Date getCreatedOn()
	{
		return createdOn;
	}
	public void setCreatedOn(Date createdOn)
	{
		this.createdOn = createdOn;
	}
	
	public Category getCategory()
	{
		return category;
	}
	public void setCategory(Category category)
	{
		this.category = category;
	}
	
}
