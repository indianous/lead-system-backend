package br.com.joaovictornascimento.lead_system.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('EDIT_CATALOG')")
	public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
		return productService.create(request);
	}

	@GetMapping
	public List<ProductResponse> list() {
		return productService.findAll();
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('EDIT_CATALOG')")
	public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
		return productService.update(id, request);
	}

}
