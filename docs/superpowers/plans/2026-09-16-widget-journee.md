# Widget « journée » — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** un widget d'écran d'accueil qui liste les prises restantes du jour et les rend cochables, avec dix secondes pour annuler chaque coche.

**Architecture:** un `GlanceAppWidget` dans un nouveau paquet `widget/`, qui tire ses données de `ObserverJournee` à travers un `EntryPoint` Hilt et n'écrit qu'à travers `EnregistrerPrise` et un nouveau `AnnulerPrise`. La règle d'affichage est isolée dans une fonction pure `lignesDuWidget`, testée sans Glance. L'annulable vit dans l'état Glance avec une expiration absolue.

**Tech Stack:** Kotlin 2.1.20, Glance 1.2.0 (`glance-appwidget`, `glance-material3`, `glance-appwidget-testing`), Room, Hilt, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-16-widget-journee-design.md`

## Global Constraints

- Gradle 9.3.1 · AGP 8.12.0 · Kotlin 2.1.20 · `compileSdk`/`targetSdk` 36 · `minSdk` 26
- Version unique de Glance : **1.2.0**, déclarée dans `gradle/libs.versions.toml` sous la clé `glance`
- Tout le code et toute l'interface sont **en français** : noms de classes, de fonctions, de variables, de tests, et libellés affichés
- Tous les tests tournent **en JVM** (Robolectric), jamais sur émulateur. Les tests Android portent `@RunWith(AndroidJUnit4::class)` et `@Config(sdk = [34])`, comme le reste de la suite
- Le widget n'écrit **jamais** en base directement : il passe par les cas d'usage
- Commande de test : `./gradlew :app:testDebugUnitTest`
- Les tests existants (144) doivent rester verts à chaque commit

---

### Task 1: `AnnulerPrise` — le pendant de `EnregistrerPrise`

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/db/EvenementPriseDao.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/AnnulerPrise.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/AnnulerPriseTest.kt`

**Interfaces:**
- Consomme : `PillulierDatabase`, `EvenementPriseDao`, `MedicamentDao`, `MedicamentDao.ajusterStock(id, delta)` (déjà là), `HorlogeFigee` (déjà définie dans `EnregistrerPriseTest.kt`, même paquet — ne pas la redéclarer)
- Produit : `AnnulerPrise.invoke(medicamentId: Long, date: LocalDate, moment: Moment): Boolean` — `true` si un événement a été supprimé, `false` s'il n'y en avait pas. Et sur le DAO : `planifie(medicamentId, date, moment): EvenementPriseEntity?` et `supprimerPlanifie(medicamentId, date, moment)`

- [ ] **Step 1: Écrire le test qui échoue**

Créer `app/src/test/kotlin/fr/pillulier/app/usecase/AnnulerPriseTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AnnulerPriseTest {

    private lateinit var base: PillulierDatabase
    private lateinit var enregistrer: EnregistrerPrise
    private lateinit var annuler: AnnulerPrise
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 8, 5))
    private val jour = LocalDate.of(2026, 1, 5)

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        annuler = AnnulerPrise(base, base.evenements(), base.medicaments())
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicament(stock: Double = 10.0): Long = base.medicaments().inserer(
        MedicamentEntity(
            nom = "Levothyrox",
            dosage = "75 µg",
            forme = Forme.COMPRIME,
            unitesParBoite = 30,
            stockUnites = stock,
            seuilAlerteJours = 7,
            seuilAlerteUnites = null,
            critique = true,
        ),
    )

    @Test
    fun `annuler supprime l evenement et rend le stock`() = runTest {
        val id = medicament(stock = 10.0)
        enregistrer(id, jour, Moment.MATIN, dose = 1.0)

        assertTrue(annuler(id, jour, Moment.MATIN))

        assertEquals(10.0, base.medicaments().parId(id)!!.stockUnites)
        assertTrue(base.evenements().duJour(jour).isEmpty())
    }

    @Test
    fun `annuler rend la dose reellement enregistree et non la dose theorique`() = runTest {
        val id = medicament(stock = 10.0)
        enregistrer(id, jour, Moment.MATIN, dose = 0.5)

        assertTrue(annuler(id, jour, Moment.MATIN))

        assertEquals(10.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `annuler une prise inexistante ne fait rien`() = runTest {
        val id = medicament(stock = 10.0)

        assertFalse(annuler(id, jour, Moment.MATIN))

        assertEquals(10.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `annuler deux fois ne recredite qu une fois`() = runTest {
        val id = medicament(stock = 10.0)
        enregistrer(id, jour, Moment.MATIN, dose = 1.0)

        assertTrue(annuler(id, jour, Moment.MATIN))
        assertFalse(annuler(id, jour, Moment.MATIN))

        assertEquals(10.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `annuler un moment ne touche pas les autres moments du jour`() = runTest {
        val id = medicament(stock = 10.0)
        enregistrer(id, jour, Moment.MATIN, dose = 1.0)
        enregistrer(id, jour, Moment.SOIR, dose = 1.0)

        assertTrue(annuler(id, jour, Moment.MATIN))

        assertEquals(9.0, base.medicaments().parId(id)!!.stockUnites)
        assertEquals(Moment.SOIR, base.evenements().duJour(jour).single().moment)
    }
}
```

