# Journal IA — Kilasy

Outil utilisé : opencode (modèle deepseek-v4.1-flash-free), dans le terminal / IntelliJ IDEA

## Changement 3 : exercices minutés

### Échange 1 — 16:05
Demande : « Certains exercices deviennent minutés : l'enseignant fixe une durée en
minutes. Le décompte démarre quand l'étudiant ouvre l'exercice, et la soumission
doit être envoyée avant la fin de cette durée. Ajouter POST /exercises/{id}/start. »
Proposition : rendre l'échéance nullable, ajouter une colonne duration_minutes,
stocker l'heure de démarrage de chaque étudiant dans une table dédiée
exercise_start, et vérifier l'heure de démarrage lors de la soumission.
Décision : gardé.
Pourquoi : l'heure de démarrage doit exister avant la ligne de soumission, donc
elle ne peut pas être stockée uniquement dans submission ; une table séparée
préserve aussi le flux de soumission existant.
Vérification : `docker compose down` puis `docker compose up -d`, puis `mvn test`
(12/12).

### Échange 2 — 16:10
Demande : « Que doit-il se passer quand un exercice minuté est soumis sans appeler
/start, ou quand /start est appelé sur un exercice non minuté ? »
Proposition : refuser la soumission avec HTTP 422 et un message explicite ;
renvoyer HTTP 200 avec `timed=false` pour un exercice non minuté.
Décision : gardé.
Pourquoi : le décompte ne doit pas être contourné, tandis qu'un client qui appelle
toujours /start fonctionne encore pour les exercices sans durée.
Vérification : exercice 7 soumis sans /start → 422
`You must start the exercise before submitting` ; /start sur l'exercice 1 → 200
`{"timed":false,"message":"This exercise is not timed"}`.

### Échange 3 — 16:20
Demande : « Comment doivent fonctionner le décompte et le relevé ? »
Proposition : /start est idempotent, l'heure de fin est `startedAt +
durationMinutes`, et le relevé ajoute `durationMinutes` plus `timeUsedMinutes`
uniquement pour les exercices minutés.
Décision : gardé.
Pourquoi : rouvrir un exercice ne doit pas remettre le chrono à zéro ; le relevé
conserve la sortie existante pour les exercices non minutés.
Vérification : deux appels successifs à /start sur l'exercice 6 ont conservé le
même `startedAt` ; le relevé de l'étudiant 1 affichait `durationMinutes: 30` et
`timeUsedMinutes: 0.1`.

### Échange 4 — 16:30
Demande : « Vérifier que la pénalité de retard s'applique toujours quand un
exercice minuté a aussi une échéance. »
Proposition : conserver la pénalité existante de `GradeCalculator` basée sur
l'échéance ; seule la durée peut refuser une soumission.
Décision : gardé.
Pourquoi : les deux règles sont indépendantes : la durée est une limite stricte,
l'échéance est une pénalité.
Vérification : exercice 1 (échéance 2026-11-10) avec une durée de 60 minutes,
l'étudiant 5 a soumis dans la durée → `late: true`, la note 16/20 est devenue
8.0/20 après correction.

### Bilan — Changement 3
- Fichiers modifiés :
  - src/main/resources/schema.sql
  - src/main/java/hei/school/Exercise.java
  - src/main/java/hei/school/ExerciseController.java
  - src/main/java/hei/school/ExerciseTimer.java (créé)
  - src/main/java/hei/school/SubmissionController.java
  - src/main/java/hei/school/GradeReport.java
  - src/main/java/hei/school/CsvExport.java
- Base de données : `docker compose down` puis `docker compose up -d` car
  schema.sql a changé.