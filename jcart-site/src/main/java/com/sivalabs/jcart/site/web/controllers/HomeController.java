/**
 * 
 */
package com.sivalabs.jcart.site.web.controllers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.sivalabs.jcart.catalog.CatalogService;
import com.sivalabs.jcart.entities.Category;
import com.sivalabs.jcart.entities.Product;
import com.sivalabs.jcart.site.web.models.CategoryView;

/**
 * @author Siva
 *
 */
@Controller
public class HomeController extends JCartSiteBaseController
{	
	@Autowired
	private CatalogService catalogService;
	
	@Override
	protected String getHeaderTitle()
	{
		return "Home";
	}
	
	@RequestMapping("/home")
	public String home(Model model)
	{
		List<CategoryView> previewCategories = new ArrayList<>();
		List<Category> categories = catalogService.getActiveCategories();
		for (Category category : categories)
		{
			List<Product> products = catalogService.getActiveProductsByCategory(category);
			int noOfProductsToDisplay = 4;
			List<Product> previewProducts = new ArrayList<>(
					products.subList(0, Math.min(noOfProductsToDisplay, products.size())));
			previewCategories.add(new CategoryView(category, previewProducts));
		}
		model.addAttribute("categories", previewCategories);
		return "home";
	}
	
	@RequestMapping("/categories/{name}")
	public String category(@PathVariable String name, Model model)
	{
		Category category = catalogService.getActiveCategoryByName(name);
		if(category == null){
			throw new NotFoundException("Category "+name+" not found");
		}
		model.addAttribute("category", new CategoryView(category, catalogService.getActiveProductsByCategory(category)));
		return "category";
	}
	
}