- [ ] **Step 2: Lancer le test et vérifier qu'il échoue**

Run: `./gradlew :app:testDebugUnitTest --tests '*AnnulerPriseTest*'`
Expected: échec de **compilation** — `Unresolved reference: AnnulerPrise`.

- [ ] **Step 3: Ajouter les deux requêtes au DAO**

Dans `app/src/main/kotlin/fr/pillulier/app/data/db/EvenementPriseDao.kt`, ajouter avant l'accolade fermante (l'import `fr.pillulier.domain.Moment` est à ajouter en tête) :

```kotlin
    @Query(
        "SELECT * FROM evenement_prise " +
            "WHERE medicamentId = :medicamentId AND date = :date AND moment = :moment LIMIT 1",
    )
    suspend fun planifie(medicamentId: Long, date: LocalDate, moment: Moment): EvenementPriseEntity?

    @Query(
        "DELETE FROM evenement_prise " +
            "WHERE medicamentId = :medicamentId AND date = :date AND moment = :moment",
    )
    suspend fun supprimerPlanifie(medicamentId: Long, date: LocalDate, moment: Moment)
```

- [ ] **Step 4: Écrire `AnnulerPrise`**

Créer `app/src/main/kotlin/fr/pillulier/app/usecase/AnnulerPrise.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.withTransaction
import fr.pillulier.app.data.db.EvenementPriseDao
import fr.pillulier.app.data.db.MedicamentDao
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.domain.Moment
import java.time.LocalDate
import javax.inject.Inject

/**
 * Efface le journal du réel et rend le stock dans la même transaction. Le stock
 * est recrédité de la dose **réellement enregistrée**, pas de la dose théorique
 * de l'ordonnance : une prise ajustée se rendrait sinon faux.
 *
 * Renvoie `false` si aucune prise planifiée n'était enregistrée, ce qui rend le
 * double appui inoffensif.
 */
class AnnulerPrise @Inject constructor(
    private val base: PillulierDatabase,
    private val evenements: EvenementPriseDao,
    private val medicaments: MedicamentDao,
) {
    suspend operator fun invoke(
        medicamentId: Long,
        date: LocalDate,
        moment: Moment,
    ): Boolean = base.withTransaction {
        val evenement = evenements.planifie(medicamentId, date, moment)
            ?: return@withTransaction false

        evenements.supprimerPlanifie(medicamentId, date, moment)
        medicaments.ajusterStock(medicamentId, evenement.doseReelle)
        true
    }
}
```

- [ ] **Step 5: Lancer le test et vérifier qu'il passe**

Run: `./gradlew :app:testDebugUnitTest --tests '*AnnulerPriseTest*'`
Expected: PASS, 5 tests.

- [ ] **Step 6: Lancer toute la suite**

Run: `./gradlew :domain:test :app:testDebugUnitTest`
Expected: PASS (149 tests : 144 + 5).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/fr/pillulier/app/data/db/EvenementPriseDao.kt \
        app/src/main/kotlin/fr/pillulier/app/usecase/AnnulerPrise.kt \
        app/src/test/kotlin/fr/pillulier/app/usecase/AnnulerPriseTest.kt
git commit -m "feat(app): annuler une prise enregistree et rendre le stock"
```

---

### Task 2: la règle d'affichage, en fonction pure

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/widget/LignesDuWidget.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/widget/LignesDuWidgetTest.kt`

**Interfaces:**
- Consomme : `fr.pillulier.app.usecase.LigneJournee` (champs : `medicamentId`, `nom`, `dosage`, `moment`, `heure: LocalTime`, `dose: Double`, `libelleDose`, `statut: StatutPrise`)
- Produit :
  - `data class Annulable(val medicamentId: Long, val moment: Moment, val expiration: Instant)`
  - `data class LigneWidget(val medicamentId: Long, val moment: Moment, val nom: String, val dosage: String, val libelleDose: String, val dose: Double, val heure: LocalTime, val enRetard: Boolean, val barree: Boolean)`
  - `fun lignesDuWidget(lignes: List<LigneJournee>, annulable: Annulable?, maintenant: Instant): List<LigneWidget>`

Cette tâche n'introduit aucune dépendance Glance : c'est du Kotlin pur, testé sans Robolectric.

- [ ] **Step 1: Écrire le test qui échoue**

Créer `app/src/test/kotlin/fr/pillulier/app/widget/LignesDuWidgetTest.kt` :

