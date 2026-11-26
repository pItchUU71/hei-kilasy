package hei.school;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubmissionController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private GradeCalculator gradeCalculator;

    @Autowired
    private SmsSender smsSender;

    @Autowired
    private ExerciseTimer exerciseTimer;

    @PostMapping("/exercises/{id}/submissions")
    public ResponseEntity<?> submit(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (body.get("studentId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "studentId est obligatoire"));
        }
        long studentId = ((Number) body.get("studentId")).longValue();
        String content = (String) body.get("content");
        String repoUrl = (String) body.get("repoUrl");
        if ((content == null || content.isBlank()) && (repoUrl == null || repoUrl.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Il faut du code ou un lien vers un dépôt GitHub"));
        }
        if (repoUrl != null && !repoUrl.isBlank() && !repoUrl.startsWith("https://github.com/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le lien doit pointer vers GitHub"));
        }

        List<Map<String, Object>> exercises = jdbcTemplate.queryForList("SELECT * FROM exercise WHERE id = ?", id);
        if (exercises.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Exercice introuvable"));
        }
        List<Map<String, Object>> students = jdbcTemplate.queryForList("SELECT * FROM student WHERE id = ?", studentId);
        if (students.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Étudiant introuvable"));
        }
        Integer already = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission WHERE exercise_id = ? AND student_id = ?", Integer.class, id, studentId);
        if (already != null && already > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "Cet étudiant a déjà soumis cet exercice"));
        }

        Map<String, Object> exercise = exercises.get(0);
        Object deadlineValue = exercise.get("deadline");
        LocalDate deadline = deadlineValue == null ? null : ((java.sql.Date) deadlineValue).toLocalDate();
        Object durationValue = exercise.get("duration_minutes");
        Integer durationMinutes = durationValue == null ? null : ((Number) durationValue).intValue();

        LocalDateTime now = LocalDateTime.now();
        BigDecimal timeUsedMinutes = null;
        if (durationMinutes != null) {
            List<Map<String, Object>> starts = jdbcTemplate.queryForList(
                    "SELECT started_at FROM exercise_start WHERE exercise_id = ? AND student_id = ?", id, studentId);
            if (starts.isEmpty()) {
                return ResponseEntity.status(422)
                        .body(Map.of("error", "You must start the exercise before submitting"));
            }
            LocalDateTime startedAt = ((Timestamp) starts.get(0).get("started_at")).toLocalDateTime();
            if (exerciseTimer.isExpired(startedAt, durationMinutes, now)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "The time limit has expired: the submission had to be sent before the end of the duration");
                error.put("durationMinutes", durationMinutes);
                error.put("endsAt", exerciseTimer.endsAt(startedAt, durationMinutes));
                return ResponseEntity.status(422).body(error);
            }
            timeUsedMinutes = exerciseTimer.minutesUsed(startedAt, now);
        }

        boolean late = deadline != null && now.toLocalDate().isAfter(deadline);

        jdbcTemplate.update(
                "INSERT INTO submission (exercise_id, student_id, content, repo_url, submitted_at) VALUES (?, ?, ?, ?, ?)",
                id, studentId, content, repoUrl, Timestamp.valueOf(now));
        Long submissionId = jdbcTemplate.queryForObject("SELECT max(id) FROM submission", Long.class);

        Map<String, Object> result = new HashMap<>();
        result.put("id", submissionId);
        result.put("submittedAt", now);
        result.put("late", late);
        if (timeUsedMinutes != null) {
            result.put("timeUsedMinutes", timeUsedMinutes);
        }
        result.put("message", late ? "Soumission enregistrée, mais en retard" : "Soumission enregistrée");
        return ResponseEntity.status(201).body(result);
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<?> get(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.*, e.title, e.deadline, e.max_points FROM submission s JOIN exercise e ON e.id = s.exercise_id WHERE s.id = ?",
                id);
        if (rows.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Soumission introuvable"));
        }
        Map<String, Object> row = rows.get(0);
        Object deadlineValue = row.get("deadline");
        LocalDate deadline = deadlineValue == null ? null : ((java.sql.Date) deadlineValue).toLocalDate();
        LocalDateTime submittedAt = ((Timestamp) row.get("submitted_at")).toLocalDateTime();

        Map<String, Object> result = new HashMap<>();
        result.put("id", row.get("id"));
        result.put("exercise", row.get("title"));
        result.put("studentId", row.get("student_id"));
        result.put("content", row.get("content"));
        result.put("repoUrl", row.get("repo_url"));
        result.put("submittedAt", submittedAt);
        result.put("late", deadline != null && submittedAt.toLocalDate().isAfter(deadline));
        if (row.get("raw_points") != null) {
            double raw = ((BigDecimal) row.get("raw_points")).doubleValue();
            double max = ((BigDecimal) row.get("max_points")).doubleValue();
            result.put("rawPoints", raw);
            result.put("grade", gradeCalculator.penalizedGrade(raw, max, deadline, submittedAt).doubleValue());
            result.put("feedback", row.get("feedback"));
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/submissions/{id}/grade")
    public ResponseEntity<?> grade(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (body.get("points") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "points est obligatoire"));
        }
        double points = ((Number) body.get("points")).doubleValue();
        String feedback = (String) body.get("feedback");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.*, e.title, e.deadline, e.max_points, st.full_name, st.email, st.phone "
                        + "FROM submission s JOIN exercise e ON e.id = s.exercise_id JOIN student st ON st.id = s.student_id "
                        + "WHERE s.id = ?",
                id);
        if (rows.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Soumission introuvable"));
        }
        Map<String, Object> row = rows.get(0);
        double max = ((BigDecimal) row.get("max_points")).doubleValue();
        if (points < 0 || points > max) {
            return ResponseEntity.badRequest().body(Map.of("error", "Les points doivent être entre 0 et " + max));
        }

        jdbcTemplate.update("UPDATE submission SET raw_points = ?, feedback = ?, graded_at = now() WHERE id = ?",
                points, feedback, id);

        Object deadlineValue = row.get("deadline");
        LocalDate deadline = deadlineValue == null ? null : ((java.sql.Date) deadlineValue).toLocalDate();
        LocalDateTime submittedAt = ((Timestamp) row.get("submitted_at")).toLocalDateTime();
        boolean late = deadline != null && submittedAt.toLocalDate().isAfter(deadline);

        BigDecimal grade = gradeCalculator.penalizedGrade(points, max, deadline, submittedAt);

        // notify the student by email
        String fullName = (String) row.get("full_name");
        String firstName = fullName.split(" ")[0];
        String title = (String) row.get("title");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("notes@kilasy.test");
        message.setTo((String) row.get("email"));
        message.setSubject("Kilasy : nouvelle note pour " + title);
        String text = "Bonjour " + firstName + ",\n\n"
                + "Ta note pour « " + title + " » est disponible : " + grade + "/20.\n";
        if (feedback != null && !feedback.isBlank()) {
            text += "\nCommentaire de l'enseignant : " + feedback + "\n";
        }
        text += "\nL'équipe Kilasy";
        message.setText(text);
        mailSender.send(message);
        System.out.println("Email de note envoyé à " + row.get("email"));

        String phone = (String) row.get("phone");
        if (phone != null && !phone.isBlank()) {
            String smsText = "Votre note pour « " + title + " » est disponible : "
                    + grade.toPlainString().replace('.', ',') + "/20.";
            try {
                smsSender.send(phone, smsText);
            } catch (Exception e) {
                System.out.println("[SMS] Échec de l'envoi à " + phone + " : " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("rawPoints", points);
        result.put("maxPoints", max);
        result.put("grade", grade);
        result.put("late", late);
        result.put("feedback", feedback);
        return ResponseEntity.ok(result);
    }
}
