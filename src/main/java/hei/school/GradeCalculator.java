package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

@Component
public class GradeCalculator {

    private static final BigDecimal TWENTY = new BigDecimal("20");
    private static final BigDecimal DAILY_PENALTY = new BigDecimal("0.10");
    private static final BigDecimal MAX_PENALTY = new BigDecimal("0.50");

    public int daysLate(LocalDate deadline, LocalDateTime submittedAt) {
        if (deadline == null || submittedAt == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(deadline, submittedAt.toLocalDate());
        return days > 0 ? (int) days : 0;
    }

    public BigDecimal penaltyRate(LocalDate deadline, LocalDateTime submittedAt) {
        BigDecimal rate = DAILY_PENALTY.multiply(BigDecimal.valueOf(daysLate(deadline, submittedAt)));
        return rate.min(MAX_PENALTY);
    }

    public BigDecimal penalizedGrade(double rawPoints, double maxPoints, LocalDate deadline, LocalDateTime submittedAt) {
        BigDecimal rawGrade = BigDecimal.valueOf(rawPoints)
                .multiply(TWENTY)
                .divide(BigDecimal.valueOf(maxPoints), 10, RoundingMode.HALF_UP);
        BigDecimal factor = BigDecimal.ONE.subtract(penaltyRate(deadline, submittedAt));
        BigDecimal grade = rawGrade.multiply(factor).setScale(1, RoundingMode.HALF_UP);
        return grade.max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
    }
}