```kotlin
package fr.pillulier.app.widget

import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LignesDuWidgetTest {

    private val maintenant = Instant.parse("2026-01-05T08:05:00Z")

    private fun ligne(
        id: Long,
        moment: Moment = Moment.MATIN,
        statut: StatutPrise,
    ) = LigneJournee(
        medicamentId = id,
        nom = "Levothyrox",
        dosage = "75 µg",
        moment = moment,
        heure = LocalTime.of(8, 0),
        dose = 1.0,
        libelleDose = "1 comprimé",
        statut = statut,
    )

    @Test
    fun `seules les prises restantes sont gardees`() {
        val lignes = listOf(
            ligne(1, statut = StatutPrise.EN_RETARD),
            ligne(2, statut = StatutPrise.A_VENIR),
            ligne(3, statut = StatutPrise.PRISE),
            ligne(4, statut = StatutPrise.OUBLIEE),
        )

        val gardees = lignesDuWidget(lignes, annulable = null, maintenant = maintenant)

        assertEquals(listOf(1L, 2L), gardees.map { it.medicamentId })
    }

    @Test
    fun `une prise en retard est marquee comme telle`() {
        val gardees = lignesDuWidget(
            listOf(ligne(1, statut = StatutPrise.EN_RETARD), ligne(2, statut = StatutPrise.A_VENIR)),
            annulable = null,
            maintenant = maintenant,
        )

        assertTrue(gardees.first { it.medicamentId == 1L }.enRetard)
        assertTrue(!gardees.first { it.medicamentId == 2L }.enRetard)
    }

    @Test
    fun `la prise annulable est reinjectee barree`() {
        val lignes = listOf(ligne(3, statut = StatutPrise.PRISE))
        val annulable = Annulable(3L, Moment.MATIN, maintenant.plusSeconds(7))

        val gardees = lignesDuWidget(lignes, annulable, maintenant)

        assertEquals(1, gardees.size)
        assertTrue(gardees.single().barree)
    }

    @Test
    fun `un annulable expire est ignore`() {
        val lignes = listOf(ligne(3, statut = StatutPrise.PRISE))
        val annulable = Annulable(3L, Moment.MATIN, maintenant.minusSeconds(1))

        assertTrue(lignesDuWidget(lignes, annulable, maintenant).isEmpty())
    }

    @Test
    fun `un annulable qui designe une autre prise ne reinjecte rien`() {
        val lignes = listOf(ligne(3, moment = Moment.MATIN, statut = StatutPrise.PRISE))
        val annulable = Annulable(3L, Moment.SOIR, maintenant.plusSeconds(7))

        assertTrue(lignesDuWidget(lignes, annulable, maintenant).isEmpty())
    }

    @Test
    fun `une journee sans prise donne une liste vide`() {
        assertTrue(lignesDuWidget(emptyList(), annulable = null, maintenant = maintenant).isEmpty())
    }
}
```

- [ ] **Step 2: Lancer le test et vérifier qu'il échoue**

Run: `./gradlew :app:testDebugUnitTest --tests '*LignesDuWidgetTest*'`
Expected: échec de compilation — `Unresolved reference: lignesDuWidget`.

- [ ] **Step 3: Écrire la fonction**

Créer `app/src/main/kotlin/fr/pillulier/app/widget/LignesDuWidget.kt` :

```kotlin
package fr.pillulier.app.widget

import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import java.time.Instant
import java.time.LocalTime

/** La prise qu'on vient de cocher, et jusqu'à quand on peut encore la décocher. */
data class Annulable(
    val medicamentId: Long,
    val moment: Moment,
    val expiration: Instant,
)

/** Une ligne telle que le widget la dessine. */
data class LigneWidget(
    val medicamentId: Long,
    val moment: Moment,
    val nom: String,
    val dosage: String,
    val libelleDose: String,
    val dose: Double,
    val heure: LocalTime,
    val enRetard: Boolean,
    val barree: Boolean,
)

/**
 * Le widget ne montre que ce qu'il reste à prendre — plus, le temps de la
 * fenêtre d'annulation, la prise qu'on vient de cocher. Celle-ci est passée
 * `PRISE`, donc sortie du filtre : il faut la réinjecter pour l'afficher barrée.
 *
 * L'expiration est absolue et revérifiée ici, et pas seulement effacée par la
 * composition : si le processus a été tué pendant la fenêtre, l'état persisté
 * survit et ne doit pas ressusciter une ligne barrée.
 */
fun lignesDuWidget(
    lignes: List<LigneJournee>,
    annulable: Annulable?,
    maintenant: Instant,
): List<LigneWidget> {
    val ouvert = annulable?.takeIf { it.expiration.isAfter(maintenant) }

    return lignes
        .filter { ligne ->
            when (ligne.statut) {
                StatutPrise.EN_RETARD, StatutPrise.A_VENIR -> true
                StatutPrise.PRISE ->
                    ouvert != null &&
                        ouvert.medicamentId == ligne.medicamentId &&
                        ouvert.moment == ligne.moment
                StatutPrise.OUBLIEE -> false
            }
        }
        .map { ligne ->
            LigneWidget(
                medicamentId = ligne.medicamentId,
                moment = ligne.moment,
                nom = ligne.nom,
                dosage = ligne.dosage,
                libelleDose = ligne.libelleDose,
                dose = ligne.dose,
                heure = ligne.heure,
                enRetard = ligne.statut == StatutPrise.EN_RETARD,
                barree = ligne.statut == StatutPrise.PRISE,
            )
        }
}
```

