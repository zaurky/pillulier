# Widget « journée » — conception

Un widget d'écran d'accueil qui montre les prises **qu'il reste à faire aujourd'hui** et
les rend cochables sans ouvrir l'application, avec une fenêtre d'annulation de dix
secondes après chaque coche.

## Ce que le widget montre

Les prises planifiées du jour dont le statut est `EN_RETARD` ou `A_VENIR`, groupées par
moment comme l'écran Aujourd'hui. Une prise cochée disparaît de la liste.

Sont explicitement hors périmètre : les alertes de renouvellement, les médicaments à la
demande, les prises déjà faites, les jours autres qu'aujourd'hui.

Quand il ne reste rien — tout est pris, ou la journée ne comporte aucune prise — le
widget affiche une seule ligne grise centrée, « Rien à prendre », dont l'appui ouvre
l'application.

## Décisions d'architecture

**Glance plutôt que RemoteViews.** L'application est en Compose de bout en bout ; un
`RemoteViewsFactory` et des `PendingIntent` template/fill-in y seraient un corps
étranger, et ne se testeraient qu'avec un host. `androidx.glance:glance-appwidget:1.2.0`
est stable, et `glance-appwidget-testing` permet de tester la composition **en JVM**,
donc la suite reste sans émulateur — la contrainte que le projet s'est donnée.

**Le widget tire ses données, il ne les reçoit pas.** La composition collecte
`ObserverJournee(aujourd'hui)` à travers un `EntryPoint` Hilt. `prisesAttendues` reste
l'unique chemin de calcul du planning : une divergence entre le widget et l'écran
Aujourd'hui est impossible par construction. L'alternative — sérialiser un instantané
dans l'état Glance à chaque écriture — dupliquerait la vérité et ferait mentir le widget
le jour où un nouveau chemin d'écriture oublierait de pousser.

**Le widget n'écrit jamais en base directement.** Il passe par `EnregistrerPrise` et
`AnnulerPrise`, donc la transaction journal + stock et la garantie d'idempotence de
l'index unique `(medicamentId, date, moment)` restent le seul chemin d'écriture.

## Fichiers

Nouveau paquet `app/src/main/kotlin/fr/pillulier/app/widget/`, en miroir de `rappels/` :

| Fichier | Rôle |
|---|---|
| `PillulierWidget.kt` | le `GlanceAppWidget` : composition de la liste, état vide, thème |
| `RecepteurWidget.kt` | le `GlanceAppWidgetReceiver` déclaré au manifeste |
| `ActionsWidget.kt` | les deux `ActionCallback` — cocher, annuler |
| `AccesWidget.kt` | l'`EntryPoint` Hilt qui expose les cas d'usage au widget |
| `LignesDuWidget.kt` | la fonction pure de filtrage, testable sans Glance |
| `RafraichirWidget.kt` | le point d'appel unique de `PillulierWidget.updateAll(contexte)` |

Ailleurs :

- `usecase/AnnulerPrise.kt` — nouveau, le pendant de `EnregistrerPrise`
- `data/db/EvenementPriseDao.kt` — une requête de suppression et une de lecture par clé
  (le DAO n'a aujourd'hui aucun `delete`)
- `res/xml/widget_info.xml`, `res/layout/widget_chargement.xml` (l'`initialLayout` exigé
  par le système), et le `<receiver>` au manifeste
- `app/build.gradle.kts` et `gradle/libs.versions.toml` — `glance-appwidget` en
  `implementation`, `glance-appwidget-testing` en `testImplementation`

Glance ne s'injecte pas par `@AndroidEntryPoint` : le `GlanceAppWidget` et les
`ActionCallback` récupèrent leurs dépendances par `EntryPointAccessors.fromApplication`,
centralisé dans `AccesWidget.kt`.

Le thème reprend `lightColorScheme()` / `darkColorScheme()` comme `MainActivity`, et non
les couleurs dynamiques par défaut de `GlanceTheme` : le widget doit ressembler à
l'application.

Le widget est redimensionnable et la liste est une `LazyColumn`, donc une journée
chargée défile au lieu d'être tronquée.

L'appui sur une ligne ailleurs que sur sa case — et l'appui sur l'état vide — lance
`MainActivity`, ce qui ramène la tâche existante là où elle en était plutôt que de la
réinitialiser sur l'écran Aujourd'hui : le comportement normal d'Android pour une
activité déjà ouverte. Seule la case à cocher enregistre.

## Rafraîchissement

