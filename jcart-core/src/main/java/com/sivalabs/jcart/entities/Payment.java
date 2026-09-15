/**
 * 
 */
package com.sivalabs.jcart.entities;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * @author Siva
 *
 */
@Entity
@Table(name="payments")
public class Payment implements Serializable
{
	private static final long serialVersionUID = 1L;
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	private Integer id;
	@Column(name="cc_number")
	private String ccNumber;
	private String cvv;
	private BigDecimal amount;
	
	public Integer getId()
	{
		return id;
	}
	public void setId(Integer id)
	{
		this.id = id;
	}
	public String getCcNumber()
	{
		return ccNumber;
	}
	public void setCcNumber(String ccNumber)
	{
		this.ccNumber = ccNumber;
	}
	public String getCvv()
	{
		return cvv;
	}
	public void setCvv(String cvv)
	{
		this.cvv = cvv;
	}
	public BigDecimal getAmount()
	{
		return amount;
	}
	public void setAmount(BigDecimal amount)
	{
		this.amount = amount;
	}
		
}