- [ ] **Step 4: Lancer le test et vérifier qu'il passe**

Run: `./gradlew :app:testDebugUnitTest --tests '*LignesDuWidgetTest*'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/fr/pillulier/app/widget/LignesDuWidget.kt \
        app/src/test/kotlin/fr/pillulier/app/widget/LignesDuWidgetTest.kt
git commit -m "feat(app): regle d affichage des lignes du widget"
```

---

### Task 3: le widget posable, avec son état vide

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/xml/widget_info.xml`
- Create: `app/src/main/res/layout/widget_chargement.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/fr/pillulier/app/widget/PillulierWidget.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/widget/RecepteurWidget.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/widget/PillulierWidgetTest.kt`

**Interfaces:**
- Consomme : `LigneWidget` (Task 2), `fr.pillulier.app.MainActivity`
- Produit :
  - `object PillulierWidget : GlanceAppWidget()` — `provideGlance` reste vide de données à ce stade, il affiche l'état vide
  - `@Composable fun ContenuWidget(lignes: List<LigneWidget>)` — le composable testable, sans Hilt ni base
  - `class RecepteurWidget : GlanceAppWidgetReceiver()`

À ce stade `provideGlance` n'affiche que l'état vide : la vraie liste arrive en Task 4. Le livrable de cette tâche est un widget qu'on peut poser sur l'écran d'accueil et qui s'affiche sans planter.

- [ ] **Step 1: Déclarer les dépendances Glance**

Dans `gradle/libs.versions.toml`, ajouter sous `[versions]` :

```toml
glance = "1.2.0"
```

et sous `[libraries]` :

```toml
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
glance-appwidget-testing = { group = "androidx.glance", name = "glance-appwidget-testing", version.ref = "glance" }
```

Dans `app/build.gradle.kts`, ajouter dans le bloc `dependencies` — les deux premières près des autres `implementation`, la troisième près des `testImplementation` :

```kotlin
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.glance.appwidget.testing)
```

- [ ] **Step 2: Écrire le test qui échoue**

Créer `app/src/test/kotlin/fr/pillulier/app/widget/PillulierWidgetTest.kt` :

```kotlin
package fr.pillulier.app.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PillulierWidgetTest {

    @Test
    fun `sans prise restante le widget invite a ne rien faire`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = emptyList()) }

        onNode(hasText("Rien à prendre")).assertExists()
    }
}
```

- [ ] **Step 3: Lancer le test et vérifier qu'il échoue**

Run: `./gradlew :app:testDebugUnitTest --tests '*PillulierWidgetTest*'`
Expected: échec de compilation — `Unresolved reference: ContenuWidget`.

- [ ] **Step 4: Écrire le widget et son contenu**

Créer `app/src/main/kotlin/fr/pillulier/app/widget/PillulierWidget.kt` :

```kotlin
package fr.pillulier.app.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import fr.pillulier.app.MainActivity

/** Les mêmes schémas de couleurs que `MainActivity` : le widget doit ressembler à l'app. */
private val couleursWidget = ColorProviders(light = lightColorScheme(), dark = darkColorScheme())

object PillulierWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = couleursWidget) {
                ContenuWidget(lignes = emptyList())
            }
        }
    }
}

