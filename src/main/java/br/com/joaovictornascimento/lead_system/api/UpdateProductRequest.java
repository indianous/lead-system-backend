package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.ProductType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateProductRequest(@NotBlank String name, @NotNull ProductType type, String description,
		Integer minPriceCents, Integer maxPriceCents, boolean active) {

	@AssertTrue(message = "minPriceCents não pode ser maior que maxPriceCents")
	public boolean isPriceRangeValid() {
		return minPriceCents == null || maxPriceCents == null || minPriceCents <= maxPriceCents;
	}

}
