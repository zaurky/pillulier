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

L'appui sur une ligne ailleurs que sur sa case — et l'appui sur l'état vide — ouvre
`MainActivity` sur l'écran Aujourd'hui. Seule la case à cocher enregistre.

## Rafraîchissement

Tant que la session Glance vit, toute écriture en base se propage seule au widget par le
flux collecté : coche depuis l'écran Aujourd'hui, action *Pris* d'une notification,
clôture quotidienne. `updateAll` n'est appelé explicitement que dans les trois cas que le
flux ne couvre pas :

| Appelant | Pourquoi |
|---|---|
| `TravailQuotidien` | la date `aujourd'hui` est capturée à la composition ; elle change à minuit |
| `RecepteurRappel` | une prise passe `A_VENIR` → `EN_RETARD` par l'écoulement du temps, pas par une écriture |
| `RecepteurDemarrage` | après `BOOT_COMPLETED` et `MY_PACKAGE_REPLACED` |

## Cocher

`ActionCocher`, paramétrée par `medicamentId`, `moment` et `dose`, refait exactement ce
que fait `AujourdhuiViewModel.cocher` :

1. `EnregistrerPrise(medicamentId, aujourd'hui, moment, dose)`
2. `Notifications.retirer(CleRappel(medicamentId, aujourd'hui, moment))` — la
   notification d'une prise critique est `setOngoing`, donc impossible à balayer, et
   resterait affichée jusqu'à la clôture
3. `ReArmerRappels()`
4. écriture de l'annulable dans l'état Glance, puis `update()`

## Annuler

`AnnulerPrise(medicamentId, date, moment)` est le pendant de `EnregistrerPrise`. Dans
**une** transaction : elle lit l'événement, le supprime, et re-crédite le stock de la
`doseReelle` lue — pas de la dose théorique, sinon une prise enregistrée à une dose
ajustée re-créditerait faux. Elle renvoie `false` si aucun événement n'existait, ce qui
rend le double appui inoffensif.

`ActionAnnuler` l'appelle, puis `ReArmerRappels()` et efface l'annulable.

Le réarmement fait revenir l'alarme. Comme `ReArmerRappels` reprogramme une prise du jour
déjà due à `maintenant + 1 min`, annuler une coche faite en retard fait resonner le
rappel presque aussitôt. C'est cohérent avec le reste du système — la prise n'est plus
faite, donc elle doit être rappelée — mais c'est un comportement visible, pas un détail.

## L'annulable et sa fenêtre de dix secondes

L'état Glance du widget porte un `annulable` : `medicamentId`, `moment`, et un instant
d'**expiration absolu**.

Le rendu part des lignes du jour et garde :

```
EN_RETARD ∪ A_VENIR ∪ { la ligne annulable dont l'expiration n'est pas dépassée }
```

La ligne cochée est passée `PRISE`, donc sortie du filtre : il faut la réinjecter pour
l'afficher barrée, avec un lien *Annuler* à la place de la case. Cette règle est une
fonction pure, `lignesDuWidget(lignes, annulable, maintenant)`, isolée de Glance et
testée seule.

La fenêtre se referme par un `delay` dans la composition, qui efface l'annulable. Cet
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
| Doze, tâche différée | le widget ne programme aucun travail ; `updateAll` part de receveurs qui tournent déjà |

Une exception dans une `ActionCallback` est attrapée et loguée, comme dans
`RecepteurActionPrise` : une exception non rattrapée depuis l'arrière-plan tuerait le
processus.

## Tests

Tous en JVM, sans émulateur.

| Test | Ce qu'il verrouille |
|---|---|
| `AnnulerPriseTest` (Room en mémoire, Robolectric) | suppression de l'événement, re-crédit de la dose **réelle**, `false` si rien à annuler, double appui sans effet |
| `LignesDuWidgetTest` (Kotlin pur) | filtre `EN_RETARD`/`A_VENIR`, réinjection de l'annulable, annulable expiré ignoré, journée vide |
| `PillulierWidgetTest` (`runGlanceAppWidgetUnitTest`) | la liste rendue et son groupement, la ligne barrée avec *Annuler*, l'état vide « Rien à prendre » |

`prisesAttendues`, `statut` et `EnregistrerPrise` sont déjà couverts et ne sont pas
retouchés.

## Hors périmètre

- Le décochage depuis l'écran Aujourd'hui, que `AnnulerPrise` rendrait pourtant facile
- Un widget d'une autre taille ou d'un autre contenu (semaine, stock)
- La configuration du widget à la pose
