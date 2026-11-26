# Journal IA — Kilasy (pénalité de retard)

Outil utilisé : opencode (modèle deepseek-v4.1-flash-free), dans le terminal / IntelliJ IDEA

## Changement : pénalité de retard sur les notes

### Échange 1 — 09:05
Demande : « Les soumissions rendues après l'échéance sont pénalisées : −10 % par
jour de retard entamé, plafond −50 %. La note doit être la même dans la
correction, le relevé et l'export CSV. »
Proposition : créer une classe GradeCalculator unique, calculer la pénalité à la
lecture (sans toucher aux points bruts), puis l'utiliser dans SubmissionController,
GradeReport et CsvExport.
Décision : gardé.
Pourquoi : le calcul était dupliqué à trois endroits avec des arrondis différents ;
une seule classe évite les incohérences.
Vérification : duplication confirmée dans les trois fichiers, puis validée après
implémentation par mvn test (5/5) et une note identique partout — soumission 4 :
8,0 en correction, 8,0 au relevé, 8,0 dans le CSV.

### Échange 2 — 09:12
Demande : « Quel périmètre pour cette évolution ? »
Proposition : code + tests + README.
Décision : code + tests seulement.
Pourquoi : rester sur le strict nécessaire.
Vérification : sans objet.

### Échange 3 — 09:35
Demande : implémentation du calcul de pénalité.
Proposition : GradeCalculator (jours de retard, pénalité, note ≥ 0), branchement
dans les trois fichiers, remplacement du Math.floor du CSV.
Décision : gardé avec corrections.
Pourquoi : le CSV tronquait l'arrondi (13,7 au lieu de 13,8) et le plancher à zéro
renvoyait 0 au lieu de 0.0.
Vérification : mvn test 5/5 ; /submissions/1..4 → 16 / 14,4 / 11,2 / 8 ;
relevé étudiant 4 → 8,0 et 12,0, moyenne 10,00 ; CSV identique (8,0 et 13,8). OK.

## Bilan
- Temps passé : environ 30 min.
- Fichiers modifiés :
  - src/main/java/hei/school/GradeCalculator.java (créé)
  - src/main/java/hei/school/SubmissionController.java
  - src/main/java/hei/school/GradeReport.java
  - src/main/java/hei/school/CsvExport.java
