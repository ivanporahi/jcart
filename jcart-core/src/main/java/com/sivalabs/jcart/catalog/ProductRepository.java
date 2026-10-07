/**
 * 
 */
package com.sivalabs.jcart.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sivalabs.jcart.entities.Product;

/**
 * @author Siva
 *
 */
public interface ProductRepository extends JpaRepository<Product, Integer>{

	Product findByName(String name);

	Product findBySku(String sku);
	@Query("select p from Product p where p.name like ?1 or p.sku like ?1 or p.description like ?1")
	List<Product> search(String query);

	@Query("select p from Product p left join p.category c where p.sku = ?1 and p.disabled = false and (c.id is null or c.disabled = false)")
	Product findActiveBySku(String sku);

	@Query("select p from Product p left join p.category c where (p.name like ?1 or p.sku like ?1 or p.description like ?1) and p.disabled = false and (c.id is null or c.disabled = false)")
	List<Product> searchActive(String query);

	@Query("select p from Product p where p.category.id = ?1 and p.disabled = false order by p.id")
	List<Product> findActiveByCategoryId(Integer categoryId);

}