@Composable
fun ContenuWidget(lignes: List<LigneWidget>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        if (lignes.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Rien à prendre",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}
```

Note pour l'implémenteur : si `androidx.glance.unit.ColorProvider` n'est pas utilisé, retirer l'import — le projet ne tolère pas les imports morts.

- [ ] **Step 5: Lancer le test et vérifier qu'il passe**

Run: `./gradlew :app:testDebugUnitTest --tests '*PillulierWidgetTest*'`
Expected: PASS, 1 test.

- [ ] **Step 6: Déclarer le widget au système**

Créer `app/src/main/res/values/strings.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="widget_description">Les prises qu\'il reste à faire aujourd\'hui</string>
</resources>
```

Créer `app/src/main/res/layout/widget_chargement.xml` — l'`initialLayout` exigé par le système, affiché le temps que Glance compose :

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

Créer `app/src/main/res/xml/widget_info.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/widget_chargement"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description" />
```

`updatePeriodMillis` vaut 0 : le widget n'est jamais réveillé par le système, il est mis à jour par le flux et par les appels explicites de la Task 5.

Créer `app/src/main/kotlin/fr/pillulier/app/widget/RecepteurWidget.kt` :

```kotlin
package fr.pillulier.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class RecepteurWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PillulierWidget
}
```

Dans `app/src/main/AndroidManifest.xml`, ajouter avant la balise `</application>` :

```xml
        <receiver
            android:name=".widget.RecepteurWidget"
            android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_info" />
        </receiver>
```

- [ ] **Step 7: Vérifier que tout compile et que la suite reste verte**

Run: `./gradlew :app:assembleDebug :domain:test :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 156 tests (149 + 6 + 1).

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml \
        app/src/main/res/values/strings.xml app/src/main/res/xml/widget_info.xml \
        app/src/main/res/layout/widget_chargement.xml \
        app/src/main/kotlin/fr/pillulier/app/widget/ \
        app/src/test/kotlin/fr/pillulier/app/widget/PillulierWidgetTest.kt
git commit -m "feat(app): widget d ecran d accueil posable, avec son etat vide"
```

---

### Task 4: la liste cochable

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/widget/AccesWidget.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/widget/ActionsWidget.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/widget/PillulierWidget.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/widget/PillulierWidgetTest.kt`

**Interfaces:**
- Consomme : `ContenuWidget` (Task 3), `lignesDuWidget` / `LigneWidget` / `Annulable` (Task 2), `AnnulerPrise` (Task 1), `ObserverJournee`, `EnregistrerPrise`, `ReArmerRappels`, `Notifications`, `Horloge`, `libelleMoment`, `CleRappel`
- Produit :
  - `interface AccesWidget` (EntryPoint Hilt) et `fun acces(contexte: Context): AccesWidget`
  - `class ActionCocher : ActionCallback` et `class ActionAnnuler : ActionCallback`
  - les clés de paramètres `CLE_MEDICAMENT: ActionParameters.Key<Long>`, `CLE_MOMENT: ActionParameters.Key<String>`, `CLE_DOSE: ActionParameters.Key<Double>`
  - les clés d'état `ETAT_MEDICAMENT`, `ETAT_MOMENT`, `ETAT_EXPIRATION`, et `fun lireAnnulable(etat: Preferences): Annulable?`

`ActionAnnuler` est écrite ici avec `ActionCocher` — elles partagent leurs clés et leur gestion d'erreur, et séparer les deux obligerait à écrire deux fois le même préambule. La fenêtre d'annulation qu'elles alimentent est câblée en Task 5.

- [ ] **Step 1: Écrire les tests qui échouent**

Ajouter à `app/src/test/kotlin/fr/pillulier/app/widget/PillulierWidgetTest.kt` (et compléter les imports) :

```kotlin
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import fr.pillulier.domain.Moment
import java.time.LocalTime
```

et, dans la classe :

```kotlin
    private fun ligneWidget(
        id: Long = 1L,
        nom: String = "Levothyrox",
        moment: Moment = Moment.MATIN,
        barree: Boolean = false,
    ) = LigneWidget(
        medicamentId = id,
        moment = moment,
        nom = nom,
        dosage = "75 µg",
        libelleDose = "1 comprimé",
        dose = 1.0,
        heure = LocalTime.of(8, 0),
        enRetard = false,
        barree = barree,
    )

    @Test
    fun `chaque prise restante est affichee avec son moment`() = runGlanceAppWidgetUnitTest {
        provideComposable {
            ContenuWidget(
                lignes = listOf(
                    ligneWidget(id = 1L, nom = "Levothyrox", moment = Moment.MATIN),
                    ligneWidget(id = 2L, nom = "Doliprane", moment = Moment.SOIR),
                ),
            )
        }

        onNode(hasText("Levothyrox")).assertExists()
        onNode(hasText("Doliprane")).assertExists()
        onNode(hasText("Matin")).assertExists()
        onNode(hasText("Soir")).assertExists()
    }

    @Test
    fun `cocher une prise declenche l action de coche avec sa dose`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = listOf(ligneWidget(id = 7L))) }

        onNode(
            hasRunCallbackClickAction<ActionCocher>(
                parameters = actionParametersOf(
                    CLE_MEDICAMENT to 7L,
                    CLE_MOMENT to Moment.MATIN.name,
                    CLE_DOSE to 1.0,
                ),
            ),
        ).assertExists()
    }
```

- [ ] **Step 2: Lancer les tests et vérifier qu'ils échouent**

Run: `./gradlew :app:testDebugUnitTest --tests '*PillulierWidgetTest*'`
Expected: échec de compilation — `Unresolved reference: ActionCocher`.

- [ ] **Step 3: Écrire l'accès Hilt**

Créer `app/src/main/kotlin/fr/pillulier/app/widget/AccesWidget.kt` :

```kotlin
package fr.pillulier.app.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.AnnulerPrise
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.usecase.ObserverJournee
import fr.pillulier.app.usecase.ReArmerRappels

/**
 * Un `GlanceAppWidget` et ses `ActionCallback` ne sont pas des composants
 * Android : `@AndroidEntryPoint` ne s'y applique pas. On passe donc par un
 * point d'entrée explicite, centralisé ici.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccesWidget {
    fun observerJournee(): ObserverJournee
    fun enregistrerPrise(): EnregistrerPrise
    fun annulerPrise(): AnnulerPrise
    fun reArmerRappels(): ReArmerRappels
    fun notifications(): Notifications
    fun horloge(): Horloge
}

fun acces(contexte: Context): AccesWidget =
    EntryPointAccessors.fromApplication(contexte.applicationContext, AccesWidget::class.java)
```

- [ ] **Step 4: Écrire les deux actions**

Créer `app/src/main/kotlin/fr/pillulier/app/widget/ActionsWidget.kt` :

```kotlin
package fr.pillulier.app.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Moment
import java.time.Instant

private const val ETIQUETTE = "ActionsWidget"

/** Dix secondes pour revenir sur une coche. */
const val SECONDES_ANNULATION = 10L

val CLE_MEDICAMENT = ActionParameters.Key<Long>("medicamentId")
val CLE_MOMENT = ActionParameters.Key<String>("moment")
val CLE_DOSE = ActionParameters.Key<Double>("dose")

val ETAT_MEDICAMENT = longPreferencesKey("annulable_medicament")
val ETAT_MOMENT = stringPreferencesKey("annulable_moment")
val ETAT_EXPIRATION = longPreferencesKey("annulable_expiration")

/** L'annulable tel qu'il est relu de l'état Glance, ou `null` s'il n'y en a pas. */
fun lireAnnulable(etat: Preferences): Annulable? {
    val medicamentId = etat[ETAT_MEDICAMENT] ?: return null
    val moment = etat[ETAT_MOMENT] ?: return null
    val expiration = etat[ETAT_EXPIRATION] ?: return null
    return Annulable(medicamentId, Moment.valueOf(moment), Instant.ofEpochMilli(expiration))
}

private suspend fun ecrireAnnulable(context: Context, glanceId: GlanceId, annulable: Annulable?) {
    updateAppWidgetState(context, glanceId) { etat ->
        if (annulable == null) {
            etat.remove(ETAT_MEDICAMENT)
            etat.remove(ETAT_MOMENT)
            etat.remove(ETAT_EXPIRATION)
        } else {
            etat[ETAT_MEDICAMENT] = annulable.medicamentId
            etat[ETAT_MOMENT] = annulable.moment.name
            etat[ETAT_EXPIRATION] = annulable.expiration.toEpochMilli()
        }
    }
}

/** Enregistre la prise, exactement comme la coche de l'écran Aujourd'hui. */
class ActionCocher : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val medicamentId = parameters[CLE_MEDICAMENT] ?: return
        val moment = parameters[CLE_MOMENT]?.let { Moment.valueOf(it) } ?: return
        val dose = parameters[CLE_DOSE] ?: return
        val acces = acces(context)
        val horloge = acces.horloge()
        val jour = horloge.aujourdhui()

        try {
            acces.enregistrerPrise()(medicamentId, jour, moment, dose)
            // Le réarmement annule l'alarme mais pas la notification déjà
            // postée : celle d'une prise critique est `setOngoing`, donc
            // impossible à balayer.
            acces.notifications().retirer(CleRappel(medicamentId, jour, moment))
            acces.reArmerRappels()()
            ecrireAnnulable(
                context,
                glanceId,
                Annulable(medicamentId, moment, horloge.instant().plusSeconds(SECONDES_ANNULATION)),
            )
            PillulierWidget.update(context, glanceId)
        } catch (erreur: Throwable) {
            // Une exception non rattrapée ici tuerait le processus depuis
            // l'arrière-plan : on la rend visible sans faire tomber l'app.
            Log.e(ETIQUETTE, "coche impossible pour $medicamentId/$moment", erreur)
        }
    }
}

/** Efface la prise et rend le stock, puis fait revenir l'alarme. */
class ActionAnnuler : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val medicamentId = parameters[CLE_MEDICAMENT] ?: return
        val moment = parameters[CLE_MOMENT]?.let { Moment.valueOf(it) } ?: return
        val acces = acces(context)
        val jour = acces.horloge().aujourdhui()

        try {
            acces.annulerPrise()(medicamentId, jour, moment)
            acces.reArmerRappels()()
            ecrireAnnulable(context, glanceId, annulable = null)
            PillulierWidget.update(context, glanceId)
        } catch (erreur: Throwable) {
            Log.e(ETIQUETTE, "annulation impossible pour $medicamentId/$moment", erreur)
        }
    }
}
```

- [ ] **Step 5: Dessiner la liste**

Dans `app/src/main/kotlin/fr/pillulier/app/widget/PillulierWidget.kt`, remplacer le corps de `ContenuWidget` par celui-ci, et ajouter les imports nécessaires (`androidx.glance.appwidget.CheckBox`, `androidx.glance.appwidget.action.actionRunCallback`, `androidx.glance.action.actionParametersOf`, `androidx.glance.appwidget.lazy.LazyColumn`, `androidx.glance.appwidget.lazy.items`, `androidx.glance.layout.Row`, `androidx.glance.layout.fillMaxWidth`, `androidx.glance.text.FontWeight`, `androidx.glance.text.TextDecoration`, `fr.pillulier.app.ui.libelleMoment`, `java.time.format.DateTimeFormatter`) :

```kotlin
private val formatHeure = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ContenuWidget(lignes: List<LigneWidget>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp),
    ) {
        if (lignes.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Rien à prendre",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
            return@Column
        }

        LazyColumn {
            lignes.groupBy { it.moment }.forEach { (moment, duMoment) ->
                item {
                    Text(
                        "${libelleMoment(moment)} · ${duMoment.first().heure.format(formatHeure)}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = GlanceModifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(duMoment) { ligne -> LigneCochable(ligne) }
            }
        }
    }
}

@Composable
private fun LigneCochable(ligne: LigneWidget) {
    val parametres = actionParametersOf(
        CLE_MEDICAMENT to ligne.medicamentId,
        CLE_MOMENT to ligne.moment.name,
        CLE_DOSE to ligne.dose,
    )

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = ligne.barree,
            onCheckedChange = if (ligne.barree) {
                actionRunCallback<ActionAnnuler>(parametres)
            } else {
                actionRunCallback<ActionCocher>(parametres)
            },
            text = "${ligne.nom} ${ligne.dosage}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                textDecoration = if (ligne.barree) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
        Text(
            if (ligne.barree) "Annuler" else ligne.libelleDose,
            style = TextStyle(
                color = if (ligne.enRetard) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
            ),
            modifier = GlanceModifier
                .padding(start = 8.dp)
                .let { if (ligne.barree) it.clickable(actionRunCallback<ActionAnnuler>(parametres)) else it },
        )
    }
}
```

- [ ] **Step 6: Lancer les tests et vérifier qu'ils passent**

Run: `./gradlew :app:testDebugUnitTest --tests '*PillulierWidgetTest*'`
Expected: PASS, 3 tests.

Si `hasRunCallbackClickAction` ne trouve pas le nœud, c'est que l'action est portée par la case et non par la ligne : ajuster le test pour viser le bon nœud, **pas** la production — l'action doit rester sur la case.

- [ ] **Step 7: Brancher les données réelles**

Dans `PillulierWidget.provideGlance`, remplacer le corps par :

```kotlin
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val acces = acces(context)
        val horloge = acces.horloge()
        val journee = acces.observerJournee()(horloge.aujourdhui())

        provideContent {
            val lignes by journee.collectAsState(initial = emptyList())
            val annulable = lireAnnulable(currentState<Preferences>())

            GlanceTheme(colors = couleursWidget) {
                ContenuWidget(lignesDuWidget(lignes, annulable, horloge.instant()))
            }
        }
    }
```

Imports à ajouter : `androidx.compose.runtime.collectAsState`, `androidx.compose.runtime.getValue`, `androidx.datastore.preferences.core.Preferences`, `androidx.glance.currentState`.

- [ ] **Step 8: Vérifier la compilation et la suite complète**

Run: `./gradlew :app:assembleDebug :domain:test :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 158 tests.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/fr/pillulier/app/widget/ \
        app/src/test/kotlin/fr/pillulier/app/widget/PillulierWidgetTest.kt
git commit -m "feat(app): liste des prises restantes cochable depuis le widget"
```

---

### Task 5: la fenêtre d'annulation et le rafraîchissement

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/widget/PillulierWidget.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/widget/RafraichirWidget.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/rappels/TravailQuotidien.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurRappel.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurDemarrage.kt`
- Modify: `README.md`
- Test: `app/src/test/kotlin/fr/pillulier/app/widget/PillulierWidgetTest.kt`

**Interfaces:**
- Consomme : tout ce que produisent les Tasks 1 à 4
- Produit : `class RafraichirWidget @Inject constructor(private val contexte: Context) { suspend operator fun invoke() }`

- [ ] **Step 1: Écrire les tests qui échouent**

Ajouter à `PillulierWidgetTest.kt` :

```kotlin
    @Test
    fun `une prise barree propose de l annuler`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = listOf(ligneWidget(id = 7L, barree = true))) }

        onNode(hasText("Annuler")).assertExists()
    }

    @Test
    fun `annuler une prise barree declenche l action d annulation`() = runGlanceAppWidgetUnitTest {
        provideComposable { ContenuWidget(lignes = listOf(ligneWidget(id = 7L, barree = true))) }

        onNode(
            hasRunCallbackClickAction<ActionAnnuler>(
                parameters = actionParametersOf(
                    CLE_MEDICAMENT to 7L,
                    CLE_MOMENT to Moment.MATIN.name,
                    CLE_DOSE to 1.0,
                ),
            ),
        ).assertExists()
    }
