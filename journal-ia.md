# Journal IA — Kilasy

Outil utilisé : opencode (modèle deepseek-v4.1-flash-free), dans le terminal / IntelliJ IDEA

## Changement 2 : SMS de notification

### Échange 1 — 09:50
Demande : « Quand un enseignant publie une note, l'étudiant reçoit aussi un SMS
au numéro enregistré sur son profil. Le fournisseur est simulé par un message
dans les logs. Si l'envoi échoue, la note reste publiée. Changer de fournisseur
ne doit pas obliger à modifier le code de la notation. »
Proposition : créer une interface SmsSender et une implémentation LogSmsSender
injectée dans SubmissionController, puis envoyer le SMS après l'email avec un
try/catch.
Décision : gardé.
Pourquoi : l'interface isole le fournisseur, et le try/catch empêche une panne
SMS de bloquer la notation.
Vérification : soumission 2 notée 16 → email reçu et log
`[SMS] +261 34 00 000 02 : Votre note pour « Tri à bulles » est disponible : 14,4/20.`

### Échange 2 — 09:56
Demande : « Le numéro de téléphone doit-il être valide ? »
Proposition : ne pas ajouter de validation ; envoyer seulement si le champ est
renseigné.
Décision : gardé.
Pourquoi : le SMS est simulé et la demande ne fixe aucun format ; une validation
risquerait de bloquer des numéros existants.
Vérification : sans objet.

### Échange 3 — 10:10
Demande : « Comment simuler une panne SMS et vérifier que la note reste publiée ? »
Proposition : ajouter une propriété kilasy.sms.fail qui lève une exception dans
LogSmsSender, sans toucher au code de notation.
Décision : gardé.
Pourquoi : permet de tester la panne sans modifier le code et sans changer de
fournisseur.
Vérification : relance avec --kilasy.sms.fail=true, notation de la soumission 7 →
PUT 200, GET note 14,4 enregistrée, email reçu, log
`[SMS] Échec de l'envoi à +261 34 00 000 02 : Panne simulée du fournisseur SMS`. OK.



## Bilan
- Fichiers modifiés :
  - Changement 2 :
    - src/main/java/hei/school/SmsSender.java (créé)
    - src/main/java/hei/school/LogSmsSender.java (créé)
    - src/main/java/hei/school/SubmissionController.java
    - src/main/resources/application.properties
