jQuery(document).ready(function($){

	/*
	 $("#MyButton").bind("click", function() {
		  fHardCodedFunction.apply(this, [someValue]);
	 });
	 */
	//$("#cart-item-count").bind("click", updateCartItemCount);
	updateCartItemCount();

	$(document).on('click', '.add_to_cart_button[data-sku]', function(e){
		e.preventDefault();
		addItemToCart($(this).attr('data-sku'));
	});

	$(document).on('click', '.product-remove a.remove[data-sku]', function(e){
		e.preventDefault();
		removeItemFromCart($(this).attr('data-sku'));
	});

	$(document).on('change', '.product-quantity input[data-sku]', function(){
		updateCartItemQuantity($(this).attr('data-sku'), $(this).val());
	});
});

	function updateCartItemCount()
	{
		$.ajax ({ 
	        url: '/cart/items/count', 
	        type: "GET", 
	        dataType: "json",
	        contentType: "application/json",
	        complete: function(responseData, status, xhttp){ 
	        	$('#cart-item-count').text('('+responseData.responseJSON.count+')');
	        }
	    });
	}

	function addItemToCart(sku)
	{
		$.ajax ({ 
	        url: '/cart/items', 
	        type: "POST", 
	        dataType: "json",
	        contentType: "application/json",
	        data : JSON.stringify({ sku: sku }),
	        complete: function(responseData, status, xhttp){
	        	updateCartItemCount();
	        	/*
	        	$.bootstrapGrowl("Item added to cart", 
	        					{ type: 'info',
	        						offset: {
						    			from: "top",
						    			amount: 50
						    		}
	        					}
	        	);
	        	*/
	        }
	    }); 
	}

	function updateCartItemQuantity(sku, quantity)
	{
		$.ajax ({ 
	        url: '/cart/items', 
	        type: "PUT", 
	        dataType: "json",
	        contentType: "application/json",
	        data : JSON.stringify({ product: { sku: sku }, quantity: quantity }),
	        complete: function(responseData, status, xhttp){ 
	        	updateCartItemCount();        	
	        	location.href = '/cart' 
	        }
	    });
	}

	function removeItemFromCart(sku)
	{
		$.ajax ({ 
	        url: '/cart/items/' + encodeURIComponent(sku), 
	        type: "DELETE", 
	        dataType: "json",
	        contentType: "application/json",
	        complete: function(responseData, status, xhttp){ 
	        	updateCartItemCount();
	        	location.href = '/cart' 
	        }
	    });
	}