Le flux collecté ne propage rien tout seul en pratique : une session Glance est bornée
dans le temps (`TimeoutOptions` de Glance 1.2.0 — 45 s à l'ouverture, +5 s par
événement, 5 s d'inactivité en Doze), et dans le cas courant aucune session ne tourne au
moment où une prise est enregistrée depuis l'écran Aujourd'hui ou depuis une
notification. Sans rafraîchissement explicite, le widget reste figé sur des pixels
vieux de plusieurs heures.

La règle est donc simple : **qui réarme les rappels rafraîchit le widget**, plus les
deux cas où le temps change quelque chose sans écriture. `RafraichirWidget` appelle
`PillulierWidget.updateAll`, sans effet (donc sans coût) quand aucun widget n'est posé.

| Appelant | Pourquoi |
|---|---|
| `AujourdhuiViewModel.cocher` | la coche depuis l'écran Aujourd'hui change ce que la journée affiche |
| `AujourdhuiViewModel.enregistrerALaDemande` | un chemin d'écriture de plus, par cohérence |
| `RecepteurActionPrise` (*Pris*, *Plus tard*) | même écriture ou même report que depuis l'app |
| `PreferencesViewModel.definirHeure` | une heure de moment déplace les lignes affichées |
| `EnregistrerMedicament` | une ordonnance ou un médicament créé ou modifié change le planning |
| `SupprimerMedicament` | un médicament supprimé disparaît du planning |
| `TravailQuotidien` | la date `aujourd'hui` est capturée à la composition ; elle change à minuit |
| `RecepteurRappel` | une prise passe `A_VENIR` → `EN_RETARD` par l'écoulement du temps, pas par une écriture |
| `RecepteurDemarrage` | après `BOOT_COMPLETED` et `MY_PACKAGE_REPLACED` |

## Cocher

`ActionCocher` est paramétrée par `medicamentId`, `moment` et **le jour que le rendu
représente**. Les pixels d'un widget peuvent dater de plusieurs heures : avant d'écrire
quoi que ce soit, elle revalide, comme `ActionAnnuler`.

1. si le jour du rendu n'est plus le jour courant, elle n'écrit rien et se contente d'un
   `update()` — sans cette garde, un appui sur un widget figé depuis la veille
   enregistrerait une prise du jour courant pour un couple qui n'y est peut-être plus
2. elle relit le planning du jour (`ObserverJournee`) et cherche ce `(médicament,
   moment)` parmi les prises encore attendues, `EN_RETARD` ou `A_VENIR` ; s'il n'y est
   plus, même refus silencieux. La décision sort en fonction pure, `priseACocher`
3. la **dose vient de ce planning relu**, jamais de celle gravée dans le rendu : une
   ordonnance modifiée depuis décrémenterait le stock du mauvais montant

Puis elle refait exactement ce que fait `AujourdhuiViewModel.cocher` :

1. `EnregistrerPrise(medicamentId, aujourd'hui, moment, dose courante)`
2. écriture de l'annulable dans l'état Glance, tout de suite après l'enregistrement —
   sinon la ligne disparaît puis revient barrée, et la fenêtre de dix secondes démarre
   en retard sur ce que l'écran affiche déjà
3. `Notifications.retirer(CleRappel(medicamentId, aujourd'hui, moment))` — la
   notification d'une prise critique est `setOngoing`, donc impossible à balayer, et
   resterait affichée jusqu'à la clôture
4. `ReArmerRappels()`, puis `update()`

## Annuler

`AnnulerPrise(medicamentId, date, moment)` est le pendant de `EnregistrerPrise`. Dans
**une** transaction : elle lit l'événement, le supprime, et re-crédite le stock de la
`doseReelle` lue — pas de la dose théorique, sinon une prise enregistrée à une dose
ajustée re-créditerait faux. Elle renvoie `false` si aucun événement n'existait, ce qui
rend le double appui inoffensif.

Avant d'appeler `AnnulerPrise`, `ActionAnnuler` relit l'état persisté du widget
(`getAppWidgetState`) et vérifie que l'annulable qui s'y trouve désigne exactement la
même prise que le clic — même médicament, même moment, même jour — et n'est pas expiré.
Une session Glance ne vit que quelques dizaines de secondes : sans cette revalidation,
un widget dont la session est morte reste figé sur une ligne barrée avec un lien
*Annuler* tapable indéfiniment, et l'appui recalculerait « aujourd'hui » au lieu du jour
réel de la prise — risquant d'effacer une prise d'un autre jour. Si la revalidation
échoue, aucune écriture n'a lieu : le widget est simplement redessiné avec l'état réel.
Sinon, `ActionAnnuler` appelle `AnnulerPrise`, puis `ReArmerRappels()` et efface
l'annulable.

Le réarmement fait revenir l'alarme. Comme `ReArmerRappels` reprogramme une prise du jour
déjà due à `maintenant + 1 min`, annuler une coche faite en retard fait resonner le
rappel presque aussitôt. C'est cohérent avec le reste du système — la prise n'est plus
faite, donc elle doit être rappelée — mais c'est un comportement visible, pas un détail.

## L'annulable et sa fenêtre de dix secondes

L'état Glance du widget porte un `annulable` : `medicamentId`, `moment`, la `date`
exacte de la prise enregistrée, et un instant d'**expiration absolu**. La date est
nécessaire à `ActionAnnuler` : sans elle, recalculer « aujourd'hui » au moment du clic
viserait le jour suivant si le clic tombe après minuit.

Le rendu part des lignes du jour et garde :

```
EN_RETARD ∪ A_VENIR ∪ { la ligne annulable dont l'expiration n'est pas dépassée }
```

La ligne cochée est passée `PRISE`, donc sortie du filtre : il faut la réinjecter pour
l'afficher barrée, avec un lien *Annuler* à la place de la case. Cette règle est une
fonction pure, `lignesDuWidget(lignes, annulable, maintenant)`, isolée de Glance et
testée seule.

La fenêtre est bornée trois fois : par un `delay` dans la composition, qui efface
l'annulable ; par la vérification d'expiration au rendu ; et par la revalidation à
l'action, seule à tenir quand la session Glance qui a dessiné le lien est déjà morte. Cet
effacement est doublé par la vérification d'expiration au rendu, et c'est ce doublage qui
rend le cas « processus tué » correct : l'état Glance est persisté, mais un annulable
périmé est ignoré au retour. Pas de ligne barrée fantôme.

## Cycle de vie Android

| Situation | Comportement |
|---|---|
| Processus tué pendant la fenêtre | l'annulable persisté est ignoré s'il est expiré ; sinon la fenêtre reprend jusqu'à son terme |
| Application mise à jour | `RecepteurDemarrage` appelle `updateAll` sur `MY_PACKAGE_REPLACED` |
| Redémarrage du téléphone | même chemin, sur `BOOT_COMPLETED` |
| Widget retiré | le nettoyage de l'état est celui de `GlanceAppWidgetReceiver` ; rien de propre à l'application à libérer |
| Plusieurs widgets posés | l'annulable est **par instance** : cocher sur l'un fait disparaître la ligne de l'autre sans fenêtre d'annulation. Comportement assumé ; un état partagé coûterait plus qu'il ne rapporte |
| Doze, tâche différée | chaque session Glance est un `SessionWorker` WorkManager, et un `updateAll` peut lui-même être différé en Doze : la mise à jour n'est pas instantanée, seulement rattrapée au réveil |

Une exception dans une `ActionCallback` est attrapée et loguée, comme dans
`RecepteurActionPrise` : une exception non rattrapée depuis l'arrière-plan tuerait le
processus.

## Tests

Tous en JVM, sans émulateur.

| Test | Ce qu'il verrouille |
|---|---|
| `AnnulerPriseTest` (Room en mémoire, Robolectric) | suppression de l'événement, re-crédit de la dose **réelle**, `false` si rien à annuler, double appui sans effet |
| `LignesDuWidgetTest` (Kotlin pur) | filtre `EN_RETARD`/`A_VENIR`, réinjection de l'annulable, annulable expiré ignoré, journée vide, jour du rendu porté par chaque ligne |
| `ActionsWidgetTest` (Kotlin pur) | les deux revalidations : `annulationAutorisee` (annulable absent, expiré, autre jour, autre prise) et `priseACocher` (couple disparu du planning, prise déjà faite ou oubliée, dose courante et non celle du rendu) |
| `PillulierWidgetTest` (`runGlanceAppWidgetUnitTest`) | la liste rendue et son groupement, la ligne barrée avec *Annuler*, l'état vide « Rien à prendre » |

`prisesAttendues`, `statut` et `EnregistrerPrise` sont déjà couverts et ne sont pas
retouchés.

## Hors périmètre

- Le décochage depuis l'écran Aujourd'hui, que `AnnulerPrise` rendrait pourtant facile
- Un widget d'une autre taille ou d'un autre contenu (semaine, stock)
- La configuration du widget à la pose