```

- [ ] **Step 2: Lancer les tests et vérifier qu'ils passent déjà**

Run: `./gradlew :app:testDebugUnitTest --tests '*PillulierWidgetTest*'`
Expected: PASS, 5 tests — le rendu barré est déjà en place depuis la Task 4. Si un test échoue, corriger `LigneCochable` avant d'aller plus loin.

- [ ] **Step 3: Refermer la fenêtre quand elle expire**

Dans `provideContent` de `PillulierWidget.kt`, ajouter après la lecture de `annulable` :

```kotlin
            val glanceId = LocalGlanceId.current
            val contexte = LocalContext.current

            // La fenêtre se referme d'elle-même, sans attendre une autre
            // écriture. `lignesDuWidget` revérifie l'expiration de son côté :
            // si le processus a été tué entre-temps, ce `LaunchedEffect` n'a
            // jamais tourné et l'état persisté ne doit pas ressusciter la ligne.
            LaunchedEffect(annulable) {
                val restant = annulable?.let {
                    it.expiration.toEpochMilli() - horloge.instant().toEpochMilli()
                } ?: return@LaunchedEffect
                if (restant > 0) delay(restant)
                updateAppWidgetState(contexte, glanceId) { etat ->
                    etat.remove(ETAT_MEDICAMENT)
                    etat.remove(ETAT_MOMENT)
                    etat.remove(ETAT_EXPIRATION)
                }
                PillulierWidget.update(contexte, glanceId)
            }
