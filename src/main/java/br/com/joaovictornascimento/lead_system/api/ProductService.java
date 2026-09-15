package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.Product;
import br.com.joaovictornascimento.lead_system.domain.ProductRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	public ProductResponse create(CreateProductRequest request) {
		Product product = new Product(request.name(), request.type(), request.description(),
				request.minPriceCents(), request.maxPriceCents());
		return toResponse(productRepository.save(product));
	}

	public List<ProductResponse> findAll() {
		return productRepository.findAll().stream().map(this::toResponse).toList();
	}

	public ProductResponse update(UUID id, UpdateProductRequest request) {
		Product product = findProductOrThrow(id);
		product.setName(request.name());
		product.setType(request.type());
		product.setDescription(request.description());
		product.setMinPriceCents(request.minPriceCents());
		product.setMaxPriceCents(request.maxPriceCents());
		product.setActive(request.active());
		return toResponse(productRepository.save(product));
	}

	private Product findProductOrThrow(UUID id) {
		return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
	}

	private ProductResponse toResponse(Product product) {
		return new ProductResponse(product.getId(), product.getName(), product.getType(), product.getDescription(),
				product.getMinPriceCents(), product.getMaxPriceCents(), product.isActive(), product.getCreatedAt());
	}

}
