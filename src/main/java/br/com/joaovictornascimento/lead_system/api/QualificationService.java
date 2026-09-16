package br.com.joaovictornascimento.lead_system.api;

import br.com.joaovictornascimento.lead_system.domain.QualificationScore;
import java.text.Normalizer;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Qualificação automática (Etapa 7) — regra simples e configurável, sujeita a recalibração com
 * dados reais de conversão (ver estudo de caso, seção "Riscos"): pontos por critério (produto de
 * interesse, orçamento, prazo declarado), soma vira LOW/MEDIUM/HIGH.
 */
@Service
public class QualificationService {

	private final int budgetThresholdCents;

	private final List<String> urgentKeywords;

	public QualificationService(@Value("${app.qualification.budget-threshold-cents}") int budgetThresholdCents,
			@Value("${app.qualification.urgent-keywords}") List<String> urgentKeywords) {
		this.budgetThresholdCents = budgetThresholdCents;
		this.urgentKeywords = urgentKeywords.stream().map(this::normalize).toList();
	}

	public QualificationScore calculate(int productsOfInterestCount, Integer estimatedBudgetCents,
			String desiredTimeline) {
		int points = productPoints(productsOfInterestCount) + budgetPoints(estimatedBudgetCents)
				+ timelinePoints(desiredTimeline);
		if (points >= 4) {
			return QualificationScore.HIGH;
		}
		if (points >= 2) {
			return QualificationScore.MEDIUM;
		}
		return QualificationScore.LOW;
	}

	private int productPoints(int productsOfInterestCount) {
		return productsOfInterestCount > 0 ? 1 : 0;
	}

	private int budgetPoints(Integer estimatedBudgetCents) {
		if (estimatedBudgetCents == null) {
			return 0;
		}
		return estimatedBudgetCents >= budgetThresholdCents ? 2 : 1;
	}

	private int timelinePoints(String desiredTimeline) {
		if (desiredTimeline == null || desiredTimeline.isBlank()) {
			return 0;
		}
		String normalized = normalize(desiredTimeline);
		boolean urgent = urgentKeywords.stream().anyMatch(normalized::contains);
		return urgent ? 2 : 1;
	}

	private String normalize(String value) {
		String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutAccents.toLowerCase();
	}

}