```

Imports à ajouter : `androidx.compose.runtime.LaunchedEffect`, `androidx.glance.LocalContext`, `androidx.glance.LocalGlanceId`, `androidx.glance.appwidget.state.updateAppWidgetState`, `kotlinx.coroutines.delay`.

- [ ] **Step 4: Écrire le point de rafraîchissement**

Créer `app/src/main/kotlin/fr/pillulier/app/widget/RafraichirWidget.kt` :

```kotlin
package fr.pillulier.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import javax.inject.Inject

/**
 * Le flux collecté par le widget porte déjà toute écriture en base. Restent les
 * deux changements qu'aucune écriture ne signale : le passage à un nouveau jour,
 * et une prise qui devient en retard par le seul écoulement du temps.
 */
class RafraichirWidget @Inject constructor(private val contexte: Context) {
    suspend operator fun invoke() = PillulierWidget.updateAll(contexte)
}
```

`Context` est déjà fourni par `ModuleDonnees.contexte`, aucun module à modifier.

- [ ] **Step 5: Appeler le rafraîchissement aux trois endroits**

Dans `TravailQuotidien.kt` — ajouter le paramètre et l'appel :

```kotlin
    private val rafraichirWidget: RafraichirWidget,
```

```kotlin
    override suspend fun doWork(): Result {
        cloturerJournee()
        reArmerRappels()
        rafraichirWidget()
        return Result.success()
    }
