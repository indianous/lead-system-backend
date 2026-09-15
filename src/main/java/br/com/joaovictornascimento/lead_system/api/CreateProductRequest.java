package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.ProductType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProductRequest(@NotBlank String name, @NotNull ProductType type, String description,
		Integer minPriceCents, Integer maxPriceCents) {

	@AssertTrue(message = "minPriceCents não pode ser maior que maxPriceCents")
	public boolean isPriceRangeValid() {
		return minPriceCents == null || maxPriceCents == null || minPriceCents <= maxPriceCents;
	}

}
