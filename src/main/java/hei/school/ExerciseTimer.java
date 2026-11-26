package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class ExerciseTimer {

    public LocalDateTime endsAt(LocalDateTime startedAt, int durationMinutes) {
        return startedAt.plusMinutes(durationMinutes);
    }

    public boolean isExpired(LocalDateTime startedAt, int durationMinutes, LocalDateTime now) {
        return now.isAfter(endsAt(startedAt, durationMinutes));
    }

    public long remainingSeconds(LocalDateTime startedAt, int durationMinutes, LocalDateTime now) {
        long remaining = Duration.between(now, endsAt(startedAt, durationMinutes)).getSeconds();
        return Math.max(remaining, 0);
    }

    public BigDecimal minutesUsed(LocalDateTime startedAt, LocalDateTime submittedAt) {
        long seconds = Duration.between(startedAt, submittedAt).getSeconds();
        return BigDecimal.valueOf(Math.max(seconds, 0))
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }
}