```

Dans `RecepteurRappel.kt` — ajouter `@Inject lateinit var rafraichirWidget: RafraichirWidget` aux autres injections, et appeler `rafraichirWidget()` juste après `notifications.posterRappel(...)`.

Dans `RecepteurDemarrage.kt` — ajouter la même injection, et appeler `rafraichirWidget()` après `planificateur.planifier()`.

Import à ajouter dans les trois : `fr.pillulier.app.widget.RafraichirWidget`.

- [ ] **Step 6: Vérifier que la suite complète reste verte**

Run: `./gradlew :domain:test :app:testDebugUnitTest`
Expected: PASS, 160 tests.

Si un test existant de `TravailQuotidien` ou des receveurs construit ces classes à la main, il faut lui passer un `RafraichirWidget(ApplicationProvider.getApplicationContext())`. C'est une adaptation attendue, pas un échec.

- [ ] **Step 7: Documenter le widget dans le README**

Dans `README.md`, après la section « Cinq écrans », ajouter une section :

```markdown
### Le widget

Un widget d'écran d'accueil liste les prises qu'il reste à faire aujourd'hui — en
retard et à venir — et les rend cochables sans ouvrir l'application. Cocher enregistre
la prise comme l'action *Pris* d'une notification : même transaction, même décrément de
stock, même annulation d'alarme. Pendant dix secondes la ligne reste barrée avec un lien
*Annuler*, qui efface la prise, rend le stock et fait revenir le rappel.

Le widget lit `ObserverJournee`, donc le même `prisesAttendues` que les écrans : il ne
peut pas afficher autre chose qu'eux.
```

Et dans le tableau de la section `:app`, ajouter la ligne :

```markdown
- `widget/` — le widget Glance, ses deux actions et son point de rafraîchissement
```

Mettre aussi à jour les chiffres du tableau « Module / Tests » : `:app` passe de 94 à 110.

- [ ] **Step 8: Vérifier sur l'appareil**

Ce qui suit n'est pas couvert par les tests JVM et doit être vu sur le Galaxy A35 branché :

```bash
./gradlew :app:installDebug
```

1. Poser le widget sur l'écran d'accueil : il liste les prises restantes du jour
2. Cocher une prise : la ligne se barre, *Annuler* apparaît, la notification du moment disparaît
3. Attendre dix secondes sans rien faire : la ligne disparaît
4. Cocher une autre prise puis appuyer sur *Annuler* : la ligne redevient cochable et le stock est revenu (vérifier dans l'écran Stock)
5. Vérifier que l'écran Aujourd'hui et le widget montrent la même chose

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/fr/pillulier/app/widget/ \
        app/src/main/kotlin/fr/pillulier/app/rappels/ \
        app/src/test/kotlin/fr/pillulier/app/widget/PillulierWidgetTest.kt \
        README.md
git commit -m "feat(app): fenetre d annulation et rafraichissement du widget"
```
