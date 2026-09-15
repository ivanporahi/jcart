/**
 * 
 */
package com.sivalabs.jcart.entities;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * @author Siva
 *
 */
@Entity
@Table(name="permissions")
public class Permission
{
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	private Integer id;
	@Column(nullable=false, unique=true)
	private String name;
	@Column(length=1024)
	private String description;
	@ManyToMany(mappedBy="permissions")
	private List<Role> roles;

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
	public List<Role> getRoles()
	{
		return roles;
	}

	public void setRoles(List<Role> roles)
	{
		this.roles = roles;
	}
	
	
}
