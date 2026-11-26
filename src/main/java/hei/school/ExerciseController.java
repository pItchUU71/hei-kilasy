package hei.school;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExerciseController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExerciseTimer exerciseTimer;

    @GetMapping("/exercises")
    public List<Exercise> list() {
        return jdbcTemplate.query("SELECT * FROM exercise ORDER BY deadline NULLS LAST, id",
                (rs, i) -> mapExercise(rs));
    }

    @GetMapping("/exercises/{id}")
    public ResponseEntity<?> get(@PathVariable long id) {
        List<Exercise> found = jdbcTemplate.query("SELECT * FROM exercise WHERE id = ?",
                (rs, i) -> mapExercise(rs), id);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Exercice introuvable"));
        }
        return ResponseEntity.ok(found.get(0));
    }

    private Exercise mapExercise(ResultSet rs) throws SQLException {
        Exercise e = new Exercise();
        e.setId(rs.getLong("id"));
        e.setTeacherId(rs.getLong("teacher_id"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        e.setMaxPoints(rs.getDouble("max_points"));
        java.sql.Date deadline = rs.getDate("deadline");
        e.setDeadline(deadline == null ? null : deadline.toLocalDate());
        int durationMinutes = rs.getInt("duration_minutes");
        e.setDurationMinutes(rs.wasNull() ? null : durationMinutes);
        return e;
    }

    @PostMapping("/exercises")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le titre est obligatoire"));
        }
        LocalDate deadline = null;
        Object deadlineValue = body.get("deadline");
        if (deadlineValue != null && !deadlineValue.toString().isBlank()) {
            try {
                deadline = LocalDate.parse(deadlineValue.toString());
            } catch (Exception ex) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "The deadline must use the YYYY-MM-DD format"));
            }
            if (deadline.isBefore(LocalDate.now())) {
                return ResponseEntity.badRequest().body(Map.of("error", "The deadline must be in the future"));
            }
        }

        Integer durationMinutes = null;
        Object durationValue = body.get("durationMinutes") != null ? body.get("durationMinutes") : body.get("duration");
        if (durationValue != null) {
            if (!(durationValue instanceof Number)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "The duration must be a positive number of minutes"));
            }
            double durationAsDouble = ((Number) durationValue).doubleValue();
            int durationAsInt = ((Number) durationValue).intValue();
            if (durationAsDouble != durationAsInt || durationAsInt <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "The duration must be a positive number of minutes"));
            }
            durationMinutes = durationAsInt;
        }

        if (deadline == null && durationMinutes == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "A deadline or a duration is required"));
        }

        double maxPoints = 20;
        if (body.get("maxPoints") != null) {
            maxPoints = ((Number) body.get("maxPoints")).doubleValue();
        }
        if (maxPoints <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le barème doit être positif"));
        }
        // TODO: get the logged-in teacher once authentication exists
        long teacherId = 1;
        if (body.get("teacherId") != null) {
            teacherId = ((Number) body.get("teacherId")).longValue();
        }

        jdbcTemplate.update(
                "INSERT INTO exercise (teacher_id, title, description, max_points, deadline, duration_minutes) VALUES (?, ?, ?, ?, ?, ?)",
                teacherId, title, body.get("description"), maxPoints,
                deadline == null ? null : java.sql.Date.valueOf(deadline), durationMinutes);
        Long id = jdbcTemplate.queryForObject("SELECT max(id) FROM exercise", Long.class);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("title", title);
        result.put("maxPoints", maxPoints);
        result.put("deadline", deadline);
        result.put("durationMinutes", durationMinutes);
        System.out.println("Nouvel exercice créé : " + title + " (id " + id + ")");
        return ResponseEntity.status(201).body(result);
    }

    @PostMapping("/exercises/{id}/start")
    public ResponseEntity<?> start(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (body.get("studentId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "studentId is required"));
        }
        long studentId = ((Number) body.get("studentId")).longValue();

        List<Map<String, Object>> exercises = jdbcTemplate.queryForList("SELECT * FROM exercise WHERE id = ?", id);
        if (exercises.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Exercise not found"));
        }
        List<Map<String, Object>> students = jdbcTemplate.queryForList("SELECT * FROM student WHERE id = ?", studentId);
        if (students.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Student not found"));
        }

        Integer already = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission WHERE exercise_id = ? AND student_id = ?", Integer.class, id, studentId);
        if (already != null && already > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "This student has already submitted this exercise"));
        }

        Object durationValue = exercises.get(0).get("duration_minutes");
        if (durationValue == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("timed", false);
            result.put("message", "This exercise is not timed");
            return ResponseEntity.ok(result);
        }
        int durationMinutes = ((Number) durationValue).intValue();

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO exercise_start (exercise_id, student_id, started_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (exercise_id, student_id) DO NOTHING",
                id, studentId, Timestamp.valueOf(now));
        Timestamp startedAtValue = jdbcTemplate.queryForObject(
                "SELECT started_at FROM exercise_start WHERE exercise_id = ? AND student_id = ?",
                Timestamp.class, id, studentId);
        LocalDateTime startedAt = startedAtValue.toLocalDateTime();
        LocalDateTime endsAt = exerciseTimer.endsAt(startedAt, durationMinutes);

        Map<String, Object> result = new HashMap<>();
        result.put("timed", true);
        result.put("exerciseId", id);
        result.put("studentId", studentId);
        result.put("durationMinutes", durationMinutes);
        result.put("startedAt", startedAt);
        result.put("endsAt", endsAt);
        result.put("expired", exerciseTimer.isExpired(startedAt, durationMinutes, now));
        result.put("remainingSeconds", exerciseTimer.remainingSeconds(startedAt, durationMinutes, now));
        return ResponseEntity.ok(result);
    }
}
