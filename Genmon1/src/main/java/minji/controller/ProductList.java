package minji.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import common.controller.AbstractController;

public class ProductList extends AbstractController {

	@Override
	public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		InterProductDAO pdao = new ProductDAO();
		List()
		
		
	//	super.setRedirect(false);
		super.setViewPage("/WEB-INF/minji/productList.jsp");
	}

}
