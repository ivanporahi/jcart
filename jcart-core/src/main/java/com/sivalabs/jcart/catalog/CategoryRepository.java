/**
 * 
 */
package com.sivalabs.jcart.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sivalabs.jcart.entities.Category;

/**
 * @author Siva
 *
 */
public interface CategoryRepository extends JpaRepository<Category, Integer>{

	Category getByName(String name);

	@Query("select c from Category c where c.disabled = false order by c.displayOrder, c.id")
	List<Category> findActiveCategories();

	@Query("select c from Category c where c.name = ?1 and c.disabled = false")
	Category findActiveByName(String name);

}
