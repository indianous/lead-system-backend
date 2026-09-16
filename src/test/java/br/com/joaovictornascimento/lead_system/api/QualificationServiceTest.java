package br.com.joaovictornascimento.lead_system.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.joaovictornascimento.lead_system.domain.QualificationScore;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualificationServiceTest {

	private static final int BUDGET_THRESHOLD_CENTS = 200_000;

	private final QualificationService service = new QualificationService(BUDGET_THRESHOLD_CENTS,
			List.of("imediato", "urgente", "hoje", "esta semana", "essa semana", "amanha"));

	@Test
	void noCriteriaIsLow() {
		assertThat(service.calculate(0, null, null)).isEqualTo(QualificationScore.LOW);
	}

	@Test
	void onlyProductOfInterestIsLow() {
		assertThat(service.calculate(1, null, null)).isEqualTo(QualificationScore.LOW);
	}

	@Test
	void onlyLowBudgetIsLow() {
		assertThat(service.calculate(0, BUDGET_THRESHOLD_CENTS - 1, null)).isEqualTo(QualificationScore.LOW);
	}

	@Test
	void onlyNonUrgentTimelineIsLow() {
		assertThat(service.calculate(0, null, "em 3 meses")).isEqualTo(QualificationScore.LOW);
	}

	@Test
	void budgetAtThresholdAloneIsMedium() {
		assertThat(service.calculate(0, BUDGET_THRESHOLD_CENTS, null)).isEqualTo(QualificationScore.MEDIUM);
	}

	@Test
	void budgetAboveThresholdAloneIsMedium() {
		assertThat(service.calculate(0, BUDGET_THRESHOLD_CENTS + 100_000, null)).isEqualTo(QualificationScore.MEDIUM);
	}

	@Test
	void urgentTimelineAloneIsMedium() {
		assertThat(service.calculate(0, null, "imediato")).isEqualTo(QualificationScore.MEDIUM);
	}

	@Test
	void productPlusNonUrgentTimelineIsMedium() {
		assertThat(service.calculate(1, null, "em 3 meses")).isEqualTo(QualificationScore.MEDIUM);
	}

	@Test
	void productPlusLowBudgetPlusNonUrgentTimelineIsMedium() {
		assertThat(service.calculate(1, BUDGET_THRESHOLD_CENTS - 1, "em 3 meses")).isEqualTo(QualificationScore.MEDIUM);
	}

	@Test
	void budgetAtThresholdPlusUrgentTimelineIsHigh() {
		assertThat(service.calculate(0, BUDGET_THRESHOLD_CENTS, "hoje")).isEqualTo(QualificationScore.HIGH);
	}

	@Test
	void productPlusBudgetAtThresholdIsMedium() {
		assertThat(service.calculate(1, BUDGET_THRESHOLD_CENTS, null)).isEqualTo(QualificationScore.MEDIUM);
	}

	@Test
	void productPlusBudgetAtThresholdPlusUrgentTimelineIsHigh() {
		assertThat(service.calculate(1, BUDGET_THRESHOLD_CENTS, "imediato")).isEqualTo(QualificationScore.HIGH);
	}

	@Test
	void allCriteriaStrongIsHigh() {
		assertThat(service.calculate(2, BUDGET_THRESHOLD_CENTS + 500_000, "imediato")).isEqualTo(QualificationScore.HIGH);
	}

	@Test
	void keywordMatchIsCaseAndAccentInsensitive() {
		assertThat(service.calculate(0, null, "URGENTE")).isEqualTo(QualificationScore.MEDIUM);
		assertThat(service.calculate(0, null, "Preciso pra amanhã")).isEqualTo(QualificationScore.MEDIUM);
	}

	@Test
	void blankTimelineCountsAsNotInformed() {
		assertThat(service.calculate(0, null, "   ")).isEqualTo(QualificationScore.LOW);
	}

}
