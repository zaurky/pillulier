# Pillulier — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer une application Android locale et mono-utilisateur qui rappelle les prises de médicaments et alerte avant la rupture de stock.

**Architecture:** Deux modules Gradle. `:domain` en Kotlin pur contient les types métier et un moteur de règles fait de fonctions sans état (planning, statuts, projection de stock, jours fériés), testable en JVM. `:app` contient Compose, Room, les alarmes exactes et les notifications, et ne fait qu'appeler le moteur. La dépendance va de `:app` vers `:domain`, jamais l'inverse.

**Tech Stack:** Kotlin 2.1.20, Gradle 9.3.1, AGP 8.12.0, Jetpack Compose, Room, WorkManager, DataStore, Hilt, `AlarmManager` en alarmes exactes.

**Spec:** `docs/superpowers/specs/2026-09-07-pillulier-design.md`

## Global Constraints

- Gradle 9.3.1, AGP 8.12.0, Kotlin 2.1.20
- `compileSdk` 36, `targetSdk` 36, `minSdk` 26
- Toolchain Java 17 pour la compilation ; Gradle s'exécute sur le JBR d'Android Studio (Java 25)
- `minSdk` 26 rend `java.time` disponible nativement : **ne pas** activer `coreLibraryDesugaring`
- Nom de paquet : `fr.pillulier` pour `:domain`, `fr.pillulier.app` pour `:app`
- Le module `:domain` ne doit dépendre d'aucune bibliothèque Android ni de Kotlin coroutines : uniquement la bibliothèque standard
- Toute la langue du code métier et de l'interface est le français (noms de classes, de fonctions, de tables, libellés)
- Aucun accès réseau, aucune permission Internet
- TDD strict : le test échoue d'abord, puis l'implémentation minimale, puis le test passe, puis on commite
- **Si un test échoue de manière inattendue, s'arrêter et demander à l'humain avant de s'entêter** — pas de boucle d'essais successifs
- Un commit par tâche au minimum, au vert

---

## Structure des fichiers

**Racine**

| Fichier | Responsabilité |
|---|---|
| `settings.gradle.kts` | Déclare les modules `:domain` et `:app` et les dépôts |
| `build.gradle.kts` | Déclare les plugins, sans les appliquer |
| `gradle/libs.versions.toml` | Catalogue de versions, source unique des numéros |
| `gradle.properties` | Options Gradle et AndroidX |

**Module `:domain`** — `domain/src/main/kotlin/fr/pillulier/domain/`

| Fichier | Responsabilité |
|---|---|
| `Types.kt` | Énumérations, `Rythme`, entités métier immuables |
| `JoursFeriesFrance.kt` | Calcul de Pâques et des onze jours fériés métropolitains |
| `Planning.kt` | `estJourActif`, `prisesAttendues` |
| `Statut.kt` | `statut` d'une prise attendue |
| `Stock.kt` | `consommationDuJour`, `projectionStock`, `dateAlerte` |
| `Rappel.kt` | `CleRappel` et son code de requête déterministe |

**Module `:app`** — `app/src/main/kotlin/fr/pillulier/app/`

| Répertoire | Responsabilité |
|---|---|
| `data/db/` | Entités Room, DAO, convertisseurs, classe de base |
| `data/` | Mappeurs entités ↔ domaine, dépôts, préférences DataStore |
| `usecase/` | Cas d'usage : enregistrer une prise, gérer le stock, observer une journée, réarmer les rappels, clôturer |
| `rappels/` | Programmateur d'alarmes, receveurs, notifications, activité d'alarme, travail quotidien |
| `ui/` | Un sous-paquet par écran : `aujourdhui`, `semaine`, `medicaments`, `stock`, `preferences`, plus `Navigation.kt` et le thème |
| `di/` | Modules Hilt |

Chaque écran a son `ViewModel` et son état exposé en `StateFlow`, dans son propre fichier.

---

### Task 1 : Squelette Gradle des deux modules

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `domain/build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/fr/pillulier/app/MainActivity.kt`
- Create: `.gitignore`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/SocleTest.kt`

**Interfaces:**
- Consumes: rien
- Produces: les modules `:domain` et `:app` compilables, le catalogue `libs` avec les alias utilisés par toutes les tâches suivantes

- [ ] **Step 1: Initialiser le wrapper Gradle**

```bash
gradle wrapper --gradle-version 9.3.1
```

Si `gradle` n'est pas installé globalement, copier le wrapper depuis un projet Android Studio existant, puis vérifier :

```bash
./gradlew --version
```

Attendu : `Gradle 9.3.1`

- [ ] **Step 2: Écrire le catalogue de versions**

`gradle/libs.versions.toml` :

```toml
[versions]
agp = "8.12.0"
kotlin = "2.1.20"
ksp = "2.1.20-1.0.32"
hilt = "2.56.1"
room = "2.7.0"
composeBom = "2025.03.00"
activityCompose = "1.10.1"
navigationCompose = "2.8.9"
lifecycle = "2.8.7"
workManager = "2.10.0"
dataStore = "1.1.3"
hiltExt = "1.2.0"
coroutines = "1.10.1"
junit = "4.13.2"
androidxJunit = "1.2.1"
androidxRunner = "1.6.2"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "dataStore" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltExt" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltExt" }
hilt-ext-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltExt" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
androidx-runner = { group = "androidx.test", name = "runner", version.ref = "androidxRunner" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

Si une version ne se résout pas lors du premier `./gradlew` (message `Could not find ...`), monter à la version stable la plus proche publiée et le noter dans le message de commit. Ne pas changer les versions fixées par les contraintes globales (AGP, Kotlin, Gradle, SDK).

- [ ] **Step 3: Écrire les fichiers de configuration**

`settings.gradle.kts` :

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pillulier"
include(":domain")
include(":app")
```

`build.gradle.kts` à la racine :

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

`gradle.properties` :

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
```

`.gitignore` :

```
.gradle/
build/
local.properties
*.iml
.idea/
.kotlin/
```

- [ ] **Step 4: Écrire les deux fichiers de build de module**

`domain/build.gradle.kts` :

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
```

`app/build.gradle.kts` :

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "fr.pillulier.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.pillulier.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDir("src/androidTest/kotlin")
}

dependencies {
    implementation(project(":domain"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
}
```

- [ ] **Step 5: Écrire le manifeste et l'activité minimale**

`app/src/main/AndroidManifest.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:label="Pillulier"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/kotlin/fr/pillulier/app/MainActivity.kt` :

```kotlin
package fr.pillulier.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Pillulier")
                }
            }
        }
    }
}
```

- [ ] **Step 6: Écrire le test qui prouve que le socle de test fonctionne**

`domain/src/test/kotlin/fr/pillulier/domain/SocleTest.kt` :

```kotlin
package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SocleTest {
    @Test
    fun `java time est disponible dans le module domaine`() {
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 1, 4).dayOfWeek)
    }
}
```

- [ ] **Step 7: Lancer les tests et la compilation**

```bash
./gradlew :domain:test :app:assembleDebug
```

Attendu : `BUILD SUCCESSFUL`, et le test `SocleTest` au vert.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore: squelette Gradle des modules domain et app"
```

---

### Task 2 : Types métier du module domaine

**Files:**
- Create: `domain/src/main/kotlin/fr/pillulier/domain/Types.kt`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/TypesTest.kt`
- Delete: `domain/src/test/kotlin/fr/pillulier/domain/SocleTest.kt`

**Interfaces:**
- Consumes: le module `:domain` de la tâche 1
- Produces: `Forme`, `Moment`, `TypeOrdonnance`, `StatutPrise`, `Rythme`, `Medicament`, `DosePrescrite`, `Ordonnance`, `OrdonnanceAvecDoses`, `EvenementPrise`, `PriseAttendue` — toutes les tâches suivantes en dépendent

- [ ] **Step 1: Écrire le test qui échoue**

`domain/src/test/kotlin/fr/pillulier/domain/TypesTest.kt` :

```kotlin
package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypesTest {
    @Test
    fun `les moments sont dans l ordre chronologique de la journee`() {
        assertEquals(
            listOf(Moment.MATIN, Moment.MIDI, Moment.SOIR, Moment.COUCHER),
            Moment.entries.toList(),
        )
    }

    @Test
    fun `une ordonnance planifiee expose son rythme et ses doses`() {
        val ordonnance = OrdonnanceAvecDoses(
            ordonnance = Ordonnance(
                id = 1,
                medicamentId = 7,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = listOf(
                DosePrescrite(Moment.MATIN, 2.0),
                DosePrescrite(Moment.SOIR, 0.5),
            ),
        )

        assertEquals(2.0, ordonnance.doses.first { it.moment == Moment.MATIN }.dose)
        assertEquals(0.5, ordonnance.doses.first { it.moment == Moment.SOIR }.dose)
        assertTrue(ordonnance.ordonnance.rythme is Rythme.JoursDeSemaine)
    }

    @Test
    fun `un medicament a la demande porte un seuil en unites et pas en jours`() {
        val medicament = Medicament(
            id = 3,
            nom = "Doliprane",
            dosage = "500 mg",
            forme = Forme.COMPRIME,
            unitesParBoite = 16,
            stockUnites = 12.0,
            seuilAlerteJours = null,
            seuilAlerteUnites = 5,
            critique = false,
        )

        assertEquals(null, medicament.seuilAlerteJours)
        assertEquals(5, medicament.seuilAlerteUnites)
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :domain:test --tests 'fr.pillulier.domain.TypesTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: Moment`

- [ ] **Step 3: Écrire les types**

`domain/src/main/kotlin/fr/pillulier/domain/Types.kt` :

```kotlin
package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Formes dénombrables gérées par l'application. */
enum class Forme { COMPRIME, GELULE, SACHET, INJECTION }

/** Les quatre moments globaux, dans l'ordre chronologique de la journée. */
enum class Moment { MATIN, MIDI, SOIR, COUCHER }

enum class TypeOrdonnance { PLANIFIEE, A_LA_DEMANDE }

enum class StatutPrise { A_VENIR, EN_RETARD, PRISE, OUBLIEE }

/** Rythme d'une ordonnance planifiée. */
sealed interface Rythme {
    data object TousLesJours : Rythme

    data class JoursDeSemaine(val jours: Set<DayOfWeek>) : Rythme

    /** Un jour sur [n], compté depuis la date de début de l'ordonnance. */
    data class UnJourSurN(val n: Int) : Rythme {
        init {
            require(n >= 2) { "un jour sur N exige n >= 2, reçu $n" }
        }
    }
}

data class Medicament(
    val id: Long,
    val nom: String,
    val dosage: String,
    val forme: Forme,
    val unitesParBoite: Int,
    val stockUnites: Double,
    /** Seuil d'alerte en jours pour une ordonnance planifiée ; nul = valeur par défaut des préférences. */
    val seuilAlerteJours: Int?,
    /** Seuil d'alerte en unités pour une ordonnance à la demande. */
    val seuilAlerteUnites: Int?,
    /** Déclenche l'alarme plein écran au lieu d'une notification classique. */
    val critique: Boolean,
)

data class DosePrescrite(val moment: Moment, val dose: Double)

data class Ordonnance(
    val id: Long,
    val medicamentId: Long,
    val type: TypeOrdonnance,
    val rythme: Rythme,
    val dateDebut: LocalDate,
    /** Nulle = traitement au long cours. */
    val dateFin: LocalDate?,
)

data class OrdonnanceAvecDoses(
    val ordonnance: Ordonnance,
    val doses: List<DosePrescrite>,
)

/** Journal du réel : une instance n'existe que si la prise a eu lieu. */
data class EvenementPrise(
    val id: Long,
    val medicamentId: Long,
    val date: LocalDate,
    /** Nul pour une prise à la demande, hors planning. */
    val moment: Moment?,
    val doseReelle: Double,
    val enregistreLe: Instant,
)

/** Prise calculée par le moteur, jamais persistée. */
data class PriseAttendue(
    val medicamentId: Long,
    val moment: Moment,
    val dose: Double,
    val heure: LocalDateTime,
)
```

- [ ] **Step 4: Supprimer le test du socle, devenu inutile**

```bash
rm domain/src/test/kotlin/fr/pillulier/domain/SocleTest.kt
```

- [ ] **Step 5: Lancer les tests**

```bash
./gradlew :domain:test
```

Attendu : les trois tests de `TypesTest` au vert.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(domain): types metier du pillulier"
```

---

### Task 3 : Jours fériés français

**Files:**
- Create: `domain/src/main/kotlin/fr/pillulier/domain/JoursFeriesFrance.kt`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/JoursFeriesFranceTest.kt`

**Interfaces:**
- Consumes: rien du domaine
- Produces: `JoursFeriesFrance.paques(annee: Int): LocalDate` et `JoursFeriesFrance.estFerie(date: LocalDate): Boolean`, consommés par `dateAlerte` en tâche 6

- [ ] **Step 1: Écrire le test qui échoue**

`domain/src/test/kotlin/fr/pillulier/domain/JoursFeriesFranceTest.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class JoursFeriesFranceTest {

    @Test
    fun `paques est calculee par le comput gregorien`() {
        assertEquals(LocalDate.of(2024, 3, 31), JoursFeriesFrance.paques(2024))
        assertEquals(LocalDate.of(2025, 4, 20), JoursFeriesFrance.paques(2025))
        assertEquals(LocalDate.of(2026, 4, 5), JoursFeriesFrance.paques(2026))
        assertEquals(LocalDate.of(2027, 3, 28), JoursFeriesFrance.paques(2027))
    }

    @Test
    fun `paques precoce et annee bissextile`() {
        assertEquals(LocalDate.of(2008, 3, 23), JoursFeriesFrance.paques(2008))
        assertEquals(LocalDate.of(2016, 3, 27), JoursFeriesFrance.paques(2016))
    }

    @Test
    fun `les huit jours fixes sont feries`() {
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 8),
            LocalDate.of(2026, 7, 14),
            LocalDate.of(2026, 8, 15),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 11),
            LocalDate.of(2026, 12, 25),
        ).forEach { assertTrue(JoursFeriesFrance.estFerie(it), "$it devrait etre ferie") }
    }

    @Test
    fun `les trois jours mobiles de 2026 sont feries`() {
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2026, 4, 6)), "lundi de Paques")
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2026, 5, 14)), "Ascension")
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2026, 5, 25)), "lundi de Pentecote")
    }

    @Test
    fun `les trois jours mobiles de 2024 sont feries`() {
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2024, 4, 1)), "lundi de Paques")
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2024, 5, 9)), "Ascension")
        assertTrue(JoursFeriesFrance.estFerie(LocalDate.of(2024, 5, 20)), "lundi de Pentecote")
    }

    @Test
    fun `un jour ordinaire n est pas ferie`() {
        assertFalse(JoursFeriesFrance.estFerie(LocalDate.of(2026, 3, 17)))
        assertFalse(JoursFeriesFrance.estFerie(LocalDate.of(2026, 4, 5)), "Paques est un dimanche, pas un jour ferie legal distinct a tester ici")
        assertFalse(JoursFeriesFrance.estFerie(LocalDate.of(2026, 12, 26)))
    }

    @Test
    fun `il y a exactement onze jours feries par an`() {
        listOf(2024, 2025, 2026, 2027).forEach { annee ->
            val compte = (1..LocalDate.of(annee, 12, 31).dayOfYear)
                .map { LocalDate.ofYearDay(annee, it) }
                .count { JoursFeriesFrance.estFerie(it) }
            assertEquals(11, compte, "annee $annee")
        }
    }
}
```

Note : `estFerie` renvoie `false` pour le dimanche de Pâques lui-même, qui n'est pas un jour férié légal en France ; c'est le lundi qui l'est. La règle de marge traite les dimanches à part, indépendamment des fériés.

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :domain:test --tests 'fr.pillulier.domain.JoursFeriesFranceTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: JoursFeriesFrance`

- [ ] **Step 3: Écrire l'implémentation**

`domain/src/main/kotlin/fr/pillulier/domain/JoursFeriesFrance.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDate
import java.time.MonthDay

/**
 * Jours fériés de France métropolitaine, calculés sans aucune donnée stockée.
 * Ne couvre ni les deux jours d'Alsace-Moselle ni les dates d'outre-mer.
 */
object JoursFeriesFrance {

    private val joursFixes = setOf(
        MonthDay.of(1, 1),   // Jour de l'an
        MonthDay.of(5, 1),   // Fête du Travail
        MonthDay.of(5, 8),   // Victoire 1945
        MonthDay.of(7, 14),  // Fête nationale
        MonthDay.of(8, 15),  // Assomption
        MonthDay.of(11, 1),  // Toussaint
        MonthDay.of(11, 11), // Armistice 1918
        MonthDay.of(12, 25), // Noël
    )

    /** Dimanche de Pâques, par l'algorithme de Meeus pour le calendrier grégorien. */
    fun paques(annee: Int): LocalDate {
        val a = annee % 19
        val b = annee / 100
        val c = annee % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val n = h + l - 7 * m + 114
        return LocalDate.of(annee, n / 31, (n % 31) + 1)
    }

    /** Les trois fériés mobiles : lundi de Pâques, Ascension, lundi de Pentecôte. */
    private fun joursMobiles(annee: Int): Set<LocalDate> {
        val paques = paques(annee)
        return setOf(
            paques.plusDays(1),
            paques.plusDays(39),
            paques.plusDays(50),
        )
    }

    fun estFerie(date: LocalDate): Boolean =
        MonthDay.from(date) in joursFixes || date in joursMobiles(date.year)
}
```

- [ ] **Step 4: Lancer les tests**

```bash
./gradlew :domain:test --tests 'fr.pillulier.domain.JoursFeriesFranceTest'
```

Attendu : les sept tests au vert.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(domain): calcul des jours feries francais"
```

---

### Task 4 : Planning — jour actif et prises attendues

**Files:**
- Create: `domain/src/main/kotlin/fr/pillulier/domain/Planning.kt`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/PlanningTest.kt`

**Interfaces:**
- Consumes: les types de la tâche 2
- Produces:
  - `estJourActif(ordonnance: Ordonnance, date: LocalDate): Boolean`
  - `prisesAttendues(date: LocalDate, ordonnances: List<OrdonnanceAvecDoses>, heures: Map<Moment, LocalTime>): List<PriseAttendue>` — triée chronologiquement puis par `medicamentId`

- [ ] **Step 1: Écrire le test qui échoue**

`domain/src/test/kotlin/fr/pillulier/domain/PlanningTest.kt` :

```kotlin
package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2026-01-01 est un jeudi ; toutes les dates de ce test s'y réfèrent. */
class PlanningTest {

    private val heures = mapOf(
        Moment.MATIN to LocalTime.of(8, 0),
        Moment.MIDI to LocalTime.of(12, 0),
        Moment.SOIR to LocalTime.of(19, 0),
        Moment.COUCHER to LocalTime.of(22, 0),
    )

    private fun ordonnance(
        id: Long = 1,
        medicamentId: Long = 1,
        type: TypeOrdonnance = TypeOrdonnance.PLANIFIEE,
        rythme: Rythme = Rythme.TousLesJours,
        dateDebut: LocalDate = LocalDate.of(2026, 1, 1),
        dateFin: LocalDate? = null,
    ) = Ordonnance(id, medicamentId, type, rythme, dateDebut, dateFin)

    @Test
    fun `tous les jours retient chaque date de la fenetre`() {
        val o = ordonnance()
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 1)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 6, 30)))
    }

    @Test
    fun `jours de semaine ne retient que les jours listes`() {
        val o = ordonnance(rythme = Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 1)), "jeudi")
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 2)), "vendredi")
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 5)), "lundi")
    }

    @Test
    fun `un jour sur N est compte depuis la date de debut`() {
        val o = ordonnance(rythme = Rythme.UnJourSurN(2), dateDebut = LocalDate.of(2026, 1, 1))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 1)))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 2)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 3)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 11)))
    }

    @Test
    fun `un jour sur trois est compte depuis la date de debut`() {
        val o = ordonnance(rythme = Rythme.UnJourSurN(3), dateDebut = LocalDate.of(2026, 1, 2))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 1)), "avant le debut")
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 2)))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 3)))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 4)))
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `la fenetre de dates borne le rythme`() {
        val o = ordonnance(dateDebut = LocalDate.of(2026, 1, 5), dateFin = LocalDate.of(2026, 1, 7))
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 4)), "veille du debut")
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 5)), "premier jour de la cure")
        assertTrue(estJourActif(o, LocalDate.of(2026, 1, 7)), "dernier jour de la cure")
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 8)), "lendemain de la fin")
    }

    @Test
    fun `une ordonnance a la demande n est jamais un jour actif`() {
        val o = ordonnance(type = TypeOrdonnance.A_LA_DEMANDE)
        assertFalse(estJourActif(o, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `chaque dose prescrite produit une prise attendue horodatee`() {
        val ordonnances = listOf(
            OrdonnanceAvecDoses(
                ordonnance = ordonnance(id = 1, medicamentId = 10),
                doses = listOf(DosePrescrite(Moment.MATIN, 2.0), DosePrescrite(Moment.SOIR, 0.5)),
            ),
        )

        val prises = prisesAttendues(LocalDate.of(2026, 1, 1), ordonnances, heures)

        assertEquals(
            listOf(
                PriseAttendue(10, Moment.MATIN, 2.0, LocalDateTime.of(2026, 1, 1, 8, 0)),
                PriseAttendue(10, Moment.SOIR, 0.5, LocalDateTime.of(2026, 1, 1, 19, 0)),
            ),
            prises,
        )
    }

    @Test
    fun `les prises de plusieurs medicaments sont triees par heure puis par medicament`() {
        val ordonnances = listOf(
            OrdonnanceAvecDoses(ordonnance(id = 2, medicamentId = 20), listOf(DosePrescrite(Moment.SOIR, 1.0))),
            OrdonnanceAvecDoses(ordonnance(id = 1, medicamentId = 30), listOf(DosePrescrite(Moment.MATIN, 1.0))),
            OrdonnanceAvecDoses(ordonnance(id = 3, medicamentId = 10), listOf(DosePrescrite(Moment.MATIN, 1.0))),
        )

        val prises = prisesAttendues(LocalDate.of(2026, 1, 1), ordonnances, heures)

        assertEquals(listOf(10L, 30L, 20L), prises.map { it.medicamentId })
    }

    @Test
    fun `une ordonnance hors de son rythme ne produit aucune prise`() {
        val ordonnances = listOf(
            OrdonnanceAvecDoses(
                ordonnance(rythme = Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY))),
                listOf(DosePrescrite(Moment.MATIN, 1.0)),
            ),
        )

        assertTrue(prisesAttendues(LocalDate.of(2026, 1, 1), ordonnances, heures).isEmpty())
    }

    @Test
    fun `une heure manquante est une erreur de programmation`() {
        val ordonnances = listOf(
            OrdonnanceAvecDoses(ordonnance(), listOf(DosePrescrite(Moment.MATIN, 1.0))),
        )

        assertFailsWith<IllegalArgumentException> {
            prisesAttendues(LocalDate.of(2026, 1, 1), ordonnances, mapOf(Moment.MATIN to LocalTime.of(8, 0)))
        }
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :domain:test --tests 'fr.pillulier.domain.PlanningTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: estJourActif`

- [ ] **Step 3: Écrire l'implémentation**

`domain/src/main/kotlin/fr/pillulier/domain/Planning.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Vrai si l'ordonnance planifiée prévoit une prise à cette date : elle est dans
 * la fenêtre début/fin, et son rythme retient le jour.
 */
fun estJourActif(ordonnance: Ordonnance, date: LocalDate): Boolean {
    if (ordonnance.type != TypeOrdonnance.PLANIFIEE) return false
    if (date < ordonnance.dateDebut) return false
    ordonnance.dateFin?.let { if (date > it) return false }

    return when (val rythme = ordonnance.rythme) {
        Rythme.TousLesJours -> true
        is Rythme.JoursDeSemaine -> date.dayOfWeek in rythme.jours
        is Rythme.UnJourSurN ->
            (date.toEpochDay() - ordonnance.dateDebut.toEpochDay()) % rythme.n == 0L
    }
}

/**
 * Unique chemin de calcul du planning : Aujourd'hui, la vue semaine et
 * la programmation des alarmes passent tous les trois par ici.
 */
fun prisesAttendues(
    date: LocalDate,
    ordonnances: List<OrdonnanceAvecDoses>,
    heures: Map<Moment, LocalTime>,
): List<PriseAttendue> {
    require(heures.keys.containsAll(Moment.entries.toSet())) {
        "les quatre moments doivent avoir une heure, reçu ${heures.keys}"
    }

    return ordonnances
        .filter { estJourActif(it.ordonnance, date) }
        .flatMap { avecDoses ->
            avecDoses.doses.map { dose ->
                PriseAttendue(
                    medicamentId = avecDoses.ordonnance.medicamentId,
                    moment = dose.moment,
                    dose = dose.dose,
                    heure = LocalDateTime.of(date, heures.getValue(dose.moment)),
                )
            }
        }
        .sortedWith(compareBy({ it.heure }, { it.medicamentId }))
}
```

- [ ] **Step 4: Lancer les tests**

```bash
./gradlew :domain:test
```

Attendu : tous les tests au vert.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(domain): calcul des prises attendues d une journee"
```

---

### Task 5 : Statut d'une prise attendue

**Files:**
- Create: `domain/src/main/kotlin/fr/pillulier/domain/Statut.kt`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/StatutTest.kt`

**Interfaces:**
- Consumes: `PriseAttendue`, `EvenementPrise`, `StatutPrise` de la tâche 2
- Produces: `statut(prise: PriseAttendue, evenements: List<EvenementPrise>, maintenant: LocalDateTime): StatutPrise`

- [ ] **Step 1: Écrire le test qui échoue**

`domain/src/test/kotlin/fr/pillulier/domain/StatutTest.kt` :

```kotlin
package fr.pillulier.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StatutTest {

    private val prise = PriseAttendue(
        medicamentId = 10,
        moment = Moment.MATIN,
        dose = 1.0,
        heure = LocalDateTime.of(2026, 1, 5, 8, 0),
    )

    private fun evenement(
        medicamentId: Long = 10,
        date: LocalDate = LocalDate.of(2026, 1, 5),
        moment: Moment? = Moment.MATIN,
    ) = EvenementPrise(
        id = 1,
        medicamentId = medicamentId,
        date = date,
        moment = moment,
        doseReelle = 1.0,
        enregistreLe = Instant.parse("2026-01-05T07:55:00Z"),
    )

    @Test
    fun `a venir avant l heure du moment`() {
        val statut = statut(prise, emptyList(), LocalDateTime.of(2026, 1, 5, 7, 59))
        assertEquals(StatutPrise.A_VENIR, statut)
    }

    @Test
    fun `en retard a l heure exacte du moment`() {
        val statut = statut(prise, emptyList(), LocalDateTime.of(2026, 1, 5, 8, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `en retard tant que la journee court`() {
        val statut = statut(prise, emptyList(), LocalDateTime.of(2026, 1, 5, 23, 59))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `oubliee des que la journee est terminee`() {
        val statut = statut(prise, emptyList(), LocalDateTime.of(2026, 1, 6, 0, 0))
        assertEquals(StatutPrise.OUBLIEE, statut)
    }

    @Test
    fun `prise si un evenement correspond`() {
        val statut = statut(prise, listOf(evenement()), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.PRISE, statut)
    }

    @Test
    fun `prise reste prise apres la fin de la journee`() {
        val statut = statut(prise, listOf(evenement()), LocalDateTime.of(2026, 1, 9, 9, 0))
        assertEquals(StatutPrise.PRISE, statut)
    }

    @Test
    fun `un evenement d un autre medicament ne compte pas`() {
        val statut = statut(prise, listOf(evenement(medicamentId = 99)), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `un evenement d un autre moment ne compte pas`() {
        val statut = statut(prise, listOf(evenement(moment = Moment.SOIR)), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `un evenement d un autre jour ne compte pas`() {
        val statut = statut(prise, listOf(evenement(date = LocalDate.of(2026, 1, 4))), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }

    @Test
    fun `une prise a la demande ne satisfait pas une prise planifiee`() {
        val statut = statut(prise, listOf(evenement(moment = null)), LocalDateTime.of(2026, 1, 5, 9, 0))
        assertEquals(StatutPrise.EN_RETARD, statut)
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :domain:test --tests 'fr.pillulier.domain.StatutTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: statut`

- [ ] **Step 3: Écrire l'implémentation**

`domain/src/main/kotlin/fr/pillulier/domain/Statut.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDateTime

/**
 * Statut dérivé, jamais persisté. Une prise non cochée dont la journée est
 * terminée est oubliée par la seule absence d'événement.
 */
fun statut(
    prise: PriseAttendue,
    evenements: List<EvenementPrise>,
    maintenant: LocalDateTime,
): StatutPrise {
    val jour = prise.heure.toLocalDate()

    val faite = evenements.any {
        it.medicamentId == prise.medicamentId && it.moment == prise.moment && it.date == jour
    }
    if (faite) return StatutPrise.PRISE

    return when {
        jour < maintenant.toLocalDate() -> StatutPrise.OUBLIEE
        !prise.heure.isAfter(maintenant) -> StatutPrise.EN_RETARD
        else -> StatutPrise.A_VENIR
    }
}
```

- [ ] **Step 4: Lancer les tests**

```bash
./gradlew :domain:test
```

Attendu : tous les tests au vert.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(domain): derivation du statut des prises"
```

---

### Task 6 : Stock — consommation, projection et date d'alerte

**Files:**
- Create: `domain/src/main/kotlin/fr/pillulier/domain/Stock.kt`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/StockTest.kt`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/DateAlerteTest.kt`

**Interfaces:**
- Consumes: `estJourActif` de la tâche 4, `JoursFeriesFrance` de la tâche 3
- Produces:
  - `consommationDuJour(ordonnance: OrdonnanceAvecDoses, date: LocalDate): Double`
  - `projectionStock(stockUnites: Double, ordonnance: OrdonnanceAvecDoses, depuis: LocalDate, horizonJours: Int = 365): LocalDate?`
  - `dateAlerte(dateEpuisement: LocalDate, seuilJours: Int, estFerie: (LocalDate) -> Boolean): LocalDate`

- [ ] **Step 1: Écrire le test de projection qui échoue**

`domain/src/test/kotlin/fr/pillulier/domain/StockTest.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StockTest {

    private val debut = LocalDate.of(2026, 1, 1)

    private fun ordonnance(
        rythme: Rythme = Rythme.TousLesJours,
        dateFin: LocalDate? = null,
        doses: List<DosePrescrite> = listOf(DosePrescrite(Moment.MATIN, 1.0)),
    ) = OrdonnanceAvecDoses(
        ordonnance = Ordonnance(1, 10, TypeOrdonnance.PLANIFIEE, rythme, debut, dateFin),
        doses = doses,
    )

    @Test
    fun `la consommation d un jour est la somme des doses du jour`() {
        val o = ordonnance(doses = listOf(DosePrescrite(Moment.MATIN, 2.0), DosePrescrite(Moment.SOIR, 0.5)))
        assertEquals(2.5, consommationDuJour(o, debut))
    }

    @Test
    fun `la consommation est nulle un jour non retenu par le rythme`() {
        val o = ordonnance(rythme = Rythme.UnJourSurN(2))
        assertEquals(0.0, consommationDuJour(o, LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun `dix comprimes a un par jour s epuisent le onzieme jour`() {
        assertEquals(
            LocalDate.of(2026, 1, 11),
            projectionStock(stockUnites = 10.0, ordonnance = ordonnance(), depuis = debut),
        )
    }

    @Test
    fun `les demi doses sont comptees exactement`() {
        val o = ordonnance(doses = listOf(DosePrescrite(Moment.MATIN, 0.5), DosePrescrite(Moment.SOIR, 0.5)))
        assertEquals(LocalDate.of(2026, 1, 11), projectionStock(10.0, o, debut))
    }

    @Test
    fun `un rythme irregulier etire la date d epuisement`() {
        val o = ordonnance(rythme = Rythme.UnJourSurN(2))
        // Consommation les 1, 3, 5, 7 et 9 janvier ; le 11 ne peut plus etre servi.
        assertEquals(LocalDate.of(2026, 1, 11), projectionStock(5.0, o, debut))
    }

    @Test
    fun `une cure qui s arrete avant l epuisement n a pas de rupture`() {
        val o = ordonnance(dateFin = LocalDate.of(2026, 1, 5))
        assertNull(projectionStock(10.0, o, debut))
    }

    @Test
    fun `un stock vide s epuise le jour meme`() {
        assertEquals(debut, projectionStock(0.0, ordonnance(), debut))
    }

    @Test
    fun `un stock insuffisant pour une journee complete s epuise ce jour la`() {
        val o = ordonnance(doses = listOf(DosePrescrite(Moment.MATIN, 2.0)))
        assertEquals(LocalDate.of(2026, 1, 2), projectionStock(3.0, o, debut))
    }

    @Test
    fun `au dela de l horizon il n y a pas de rupture a annoncer`() {
        assertNull(projectionStock(10_000.0, ordonnance(), debut))
    }

    @Test
    fun `l horizon est configurable`() {
        assertNull(projectionStock(30.0, ordonnance(), debut, horizonJours = 10))
    }
}
```

- [ ] **Step 2: Écrire le test de date d'alerte**

`domain/src/test/kotlin/fr/pillulier/domain/DateAlerteTest.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Repères : le 2026-01-01 est un jeudi, le 2026-01-11 un dimanche,
 * le 2026-05-01 un vendredi (férié), le 2026-05-08 un vendredi (férié).
 */
class DateAlerteTest {

    private val ferie = JoursFeriesFrance::estFerie

    @Test
    fun `sans jour ferme la fenetre l alerte tombe au seuil exact`() {
        assertEquals(
            LocalDate.of(2026, 1, 12),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 1, 15), seuilJours = 3, estFerie = ferie),
        )
    }

    @Test
    fun `un dimanche dans la fenetre recule l alerte d un jour`() {
        assertEquals(
            LocalDate.of(2026, 1, 7),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 1, 15), seuilJours = 7, estFerie = ferie),
        )
    }

    @Test
    fun `un ferie dans la fenetre recule l alerte d un jour`() {
        assertEquals(
            LocalDate.of(2026, 4, 30),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 5, 2), seuilJours = 1, estFerie = ferie),
        )
    }

    @Test
    fun `plusieurs jours fermes n ajoutent qu un seul jour de marge`() {
        assertEquals(
            LocalDate.of(2026, 5, 3),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 5, 11), seuilJours = 7, estFerie = ferie),
        )
    }

    @Test
    fun `le jour d epuisement fait partie de la fenetre`() {
        assertEquals(
            LocalDate.of(2026, 1, 10),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 1, 11), seuilJours = 0, estFerie = ferie),
        )
    }

    @Test
    fun `un seuil nul sans jour ferme laisse l alerte le jour de l epuisement`() {
        assertEquals(
            LocalDate.of(2026, 1, 15),
            dateAlerte(dateEpuisement = LocalDate.of(2026, 1, 15), seuilJours = 0, estFerie = ferie),
        )
    }
}
```

- [ ] **Step 3: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew :domain:test --tests 'fr.pillulier.domain.StockTest' --tests 'fr.pillulier.domain.DateAlerteTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: consommationDuJour`

- [ ] **Step 4: Écrire l'implémentation**

`domain/src/main/kotlin/fr/pillulier/domain/Stock.kt` :

```kotlin
package fr.pillulier.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** Tolérance de comparaison des doses décimales. */
private const val EPSILON = 1e-9

/** Somme des doses prévues ce jour-là, ou zéro si le rythme ne retient pas le jour. */
fun consommationDuJour(ordonnance: OrdonnanceAvecDoses, date: LocalDate): Double =
    if (estJourActif(ordonnance.ordonnance, date)) ordonnance.doses.sumOf { it.dose } else 0.0

/**
 * Premier jour dont la consommation prévue ne peut plus être servie, ou `null`
 * s'il n'y a pas de rupture à annoncer : l'ordonnance s'arrête avant, ou le stock
 * survit à l'horizon.
 *
 * L'itération jour par jour est nécessaire : un rythme irrégulier ou une cure
 * datée donnent une consommation qu'une division ne capture pas.
 */
fun projectionStock(
    stockUnites: Double,
    ordonnance: OrdonnanceAvecDoses,
    depuis: LocalDate,
    horizonJours: Int = 365,
): LocalDate? {
    var restant = stockUnites
    var jour = depuis

    repeat(horizonJours) {
        ordonnance.ordonnance.dateFin?.let { fin -> if (jour > fin) return null }

        val conso = consommationDuJour(ordonnance, jour)
        if (conso > EPSILON) {
            if (restant < conso - EPSILON) return jour
            restant -= conso
        }
        jour = jour.plusDays(1)
    }

    return null
}

/**
 * Date à laquelle prévenir qu'il faut renouveler : le seuil en jours retiré de la
 * date d'épuisement, reculé d'un jour supplémentaire si un dimanche ou un jour
 * férié tombe dans la fenêtre — la pharmacie est alors fermée. Un seul jour de
 * marge est ajouté, quel que soit le nombre de jours fermés.
 */
fun dateAlerte(
    dateEpuisement: LocalDate,
    seuilJours: Int,
    estFerie: (LocalDate) -> Boolean,
): LocalDate {
    val candidat = dateEpuisement.minusDays(seuilJours.toLong())

    val fenetreContientJourFerme = generateSequence(candidat) { it.plusDays(1) }
        .takeWhile { !it.isAfter(dateEpuisement) }
        .any { it.dayOfWeek == DayOfWeek.SUNDAY || estFerie(it) }

    return if (fenetreContientJourFerme) candidat.minusDays(1) else candidat
}
```

- [ ] **Step 5: Lancer les tests**

```bash
./gradlew :domain:test
```

Attendu : tous les tests au vert.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(domain): projection du stock et date d alerte avec marge ferie"
```

---

### Task 7 : Clé de rappel et code de requête déterministe

**Files:**
- Create: `domain/src/main/kotlin/fr/pillulier/domain/Rappel.kt`
- Test: `domain/src/test/kotlin/fr/pillulier/domain/RappelTest.kt`

**Interfaces:**
- Consumes: `Moment` de la tâche 2
- Produces:
  - `data class CleRappel(val medicamentId: Long, val date: LocalDate, val moment: Moment)`
  - `fun CleRappel.codeRequete(): Int` — identifiant stable réutilisé comme `requestCode` de `PendingIntent` et comme identifiant de notification en tâches 11 à 14

- [ ] **Step 1: Écrire le test qui échoue**

`domain/src/test/kotlin/fr/pillulier/domain/RappelTest.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RappelTest {

    @Test
    fun `le code de requete est deterministe`() {
        val cle = CleRappel(42, LocalDate.of(2026, 1, 5), Moment.SOIR)
        assertEquals(cle.codeRequete(), CleRappel(42, LocalDate.of(2026, 1, 5), Moment.SOIR).codeRequete())
    }

    @Test
    fun `le code de requete est toujours positif`() {
        val cle = CleRappel(1, LocalDate.of(1970, 1, 1), Moment.MATIN)
        assertTrue(cle.codeRequete() >= 0)
        assertTrue(CleRappel(999, LocalDate.of(2030, 12, 31), Moment.COUCHER).codeRequete() >= 0)
    }

    @Test
    fun `les codes sont distincts sur une fenetre realiste`() {
        val debut = LocalDate.of(2026, 1, 1)
        val codes = buildList {
            (0..3L).forEach { decalage ->
                (1L..200L).forEach { medicamentId ->
                    Moment.entries.forEach { moment ->
                        add(CleRappel(medicamentId, debut.plusDays(decalage), moment).codeRequete())
                    }
                }
            }
        }

        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `changer un seul composant change le code`() {
        val base = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)
        assertTrue(base.codeRequete() != base.copy(medicamentId = 8).codeRequete())
        assertTrue(base.codeRequete() != base.copy(moment = Moment.MIDI).codeRequete())
        assertTrue(base.codeRequete() != base.copy(date = LocalDate.of(2026, 1, 6)).codeRequete())
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :domain:test --tests 'fr.pillulier.domain.RappelTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: CleRappel`

- [ ] **Step 3: Écrire l'implémentation**

`domain/src/main/kotlin/fr/pillulier/domain/Rappel.kt` :

```kotlin
package fr.pillulier.domain

import java.time.LocalDate

/** Identifie un rappel : un médicament, un jour, un moment. */
data class CleRappel(
    val medicamentId: Long,
    val date: LocalDate,
    val moment: Moment,
)

/**
 * Identifiant stable et positif, calculé sans état : il sert de `requestCode` de
 * `PendingIntent` et d'identifiant de notification, ce qui permet de reconstruire
 * ou d'annuler un rappel sans avoir mémorisé quoi que ce soit.
 *
 * Injectif tant que les identifiants de médicament restent sous 1000 et que la
 * fenêtre de programmation reste sous 1000 jours — la fenêtre réelle est de trois
 * jours.
 */
fun CleRappel.codeRequete(): Int {
    val jour = (date.toEpochDay().mod(1_000L)).toInt()
    val medicament = (medicamentId.mod(1_000L)).toInt()
    return jour * 4_000 + moment.ordinal * 1_000 + medicament
}
```

- [ ] **Step 4: Lancer les tests**

```bash
./gradlew :domain:test
```

Attendu : tous les tests du module `:domain` au vert. Le moteur est complet.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(domain): cle de rappel et code de requete deterministe"
```

---

### Task 8 : Persistance Room

**Files:**
- Modify: `gradle/libs.versions.toml` (ajout de Robolectric)
- Modify: `app/build.gradle.kts` (schéma Room, Robolectric, ressources dans les tests JVM)
- Create: `app/src/main/kotlin/fr/pillulier/app/data/db/Convertisseurs.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/db/Entites.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/db/MedicamentDao.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/db/OrdonnanceDao.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/db/EvenementPriseDao.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/db/MomentConfigDao.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/db/PillulierDatabase.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/data/db/PillulierDatabaseTest.kt`

**Interfaces:**
- Consumes: les énumérations et types de la tâche 2
- Produces: `PillulierDatabase` avec `medicaments()`, `ordonnances()`, `evenements()`, `moments()` ; les entités `MedicamentEntity`, `OrdonnanceEntity`, `DosePrescriteEntity`, `MomentConfigEntity`, `EvenementPriseEntity`, `OrdonnanceAvecDosesEntity`

Les tests de base tournent sous Robolectric, donc en JVM avec `./gradlew :app:testDebugUnitTest` : aucun émulateur n'est nécessaire.

- [ ] **Step 1: Ajouter Robolectric au catalogue et configurer le module**

Dans `gradle/libs.versions.toml`, ajouter sous `[versions]` :

```toml
robolectric = "4.14.1"
androidxTestCore = "1.6.1"
```

et sous `[libraries]` :

```toml
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
```

Dans `app/build.gradle.kts`, à l'intérieur du bloc `android`, ajouter :

```kotlin
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
```

Après le bloc `android`, ajouter :

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Et dans `dependencies`, ajouter :

```kotlin
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.testing)
```

- [ ] **Step 2: Écrire le test qui échoue**

`app/src/test/kotlin/fr/pillulier/app/data/db/PillulierDatabaseTest.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PillulierDatabaseTest {

    private lateinit var base: PillulierDatabase

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        )
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun fermer() {
        base.close()
    }

    private fun medicament(nom: String = "Levothyrox", stock: Double = 30.0) = MedicamentEntity(
        nom = nom,
        dosage = "75 µg",
        forme = Forme.COMPRIME,
        unitesParBoite = 30,
        stockUnites = stock,
        seuilAlerteJours = 7,
        seuilAlerteUnites = null,
        critique = true,
    )

    @Test
    fun `les quatre moments sont amorces a la creation de la base`() = runTest {
        val configs = base.moments().tous().associate { it.moment to it.heure }

        assertEquals(4, configs.size)
        assertEquals(LocalTime.of(8, 0), configs[Moment.MATIN])
        assertEquals(LocalTime.of(12, 0), configs[Moment.MIDI])
        assertEquals(LocalTime.of(19, 0), configs[Moment.SOIR])
        assertEquals(LocalTime.of(22, 0), configs[Moment.COUCHER])
    }

    @Test
    fun `un medicament fait l aller retour sans perdre ses champs`() = runTest {
        val id = base.medicaments().inserer(medicament())

        val relu = base.medicaments().parId(id)!!

        assertEquals("Levothyrox", relu.nom)
        assertEquals("75 µg", relu.dosage)
        assertEquals(Forme.COMPRIME, relu.forme)
        assertEquals(30.0, relu.stockUnites)
        assertEquals(7, relu.seuilAlerteJours)
        assertNull(relu.seuilAlerteUnites)
        assertTrue(relu.critique)
    }

    @Test
    fun `ajuster le stock l incremente et le decremente`() = runTest {
        val id = base.medicaments().inserer(medicament(stock = 10.0))

        base.medicaments().ajusterStock(id, -0.5)
        assertEquals(9.5, base.medicaments().parId(id)!!.stockUnites)

        base.medicaments().ajusterStock(id, 30.0)
        assertEquals(39.5, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `definir le stock ecrase la valeur`() = runTest {
        val id = base.medicaments().inserer(medicament(stock = 10.0))

        base.medicaments().definirStock(id, 42.0)

        assertEquals(42.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `une ordonnance revient avec ses doses`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        val ordonnanceId = base.ordonnances().insererOrdonnance(
            OrdonnanceEntity(
                medicamentId = medicamentId,
                type = TypeOrdonnance.PLANIFIEE,
                rythmeType = "TOUS_LES_JOURS",
                rythmeJours = null,
                rythmeN = null,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
        )
        base.ordonnances().insererDoses(
            listOf(
                DosePrescriteEntity(ordonnanceId = ordonnanceId, moment = Moment.MATIN, dose = 1.0),
                DosePrescriteEntity(ordonnanceId = ordonnanceId, moment = Moment.SOIR, dose = 0.5),
            ),
        )

        val relu = base.ordonnances().pourMedicament(medicamentId)!!

        assertEquals(LocalDate.of(2026, 1, 1), relu.ordonnance.dateDebut)
        assertEquals(setOf(Moment.MATIN, Moment.SOIR), relu.doses.map { it.moment }.toSet())
        assertEquals(0.5, relu.doses.first { it.moment == Moment.SOIR }.dose)
    }

    @Test
    fun `supprimer un medicament supprime son ordonnance et ses doses en cascade`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        val ordonnanceId = base.ordonnances().insererOrdonnance(
            OrdonnanceEntity(
                medicamentId = medicamentId,
                type = TypeOrdonnance.PLANIFIEE,
                rythmeType = "TOUS_LES_JOURS",
                rythmeJours = null,
                rythmeN = null,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
        )
        base.ordonnances().insererDoses(
            listOf(DosePrescriteEntity(ordonnanceId = ordonnanceId, moment = Moment.MATIN, dose = 1.0)),
        )

        base.medicaments().supprimer(medicamentId)

        assertNull(base.ordonnances().pourMedicament(medicamentId))
        assertEquals(0, base.ordonnances().comptePourOrdonnance(ordonnanceId))
    }

    @Test
    fun `enregistrer deux fois la meme prise planifiee est sans effet`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        val prise = EvenementPriseEntity(
            medicamentId = medicamentId,
            date = LocalDate.of(2026, 1, 5),
            moment = Moment.MATIN,
            doseReelle = 1.0,
            enregistreLe = Instant.parse("2026-01-05T07:00:00Z"),
        )

        val premier = base.evenements().inserer(prise)
        val second = base.evenements().inserer(prise.copy(enregistreLe = Instant.parse("2026-01-05T08:00:00Z")))

        assertTrue(premier > 0)
        assertEquals(-1L, second, "la seconde insertion doit etre ignoree")
        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
    }

    @Test
    fun `plusieurs prises a la demande le meme jour sont autorisees`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        val prise = EvenementPriseEntity(
            medicamentId = medicamentId,
            date = LocalDate.of(2026, 1, 5),
            moment = null,
            doseReelle = 1.0,
            enregistreLe = Instant.parse("2026-01-05T07:00:00Z"),
        )

        base.evenements().inserer(prise)
        base.evenements().inserer(prise.copy(enregistreLe = Instant.parse("2026-01-05T15:00:00Z")))

        assertEquals(2, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
    }

    @Test
    fun `les evenements se lisent par intervalle de dates`() = runTest {
        val medicamentId = base.medicaments().inserer(medicament())
        listOf(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)).forEach { date ->
            base.evenements().inserer(
                EvenementPriseEntity(
                    medicamentId = medicamentId,
                    date = date,
                    moment = Moment.MATIN,
                    doseReelle = 1.0,
                    enregistreLe = Instant.parse("2026-01-01T07:00:00Z"),
                ),
            )
        }

        val dansLaSemaine = base.evenements().entre(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11))

        assertEquals(listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)), dansLaSemaine.map { it.date })
    }
}
```

- [ ] **Step 3: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.data.db.PillulierDatabaseTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: PillulierDatabase`

- [ ] **Step 4: Écrire les convertisseurs**

`app/src/main/kotlin/fr/pillulier/app/data/db/Convertisseurs.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.TypeConverter
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.TypeOrdonnance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Convertisseurs {

    @TypeConverter fun versJourEpoch(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter fun versLocalDate(jourEpoch: Long?): LocalDate? = jourEpoch?.let(LocalDate::ofEpochDay)

    @TypeConverter fun versMinutes(heure: LocalTime?): Int? = heure?.toSecondOfDay()?.div(60)

    @TypeConverter fun versLocalTime(minutes: Int?): LocalTime? =
        minutes?.let { LocalTime.ofSecondOfDay(it.toLong() * 60) }

    @TypeConverter fun versMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter fun versInstant(millis: Long?): Instant? = millis?.let(Instant::ofEpochMilli)

    @TypeConverter fun versNomForme(forme: Forme?): String? = forme?.name

    @TypeConverter fun versForme(nom: String?): Forme? = nom?.let(Forme::valueOf)

    @TypeConverter fun versNomMoment(moment: Moment?): String? = moment?.name

    @TypeConverter fun versMoment(nom: String?): Moment? = nom?.let(Moment::valueOf)

    @TypeConverter fun versNomType(type: TypeOrdonnance?): String? = type?.name

    @TypeConverter fun versTypeOrdonnance(nom: String?): TypeOrdonnance? = nom?.let(TypeOrdonnance::valueOf)
}
```

- [ ] **Step 5: Écrire les entités**

`app/src/main/kotlin/fr/pillulier/app/data/db/Entites.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.TypeOrdonnance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "medicament")
data class MedicamentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val dosage: String,
    val forme: Forme,
    val unitesParBoite: Int,
    val stockUnites: Double,
    val seuilAlerteJours: Int?,
    val seuilAlerteUnites: Int?,
    val critique: Boolean,
)

/** Un médicament porte au plus une ordonnance : l'index sur `medicamentId` est unique. */
@Entity(
    tableName = "ordonnance",
    foreignKeys = [
        ForeignKey(
            entity = MedicamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicamentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["medicamentId"], unique = true)],
)
data class OrdonnanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentId: Long,
    val type: TypeOrdonnance,
    /** `TOUS_LES_JOURS`, `JOURS_DE_SEMAINE` ou `UN_JOUR_SUR_N`. */
    val rythmeType: String,
    /** Jours de semaine séparés par des virgules, ex. `MONDAY,THURSDAY`. */
    val rythmeJours: String?,
    val rythmeN: Int?,
    val dateDebut: LocalDate,
    val dateFin: LocalDate?,
)

@Entity(
    tableName = "dose_prescrite",
    foreignKeys = [
        ForeignKey(
            entity = OrdonnanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["ordonnanceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["ordonnanceId", "moment"], unique = true)],
)
data class DosePrescriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ordonnanceId: Long,
    val moment: Moment,
    val dose: Double,
)

@Entity(tableName = "moment_config")
data class MomentConfigEntity(
    @PrimaryKey val moment: Moment,
    val heure: LocalTime,
)

/**
 * Journal du réel. L'index unique rend l'enregistrement d'une prise planifiée
 * idempotent ; comme SQLite considère deux `NULL` comme distincts, les prises à
 * la demande (`moment` nul) ne sont pas contraintes.
 */
@Entity(
    tableName = "evenement_prise",
    foreignKeys = [
        ForeignKey(
            entity = MedicamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicamentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["medicamentId", "date", "moment"], unique = true),
        Index(value = ["date"]),
    ],
)
data class EvenementPriseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentId: Long,
    val date: LocalDate,
    val moment: Moment?,
    val doseReelle: Double,
    val enregistreLe: Instant,
)

data class OrdonnanceAvecDosesEntity(
    @Embedded val ordonnance: OrdonnanceEntity,
    @Relation(parentColumn = "id", entityColumn = "ordonnanceId")
    val doses: List<DosePrescriteEntity>,
)
```

- [ ] **Step 6: Écrire les DAO**

`app/src/main/kotlin/fr/pillulier/app/data/db/MedicamentDao.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicamentDao {

    @Query("SELECT * FROM medicament ORDER BY nom COLLATE NOCASE")
    fun observerTous(): Flow<List<MedicamentEntity>>

    @Query("SELECT * FROM medicament")
    suspend fun tous(): List<MedicamentEntity>

    @Query("SELECT * FROM medicament WHERE id = :id")
    suspend fun parId(id: Long): MedicamentEntity?

    @Insert
    suspend fun inserer(medicament: MedicamentEntity): Long

    @Update
    suspend fun mettreAJour(medicament: MedicamentEntity)

    @Query("DELETE FROM medicament WHERE id = :id")
    suspend fun supprimer(id: Long)

    @Query("UPDATE medicament SET stockUnites = stockUnites + :delta WHERE id = :id")
    suspend fun ajusterStock(id: Long, delta: Double)

    @Query("UPDATE medicament SET stockUnites = :valeur WHERE id = :id")
    suspend fun definirStock(id: Long, valeur: Double)
}
```

`app/src/main/kotlin/fr/pillulier/app/data/db/OrdonnanceDao.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrdonnanceDao {

    @Transaction
    @Query("SELECT * FROM ordonnance")
    fun observerToutes(): Flow<List<OrdonnanceAvecDosesEntity>>

    @Transaction
    @Query("SELECT * FROM ordonnance")
    suspend fun toutes(): List<OrdonnanceAvecDosesEntity>

    @Transaction
    @Query("SELECT * FROM ordonnance WHERE medicamentId = :medicamentId")
    suspend fun pourMedicament(medicamentId: Long): OrdonnanceAvecDosesEntity?

    @Insert
    suspend fun insererOrdonnance(ordonnance: OrdonnanceEntity): Long

    @Update
    suspend fun mettreAJourOrdonnance(ordonnance: OrdonnanceEntity)

    @Insert
    suspend fun insererDoses(doses: List<DosePrescriteEntity>)

    @Query("DELETE FROM dose_prescrite WHERE ordonnanceId = :ordonnanceId")
    suspend fun supprimerDoses(ordonnanceId: Long)

    @Query("SELECT COUNT(*) FROM dose_prescrite WHERE ordonnanceId = :ordonnanceId")
    suspend fun comptePourOrdonnance(ordonnanceId: Long): Int
}
```

`app/src/main/kotlin/fr/pillulier/app/data/db/EvenementPriseDao.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface EvenementPriseDao {

    /** Renvoie -1 si la prise planifiée était déjà enregistrée : l'insertion est idempotente. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserer(evenement: EvenementPriseEntity): Long

    @Query("SELECT * FROM evenement_prise WHERE date BETWEEN :debut AND :fin ORDER BY date, enregistreLe")
    fun observerEntre(debut: LocalDate, fin: LocalDate): Flow<List<EvenementPriseEntity>>

    @Query("SELECT * FROM evenement_prise WHERE date BETWEEN :debut AND :fin ORDER BY date, enregistreLe")
    suspend fun entre(debut: LocalDate, fin: LocalDate): List<EvenementPriseEntity>

    @Query("SELECT * FROM evenement_prise WHERE date = :date")
    suspend fun duJour(date: LocalDate): List<EvenementPriseEntity>
}
```

`app/src/main/kotlin/fr/pillulier/app/data/db/MomentConfigDao.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentConfigDao {

    @Query("SELECT * FROM moment_config")
    fun observer(): Flow<List<MomentConfigEntity>>

    @Query("SELECT * FROM moment_config")
    suspend fun tous(): List<MomentConfigEntity>

    @Upsert
    suspend fun enregistrer(config: MomentConfigEntity)
}
```

- [ ] **Step 7: Écrire la base et l'amorçage des moments**

`app/src/main/kotlin/fr/pillulier/app/data/db/PillulierDatabase.kt` :

```kotlin
package fr.pillulier.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.pillulier.domain.Moment

@Database(
    entities = [
        MedicamentEntity::class,
        OrdonnanceEntity::class,
        DosePrescriteEntity::class,
        MomentConfigEntity::class,
        EvenementPriseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertisseurs::class)
abstract class PillulierDatabase : RoomDatabase() {
    abstract fun medicaments(): MedicamentDao
    abstract fun ordonnances(): OrdonnanceDao
    abstract fun evenements(): EvenementPriseDao
    abstract fun moments(): MomentConfigDao
}

/** Amorce les quatre moments à leurs heures par défaut, à la création de la base. */
val momentsParDefaut = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        listOf(
            Moment.MATIN to 8 * 60,
            Moment.MIDI to 12 * 60,
            Moment.SOIR to 19 * 60,
            Moment.COUCHER to 22 * 60,
        ).forEach { (moment, minutes) ->
            db.execSQL("INSERT INTO moment_config (moment, heure) VALUES ('${moment.name}', $minutes)")
        }
    }
}
```

- [ ] **Step 8: Lancer les tests**

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

Attendu : les neuf tests de `PillulierDatabaseTest` au vert, et le schéma généré dans `app/schemas/`

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(app): persistance Room du pillulier"
```

---

### Task 9 : Mappeurs, dépôts, préférences et injection

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/data/Mappeurs.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/DepotMedicaments.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/DepotOrdonnances.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/DepotEvenements.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/DepotMoments.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/data/DepotPreferences.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/di/ModuleDonnees.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/PillulierApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/fr/pillulier/app/MainActivity.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/data/MappeursTest.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/data/DepotOrdonnancesTest.kt`

**Interfaces:**
- Consumes: la base et les entités de la tâche 8, les types de la tâche 2
- Produces:
  - `MedicamentEntity.versDomaine(): Medicament`, `Medicament.versEntite(): MedicamentEntity`
  - `OrdonnanceAvecDosesEntity.versDomaine(): OrdonnanceAvecDoses`, `Rythme.versColonnes(): Triple<String, String?, Int?>`, `rythmeDepuisColonnes(type: String, jours: String?, n: Int?): Rythme`
  - `EvenementPriseEntity.versDomaine(): EvenementPrise`
  - `DepotMedicaments` : `observerTous(): Flow<List<Medicament>>`, `tous(): List<Medicament>`, `parId(id: Long): Medicament?`, `enregistrer(medicament: Medicament): Long`, `supprimer(id: Long)`
  - `DepotOrdonnances` : `observerToutes(): Flow<List<OrdonnanceAvecDoses>>`, `toutes(): List<OrdonnanceAvecDoses>`, `pourMedicament(medicamentId: Long): OrdonnanceAvecDoses?`, `enregistrer(medicamentId: Long, ordonnance: Ordonnance, doses: List<DosePrescrite>)`
  - `DepotEvenements` : `observerEntre(debut, fin): Flow<List<EvenementPrise>>`, `entre(debut, fin): List<EvenementPrise>`
  - `DepotMoments` : `observer(): Flow<Map<Moment, LocalTime>>`, `heures(): Map<Moment, LocalTime>`, `definir(moment: Moment, heure: LocalTime)`
  - `DepotPreferences` : `delaiPlusTard: Flow<Int>`, `intervalleRelance: Flow<Int>`, `seuilAlerteJoursDefaut: Flow<Int>`, plus les trois `definir...`, et `instantane(): Preferences`

- [ ] **Step 1: Écrire les tests qui échouent**

`app/src/test/kotlin/fr/pillulier/app/data/MappeursTest.kt` :

```kotlin
package fr.pillulier.app.data

import fr.pillulier.app.data.db.DosePrescriteEntity
import fr.pillulier.app.data.db.OrdonnanceAvecDosesEntity
import fr.pillulier.app.data.db.OrdonnanceEntity
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals

class MappeursTest {

    @Test
    fun `tous les jours fait l aller retour`() {
        val (type, jours, n) = Rythme.TousLesJours.versColonnes()
        assertEquals(Rythme.TousLesJours, rythmeDepuisColonnes(type, jours, n))
    }

    @Test
    fun `les jours de semaine font l aller retour`() {
        val rythme = Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val (type, jours, n) = rythme.versColonnes()
        assertEquals(rythme, rythmeDepuisColonnes(type, jours, n))
    }

    @Test
    fun `un jour sur N fait l aller retour`() {
        val rythme = Rythme.UnJourSurN(3)
        val (type, jours, n) = rythme.versColonnes()
        assertEquals(rythme, rythmeDepuisColonnes(type, jours, n))
    }

    @Test
    fun `une ordonnance persistee revient en objet du domaine`() {
        val entite = OrdonnanceAvecDosesEntity(
            ordonnance = OrdonnanceEntity(
                id = 4,
                medicamentId = 10,
                type = TypeOrdonnance.PLANIFIEE,
                rythmeType = "UN_JOUR_SUR_N",
                rythmeJours = null,
                rythmeN = 2,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = LocalDate.of(2026, 1, 10),
            ),
            doses = listOf(DosePrescriteEntity(1, 4, Moment.MATIN, 1.5)),
        )

        val domaine = entite.versDomaine()

        assertEquals(Rythme.UnJourSurN(2), domaine.ordonnance.rythme)
        assertEquals(LocalDate.of(2026, 1, 10), domaine.ordonnance.dateFin)
        assertEquals(1.5, domaine.doses.single().dose)
        assertEquals(Moment.MATIN, domaine.doses.single().moment)
    }
}
```

`app/src/test/kotlin/fr/pillulier/app/data/DepotOrdonnancesTest.kt` :

```kotlin
package fr.pillulier.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DepotOrdonnancesTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var moments: DepotMoments

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        moments = DepotMoments(base.moments())
    }

    @After
    fun fermer() = base.close()

    private fun medicament(nom: String = "Kardegic") = Medicament(
        id = 0,
        nom = nom,
        dosage = "75 mg",
        forme = Forme.SACHET,
        unitesParBoite = 30,
        stockUnites = 30.0,
        seuilAlerteJours = null,
        seuilAlerteUnites = null,
        critique = false,
    )

    @Test
    fun `enregistrer une ordonnance puis la relire conserve le rythme et les doses`() = runTest {
        val medicamentId = medicaments.enregistrer(medicament())

        ordonnances.enregistrer(
            medicamentId = medicamentId,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = medicamentId,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.JoursDeSemaine(setOf(java.time.DayOfWeek.MONDAY)),
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        val relue = ordonnances.pourMedicament(medicamentId)!!

        assertEquals(Rythme.JoursDeSemaine(setOf(java.time.DayOfWeek.MONDAY)), relue.ordonnance.rythme)
        assertEquals(1, relue.doses.size)
    }

    @Test
    fun `reenregistrer une ordonnance remplace ses doses sans les cumuler`() = runTest {
        val medicamentId = medicaments.enregistrer(medicament())
        val ordonnance = Ordonnance(
            id = 0,
            medicamentId = medicamentId,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
        )

        ordonnances.enregistrer(medicamentId, ordonnance, listOf(DosePrescrite(Moment.MATIN, 1.0)))
        ordonnances.enregistrer(medicamentId, ordonnance, listOf(DosePrescrite(Moment.SOIR, 2.0)))

        val relue = ordonnances.pourMedicament(medicamentId)!!

        assertEquals(listOf(Moment.SOIR), relue.doses.map { it.moment })
        assertEquals(2.0, relue.doses.single().dose)
    }

    @Test
    fun `les heures des moments sont modifiables`() = runTest {
        moments.definir(Moment.MATIN, LocalTime.of(7, 30))

        assertEquals(LocalTime.of(7, 30), moments.heures().getValue(Moment.MATIN))
        assertEquals(LocalTime.of(12, 0), moments.heures().getValue(Moment.MIDI))
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.data.*'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: versColonnes`

- [ ] **Step 3: Écrire les mappeurs**

`app/src/main/kotlin/fr/pillulier/app/data/Mappeurs.kt` :

```kotlin
package fr.pillulier.app.data

import fr.pillulier.app.data.db.DosePrescriteEntity
import fr.pillulier.app.data.db.EvenementPriseEntity
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.OrdonnanceAvecDosesEntity
import fr.pillulier.app.data.db.OrdonnanceEntity
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.EvenementPrise
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.OrdonnanceAvecDoses
import fr.pillulier.domain.Rythme
import java.time.DayOfWeek

private const val TOUS_LES_JOURS = "TOUS_LES_JOURS"
private const val JOURS_DE_SEMAINE = "JOURS_DE_SEMAINE"
private const val UN_JOUR_SUR_N = "UN_JOUR_SUR_N"

/** Aplatit un rythme en ses trois colonnes : discriminant, jours, N. */
fun Rythme.versColonnes(): Triple<String, String?, Int?> = when (this) {
    Rythme.TousLesJours -> Triple(TOUS_LES_JOURS, null, null)
    is Rythme.JoursDeSemaine -> Triple(JOURS_DE_SEMAINE, jours.joinToString(",") { it.name }, null)
    is Rythme.UnJourSurN -> Triple(UN_JOUR_SUR_N, null, n)
}

fun rythmeDepuisColonnes(type: String, jours: String?, n: Int?): Rythme = when (type) {
    TOUS_LES_JOURS -> Rythme.TousLesJours
    JOURS_DE_SEMAINE -> Rythme.JoursDeSemaine(
        requireNotNull(jours) { "rythme $JOURS_DE_SEMAINE sans jours" }
            .split(",")
            .filter { it.isNotBlank() }
            .map(DayOfWeek::valueOf)
            .toSet(),
    )
    UN_JOUR_SUR_N -> Rythme.UnJourSurN(requireNotNull(n) { "rythme $UN_JOUR_SUR_N sans N" })
    else -> error("rythme inconnu : $type")
}

fun MedicamentEntity.versDomaine() = Medicament(
    id = id,
    nom = nom,
    dosage = dosage,
    forme = forme,
    unitesParBoite = unitesParBoite,
    stockUnites = stockUnites,
    seuilAlerteJours = seuilAlerteJours,
    seuilAlerteUnites = seuilAlerteUnites,
    critique = critique,
)

fun Medicament.versEntite() = MedicamentEntity(
    id = id,
    nom = nom,
    dosage = dosage,
    forme = forme,
    unitesParBoite = unitesParBoite,
    stockUnites = stockUnites,
    seuilAlerteJours = seuilAlerteJours,
    seuilAlerteUnites = seuilAlerteUnites,
    critique = critique,
)

fun OrdonnanceAvecDosesEntity.versDomaine() = OrdonnanceAvecDoses(
    ordonnance = Ordonnance(
        id = ordonnance.id,
        medicamentId = ordonnance.medicamentId,
        type = ordonnance.type,
        rythme = rythmeDepuisColonnes(ordonnance.rythmeType, ordonnance.rythmeJours, ordonnance.rythmeN),
        dateDebut = ordonnance.dateDebut,
        dateFin = ordonnance.dateFin,
    ),
    doses = doses.map { DosePrescrite(it.moment, it.dose) },
)

fun Ordonnance.versEntite(): OrdonnanceEntity {
    val (type, jours, n) = rythme.versColonnes()
    return OrdonnanceEntity(
        id = id,
        medicamentId = medicamentId,
        type = this.type,
        rythmeType = type,
        rythmeJours = jours,
        rythmeN = n,
        dateDebut = dateDebut,
        dateFin = dateFin,
    )
}

fun DosePrescrite.versEntite(ordonnanceId: Long) = DosePrescriteEntity(
    ordonnanceId = ordonnanceId,
    moment = moment,
    dose = dose,
)

fun EvenementPriseEntity.versDomaine() = EvenementPrise(
    id = id,
    medicamentId = medicamentId,
    date = date,
    moment = moment,
    doseReelle = doseReelle,
    enregistreLe = enregistreLe,
)
```

Attention au conflit de noms dans `Ordonnance.versEntite` : la variable locale `type` du `Triple` masquerait la propriété `type` de l'ordonnance — c'est pourquoi le champ est écrit `type = this.type`.

- [ ] **Step 4: Écrire les dépôts**

`app/src/main/kotlin/fr/pillulier/app/data/DepotMedicaments.kt` :

```kotlin
package fr.pillulier.app.data

import fr.pillulier.app.data.db.MedicamentDao
import fr.pillulier.domain.Medicament
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotMedicaments @Inject constructor(private val dao: MedicamentDao) {

    fun observerTous(): Flow<List<Medicament>> =
        dao.observerTous().map { entites -> entites.map { it.versDomaine() } }

    suspend fun tous(): List<Medicament> = dao.tous().map { it.versDomaine() }

    suspend fun parId(id: Long): Medicament? = dao.parId(id)?.versDomaine()

    /** Insère si l'identifiant est nul, met à jour sinon. Renvoie l'identifiant. */
    suspend fun enregistrer(medicament: Medicament): Long =
        if (medicament.id == 0L) {
            dao.inserer(medicament.versEntite())
        } else {
            dao.mettreAJour(medicament.versEntite())
            medicament.id
        }

    suspend fun supprimer(id: Long) = dao.supprimer(id)
}
```

`app/src/main/kotlin/fr/pillulier/app/data/DepotOrdonnances.kt` :

```kotlin
package fr.pillulier.app.data

import fr.pillulier.app.data.db.OrdonnanceDao
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.OrdonnanceAvecDoses
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotOrdonnances @Inject constructor(private val dao: OrdonnanceDao) {

    fun observerToutes(): Flow<List<OrdonnanceAvecDoses>> =
        dao.observerToutes().map { entites -> entites.map { it.versDomaine() } }

    suspend fun toutes(): List<OrdonnanceAvecDoses> = dao.toutes().map { it.versDomaine() }

    suspend fun pourMedicament(medicamentId: Long): OrdonnanceAvecDoses? =
        dao.pourMedicament(medicamentId)?.versDomaine()

    /**
     * Écrit l'ordonnance du médicament et remplace ses doses. Un médicament n'a
     * qu'une ordonnance : réenregistrer met à jour celle qui existe.
     */
    suspend fun enregistrer(
        medicamentId: Long,
        ordonnance: Ordonnance,
        doses: List<DosePrescrite>,
    ) {
        val existante = dao.pourMedicament(medicamentId)
        val id = if (existante == null) {
            dao.insererOrdonnance(ordonnance.copy(id = 0, medicamentId = medicamentId).versEntite())
        } else {
            dao.mettreAJourOrdonnance(
                ordonnance.copy(id = existante.ordonnance.id, medicamentId = medicamentId).versEntite(),
            )
            existante.ordonnance.id
        }

        dao.supprimerDoses(id)
        dao.insererDoses(doses.map { it.versEntite(id) })
    }
}
```

`app/src/main/kotlin/fr/pillulier/app/data/DepotEvenements.kt` :

```kotlin
package fr.pillulier.app.data

import fr.pillulier.app.data.db.EvenementPriseDao
import fr.pillulier.domain.EvenementPrise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotEvenements @Inject constructor(private val dao: EvenementPriseDao) {

    fun observerEntre(debut: LocalDate, fin: LocalDate): Flow<List<EvenementPrise>> =
        dao.observerEntre(debut, fin).map { entites -> entites.map { it.versDomaine() } }

    suspend fun entre(debut: LocalDate, fin: LocalDate): List<EvenementPrise> =
        dao.entre(debut, fin).map { it.versDomaine() }
}
```

`app/src/main/kotlin/fr/pillulier/app/data/DepotMoments.kt` :

```kotlin
package fr.pillulier.app.data

import fr.pillulier.app.data.db.MomentConfigDao
import fr.pillulier.app.data.db.MomentConfigEntity
import fr.pillulier.domain.Moment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotMoments @Inject constructor(private val dao: MomentConfigDao) {

    fun observer(): Flow<Map<Moment, LocalTime>> =
        dao.observer().map { configs -> configs.associate { it.moment to it.heure } }

    suspend fun heures(): Map<Moment, LocalTime> = dao.tous().associate { it.moment to it.heure }

    suspend fun definir(moment: Moment, heure: LocalTime) =
        dao.enregistrer(MomentConfigEntity(moment, heure))
}
```

`app/src/main/kotlin/fr/pillulier/app/data/DepotPreferences.kt` :

```kotlin
package fr.pillulier.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

private val CLE_DELAI_PLUS_TARD = intPreferencesKey("delai_plus_tard_minutes")
private val CLE_INTERVALLE_RELANCE = intPreferencesKey("intervalle_relance_minutes")
private val CLE_SEUIL_ALERTE_JOURS = intPreferencesKey("seuil_alerte_jours_defaut")

const val DELAI_PLUS_TARD_DEFAUT = 15
const val INTERVALLE_RELANCE_DEFAUT = 15
const val SEUIL_ALERTE_JOURS_DEFAUT = 7

data class PreferencesPillulier(
    val delaiPlusTardMinutes: Int = DELAI_PLUS_TARD_DEFAUT,
    val intervalleRelanceMinutes: Int = INTERVALLE_RELANCE_DEFAUT,
    val seuilAlerteJoursDefaut: Int = SEUIL_ALERTE_JOURS_DEFAUT,
)

@Singleton
class DepotPreferences @Inject constructor(private val contexte: Context) {

    val preferences: Flow<PreferencesPillulier> = contexte.dataStore.data.map { stockees ->
        PreferencesPillulier(
            delaiPlusTardMinutes = stockees[CLE_DELAI_PLUS_TARD] ?: DELAI_PLUS_TARD_DEFAUT,
            intervalleRelanceMinutes = stockees[CLE_INTERVALLE_RELANCE] ?: INTERVALLE_RELANCE_DEFAUT,
            seuilAlerteJoursDefaut = stockees[CLE_SEUIL_ALERTE_JOURS] ?: SEUIL_ALERTE_JOURS_DEFAUT,
        )
    }

    suspend fun instantane(): PreferencesPillulier = preferences.first()

    suspend fun definirDelaiPlusTard(minutes: Int) =
        ecrire(CLE_DELAI_PLUS_TARD, minutes)

    suspend fun definirIntervalleRelance(minutes: Int) =
        ecrire(CLE_INTERVALLE_RELANCE, minutes)

    suspend fun definirSeuilAlerteJours(jours: Int) =
        ecrire(CLE_SEUIL_ALERTE_JOURS, jours)

    private suspend fun ecrire(cle: Preferences.Key<Int>, valeur: Int) {
        require(valeur > 0) { "une duree ou un seuil doit etre strictement positif, recu $valeur" }
        contexte.dataStore.edit { it[cle] = valeur }
    }
}
```

- [ ] **Step 5: Écrire le module Hilt et la classe Application**

`app/src/main/kotlin/fr/pillulier/app/di/ModuleDonnees.kt` :

```kotlin
package fr.pillulier.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.pillulier.app.data.db.EvenementPriseDao
import fr.pillulier.app.data.db.MedicamentDao
import fr.pillulier.app.data.db.MomentConfigDao
import fr.pillulier.app.data.db.OrdonnanceDao
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModuleDonnees {

    @Provides
    @Singleton
    fun base(@ApplicationContext contexte: Context): PillulierDatabase =
        Room.databaseBuilder(contexte, PillulierDatabase::class.java, "pillulier.db")
            .addCallback(momentsParDefaut)
            .build()

    @Provides fun medicamentDao(base: PillulierDatabase): MedicamentDao = base.medicaments()

    @Provides fun ordonnanceDao(base: PillulierDatabase): OrdonnanceDao = base.ordonnances()

    @Provides fun evenementDao(base: PillulierDatabase): EvenementPriseDao = base.evenements()

    @Provides fun momentDao(base: PillulierDatabase): MomentConfigDao = base.moments()

    @Provides
    @Singleton
    fun contexte(@ApplicationContext contexte: Context): Context = contexte
}
```

`app/src/main/kotlin/fr/pillulier/app/PillulierApplication.kt` :

```kotlin
package fr.pillulier.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PillulierApplication : Application()
```

Dans `app/src/main/AndroidManifest.xml`, ajouter l'attribut `android:name=".PillulierApplication"` à la balise `<application>`.

Dans `app/src/main/kotlin/fr/pillulier/app/MainActivity.kt`, annoter la classe avec `@AndroidEntryPoint` et ajouter l'import `dagger.hilt.android.AndroidEntryPoint`.

- [ ] **Step 6: Lancer les tests**

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

Attendu : les tests de mappeurs et de dépôts au vert.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(app): mappeurs, depots, preferences et injection Hilt"
```

---

### Task 10 : Cas d'usage de prise et de stock

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/temps/Horloge.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerPrise.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/AjouterBoite.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/CorrigerStock.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/di/ModuleHorloge.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerPriseTest.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/StockTest.kt`

**Interfaces:**
- Consumes: la base et les DAO de la tâche 8, les dépôts de la tâche 9
- Produces:
  - `interface Horloge { fun maintenant(): LocalDateTime; fun aujourdhui(): LocalDate; fun instant(): Instant }` et `HorlogeSysteme`
  - `EnregistrerPrise.invoke(medicamentId: Long, date: LocalDate, moment: Moment?, dose: Double): Boolean` — `false` si la prise planifiée était déjà enregistrée
  - `AjouterBoite.invoke(medicamentId: Long)`
  - `CorrigerStock.invoke(medicamentId: Long, unites: Double)`

- [ ] **Step 1: Écrire les tests qui échouent**

`app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerPriseTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HorlogeFigee(private var valeur: LocalDateTime) : Horloge {
    override fun maintenant(): LocalDateTime = valeur
    override fun aujourdhui(): LocalDate = valeur.toLocalDate()
    override fun instant(): Instant = valeur.toInstant(java.time.ZoneOffset.UTC)
    fun avancerA(nouvelle: LocalDateTime) { valeur = nouvelle }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnregistrerPriseTest {

    private lateinit var base: PillulierDatabase
    private lateinit var enregistrer: EnregistrerPrise
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 8, 5))

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
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
    fun `enregistrer une prise decremente le stock de la dose`() = runTest {
        val id = medicament(stock = 10.0)

        val enregistree = enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 0.5)

        assertTrue(enregistree)
        assertEquals(9.5, base.medicaments().parId(id)!!.stockUnites)
        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
    }

    @Test
    fun `enregistrer deux fois la meme prise ne decremente qu une fois`() = runTest {
        val id = medicament(stock = 10.0)

        assertTrue(enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0))
        assertFalse(enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0))

        assertEquals(9.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `une prise a la demande peut etre enregistree plusieurs fois par jour`() = runTest {
        val id = medicament(stock = 10.0)

        assertTrue(enregistrer(id, LocalDate.of(2026, 1, 5), moment = null, dose = 1.0))
        assertTrue(enregistrer(id, LocalDate.of(2026, 1, 5), moment = null, dose = 1.0))

        assertEquals(8.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `l instant d enregistrement vient de l horloge`() = runTest {
        val id = medicament()

        enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0)

        assertEquals(
            horloge.instant(),
            base.evenements().duJour(LocalDate.of(2026, 1, 5)).single().enregistreLe,
        )
    }

    @Test
    fun `le stock peut devenir negatif si on prend sans avoir mis a jour la boite`() = runTest {
        val id = medicament(stock = 0.0)

        assertTrue(enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0))

        assertEquals(-1.0, base.medicaments().parId(id)!!.stockUnites)
    }
}
```

Le dernier test fixe une décision : une prise réelle est toujours enregistrée, même si le stock saisi ne suit plus. Le journal du réel ne ment pas pour préserver un compteur ; l'écran Stock affichera la valeur négative comme un signal de recomptage.

`app/src/test/kotlin/fr/pillulier/app/usecase/StockTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.Forme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StockTest {

    private lateinit var base: PillulierDatabase
    private lateinit var ajouterBoite: AjouterBoite
    private lateinit var corrigerStock: CorrigerStock

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        ajouterBoite = AjouterBoite(base.medicaments())
        corrigerStock = CorrigerStock(base.medicaments())
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicament(stock: Double, unitesParBoite: Int): Long =
        base.medicaments().inserer(
            MedicamentEntity(
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = unitesParBoite,
                stockUnites = stock,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )

    @Test
    fun `ajouter une boite ajoute ses unites au stock`() = runTest {
        val id = medicament(stock = 4.0, unitesParBoite = 16)

        ajouterBoite(id)

        assertEquals(20.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `corriger le stock ecrase la valeur`() = runTest {
        val id = medicament(stock = 4.0, unitesParBoite = 16)

        corrigerStock(id, 12.5)

        assertEquals(12.5, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `corriger avec une valeur negative est refuse`() = runTest {
        val id = medicament(stock = 4.0, unitesParBoite = 16)

        assertFailsWith<IllegalArgumentException> { corrigerStock(id, -1.0) }
    }

    @Test
    fun `ajouter une boite a un medicament inconnu est refuse`() = runTest {
        assertFailsWith<IllegalStateException> { ajouterBoite(404) }
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.usecase.*'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: EnregistrerPrise`

- [ ] **Step 3: Écrire l'horloge**

`app/src/main/kotlin/fr/pillulier/app/temps/Horloge.kt` :

```kotlin
package fr.pillulier.app.temps

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Injectée partout où l'heure courante intervient, pour rendre le code testable. */
interface Horloge {
    fun maintenant(): LocalDateTime
    fun aujourdhui(): LocalDate
    fun instant(): Instant
}

class HorlogeSysteme @Inject constructor() : Horloge {
    override fun maintenant(): LocalDateTime = LocalDateTime.now()
    override fun aujourdhui(): LocalDate = LocalDate.now()
    override fun instant(): Instant = Instant.now()
}
```

`app/src/main/kotlin/fr/pillulier/app/di/ModuleHorloge.kt` :

```kotlin
package fr.pillulier.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.temps.HorlogeSysteme
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuleHorloge {
    @Binds
    @Singleton
    abstract fun horloge(systeme: HorlogeSysteme): Horloge
}
```

- [ ] **Step 4: Écrire les trois cas d'usage**

`app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerPrise.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.withTransaction
import fr.pillulier.app.data.db.EvenementPriseDao
import fr.pillulier.app.data.db.EvenementPriseEntity
import fr.pillulier.app.data.db.MedicamentDao
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.Moment
import java.time.LocalDate
import javax.inject.Inject

/**
 * Écrit le journal du réel et décrémente le stock dans la même transaction.
 * Renvoie `false` si la prise planifiée était déjà enregistrée : l'appel est
 * idempotent, ce qui protège du double appui sur l'action d'une notification.
 */
class EnregistrerPrise @Inject constructor(
    private val base: PillulierDatabase,
    private val evenements: EvenementPriseDao,
    private val medicaments: MedicamentDao,
    private val horloge: Horloge,
) {
    suspend operator fun invoke(
        medicamentId: Long,
        date: LocalDate,
        moment: Moment?,
        dose: Double,
    ): Boolean = base.withTransaction {
        val insere = evenements.inserer(
            EvenementPriseEntity(
                medicamentId = medicamentId,
                date = date,
                moment = moment,
                doseReelle = dose,
                enregistreLe = horloge.instant(),
            ),
        )

        if (insere == -1L) {
            false
        } else {
            medicaments.ajusterStock(medicamentId, -dose)
            true
        }
    }
}
```

`app/src/main/kotlin/fr/pillulier/app/usecase/AjouterBoite.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.db.MedicamentDao
import javax.inject.Inject

class AjouterBoite @Inject constructor(private val medicaments: MedicamentDao) {

    suspend operator fun invoke(medicamentId: Long) {
        val medicament = checkNotNull(medicaments.parId(medicamentId)) {
            "medicament $medicamentId inconnu"
        }
        medicaments.ajusterStock(medicamentId, medicament.unitesParBoite.toDouble())
    }
}
```

`app/src/main/kotlin/fr/pillulier/app/usecase/CorrigerStock.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.db.MedicamentDao
import javax.inject.Inject

class CorrigerStock @Inject constructor(private val medicaments: MedicamentDao) {

    suspend operator fun invoke(medicamentId: Long, unites: Double) {
        require(unites >= 0.0) { "un stock recompte ne peut pas etre negatif, recu $unites" }
        medicaments.definirStock(medicamentId, unites)
    }
}
```

- [ ] **Step 5: Lancer les tests**

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

Attendu : les neuf tests de cas d'usage au vert.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(app): cas d usage de prise et de gestion du stock"
```

---

### Task 11 : Programmateur d'alarmes et réarmement de la fenêtre

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/rappels/ProgrammateurAlarmes.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/rappels/ProgrammateurAlarmesAndroid.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/ReArmerRappels.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/di/ModuleRappels.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/ReArmerRappelsTest.kt`

**Interfaces:**
- Consumes: `CleRappel` et `codeRequete` de la tâche 7, `prisesAttendues` et `statut` des tâches 4 et 5, les dépôts de la tâche 9, `Horloge` de la tâche 10
- Produces:
  - `interface ProgrammateurAlarmes { fun programmer(cle: CleRappel, quand: LocalDateTime, critique: Boolean); fun annuler(cle: CleRappel) }`
  - `ReArmerRappels.invoke()` et la constante `ReArmerRappels.JOURS_FENETRE = 3`

- [ ] **Step 1: Écrire le test qui échoue**

`app/src/test/kotlin/fr/pillulier/app/usecase/ReArmerRappelsTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgrammateurEspion : ProgrammateurAlarmes {
    data class Programmation(val cle: CleRappel, val quand: LocalDateTime, val critique: Boolean)

    val programmees = mutableListOf<Programmation>()
    val annulees = mutableListOf<CleRappel>()

    override fun programmer(cle: CleRappel, quand: LocalDateTime, critique: Boolean) {
        programmees += Programmation(cle, quand, critique)
    }

    override fun annuler(cle: CleRappel) {
        annulees += cle
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReArmerRappelsTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var enregistrer: EnregistrerPrise
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))
    private lateinit var reArmer: ReArmerRappels

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = programmateur,
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentPlanifie(
        nom: String,
        critique: Boolean = false,
        doses: List<DosePrescrite> = listOf(DosePrescrite(Moment.MATIN, 1.0)),
    ): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "1 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = critique,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = doses,
        )
        return id
    }

    @Test
    fun `la fenetre couvre trois jours`() = runTest {
        medicamentPlanifie("Levothyrox")

        reArmer()

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 5, 8, 0),
                LocalDateTime.of(2026, 1, 6, 8, 0),
                LocalDateTime.of(2026, 1, 7, 8, 0),
            ),
            programmateur.programmees.map { it.quand },
        )
    }

    @Test
    fun `le drapeau critique du medicament est transmis a l alarme`() = runTest {
        medicamentPlanifie("Insuline", critique = true)

        reArmer()

        assertTrue(programmateur.programmees.all { it.critique })
    }

    @Test
    fun `une prise deja enregistree n est pas reprogrammee`() = runTest {
        val id = medicamentPlanifie("Levothyrox")
        enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0)

        reArmer()

        assertEquals(
            listOf(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7)),
            programmateur.programmees.map { it.cle.date },
        )
    }

    @Test
    fun `une prise du jour deja passee est reprogrammee juste apres maintenant`() = runTest {
        horloge.avancerA(LocalDateTime.of(2026, 1, 5, 9, 30))
        medicamentPlanifie("Levothyrox")

        reArmer()

        assertEquals(
            LocalDateTime.of(2026, 1, 5, 9, 31),
            programmateur.programmees.first { it.cle.date == LocalDate.of(2026, 1, 5) }.quand,
        )
    }

    @Test
    fun `la veille et la fenetre sont annulees avant d etre reprogrammees`() = runTest {
        medicamentPlanifie("Levothyrox")

        reArmer()

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 6),
                LocalDate.of(2026, 1, 7),
            ),
            programmateur.annulees.map { it.date },
        )
    }

    @Test
    fun `un medicament a la demande ne produit aucune alarme`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 16.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.A_LA_DEMANDE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = emptyList(),
        )

        reArmer()

        assertTrue(programmateur.programmees.isEmpty())
    }

    @Test
    fun `deux doses le meme jour donnent deux alarmes distinctes`() = runTest {
        medicamentPlanifie(
            "Metformine",
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0), DosePrescrite(Moment.SOIR, 1.0)),
        )

        reArmer()

        val duJour = programmateur.programmees.filter { it.cle.date == LocalDate.of(2026, 1, 5) }
        assertEquals(setOf(Moment.MATIN, Moment.SOIR), duJour.map { it.cle.moment }.toSet())
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.usecase.ReArmerRappelsTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: ProgrammateurAlarmes`

- [ ] **Step 3: Écrire l'interface du programmateur**

`app/src/main/kotlin/fr/pillulier/app/rappels/ProgrammateurAlarmes.kt` :

```kotlin
package fr.pillulier.app.rappels

import fr.pillulier.domain.CleRappel
import java.time.LocalDateTime

/**
 * Frontière derrière laquelle vit `AlarmManager`. Le réarmement se teste ainsi
 * sans Android, et rien n'est stocké : la clé suffit à annuler ou reprogrammer.
 */
interface ProgrammateurAlarmes {
    fun programmer(cle: CleRappel, quand: LocalDateTime, critique: Boolean)
    fun annuler(cle: CleRappel)
}
```

- [ ] **Step 4: Écrire le réarmement**

`app/src/main/kotlin/fr/pillulier/app/usecase/ReArmerRappels.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.prisesAttendues
import fr.pillulier.domain.statut
import javax.inject.Inject

/**
 * Reconstruit la fenêtre glissante d'alarmes depuis le moteur. Appelé au
 * démarrage du téléphone, à la clôture quotidienne, et après toute modification
 * d'ordonnance, de médicament ou d'heure de moment.
 */
class ReArmerRappels @Inject constructor(
    private val ordonnances: DepotOrdonnances,
    private val medicaments: DepotMedicaments,
    private val moments: DepotMoments,
    private val evenements: DepotEvenements,
    private val programmateur: ProgrammateurAlarmes,
    private val horloge: Horloge,
) {
    suspend operator fun invoke() {
        val maintenant = horloge.maintenant()
        val aujourdhui = maintenant.toLocalDate()
        val heures = moments.heures()
        val toutes = ordonnances.toutes()
        val critiques = medicaments.tous().filter { it.critique }.map { it.id }.toSet()

        // La veille est annulée en même temps que la fenêtre : elle peut porter
        // une relance qui n'a pas à survivre à la journée.
        val jourAnnules = (-1L until JOURS_FENETRE).map { aujourdhui.plusDays(it) }
        jourAnnules.forEach { jour ->
            prisesAttendues(jour, toutes, heures).forEach { prise ->
                programmateur.annuler(CleRappel(prise.medicamentId, jour, prise.moment))
            }
        }

        val dernierJour = aujourdhui.plusDays(JOURS_FENETRE - 1)
        val dejaPrises = evenements.entre(aujourdhui, dernierJour)

        (0L until JOURS_FENETRE).map { aujourdhui.plusDays(it) }.forEach { jour ->
            prisesAttendues(jour, toutes, heures)
                .filter { statut(it, dejaPrises, maintenant) != StatutPrise.PRISE }
                .forEach { prise ->
                    programmateur.programmer(
                        cle = CleRappel(prise.medicamentId, jour, prise.moment),
                        // Une prise du jour déjà due mais sans réponse repart
                        // tout de suite plutôt que d'être perdue.
                        quand = if (prise.heure.isAfter(maintenant)) prise.heure else maintenant.plusMinutes(1),
                        critique = prise.medicamentId in critiques,
                    )
                }
        }
    }

    companion object {
        const val JOURS_FENETRE = 3L
    }
}
```

- [ ] **Step 5: Écrire l'implémentation Android du programmateur**

`app/src/main/kotlin/fr/pillulier/app/rappels/ProgrammateurAlarmesAndroid.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Moment
import fr.pillulier.domain.codeRequete
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

const val EXTRA_MEDICAMENT_ID = "medicamentId"
const val EXTRA_DATE = "dateEpochDay"
const val EXTRA_MOMENT = "moment"
const val EXTRA_CRITIQUE = "critique"

class ProgrammateurAlarmesAndroid @Inject constructor(
    private val contexte: Context,
) : ProgrammateurAlarmes {

    private val gestionnaire = contexte.getSystemService(AlarmManager::class.java)

    override fun programmer(cle: CleRappel, quand: LocalDateTime, critique: Boolean) {
        val declenchement = quand.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        gestionnaire.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            declenchement,
            intentEnAttente(cle, critique, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    override fun annuler(cle: CleRappel) {
        val existant = intentEnAttente(cle, critique = false, drapeaux = PendingIntent.FLAG_NO_CREATE)
        existant?.let {
            gestionnaire.cancel(it)
            it.cancel()
        }
    }

    private fun intentEnAttente(cle: CleRappel, critique: Boolean, drapeaux: Int): PendingIntent? {
        val intention = Intent(contexte, RecepteurRappel::class.java).apply {
            putExtra(EXTRA_MEDICAMENT_ID, cle.medicamentId)
            putExtra(EXTRA_DATE, cle.date.toEpochDay())
            putExtra(EXTRA_MOMENT, cle.moment.name)
            putExtra(EXTRA_CRITIQUE, critique)
        }

        return PendingIntent.getBroadcast(
            contexte,
            cle.codeRequete(),
            intention,
            drapeaux or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Reconstruit une clé depuis les extras d'une intention. */
fun cleDepuisIntent(intention: Intent): CleRappel = CleRappel(
    medicamentId = intention.getLongExtra(EXTRA_MEDICAMENT_ID, -1),
    date = LocalDate.ofEpochDay(intention.getLongExtra(EXTRA_DATE, 0)),
    moment = Moment.valueOf(requireNotNull(intention.getStringExtra(EXTRA_MOMENT))),
)
```

`RecepteurRappel` est écrit en tâche 12 ; cette tâche ne compile donc pas seule. Écrire dans le même commit un receveur vide qui sera rempli en tâche 12 :

`app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurRappel.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/** Rempli en tâche 12 : poste la notification et arme la relance. */
@AndroidEntryPoint
class RecepteurRappel : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
```

- [ ] **Step 6: Déclarer le module Hilt et les permissions**

`app/src/main/kotlin/fr/pillulier/app/di/ModuleRappels.kt` :

```kotlin
package fr.pillulier.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.app.rappels.ProgrammateurAlarmesAndroid
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuleRappels {
    @Binds
    @Singleton
    abstract fun programmateur(android: ProgrammateurAlarmesAndroid): ProgrammateurAlarmes
}
```

Dans `app/src/main/AndroidManifest.xml`, avant `<application>` :

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

et dans `<application>` :

```xml
        <receiver
            android:name=".rappels.RecepteurRappel"
            android:exported="false" />
```

- [ ] **Step 7: Lancer les tests**

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

Attendu : les sept tests de `ReArmerRappelsTest` au vert.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(app): programmateur d alarmes et rearmement de la fenetre"
```

---

### Task 12 : Notifications par médicament, actions et relance

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/Libelles.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/rappels/Notifications.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurRappel.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurActionPrise.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/PillulierApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/fr/pillulier/app/ui/LibellesTest.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/rappels/NotificationsTest.kt`

**Interfaces:**
- Consumes: `CleRappel` et `codeRequete` (tâche 7), `Medicament` (tâche 2), les dépôts (tâche 9), `EnregistrerPrise` (tâche 10), `ProgrammateurAlarmes` (tâche 11)
- Produces:
  - `formaterDose(dose: Double): String`, `libelleForme(forme: Forme, dose: Double): String`, `libelleMoment(moment: Moment): String`, `libelleDoseComplete(dose: Double, forme: Forme): String`
  - `Notifications` : `creerCanaux()`, `posterRappel(cle: CleRappel, medicament: Medicament, dose: Double, critique: Boolean)`, `retirer(cle: CleRappel)`
  - constantes `CANAL_RAPPELS`, `CANAL_CRITIQUES`, `ACTION_PRIS`, `ACTION_PLUS_TARD`, `EXTRA_DOSE`

- [ ] **Step 1: Écrire les tests qui échouent**

`app/src/test/kotlin/fr/pillulier/app/ui/LibellesTest.kt` :

```kotlin
package fr.pillulier.app.ui

import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import org.junit.Test
import kotlin.test.assertEquals

class LibellesTest {

    @Test
    fun `une demi unite s ecrit en fraction`() {
        assertEquals("½", formaterDose(0.5))
        assertEquals("1½", formaterDose(1.5))
        assertEquals("2½", formaterDose(2.5))
    }

    @Test
    fun `une dose entiere s ecrit sans decimale`() {
        assertEquals("1", formaterDose(1.0))
        assertEquals("2", formaterDose(2.0))
        assertEquals("10", formaterDose(10.0))
    }

    @Test
    fun `une dose exotique garde une decimale`() {
        assertEquals("0,25", formaterDose(0.25))
        assertEquals("1,75", formaterDose(1.75))
    }

    @Test
    fun `la forme s accorde au pluriel au dela d une unite`() {
        assertEquals("comprimé", libelleForme(Forme.COMPRIME, 1.0))
        assertEquals("comprimé", libelleForme(Forme.COMPRIME, 0.5))
        assertEquals("comprimés", libelleForme(Forme.COMPRIME, 2.0))
        assertEquals("gélules", libelleForme(Forme.GELULE, 3.0))
        assertEquals("sachet", libelleForme(Forme.SACHET, 1.0))
        assertEquals("injections", libelleForme(Forme.INJECTION, 2.0))
    }

    @Test
    fun `la dose complete associe le nombre et la forme`() {
        assertEquals("½ comprimé", libelleDoseComplete(0.5, Forme.COMPRIME))
        assertEquals("2 gélules", libelleDoseComplete(2.0, Forme.GELULE))
    }

    @Test
    fun `les moments ont un libelle francais`() {
        assertEquals("Matin", libelleMoment(Moment.MATIN))
        assertEquals("Midi", libelleMoment(Moment.MIDI))
        assertEquals("Soir", libelleMoment(Moment.SOIR))
        assertEquals("Coucher", libelleMoment(Moment.COUCHER))
    }
}
```

`app/src/test/kotlin/fr/pillulier/app/rappels/NotificationsTest.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.codeRequete
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationsTest {

    private lateinit var contexte: Context
    private lateinit var notifications: Notifications
    private lateinit var gestionnaire: NotificationManager

    @Before
    fun preparer() {
        contexte = ApplicationProvider.getApplicationContext()
        notifications = Notifications(contexte)
        gestionnaire = contexte.getSystemService(NotificationManager::class.java)
        notifications.creerCanaux()
    }

    private fun medicament(critique: Boolean = false) = Medicament(
        id = 7,
        nom = "Levothyrox",
        dosage = "75 µg",
        forme = Forme.COMPRIME,
        unitesParBoite = 30,
        stockUnites = 30.0,
        seuilAlerteJours = 7,
        seuilAlerteUnites = null,
        critique = critique,
    )

    @Test
    fun `les deux canaux sont crees`() {
        assertNotNull(gestionnaire.getNotificationChannel(CANAL_RAPPELS))
        assertNotNull(gestionnaire.getNotificationChannel(CANAL_CRITIQUES))
    }

    @Test
    fun `un rappel est poste sous l identifiant de sa cle`() {
        val cle = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)

        notifications.posterRappel(cle, medicament(), dose = 0.5, critique = false)

        val postees = shadowOf(gestionnaire).allNotifications
        assertTrue(postees.isNotEmpty())
        assertTrue(shadowOf(gestionnaire).activeNotifications.any { it.id == cle.codeRequete() })
    }

    @Test
    fun `deux medicaments du meme moment donnent deux notifications distinctes`() {
        val matinA = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)
        val matinB = CleRappel(8, LocalDate.of(2026, 1, 5), Moment.MATIN)

        notifications.posterRappel(matinA, medicament(), dose = 1.0, critique = false)
        notifications.posterRappel(matinB, medicament().copy(id = 8, nom = "Kardegic"), dose = 1.0, critique = false)

        val identifiants = shadowOf(gestionnaire).activeNotifications.map { it.id }.toSet()
        assertTrue(matinA.codeRequete() in identifiants)
        assertTrue(matinB.codeRequete() in identifiants)
    }

    @Test
    fun `retirer un rappel enleve sa notification`() {
        val cle = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)
        notifications.posterRappel(cle, medicament(), dose = 1.0, critique = false)

        notifications.retirer(cle)

        assertEquals(
            0,
            shadowOf(gestionnaire).activeNotifications.count { it.id == cle.codeRequete() },
        )
    }

    @Test
    fun `un medicament critique passe par le canal critiques`() {
        val cle = CleRappel(7, LocalDate.of(2026, 1, 5), Moment.MATIN)

        notifications.posterRappel(cle, medicament(critique = true), dose = 1.0, critique = true)

        val postee = shadowOf(gestionnaire).activeNotifications.first { it.id == cle.codeRequete() }
        assertEquals(CANAL_CRITIQUES, postee.notification.channelId)
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.ui.LibellesTest' --tests 'fr.pillulier.app.rappels.NotificationsTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: formaterDose`

- [ ] **Step 3: Écrire les libellés**

`app/src/main/kotlin/fr/pillulier/app/ui/Libelles.kt` :

```kotlin
package fr.pillulier.app.ui

import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise

/** `0,5` s'écrit `½`, `1,5` s'écrit `1½`, une valeur entière sans décimale. */
fun formaterDose(dose: Double): String {
    val entiere = dose.toInt()
    val reste = dose - entiere

    return when {
        reste == 0.0 -> entiere.toString()
        reste == 0.5 -> if (entiere == 0) "½" else "$entiere½"
        else -> dose.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }
}

fun libelleForme(forme: Forme, dose: Double): String {
    val singulier = when (forme) {
        Forme.COMPRIME -> "comprimé"
        Forme.GELULE -> "gélule"
        Forme.SACHET -> "sachet"
        Forme.INJECTION -> "injection"
    }
    return if (dose > 1.0) singulier + "s" else singulier
}

fun libelleDoseComplete(dose: Double, forme: Forme): String =
    "${formaterDose(dose)} ${libelleForme(forme, dose)}"

fun libelleMoment(moment: Moment): String = when (moment) {
    Moment.MATIN -> "Matin"
    Moment.MIDI -> "Midi"
    Moment.SOIR -> "Soir"
    Moment.COUCHER -> "Coucher"
}

fun libelleStatut(statut: StatutPrise): String = when (statut) {
    StatutPrise.A_VENIR -> "À venir"
    StatutPrise.EN_RETARD -> "En retard"
    StatutPrise.PRISE -> "Prise"
    StatutPrise.OUBLIEE -> "Oubliée"
}
```

- [ ] **Step 4: Écrire les notifications**

`app/src/main/kotlin/fr/pillulier/app/rappels/Notifications.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import fr.pillulier.app.MainActivity
import fr.pillulier.app.R
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.codeRequete
import javax.inject.Inject

const val CANAL_RAPPELS = "rappels"
const val CANAL_CRITIQUES = "critiques"

const val ACTION_PRIS = "fr.pillulier.app.PRIS"
const val ACTION_PLUS_TARD = "fr.pillulier.app.PLUS_TARD"
const val EXTRA_DOSE = "dose"

/** Décalage d'identifiant des notifications de groupe, hors de la plage des clés. */
private const val BASE_GROUPE = 9_000_000

class Notifications @Inject constructor(private val contexte: Context) {

    private val gestionnaire = contexte.getSystemService(NotificationManager::class.java)

    fun creerCanaux() {
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_RAPPELS, "Rappels de prise", NotificationManager.IMPORTANCE_HIGH),
        )
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_CRITIQUES, "Prises critiques", NotificationManager.IMPORTANCE_HIGH).apply {
                setBypassDnd(true)
            },
        )
    }

    /**
     * Une notification par médicament, regroupée sous un résumé par moment : on
     * peut valider les comprimés et laisser la piqûre en attente.
     */
    fun posterRappel(cle: CleRappel, medicament: Medicament, dose: Double, critique: Boolean) {
        val notification = NotificationCompat.Builder(
            contexte,
            if (critique) CANAL_CRITIQUES else CANAL_RAPPELS,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${medicament.nom} ${medicament.dosage}")
            .setContentText("${libelleMoment(cle.moment)} — ${libelleDoseComplete(dose, medicament.forme)}")
            .setContentIntent(intentOuvertureApp())
            .setGroup(cleGroupe(cle.moment))
            .setAutoCancel(false)
            .setOngoing(critique)
            .addAction(0, "Pris", intentAction(ACTION_PRIS, cle, dose))
            .addAction(0, "Plus tard", intentAction(ACTION_PLUS_TARD, cle, dose))
            .apply { if (critique) setFullScreenIntent(intentAlarme(cle, dose), true) }
            .build()

        gestionnaire.notify(cle.codeRequete(), notification)
        gestionnaire.notify(BASE_GROUPE + cle.moment.ordinal, resumeDeGroupe(cle.moment))
    }

    fun retirer(cle: CleRappel) {
        gestionnaire.cancel(cle.codeRequete())
    }

    /** Retire toutes les notifications d'un moment, résumé compris. */
    fun retirerGroupe(moment: Moment) {
        gestionnaire.cancel(BASE_GROUPE + moment.ordinal)
    }

    private fun resumeDeGroupe(moment: Moment) = NotificationCompat.Builder(contexte, CANAL_RAPPELS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(libelleMoment(moment))
        .setGroup(cleGroupe(moment))
        .setGroupSummary(true)
        .setContentIntent(intentOuvertureApp())
        .build()

    private fun cleGroupe(moment: Moment) = "moment_${moment.name}"

    private fun intentOuvertureApp(): PendingIntent = PendingIntent.getActivity(
        contexte,
        0,
        Intent(contexte, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun intentAction(action: String, cle: CleRappel, dose: Double): PendingIntent {
        val intention = Intent(contexte, RecepteurActionPrise::class.java).apply {
            this.action = action
            putExtra(EXTRA_MEDICAMENT_ID, cle.medicamentId)
            putExtra(EXTRA_DATE, cle.date.toEpochDay())
            putExtra(EXTRA_MOMENT, cle.moment.name)
            putExtra(EXTRA_DOSE, dose)
        }

        // Le code de requête distingue les deux actions d'une même clé.
        val code = cle.codeRequete() + if (action == ACTION_PRIS) 10_000_000 else 20_000_000

        return PendingIntent.getBroadcast(
            contexte,
            code,
            intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun intentAlarme(cle: CleRappel, dose: Double): PendingIntent {
        val intention = Intent(contexte, AlarmeActivity::class.java).apply {
            putExtra(EXTRA_MEDICAMENT_ID, cle.medicamentId)
            putExtra(EXTRA_DATE, cle.date.toEpochDay())
            putExtra(EXTRA_MOMENT, cle.moment.name)
            putExtra(EXTRA_DOSE, dose)
        }

        return PendingIntent.getActivity(
            contexte,
            cle.codeRequete() + 30_000_000,
            intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
```

`AlarmeActivity` est écrite en tâche 13 : créer dès maintenant un fichier minimal pour que le module compile.

`app/src/main/kotlin/fr/pillulier/app/rappels/AlarmeActivity.kt` :

```kotlin
package fr.pillulier.app.rappels

import androidx.activity.ComponentActivity

/** Remplie en tâche 13 : alarme plein écran des médicaments critiques. */
class AlarmeActivity : ComponentActivity()
```

- [ ] **Step 5: Créer l'icône de notification**

`app/src/main/res/drawable/ic_notification.xml` :

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="#FF000000"
        android:pathData="M4,6h16v2H4zM4,11h16v2H4zM4,16h16v2H4z" />
</vector>
```

- [ ] **Step 6: Remplir le receveur de rappel**

`app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurRappel.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Poste la notification du médicament attendu, puis arme immédiatement la
 * relance suivante. La chaîne s'interrompt quand la prise est enregistrée ou à
 * la clôture de la journée, qui annule la clé.
 */
@AndroidEntryPoint
class RecepteurRappel : BroadcastReceiver() {

    @Inject lateinit var medicaments: DepotMedicaments
    @Inject lateinit var ordonnances: DepotOrdonnances
    @Inject lateinit var preferences: DepotPreferences
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var programmateur: ProgrammateurAlarmes
    @Inject lateinit var horloge: Horloge

    override fun onReceive(context: Context, intent: Intent) {
        val cle = cleDepuisIntent(intent)
        val critique = intent.getBooleanExtra(EXTRA_CRITIQUE, false)
        val termine = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val medicament = medicaments.parId(cle.medicamentId) ?: return@launch
                val dose = ordonnances.pourMedicament(cle.medicamentId)
                    ?.doses
                    ?.firstOrNull { it.moment == cle.moment }
                    ?.dose
                    ?: return@launch

                notifications.posterRappel(cle, medicament, dose, critique)

                val intervalle = preferences.instantane().intervalleRelanceMinutes
                programmateur.programmer(
                    cle = cle,
                    quand = horloge.maintenant().plusMinutes(intervalle.toLong()),
                    critique = critique,
                )
            } finally {
                termine.finish()
            }
        }
    }
}
```

- [ ] **Step 7: Écrire le receveur des actions de notification**

`app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurActionPrise.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.EnregistrerPrise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Traite les deux actions d'une notification de rappel : *Pris* et *Plus tard*. */
@AndroidEntryPoint
class RecepteurActionPrise : BroadcastReceiver() {

    @Inject lateinit var enregistrerPrise: EnregistrerPrise
    @Inject lateinit var medicaments: DepotMedicaments
    @Inject lateinit var preferences: DepotPreferences
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var programmateur: ProgrammateurAlarmes
    @Inject lateinit var horloge: Horloge

    override fun onReceive(context: Context, intent: Intent) {
        val cle = cleDepuisIntent(intent)
        val dose = intent.getDoubleExtra(EXTRA_DOSE, 0.0)
        val action = intent.action
        val termine = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_PRIS -> {
                        enregistrerPrise(cle.medicamentId, cle.date, cle.moment, dose)
                        programmateur.annuler(cle)
                        notifications.retirer(cle)
                    }

                    ACTION_PLUS_TARD -> {
                        val critique = medicaments.parId(cle.medicamentId)?.critique ?: false
                        val delai = preferences.instantane().delaiPlusTardMinutes
                        programmateur.programmer(
                            cle = cle,
                            quand = horloge.maintenant().plusMinutes(delai.toLong()),
                            critique = critique,
                        )
                        notifications.retirer(cle)
                    }
                }
            } finally {
                termine.finish()
            }
        }
    }
}
```

- [ ] **Step 8: Créer les canaux au démarrage et déclarer les composants**

`app/src/main/kotlin/fr/pillulier/app/PillulierApplication.kt` :

```kotlin
package fr.pillulier.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.pillulier.app.rappels.Notifications
import javax.inject.Inject

@HiltAndroidApp
class PillulierApplication : Application() {

    @Inject lateinit var notifications: Notifications

    override fun onCreate() {
        super.onCreate()
        notifications.creerCanaux()
    }
}
```

Dans `app/src/main/AndroidManifest.xml`, à l'intérieur de `<application>` :

```xml
        <receiver
            android:name=".rappels.RecepteurActionPrise"
            android:exported="false">
            <intent-filter>
                <action android:name="fr.pillulier.app.PRIS" />
                <action android:name="fr.pillulier.app.PLUS_TARD" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 9: Lancer les tests**

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

Attendu : les six tests de libellés et les cinq de notifications au vert.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(app): notifications par medicament avec actions et relance"
```

---

### Task 13 : Alarme plein écran des médicaments critiques

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/rappels/AlarmeActivity.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/rappels/AlarmeViewModel.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/fr/pillulier/app/rappels/AlarmeViewModelTest.kt`

**Interfaces:**
- Consumes: `EnregistrerPrise` (tâche 10), `ProgrammateurAlarmes` (tâche 11), `Notifications` et les extras (tâche 12)
- Produces: `AlarmeViewModel` avec `charger(cle: CleRappel, dose: Double)`, `valider()`, `reporter()`, et un état `EtatAlarme(nom, dosage, libelleDose, momentLisible, termine)`

- [ ] **Step 1: Écrire le test qui échoue**

`app/src/test/kotlin/fr/pillulier/app/rappels/AlarmeViewModelTest.kt` :

```kotlin
package fr.pillulier.app.rappels

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.ProgrammateurEspion
import fr.pillulier.domain.CleRappel
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AlarmeViewModelTest {

    private lateinit var base: PillulierDatabase
    private lateinit var vue: AlarmeViewModel
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 8, 0))
    private val cle = CleRappel(1, LocalDate.of(2026, 1, 5), Moment.MATIN)

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        vue = AlarmeViewModel(
            medicaments = DepotMedicaments(base.medicaments()),
            enregistrerPrise = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge),
            preferences = DepotPreferences(ApplicationProvider.getApplicationContext()),
            programmateur = programmateur,
            notifications = Notifications(ApplicationProvider.getApplicationContext()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun insererInsuline() {
        base.medicaments().inserer(
            MedicamentEntity(
                id = 1,
                nom = "Insuline",
                dosage = "10 UI",
                forme = Forme.INJECTION,
                unitesParBoite = 5,
                stockUnites = 5.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = true,
            ),
        )
    }

    @Test
    fun `charger expose le nom et la dose lisible`() = runTest {
        insererInsuline()

        vue.charger(cle, dose = 1.0)

        val etat = vue.etat.value
        assertEquals("Insuline", etat.nom)
        assertEquals("10 UI", etat.dosage)
        assertEquals("1 injection", etat.libelleDose)
        assertEquals("Matin", etat.momentLisible)
    }

    @Test
    fun `valider enregistre la prise annule l alarme et termine`() = runTest {
        insererInsuline()
        vue.charger(cle, dose = 1.0)

        vue.valider()

        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
        assertEquals(4.0, base.medicaments().parId(1)!!.stockUnites)
        assertTrue(cle in programmateur.annulees)
        assertTrue(vue.etat.value.termine)
    }

    @Test
    fun `reporter reprogramme l alarme sans enregistrer de prise`() = runTest {
        insererInsuline()
        vue.charger(cle, dose = 1.0)

        vue.reporter()

        assertEquals(0, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
        assertEquals(
            LocalDateTime.of(2026, 1, 5, 8, 15),
            programmateur.programmees.single().quand,
        )
        assertTrue(vue.etat.value.termine)
    }
}
```

`HorlogeFigee` et `ProgrammateurEspion` viennent des tâches 10 et 11 ; les importer depuis `fr.pillulier.app.usecase` comme ci-dessus.

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.rappels.AlarmeViewModelTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: AlarmeViewModel`

- [ ] **Step 3: Écrire le ViewModel**

`app/src/main/kotlin/fr/pillulier/app/rappels/AlarmeViewModel.kt` :

```kotlin
package fr.pillulier.app.rappels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.domain.CleRappel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EtatAlarme(
    val nom: String = "",
    val dosage: String = "",
    val libelleDose: String = "",
    val momentLisible: String = "",
    val termine: Boolean = false,
)

@HiltViewModel
class AlarmeViewModel @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val enregistrerPrise: EnregistrerPrise,
    private val preferences: DepotPreferences,
    private val programmateur: ProgrammateurAlarmes,
    private val notifications: Notifications,
    private val horloge: Horloge,
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatAlarme())
    val etat: StateFlow<EtatAlarme> = _etat.asStateFlow()

    private var cle: CleRappel? = null
    private var dose: Double = 0.0

    suspend fun charger(cle: CleRappel, dose: Double) {
        this.cle = cle
        this.dose = dose

        val medicament = medicaments.parId(cle.medicamentId) ?: return
        _etat.update {
            it.copy(
                nom = medicament.nom,
                dosage = medicament.dosage,
                libelleDose = libelleDoseComplete(dose, medicament.forme),
                momentLisible = libelleMoment(cle.moment),
            )
        }
    }

    suspend fun valider() {
        val cle = cle ?: return
        enregistrerPrise(cle.medicamentId, cle.date, cle.moment, dose)
        programmateur.annuler(cle)
        notifications.retirer(cle)
        _etat.update { it.copy(termine = true) }
    }

    suspend fun reporter() {
        val cle = cle ?: return
        val delai = preferences.instantane().delaiPlusTardMinutes
        programmateur.programmer(cle, horloge.maintenant().plusMinutes(delai.toLong()), critique = true)
        notifications.retirer(cle)
        _etat.update { it.copy(termine = true) }
    }

    /** Variantes non suspendues, appelées depuis les boutons de l'activité. */
    fun validerDepuisUi() = viewModelScope.launch { valider() }

    fun reporterDepuisUi() = viewModelScope.launch { reporter() }
}
```

- [ ] **Step 4: Écrire l'activité plein écran**

`app/src/main/kotlin/fr/pillulier/app/rappels/AlarmeActivity.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.Ringtone
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Alarme des médicaments critiques : par-dessus l'écran verrouillé, avec son. */
@AndroidEntryPoint
class AlarmeActivity : ComponentActivity() {

    private val vue: AlarmeViewModel by viewModels()
    private var sonnerie: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val cle = cleDepuisIntent(intent)
        val dose = intent.getDoubleExtra(EXTRA_DOSE, 0.0)
        lifecycleScope.launch { vue.charger(cle, dose) }

        demarrerSonnerie()

        setContent {
            val etat by vue.etat.collectAsStateWithLifecycle()

            if (etat.termine) finish()

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(etat.momentLisible, style = MaterialTheme.typography.titleMedium)
                        Text(etat.nom, style = MaterialTheme.typography.headlineLarge)
                        Text(etat.dosage, style = MaterialTheme.typography.titleLarge)
                        Text(etat.libelleDose, style = MaterialTheme.typography.headlineSmall)

                        Row(
                            modifier = Modifier.padding(top = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Button(onClick = { vue.validerDepuisUi() }) { Text("Pris") }
                            OutlinedButton(onClick = { vue.reporterDepuisUi() }) { Text("Plus tard") }
                        }
                    }
                }
            }
        }
    }

    private fun demarrerSonnerie() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        sonnerie = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
    }

    override fun onDestroy() {
        sonnerie?.stop()
        super.onDestroy()
    }
}
```

- [ ] **Step 5: Déclarer l'activité au manifeste**

Dans `app/src/main/AndroidManifest.xml`, à l'intérieur de `<application>` :

```xml
        <activity
            android:name=".rappels.AlarmeActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:launchMode="singleInstance"
            android:showOnLockScreen="true"
            android:turnScreenOn="true"
            android:theme="@style/Theme.Material3.DayNight.NoActionBar" />
```

- [ ] **Step 6: Lancer les tests**

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

Attendu : les trois tests de `AlarmeViewModelTest` au vert.

- [ ] **Step 7: Vérifier l'alarme sur un appareil**

```bash
./gradlew :app:installDebug
adb shell am start -n fr.pillulier.app/.rappels.AlarmeActivity \
  --el medicamentId 1 --el dateEpochDay 20458 --es moment MATIN
```

Attendu : l'écran d'alarme s'affiche avec le son, et les deux boutons ferment l'activité. `am start` ne sait pas transmettre un `double` : la dose affichée sera vide, ce qui est normal pour ce contrôle manuel. Il demande un appareil ou un émulateur ; s'il n'y en a pas, le noter et passer.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(app): alarme plein ecran des medicaments critiques"
```

---

### Task 14 : Clôture quotidienne, travail périodique et redémarrage

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/CloturerJournee.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/rappels/TravailQuotidien.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/rappels/PlanificateurQuotidien.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurDemarrage.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/PillulierApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/CloturerJourneeTest.kt`

**Interfaces:**
- Consumes: `prisesAttendues` (tâche 4), les dépôts (tâche 9), `ProgrammateurAlarmes` (tâche 11), `Notifications` (tâche 12), `ReArmerRappels` (tâche 11)
- Produces:
  - `CloturerJournee.invoke()` — annule les alarmes et retire les notifications de la veille, sans rien écrire en base
  - `PlanificateurQuotidien.planifier()` — enfile le travail périodique aligné sur 00h05
  - `TravailQuotidien` — clôture puis réarme

- [ ] **Step 1: Écrire le test qui échoue**

`app/src/test/kotlin/fr/pillulier/app/usecase/CloturerJourneeTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.codeRequete
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.app.NotificationManager
import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CloturerJourneeTest {

    private lateinit var base: PillulierDatabase
    private lateinit var contexte: Context
    private lateinit var notifications: Notifications
    private lateinit var cloturer: CloturerJournee
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 6, 0, 5))

    @Before
    fun preparer() {
        contexte = ApplicationProvider.getApplicationContext()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()
        notifications = Notifications(contexte)
        notifications.creerCanaux()
        cloturer = CloturerJournee(
            ordonnances = DepotOrdonnances(base.ordonnances()),
            moments = DepotMoments(base.moments()),
            programmateur = programmateur,
            notifications = notifications,
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentDuMatin(): Long {
        val depot = DepotMedicaments(base.medicaments())
        val id = depot.enregistrer(
            Medicament(
                id = 0,
                nom = "Levothyrox",
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )
        DepotOrdonnances(base.ordonnances()).enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )
        return id
    }

    @Test
    fun `la cloture annule les alarmes de la veille`() = runTest {
        val id = medicamentDuMatin()

        cloturer()

        assertEquals(
            listOf(CleRappel(id, LocalDate.of(2026, 1, 5), Moment.MATIN)),
            programmateur.annulees,
        )
    }

    @Test
    fun `la cloture retire les notifications de la veille`() = runTest {
        val id = medicamentDuMatin()
        val cle = CleRappel(id, LocalDate.of(2026, 1, 5), Moment.MATIN)
        notifications.posterRappel(
            cle,
            DepotMedicaments(base.medicaments()).parId(id)!!,
            dose = 1.0,
            critique = false,
        )

        cloturer()

        val gestionnaire = contexte.getSystemService(NotificationManager::class.java)
        assertEquals(
            0,
            shadowOf(gestionnaire).activeNotifications.count { it.id == cle.codeRequete() },
        )
    }

    @Test
    fun `la cloture n ecrit rien en base`() = runTest {
        medicamentDuMatin()

        cloturer()

        assertTrue(base.evenements().duJour(LocalDate.of(2026, 1, 5)).isEmpty())
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.usecase.CloturerJourneeTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: CloturerJournee`

- [ ] **Step 3: Écrire la clôture**

`app/src/main/kotlin/fr/pillulier/app/usecase/CloturerJournee.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Moment
import fr.pillulier.domain.prisesAttendues
import javax.inject.Inject

/**
 * Ferme la veille : les relances sont annulées et les notifications retirées.
 * Rien n'est écrit en base — une prise non cochée devient oubliée par la seule
 * absence d'événement, dérivée à l'affichage.
 */
class CloturerJournee @Inject constructor(
    private val ordonnances: DepotOrdonnances,
    private val moments: DepotMoments,
    private val programmateur: ProgrammateurAlarmes,
    private val notifications: Notifications,
    private val horloge: Horloge,
) {
    suspend operator fun invoke() {
        val veille = horloge.aujourdhui().minusDays(1)
        val heures = moments.heures()

        prisesAttendues(veille, ordonnances.toutes(), heures).forEach { prise ->
            val cle = CleRappel(prise.medicamentId, veille, prise.moment)
            programmateur.annuler(cle)
            notifications.retirer(cle)
        }

        Moment.entries.forEach(notifications::retirerGroupe)
    }
}
```

- [ ] **Step 4: Écrire le travail périodique et sa planification**

`app/src/main/kotlin/fr/pillulier/app/rappels/TravailQuotidien.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.pillulier.app.usecase.CloturerJournee
import fr.pillulier.app.usecase.ReArmerRappels

/** Clôture la veille puis réarme la fenêtre de trois jours. */
@HiltWorker
class TravailQuotidien @AssistedInject constructor(
    @Assisted contexte: Context,
    @Assisted parametres: WorkerParameters,
    private val cloturerJournee: CloturerJournee,
    private val reArmerRappels: ReArmerRappels,
) : CoroutineWorker(contexte, parametres) {

    override suspend fun doWork(): Result {
        cloturerJournee()
        reArmerRappels()
        return Result.success()
    }

    companion object {
        const val NOM = "travail-quotidien"
    }
}
```

`app/src/main/kotlin/fr/pillulier/app/rappels/PlanificateurQuotidien.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.pillulier.app.temps.Horloge
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

/** Aligne le travail quotidien sur 00h05. */
class PlanificateurQuotidien @Inject constructor(
    private val contexte: Context,
    private val horloge: Horloge,
) {
    fun planifier() {
        val maintenant = horloge.maintenant()
        val prochaine = maintenant.toLocalDate()
            .let { if (maintenant.toLocalTime() >= HEURE_CLOTURE) it.plusDays(1) else it }
            .atTime(HEURE_CLOTURE)

        val delai = Duration.between(maintenant, prochaine)

        WorkManager.getInstance(contexte).enqueueUniquePeriodicWork(
            TravailQuotidien.NOM,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TravailQuotidien>(Duration.ofDays(1))
                .setInitialDelay(delai)
                .build(),
        )
    }

    private companion object {
        val HEURE_CLOTURE: LocalTime = LocalTime.of(0, 5)
    }
}
```

`app/src/main/kotlin/fr/pillulier/app/rappels/RecepteurDemarrage.kt` :

```kotlin
package fr.pillulier.app.rappels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.usecase.ReArmerRappels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Après un redémarrage, les alarmes exactes sont perdues : on les reconstruit
 * depuis le moteur, puisque rien n'y est une vérité stockée.
 */
@AndroidEntryPoint
class RecepteurDemarrage : BroadcastReceiver() {

    @Inject lateinit var reArmerRappels: ReArmerRappels
    @Inject lateinit var planificateur: PlanificateurQuotidien

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val termine = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reArmerRappels()
                planificateur.planifier()
            } finally {
                termine.finish()
            }
        }
    }
}
```

- [ ] **Step 5: Brancher WorkManager sur Hilt**

`app/src/main/kotlin/fr/pillulier/app/PillulierApplication.kt` :

```kotlin
package fr.pillulier.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.rappels.PlanificateurQuotidien
import javax.inject.Inject

@HiltAndroidApp
class PillulierApplication : Application(), Configuration.Provider {

    @Inject lateinit var fabriqueTravailleurs: HiltWorkerFactory
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var planificateur: PlanificateurQuotidien

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabriqueTravailleurs).build()

    override fun onCreate() {
        super.onCreate()
        notifications.creerCanaux()
        planificateur.planifier()
    }
}
```

Dans `app/src/main/AndroidManifest.xml`, à l'intérieur de `<application>`, désactiver l'initialisation automatique de WorkManager et déclarer le receveur de démarrage :

```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <receiver
            android:name=".rappels.RecepteurDemarrage"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

Ajouter `xmlns:tools="http://schemas.android.com/tools"` à la balise `<manifest>`.

- [ ] **Step 6: Lancer les tests**

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

Attendu : les trois tests de `CloturerJourneeTest` au vert.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(app): cloture quotidienne, travail periodique et rearmement au demarrage"
```

---

### Task 15 : Écran Aujourd'hui

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/ObserverJournee.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/ObserverAlertes.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/aujourdhui/AujourdhuiViewModel.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/aujourdhui/AujourdhuiEcran.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/ObserverJourneeTest.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/ObserverAlertesTest.kt`

**Interfaces:**
- Consumes: le moteur (`prisesAttendues`, `statut`, `projectionStock`, `dateAlerte`), les dépôts (tâche 9), `EnregistrerPrise` (tâche 10)
- Produces:
  - `data class LigneJournee(val medicamentId: Long, val nom: String, val dosage: String, val moment: Moment, val heure: LocalTime, val dose: Double, val libelleDose: String, val statut: StatutPrise)`
  - `ObserverJournee.invoke(date: LocalDate): Flow<List<LigneJournee>>`
  - `data class Alerte(val medicamentId: Long, val nom: String, val message: String)`
  - `ObserverAlertes.invoke(): Flow<List<Alerte>>`
  - `AujourdhuiViewModel` avec `etat: StateFlow<EtatAujourdhui>`, `cocher(ligne: LigneJournee)`, `enregistrerALaDemande(medicamentId: Long, dose: Double)`

- [ ] **Step 1: Écrire les tests qui échouent**

`app/src/test/kotlin/fr/pillulier/app/usecase/ObserverJourneeTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverJourneeTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var observer: ObserverJournee
    private lateinit var enregistrer: EnregistrerPrise
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 13, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        observer = ObserverJournee(
            medicaments = medicaments,
            ordonnances = ordonnances,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun planifie(nom: String, doses: List<DosePrescrite>): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = doses,
        )
        return id
    }

    @Test
    fun `la journee est triee par heure et porte le nom du medicament`() = runTest {
        planifie("Levothyrox", listOf(DosePrescrite(Moment.SOIR, 1.0)))
        planifie("Metformine", listOf(DosePrescrite(Moment.MATIN, 2.0)))

        val lignes = observer(LocalDate.of(2026, 1, 5)).first()

        assertEquals(listOf("Metformine", "Levothyrox"), lignes.map { it.nom })
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(19, 0)), lignes.map { it.heure })
    }

    @Test
    fun `la dose est mise en forme pour l affichage`() = runTest {
        planifie("Levothyrox", listOf(DosePrescrite(Moment.MATIN, 0.5)))

        val ligne = observer(LocalDate.of(2026, 1, 5)).first().single()

        assertEquals("½ comprimé", ligne.libelleDose)
    }

    @Test
    fun `les statuts refletent l heure courante et le journal`() = runTest {
        val id = planifie(
            "Metformine",
            listOf(
                DosePrescrite(Moment.MATIN, 1.0),
                DosePrescrite(Moment.MIDI, 1.0),
                DosePrescrite(Moment.SOIR, 1.0),
            ),
        )
        enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0)

        val lignes = observer(LocalDate.of(2026, 1, 5)).first()

        assertEquals(
            listOf(StatutPrise.PRISE, StatutPrise.EN_RETARD, StatutPrise.A_VENIR),
            lignes.map { it.statut },
        )
    }

    @Test
    fun `une journee passee sans evenement est oubliee`() = runTest {
        planifie("Levothyrox", listOf(DosePrescrite(Moment.MATIN, 1.0)))

        val lignes = observer(LocalDate.of(2026, 1, 4)).first()

        assertEquals(StatutPrise.OUBLIEE, lignes.single().statut)
    }

    @Test
    fun `un medicament sans ordonnance planifiee n apparait pas`() = runTest {
        medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 16.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )

        assertEquals(emptyList(), observer(LocalDate.of(2026, 1, 5)).first())
    }
}
```

`app/src/test/kotlin/fr/pillulier/app/usecase/ObserverAlertesTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverAlertesTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var observer: ObserverAlertes
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 9, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        observer = ObserverAlertes(
            medicaments = medicaments,
            ordonnances = ordonnances,
            preferences = DepotPreferences(ApplicationProvider.getApplicationContext()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun avecStock(nom: String, stock: Double, seuilJours: Int?): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "1 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = stock,
                seuilAlerteJours = seuilJours,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )
        return id
    }

    @Test
    fun `un stock large ne declenche aucune alerte`() = runTest {
        avecStock("Levothyrox", stock = 90.0, seuilJours = 7)

        assertEquals(emptyList(), observer().first())
    }

    @Test
    fun `un stock proche de l epuisement declenche une alerte`() = runTest {
        // 5 comprimes, 1 par jour : epuisement le 10 janvier, seuil 7 jours,
        // donc la date d'alerte est deja passee.
        avecStock("Levothyrox", stock = 5.0, seuilJours = 7)

        val alertes = observer().first()

        assertEquals(1, alertes.size)
        assertEquals("Levothyrox", alertes.single().nom)
        assertTrue(alertes.single().message.isNotBlank())
    }

    @Test
    fun `un medicament a la demande alerte sur son seuil en unites`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 4.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.A_LA_DEMANDE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = emptyList(),
        )

        val alertes = observer().first()

        assertEquals(listOf("Doliprane"), alertes.map { it.nom })
    }

    @Test
    fun `un medicament a la demande au dessus de son seuil n alerte pas`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 12.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.A_LA_DEMANDE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = emptyList(),
        )

        assertEquals(emptyList(), observer().first())
    }
}
```

- [ ] **Step 2: Lancer les tests pour vérifier qu'ils échouent**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.usecase.Observer*'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: ObserverJournee`

- [ ] **Step 3: Écrire l'observation de la journée**

`app/src/main/kotlin/fr/pillulier/app/usecase/ObserverJournee.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.prisesAttendues
import fr.pillulier.domain.statut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class LigneJournee(
    val medicamentId: Long,
    val nom: String,
    val dosage: String,
    val moment: Moment,
    val heure: LocalTime,
    val dose: Double,
    val libelleDose: String,
    val statut: StatutPrise,
)

/** Les prises d'une date, décorées du nom du médicament et de leur statut. */
class ObserverJournee @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val moments: DepotMoments,
    private val evenements: DepotEvenements,
    private val horloge: Horloge,
) {
    operator fun invoke(date: LocalDate): Flow<List<LigneJournee>> = combine(
        medicaments.observerTous(),
        ordonnances.observerToutes(),
        moments.observer(),
        evenements.observerEntre(date, date),
    ) { tousMedicaments, toutesOrdonnances, heures, evenementsDuJour ->
        if (heures.size < Moment.entries.size) return@combine emptyList()

        val parId = tousMedicaments.associateBy { it.id }
        val maintenant = horloge.maintenant()

        prisesAttendues(date, toutesOrdonnances, heures).mapNotNull { prise ->
            val medicament = parId[prise.medicamentId] ?: return@mapNotNull null

            LigneJournee(
                medicamentId = prise.medicamentId,
                nom = medicament.nom,
                dosage = medicament.dosage,
                moment = prise.moment,
                heure = prise.heure.toLocalTime(),
                dose = prise.dose,
                libelleDose = libelleDoseComplete(prise.dose, medicament.forme),
                statut = statut(prise, evenementsDuJour, maintenant),
            )
        }
    }
}
```

- [ ] **Step 4: Écrire l'observation des alertes de renouvellement**

`app/src/main/kotlin/fr/pillulier/app/usecase/ObserverAlertes.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.JoursFeriesFrance
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.dateAlerte
import fr.pillulier.domain.projectionStock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class Alerte(
    val medicamentId: Long,
    val nom: String,
    val message: String,
)

/**
 * Une alerte par médicament à renouveler : sur la date d'alerte pour les
 * ordonnances planifiées, sur le seuil en unités pour celles à la demande, dont
 * la consommation n'est pas prévisible.
 */
class ObserverAlertes @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val preferences: DepotPreferences,
    private val horloge: Horloge,
) {
    operator fun invoke(): Flow<List<Alerte>> = combine(
        medicaments.observerTous(),
        ordonnances.observerToutes(),
        preferences.preferences,
    ) { tousMedicaments, toutesOrdonnances, prefs ->
        val parMedicament = toutesOrdonnances.associateBy { it.ordonnance.medicamentId }
        val aujourdhui = horloge.aujourdhui()

        tousMedicaments.mapNotNull { medicament ->
            val ordonnance = parMedicament[medicament.id] ?: return@mapNotNull null

            if (ordonnance.ordonnance.type == TypeOrdonnance.A_LA_DEMANDE) {
                val seuil = medicament.seuilAlerteUnites ?: return@mapNotNull null
                if (medicament.stockUnites > seuil) return@mapNotNull null

                Alerte(
                    medicamentId = medicament.id,
                    nom = medicament.nom,
                    message = "il reste ${medicament.stockUnites.toInt()} unités",
                )
            } else {
                val epuisement = projectionStock(medicament.stockUnites, ordonnance, aujourdhui)
                    ?: return@mapNotNull null
                val seuil = medicament.seuilAlerteJours ?: prefs.seuilAlerteJoursDefaut
                val alerte = dateAlerte(epuisement, seuil, JoursFeriesFrance::estFerie)

                if (aujourdhui < alerte) return@mapNotNull null

                val jours = ChronoUnit.DAYS.between(aujourdhui, epuisement)
                Alerte(
                    medicamentId = medicament.id,
                    nom = medicament.nom,
                    message = if (jours <= 0L) "stock épuisé" else "plus que $jours jours",
                )
            }
        }
    }
}
```

- [ ] **Step 5: Écrire le ViewModel**

`app/src/main/kotlin/fr/pillulier/app/ui/aujourdhui/AujourdhuiViewModel.kt` :

```kotlin
package fr.pillulier.app.ui.aujourdhui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.Alerte
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.app.usecase.ObserverAlertes
import fr.pillulier.app.usecase.ObserverJournee
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EtatAujourdhui(
    val lignes: List<LigneJournee> = emptyList(),
    val alertes: List<Alerte> = emptyList(),
    val aLaDemande: List<Medicament> = emptyList(),
)

@HiltViewModel
class AujourdhuiViewModel @Inject constructor(
    observerJournee: ObserverJournee,
    observerAlertes: ObserverAlertes,
    medicaments: DepotMedicaments,
    ordonnances: DepotOrdonnances,
    private val enregistrerPrise: EnregistrerPrise,
    private val reArmerRappels: ReArmerRappels,
    private val horloge: Horloge,
) : ViewModel() {

    val etat: StateFlow<EtatAujourdhui> = combine(
        observerJournee(horloge.aujourdhui()),
        observerAlertes(),
        medicaments.observerTous(),
        ordonnances.observerToutes(),
    ) { lignes, alertes, tous, toutesOrdonnances ->
        // Les médicaments à la demande n'ont aucune prise planifiée : ils sont
        // proposés à part, pour un enregistrement ponctuel.
        val idsALaDemande = toutesOrdonnances
            .filter { it.ordonnance.type == TypeOrdonnance.A_LA_DEMANDE }
            .map { it.ordonnance.medicamentId }
            .toSet()

        EtatAujourdhui(
            lignes = lignes,
            alertes = alertes,
            aLaDemande = tous.filter { it.id in idsALaDemande },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatAujourdhui())

    fun cocher(ligne: LigneJournee) = viewModelScope.launch {
        enregistrerPrise(ligne.medicamentId, horloge.aujourdhui(), ligne.moment, ligne.dose)
        reArmerRappels()
    }

    fun enregistrerALaDemande(medicamentId: Long, dose: Double) = viewModelScope.launch {
        enregistrerPrise(medicamentId, horloge.aujourdhui(), moment = null, dose = dose)
    }
}
```

- [ ] **Step 6: Écrire l'écran**

`app/src/main/kotlin/fr/pillulier/app/ui/aujourdhui/AujourdhuiEcran.kt` :

```kotlin
package fr.pillulier.app.ui.aujourdhui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.app.ui.libelleStatut
import fr.pillulier.domain.StatutPrise
import java.time.format.DateTimeFormatter

private val formatHeure = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun AujourdhuiEcran(vue: AujourdhuiViewModel = hiltViewModel()) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (etat.alertes.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("À renouveler", style = MaterialTheme.typography.titleMedium)
                        etat.alertes.forEach { alerte ->
                            Text("${alerte.nom} : ${alerte.message}")
                        }
                    }
                }
            }
        }

        etat.lignes.groupBy { it.moment }.forEach { (moment, lignes) ->
            item {
                Text(
                    "${libelleMoment(moment)} · ${lignes.first().heure.format(formatHeure)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(lignes) { ligne ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = ligne.statut == StatutPrise.PRISE,
                        enabled = ligne.statut != StatutPrise.PRISE && ligne.statut != StatutPrise.OUBLIEE,
                        onCheckedChange = { vue.cocher(ligne) },
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("${ligne.nom} ${ligne.dosage}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${ligne.libelleDose} · ${libelleStatut(ligne.statut)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (etat.aLaDemande.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text("À la demande", style = MaterialTheme.typography.titleMedium)
            }

            items(etat.aLaDemande) { medicament ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${medicament.nom} ${medicament.dosage}",
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = { vue.enregistrerALaDemande(medicament.id, 1.0) }) {
                        Text("J'en ai pris 1")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: Lancer les tests et compiler**

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug
```

Attendu : les neuf tests d'observation au vert et la compilation réussie.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(app): ecran Aujourd hui avec statuts et alertes de renouvellement"
```

---

### Task 16 : Écran Mes médicaments

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerMedicament.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/MedicamentsViewModel.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModel.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/MedicamentsEcran.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionEcran.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerMedicamentTest.kt`

**Interfaces:**
- Consumes: les dépôts (tâche 9), `ReArmerRappels` (tâche 11)
- Produces:
  - `EnregistrerMedicament.invoke(medicament: Medicament, type: TypeOrdonnance, rythme: Rythme, dateDebut: LocalDate, dateFin: LocalDate?, doses: List<DosePrescrite>): Long`
  - `SupprimerMedicament.invoke(medicamentId: Long)`
  - `MedicamentsViewModel` avec `medicaments: StateFlow<List<Medicament>>`
  - `EditionViewModel` avec `etat: StateFlow<EtatEdition>`, `charger(medicamentId: Long?)`, les mutateurs de formulaire, `enregistrer()`, `supprimer()`

- [ ] **Step 1: Écrire le test qui échoue**

`app/src/test/kotlin/fr/pillulier/app/usecase/EnregistrerMedicamentTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnregistrerMedicamentTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var enregistrer: EnregistrerMedicament
    private lateinit var supprimer: SupprimerMedicament
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = programmateur,
            horloge = horloge,
        )
        enregistrer = EnregistrerMedicament(medicaments, ordonnances, reArmer)
        supprimer = SupprimerMedicament(medicaments, reArmer)
    }

    @After
    fun fermer() = base.close()

    private fun levothyrox(id: Long = 0) = Medicament(
        id = id,
        nom = "Levothyrox",
        dosage = "75 µg",
        forme = Forme.COMPRIME,
        unitesParBoite = 30,
        stockUnites = 30.0,
        seuilAlerteJours = 7,
        seuilAlerteUnites = null,
        critique = false,
    )

    @Test
    fun `enregistrer cree le medicament et son ordonnance`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        assertEquals("Levothyrox", medicaments.parId(id)!!.nom)
        assertEquals(1, ordonnances.pourMedicament(id)!!.doses.size)
    }

    @Test
    fun `enregistrer rearme les rappels`() = runTest {
        enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        assertEquals(3, programmateur.programmees.size)
    }

    @Test
    fun `une ordonnance planifiee sans dose est refusee`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox(),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = emptyList(),
            )
        }
    }

    @Test
    fun `un nom vide est refuse`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox().copy(nom = "  "),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            )
        }
    }

    @Test
    fun `une date de fin avant la date de debut est refusee`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox(),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 10),
                dateFin = LocalDate.of(2026, 1, 5),
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            )
        }
    }

    @Test
    fun `une ordonnance a la demande accepte l absence de dose`() = runTest {
        val id = enregistrer(
            medicament = levothyrox().copy(nom = "Doliprane", seuilAlerteJours = null, seuilAlerteUnites = 5),
            type = TypeOrdonnance.A_LA_DEMANDE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = emptyList(),
        )

        assertTrue(ordonnances.pourMedicament(id)!!.doses.isEmpty())
    }

    @Test
    fun `reenregistrer un medicament existant le met a jour sans le dupliquer`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        enregistrer(
            medicament = levothyrox(id = id).copy(dosage = "100 µg"),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.SOIR, 2.0)),
        )

        assertEquals(1, medicaments.tous().size)
        assertEquals("100 µg", medicaments.parId(id)!!.dosage)
        assertEquals(listOf(Moment.SOIR), ordonnances.pourMedicament(id)!!.doses.map { it.moment })
    }

    @Test
    fun `supprimer un medicament supprime son ordonnance`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        supprimer(id)

        assertNull(medicaments.parId(id))
        assertNull(ordonnances.pourMedicament(id))
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.usecase.EnregistrerMedicamentTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: EnregistrerMedicament`

- [ ] **Step 3: Écrire les cas d'usage**

`app/src/main/kotlin/fr/pillulier/app/usecase/EnregistrerMedicament.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import java.time.LocalDate
import javax.inject.Inject

/**
 * Écrit le médicament et son ordonnance, puis réarme la fenêtre : toute
 * modification de posologie doit se refléter tout de suite dans les alarmes.
 */
class EnregistrerMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val reArmerRappels: ReArmerRappels,
) {
    suspend operator fun invoke(
        medicament: Medicament,
        type: TypeOrdonnance,
        rythme: Rythme,
        dateDebut: LocalDate,
        dateFin: LocalDate?,
        doses: List<DosePrescrite>,
    ): Long {
        require(medicament.nom.isNotBlank()) { "le nom du medicament est obligatoire" }
        require(medicament.unitesParBoite > 0) { "une boite contient au moins une unite" }
        require(dateFin == null || !dateFin.isBefore(dateDebut)) {
            "la date de fin ne peut pas preceder la date de debut"
        }
        if (type == TypeOrdonnance.PLANIFIEE) {
            require(doses.isNotEmpty()) { "une ordonnance planifiee doit porter au moins une dose" }
            require(doses.all { it.dose > 0.0 }) { "une dose doit etre strictement positive" }
            require(doses.map { it.moment }.distinct().size == doses.size) {
                "un moment ne peut porter qu une dose"
            }
        }

        val id = medicaments.enregistrer(medicament)

        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = type,
                rythme = rythme,
                dateDebut = dateDebut,
                dateFin = dateFin,
            ),
            doses = if (type == TypeOrdonnance.PLANIFIEE) doses else emptyList(),
        )

        reArmerRappels()
        return id
    }
}

class SupprimerMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val reArmerRappels: ReArmerRappels,
) {
    suspend operator fun invoke(medicamentId: Long) {
        medicaments.supprimer(medicamentId)
        reArmerRappels()
    }
}
```

- [ ] **Step 4: Écrire les ViewModels**

`app/src/main/kotlin/fr/pillulier/app/ui/medicaments/MedicamentsViewModel.kt` :

```kotlin
package fr.pillulier.app.ui.medicaments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.domain.Medicament
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MedicamentsViewModel @Inject constructor(
    depot: DepotMedicaments,
) : ViewModel() {

    val medicaments: StateFlow<List<Medicament>> = depot.observerTous()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

`app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionViewModel.kt` :

```kotlin
package fr.pillulier.app.ui.medicaments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.EnregistrerMedicament
import fr.pillulier.app.usecase.SupprimerMedicament
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class EtatEdition(
    val id: Long = 0,
    val nom: String = "",
    val dosage: String = "",
    val forme: Forme = Forme.COMPRIME,
    val unitesParBoite: String = "30",
    val stockUnites: String = "0",
    val critique: Boolean = false,
    val type: TypeOrdonnance = TypeOrdonnance.PLANIFIEE,
    val rythme: Rythme = Rythme.TousLesJours,
    val dateDebut: LocalDate = LocalDate.now(),
    val dateFin: LocalDate? = null,
    val doses: Map<Moment, Double> = emptyMap(),
    val seuilAlerteJours: String = "",
    val seuilAlerteUnites: String = "",
    val erreur: String? = null,
    val enregistre: Boolean = false,
)

@HiltViewModel
class EditionViewModel @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val enregistrerMedicament: EnregistrerMedicament,
    private val supprimerMedicament: SupprimerMedicament,
    private val horloge: Horloge,
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatEdition(dateDebut = horloge.aujourdhui()))
    val etat: StateFlow<EtatEdition> = _etat.asStateFlow()

    fun charger(medicamentId: Long?) = viewModelScope.launch {
        if (medicamentId == null || medicamentId == 0L) return@launch

        val medicament = medicaments.parId(medicamentId) ?: return@launch
        val ordonnance = ordonnances.pourMedicament(medicamentId)

        _etat.value = EtatEdition(
            id = medicament.id,
            nom = medicament.nom,
            dosage = medicament.dosage,
            forme = medicament.forme,
            unitesParBoite = medicament.unitesParBoite.toString(),
            stockUnites = medicament.stockUnites.toString(),
            critique = medicament.critique,
            type = ordonnance?.ordonnance?.type ?: TypeOrdonnance.PLANIFIEE,
            rythme = ordonnance?.ordonnance?.rythme ?: Rythme.TousLesJours,
            dateDebut = ordonnance?.ordonnance?.dateDebut ?: horloge.aujourdhui(),
            dateFin = ordonnance?.ordonnance?.dateFin,
            doses = ordonnance?.doses?.associate { it.moment to it.dose } ?: emptyMap(),
            seuilAlerteJours = medicament.seuilAlerteJours?.toString() ?: "",
            seuilAlerteUnites = medicament.seuilAlerteUnites?.toString() ?: "",
        )
    }

    fun modifierNom(valeur: String) = _etat.update { it.copy(nom = valeur) }
    fun modifierDosage(valeur: String) = _etat.update { it.copy(dosage = valeur) }
    fun modifierForme(valeur: Forme) = _etat.update { it.copy(forme = valeur) }
    fun modifierUnitesParBoite(valeur: String) = _etat.update { it.copy(unitesParBoite = valeur) }
    fun modifierStock(valeur: String) = _etat.update { it.copy(stockUnites = valeur) }
    fun modifierCritique(valeur: Boolean) = _etat.update { it.copy(critique = valeur) }
    fun modifierType(valeur: TypeOrdonnance) = _etat.update { it.copy(type = valeur) }
    fun modifierDateDebut(valeur: LocalDate) = _etat.update { it.copy(dateDebut = valeur) }
    fun modifierDateFin(valeur: LocalDate?) = _etat.update { it.copy(dateFin = valeur) }
    fun modifierSeuilJours(valeur: String) = _etat.update { it.copy(seuilAlerteJours = valeur) }
    fun modifierSeuilUnites(valeur: String) = _etat.update { it.copy(seuilAlerteUnites = valeur) }

    fun choisirTousLesJours() = _etat.update { it.copy(rythme = Rythme.TousLesJours) }

    fun basculerJourDeSemaine(jour: DayOfWeek) = _etat.update { etat ->
        val actuels = (etat.rythme as? Rythme.JoursDeSemaine)?.jours ?: emptySet()
        val nouveaux = if (jour in actuels) actuels - jour else actuels + jour
        etat.copy(
            rythme = if (nouveaux.isEmpty()) Rythme.TousLesJours else Rythme.JoursDeSemaine(nouveaux),
        )
    }

    fun choisirUnJourSurN(n: Int) = _etat.update {
        it.copy(rythme = if (n >= 2) Rythme.UnJourSurN(n) else Rythme.TousLesJours)
    }

    /** Une dose nulle retire le moment de l'ordonnance. */
    fun definirDose(moment: Moment, dose: Double) = _etat.update { etat ->
        val doses = etat.doses.toMutableMap()
        if (dose <= 0.0) doses.remove(moment) else doses[moment] = dose
        etat.copy(doses = doses)
    }

    fun enregistrer() = viewModelScope.launch {
        val etat = _etat.value
        try {
            enregistrerMedicament(
                medicament = Medicament(
                    id = etat.id,
                    nom = etat.nom.trim(),
                    dosage = etat.dosage.trim(),
                    forme = etat.forme,
                    unitesParBoite = etat.unitesParBoite.toIntOrNull() ?: 0,
                    stockUnites = etat.stockUnites.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    seuilAlerteJours = etat.seuilAlerteJours.toIntOrNull(),
                    seuilAlerteUnites = etat.seuilAlerteUnites.toIntOrNull(),
                    critique = etat.critique,
                ),
                type = etat.type,
                rythme = etat.rythme,
                dateDebut = etat.dateDebut,
                dateFin = etat.dateFin,
                doses = etat.doses.map { (moment, dose) -> DosePrescrite(moment, dose) },
            )
            _etat.update { it.copy(erreur = null, enregistre = true) }
        } catch (erreur: IllegalArgumentException) {
            _etat.update { it.copy(erreur = erreur.message) }
        }
    }

    fun supprimer() = viewModelScope.launch {
        val id = _etat.value.id
        if (id != 0L) supprimerMedicament(id)
        _etat.update { it.copy(enregistre = true) }
    }
}
```

- [ ] **Step 5: Écrire les deux écrans**

`app/src/main/kotlin/fr/pillulier/app/ui/medicaments/MedicamentsEcran.kt` :

```kotlin
package fr.pillulier.app.ui.medicaments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.formaterDose

@Composable
fun MedicamentsEcran(
    surSelection: (Long) -> Unit,
    vue: MedicamentsViewModel = hiltViewModel(),
) {
    val medicaments by vue.medicaments.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(medicaments) { medicament ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { surSelection(medicament.id) }
                        .padding(vertical = 12.dp),
                ) {
                    Text("${medicament.nom} ${medicament.dosage}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${formaterDose(medicament.stockUnites)} en stock" +
                            if (medicament.critique) " · critique" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { surSelection(0) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Text("+")
        }
    }
}
```

`app/src/main/kotlin/fr/pillulier/app/ui/medicaments/EditionEcran.kt` :

```kotlin
package fr.pillulier.app.ui.medicaments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.formaterDose
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import java.time.DayOfWeek

@Composable
fun EditionEcran(
    medicamentId: Long,
    surSortie: () -> Unit,
    vue: EditionViewModel = hiltViewModel(),
) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    LaunchedEffect(medicamentId) { vue.charger(medicamentId) }
    LaunchedEffect(etat.enregistre) { if (etat.enregistre) surSortie() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = etat.nom,
            onValueChange = vue::modifierNom,
            label = { Text("Nom") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = etat.dosage,
            onValueChange = vue::modifierDosage,
            label = { Text("Dosage") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Forme", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Forme.entries.forEach { forme ->
                FilterChip(
                    selected = etat.forme == forme,
                    onClick = { vue.modifierForme(forme) },
                    label = { Text(forme.name.lowercase()) },
                )
            }
        }

        OutlinedTextField(
            value = etat.unitesParBoite,
            onValueChange = vue::modifierUnitesParBoite,
            label = { Text("Unités par boîte") },
        )
        OutlinedTextField(
            value = etat.stockUnites,
            onValueChange = vue::modifierStock,
            label = { Text("Stock actuel en unités") },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = etat.critique, onCheckedChange = vue::modifierCritique)
            Text("Prise critique (alarme plein écran)", modifier = Modifier.padding(start = 8.dp))
        }

        Text("Type d'ordonnance", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeOrdonnance.entries.forEach { type ->
                FilterChip(
                    selected = etat.type == type,
                    onClick = { vue.modifierType(type) },
                    label = { Text(if (type == TypeOrdonnance.PLANIFIEE) "Planifiée" else "À la demande") },
                )
            }
        }

        if (etat.type == TypeOrdonnance.PLANIFIEE) {
            Text("Rythme", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = etat.rythme is Rythme.TousLesJours,
                    onClick = { vue.choisirTousLesJours() },
                    label = { Text("Tous les jours") },
                )
                FilterChip(
                    selected = etat.rythme is Rythme.UnJourSurN,
                    onClick = { vue.choisirUnJourSurN(2) },
                    label = { Text("Un jour sur deux") },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { jour ->
                    val actifs = (etat.rythme as? Rythme.JoursDeSemaine)?.jours ?: emptySet()
                    FilterChip(
                        selected = jour in actifs,
                        onClick = { vue.basculerJourDeSemaine(jour) },
                        label = { Text(jour.name.take(2)) },
                    )
                }
            }

            SelecteurDate(
                libelle = "Début du traitement",
                date = etat.dateDebut,
                surChangement = vue::modifierDateDebut,
            )
            SelecteurDateOptionnelle(
                libelle = "Fin du traitement",
                date = etat.dateFin,
                dateDebut = etat.dateDebut,
                surChangement = vue::modifierDateFin,
            )

            Text("Doses par moment", style = MaterialTheme.typography.titleSmall)
            Moment.entries.forEach { moment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(libelleMoment(moment), modifier = Modifier.padding(end = 8.dp))
                    OutlinedButton(onClick = { vue.definirDose(moment, (etat.doses[moment] ?: 0.0) - 0.5) }) {
                        Text("−")
                    }
                    Text(
                        formaterDose(etat.doses[moment] ?: 0.0),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    OutlinedButton(onClick = { vue.definirDose(moment, (etat.doses[moment] ?: 0.0) + 0.5) }) {
                        Text("+")
                    }
                }
            }

            OutlinedTextField(
                value = etat.seuilAlerteJours,
                onValueChange = vue::modifierSeuilJours,
                label = { Text("Alerter X jours avant la fin (vide = défaut)") },
            )
        } else {
            OutlinedTextField(
                value = etat.seuilAlerteUnites,
                onValueChange = vue::modifierSeuilUnites,
                label = { Text("Alerter sous X unités restantes") },
            )
        }

        etat.erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vue.enregistrer() }) { Text("Enregistrer") }
            if (etat.id != 0L) {
                OutlinedButton(onClick = { vue.supprimer() }) { Text("Supprimer") }
            }
        }
    }
}

private val formatDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelecteurDate(
    libelle: String,
    date: LocalDate,
    surChangement: (LocalDate) -> Unit,
) {
    var ouvert by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(libelle, modifier = Modifier.padding(end = 8.dp))
        OutlinedButton(onClick = { ouvert = true }) { Text(date.format(formatDate)) }
    }

    if (ouvert) {
        val etatSelecteur = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * MILLIS_PAR_JOUR,
        )

        DatePickerDialog(
            onDismissRequest = { ouvert = false },
            confirmButton = {
                Button(
                    onClick = {
                        etatSelecteur.selectedDateMillis?.let {
                            surChangement(LocalDate.ofEpochDay(it / MILLIS_PAR_JOUR))
                        }
                        ouvert = false
                    },
                ) {
                    Text("Valider")
                }
            },
        ) {
            DatePicker(state = etatSelecteur)
        }
    }
}

@Composable
private fun SelecteurDateOptionnelle(
    libelle: String,
    date: LocalDate?,
    dateDebut: LocalDate,
    surChangement: (LocalDate?) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = date != null,
                onCheckedChange = { actif ->
                    surChangement(if (actif) dateDebut.plusDays(6) else null)
                },
            )
            Text(
                if (date == null) "$libelle : illimité (traitement au long cours)" else libelle,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // Une cure datée : la date de fin n'apparaît que si elle existe.
        date?.let { SelecteurDate(libelle = "Dernier jour", date = it, surChangement = surChangement) }
    }
}
```

`MILLIS_PAR_JOUR` est une constante du fichier : `private const val MILLIS_PAR_JOUR = 86_400_000L`

Les imports supplémentaires du fichier `EditionEcran.kt` :

```kotlin
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
```

Le sélecteur de Material 3 travaille en millisecondes UTC : la conversion passe par `toEpochDay`, ce qui évite tout décalage de fuseau sur une date sans heure.

Les deux sélecteurs de date sont écrits à l'étape suivante, dans le même fichier.

- [ ] **Step 6: Lancer les tests et compiler**

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug
```

Attendu : les huit tests d'enregistrement au vert et la compilation réussie.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(app): ecran Mes medicaments avec edition de l ordonnance"
```

---

### Task 17 : Écran Stock

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/ObserverStock.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/stock/StockViewModel.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/stock/StockEcran.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/ObserverStockTest.kt`

**Interfaces:**
- Consumes: `projectionStock` et `dateAlerte` (tâche 6), les dépôts (tâche 9), `AjouterBoite` et `CorrigerStock` (tâche 10)
- Produces:
  - `data class LigneStock(val medicamentId: Long, val nom: String, val dosage: String, val unitesRestantes: Double, val libelleUnites: String, val joursRestants: Long?, val dateEpuisement: LocalDate?, val dateAlerte: LocalDate?, val aLaDemande: Boolean, val enAlerte: Boolean)`
  - `ObserverStock.invoke(): Flow<List<LigneStock>>`
  - `StockViewModel` avec `lignes: StateFlow<List<LigneStock>>`, `ajouterBoite(medicamentId: Long)`, `corriger(medicamentId: Long, unites: Double)`

- [ ] **Step 1: Écrire le test qui échoue**

`app/src/test/kotlin/fr/pillulier/app/usecase/ObserverStockTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverStockTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var observer: ObserverStock
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 9, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        observer = ObserverStock(
            medicaments = medicaments,
            ordonnances = ordonnances,
            preferences = DepotPreferences(ApplicationProvider.getApplicationContext()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun planifie(nom: String, stock: Double): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = stock,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )
        return id
    }

    @Test
    fun `la ligne porte la date d epuisement et les jours restants`() = runTest {
        planifie("Levothyrox", stock = 10.0)

        val ligne = observer().first().single()

        assertEquals(LocalDate.of(2026, 1, 15), ligne.dateEpuisement)
        assertEquals(10L, ligne.joursRestants)
        assertFalse(ligne.aLaDemande)
    }

    @Test
    fun `la date d alerte applique la marge du dimanche`() = runTest {
        planifie("Levothyrox", stock = 10.0)

        // Epuisement le 15 janvier, seuil 7 jours : candidat le 8, un dimanche
        // tombe dans la fenetre le 11, donc l'alerte recule au 7.
        assertEquals(LocalDate.of(2026, 1, 7), observer().first().single().dateAlerte)
    }

    @Test
    fun `une ligne devient en alerte quand la date est atteinte`() = runTest {
        planifie("Levothyrox", stock = 3.0)

        assertTrue(observer().first().single().enAlerte)
    }

    @Test
    fun `un stock confortable n est pas en alerte`() = runTest {
        planifie("Levothyrox", stock = 90.0)

        assertFalse(observer().first().single().enAlerte)
    }

    @Test
    fun `un medicament a la demande n a ni date ni jours restants`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 4.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.A_LA_DEMANDE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = emptyList(),
        )

        val ligne = observer().first().single()

        assertTrue(ligne.aLaDemande)
        assertNull(ligne.dateEpuisement)
        assertNull(ligne.joursRestants)
        assertTrue(ligne.enAlerte, "4 unites sous un seuil de 5")
    }

    @Test
    fun `les unites restantes sont mises en forme`() = runTest {
        planifie("Levothyrox", stock = 9.5)

        assertEquals("9½ comprimés", observer().first().single().libelleUnites)
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.usecase.ObserverStockTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: ObserverStock`

- [ ] **Step 3: Écrire l'observation du stock**

`app/src/main/kotlin/fr/pillulier/app/usecase/ObserverStock.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.domain.JoursFeriesFrance
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.dateAlerte
import fr.pillulier.domain.projectionStock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class LigneStock(
    val medicamentId: Long,
    val nom: String,
    val dosage: String,
    val unitesRestantes: Double,
    val libelleUnites: String,
    val joursRestants: Long?,
    val dateEpuisement: LocalDate?,
    val dateAlerte: LocalDate?,
    val aLaDemande: Boolean,
    val enAlerte: Boolean,
)

class ObserverStock @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val preferences: DepotPreferences,
    private val horloge: Horloge,
) {
    operator fun invoke(): Flow<List<LigneStock>> = combine(
        medicaments.observerTous(),
        ordonnances.observerToutes(),
        preferences.preferences,
    ) { tousMedicaments, toutesOrdonnances, prefs ->
        val parMedicament = toutesOrdonnances.associateBy { it.ordonnance.medicamentId }
        val aujourdhui = horloge.aujourdhui()

        tousMedicaments.map { medicament ->
            val ordonnance = parMedicament[medicament.id]
            val aLaDemande = ordonnance == null ||
                ordonnance.ordonnance.type == TypeOrdonnance.A_LA_DEMANDE

            val epuisement = if (aLaDemande) {
                null
            } else {
                projectionStock(medicament.stockUnites, ordonnance!!, aujourdhui)
            }

            val alerte = epuisement?.let {
                dateAlerte(
                    dateEpuisement = it,
                    seuilJours = medicament.seuilAlerteJours ?: prefs.seuilAlerteJoursDefaut,
                    estFerie = JoursFeriesFrance::estFerie,
                )
            }

            LigneStock(
                medicamentId = medicament.id,
                nom = medicament.nom,
                dosage = medicament.dosage,
                unitesRestantes = medicament.stockUnites,
                libelleUnites = libelleDoseComplete(medicament.stockUnites, medicament.forme),
                joursRestants = epuisement?.let { ChronoUnit.DAYS.between(aujourdhui, it) },
                dateEpuisement = epuisement,
                dateAlerte = alerte,
                aLaDemande = aLaDemande,
                enAlerte = if (aLaDemande) {
                    medicament.seuilAlerteUnites?.let { medicament.stockUnites <= it } ?: false
                } else {
                    alerte != null && !aujourdhui.isBefore(alerte)
                },
            )
        }
    }
}
```

- [ ] **Step 4: Écrire le ViewModel et l'écran**

`app/src/main/kotlin/fr/pillulier/app/ui/stock/StockViewModel.kt` :

```kotlin
package fr.pillulier.app.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.usecase.AjouterBoite
import fr.pillulier.app.usecase.CorrigerStock
import fr.pillulier.app.usecase.LigneStock
import fr.pillulier.app.usecase.ObserverStock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockViewModel @Inject constructor(
    observerStock: ObserverStock,
    private val ajouterBoiteCas: AjouterBoite,
    private val corrigerStockCas: CorrigerStock,
) : ViewModel() {

    val lignes: StateFlow<List<LigneStock>> = observerStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ajouterBoite(medicamentId: Long) = viewModelScope.launch {
        ajouterBoiteCas(medicamentId)
    }

    fun corriger(medicamentId: Long, unites: Double) = viewModelScope.launch {
        corrigerStockCas(medicamentId, unites)
    }
}
```

`app/src/main/kotlin/fr/pillulier/app/ui/stock/StockEcran.kt` :

```kotlin
package fr.pillulier.app.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.usecase.LigneStock
import java.time.format.DateTimeFormatter

private val formatDate = DateTimeFormatter.ofPattern("dd/MM")

@Composable
fun StockEcran(vue: StockViewModel = hiltViewModel()) {
    val lignes by vue.lignes.collectAsStateWithLifecycle()
    val (planifies, aLaDemande) = lignes.partition { !it.aLaDemande }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        items(planifies) { ligne ->
            LigneStockCarte(ligne, vue)
        }

        if (aLaDemande.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    "À la demande — consommation imprévisible",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(aLaDemande) { ligne ->
                LigneStockCarte(ligne, vue)
            }
        }
    }
}

@Composable
private fun LigneStockCarte(ligne: LigneStock, vue: StockViewModel) {
    var correction by remember(ligne.medicamentId) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${ligne.nom} ${ligne.dosage}", style = MaterialTheme.typography.bodyLarge)
            Text(ligne.libelleUnites, style = MaterialTheme.typography.bodyMedium)

            if (ligne.joursRestants != null) {
                Text(
                    "Plus que ${ligne.joursRestants} jours · épuisement le " +
                        "${ligne.dateEpuisement?.format(formatDate)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                ligne.dateAlerte?.let {
                    Text("Alerte le ${it.format(formatDate)}", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (ligne.enAlerte) {
                Text(
                    "À renouveler",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { vue.ajouterBoite(ligne.medicamentId) }) {
                    Text("+1 boîte")
                }
                OutlinedTextField(
                    value = correction,
                    onValueChange = { correction = it },
                    label = { Text("Recompté") },
                    modifier = Modifier.padding(start = 8.dp),
                )
                OutlinedButton(
                    onClick = {
                        correction.replace(',', '.').toDoubleOrNull()?.let {
                            vue.corriger(ligne.medicamentId, it)
                            correction = ""
                        }
                    },
                ) {
                    Text("OK")
                }
            }
        }
    }
}
```

- [ ] **Step 5: Lancer les tests et compiler**

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug
```

Attendu : les six tests de `ObserverStockTest` au vert et la compilation réussie.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(app): ecran Stock avec jours restants et date d alerte"
```

---

### Task 18 : Écran Semaine, la grille du pillulier

**Files:**
- Create: `app/src/main/kotlin/fr/pillulier/app/usecase/ObserverSemaine.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/semaine/SemaineViewModel.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/semaine/SemaineEcran.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/usecase/ObserverSemaineTest.kt`

**Interfaces:**
- Consumes: `prisesAttendues` et `statut` (tâches 4 et 5), les dépôts (tâche 9)
- Produces:
  - `data class EntreeSemaine(val medicamentId: Long, val nom: String, val libelleDose: String, val statut: StatutPrise)`
  - `data class EtatSemaine(val jours: List<LocalDate> = emptyList(), val cellules: Map<Pair<LocalDate, Moment>, List<EntreeSemaine>> = emptyMap())`
  - `ObserverSemaine.invoke(debut: LocalDate): Flow<EtatSemaine>` — sept jours à partir de `debut`
  - `SemaineViewModel` avec `etat: StateFlow<EtatSemaine>`, `semainePrecedente()`, `semaineSuivante()`

- [ ] **Step 1: Écrire le test qui échoue**

`app/src/test/kotlin/fr/pillulier/app/usecase/ObserverSemaineTest.kt` :

```kotlin
package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverSemaineTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var enregistrer: EnregistrerPrise
    private lateinit var observer: ObserverSemaine

    /** Le 2026-01-07 est un mercredi ; la semaine commence le lundi 5. */
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 7, 13, 0))
    private val lundi = LocalDate.of(2026, 1, 5)

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        observer = ObserverSemaine(
            medicaments = medicaments,
            ordonnances = ordonnances,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun planifie(nom: String, rythme: Rythme, doses: List<DosePrescrite>): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "1 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = rythme,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
            ),
            doses = doses,
        )
        return id
    }

    @Test
    fun `la semaine couvre sept jours a partir du debut demande`() = runTest {
        planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.MATIN, 1.0)))

        val etat = observer(lundi).first()

        assertEquals(7, etat.jours.size)
        assertEquals(lundi, etat.jours.first())
        assertEquals(LocalDate.of(2026, 1, 11), etat.jours.last())
    }

    @Test
    fun `une cellule porte le nom et la dose du medicament attendu`() = runTest {
        planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.MATIN, 0.5)))

        val cellule = observer(lundi).first().cellules.getValue(lundi to Moment.MATIN).single()

        assertEquals("Levothyrox", cellule.nom)
        assertEquals("½ comprimé", cellule.libelleDose)
    }

    @Test
    fun `un moment sans prise n a pas de cellule`() = runTest {
        planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.MATIN, 1.0)))

        val cellules = observer(lundi).first().cellules

        assertTrue((lundi to Moment.COUCHER) !in cellules)
    }

    @Test
    fun `les jours passes sans evenement sont oublies et le jour courant reste ouvert`() = runTest {
        planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.SOIR, 1.0)))

        val cellules = observer(lundi).first().cellules

        assertEquals(
            StatutPrise.OUBLIEE,
            cellules.getValue(lundi to Moment.SOIR).single().statut,
        )
        assertEquals(
            StatutPrise.A_VENIR,
            cellules.getValue(LocalDate.of(2026, 1, 7) to Moment.SOIR).single().statut,
        )
    }

    @Test
    fun `une prise enregistree apparait comme prise dans la grille`() = runTest {
        val id = planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.MATIN, 1.0)))
        enregistrer(id, LocalDate.of(2026, 1, 7), Moment.MATIN, dose = 1.0)

        val cellule = observer(lundi).first()
            .cellules
            .getValue(LocalDate.of(2026, 1, 7) to Moment.MATIN)
            .single()

        assertEquals(StatutPrise.PRISE, cellule.statut)
    }

    @Test
    fun `un rythme hebdomadaire ne remplit que ses jours`() = runTest {
        planifie(
            "Methotrexate",
            Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY)),
            listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        val cellules = observer(lundi).first().cellules

        assertTrue((lundi to Moment.MATIN) in cellules)
        assertTrue((LocalDate.of(2026, 1, 6) to Moment.MATIN) !in cellules)
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.usecase.ObserverSemaineTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: ObserverSemaine`

- [ ] **Step 3: Écrire l'observation de la semaine**

`app/src/main/kotlin/fr/pillulier/app/usecase/ObserverSemaine.kt` :

```kotlin
package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.prisesAttendues
import fr.pillulier.domain.statut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

data class EntreeSemaine(
    val medicamentId: Long,
    val nom: String,
    val libelleDose: String,
    val statut: StatutPrise,
)

data class EtatSemaine(
    val jours: List<LocalDate> = emptyList(),
    val cellules: Map<Pair<LocalDate, Moment>, List<EntreeSemaine>> = emptyMap(),
)

/** La grille du pillulier : sept jours en colonnes, quatre moments en lignes. */
class ObserverSemaine @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val moments: DepotMoments,
    private val evenements: DepotEvenements,
    private val horloge: Horloge,
) {
    operator fun invoke(debut: LocalDate): Flow<EtatSemaine> {
        val jours = (0L until 7L).map { debut.plusDays(it) }

        return combine(
            medicaments.observerTous(),
            ordonnances.observerToutes(),
            moments.observer(),
            evenements.observerEntre(jours.first(), jours.last()),
        ) { tousMedicaments, toutesOrdonnances, heures, evenementsSemaine ->
            if (heures.size < Moment.entries.size) return@combine EtatSemaine()

            val parId = tousMedicaments.associateBy { it.id }
            val maintenant = horloge.maintenant()

            val cellules = jours.flatMap { jour ->
                prisesAttendues(jour, toutesOrdonnances, heures).mapNotNull { prise ->
                    val medicament = parId[prise.medicamentId] ?: return@mapNotNull null

                    (jour to prise.moment) to EntreeSemaine(
                        medicamentId = prise.medicamentId,
                        nom = medicament.nom,
                        libelleDose = libelleDoseComplete(prise.dose, medicament.forme),
                        statut = statut(prise, evenementsSemaine, maintenant),
                    )
                }
            }
                .groupBy({ it.first }, { it.second })

            EtatSemaine(jours = jours, cellules = cellules)
        }
    }
}
```

- [ ] **Step 4: Écrire le ViewModel et l'écran**

`app/src/main/kotlin/fr/pillulier/app/ui/semaine/SemaineViewModel.kt` :

```kotlin
package fr.pillulier.app.ui.semaine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.EtatSemaine
import fr.pillulier.app.usecase.ObserverSemaine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SemaineViewModel @Inject constructor(
    observerSemaine: ObserverSemaine,
    horloge: Horloge,
) : ViewModel() {

    private val _debut = MutableStateFlow(
        horloge.aujourdhui().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    )
    val debut: StateFlow<LocalDate> = _debut.asStateFlow()

    val etat: StateFlow<EtatSemaine> = _debut
        .flatMapLatest { observerSemaine(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatSemaine())

    fun semainePrecedente() = _debut.update { it.minusWeeks(1) }

    fun semaineSuivante() = _debut.update { it.plusWeeks(1) }
}
```

`app/src/main/kotlin/fr/pillulier/app/ui/semaine/SemaineEcran.kt` :

```kotlin
package fr.pillulier.app.ui.semaine

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatJour = DateTimeFormatter.ofPattern("EEE dd", Locale.FRENCH)

@Composable
fun SemaineEcran(vue: SemaineViewModel = hiltViewModel()) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { vue.semainePrecedente() }) { Text("← Semaine") }
            TextButton(onClick = { vue.semaineSuivante() }) { Text("Semaine →") }
        }

        // La grille peut dépasser la largeur de l'écran : elle défile
        // horizontalement plutôt que de comprimer les colonnes.
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Text("", modifier = Modifier.width(72.dp))
                etat.jours.forEach { jour ->
                    Text(
                        jour.format(formatJour),
                        modifier = Modifier.width(96.dp).padding(4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            HorizontalDivider()

            Moment.entries.forEach { moment ->
                Row {
                    Text(
                        libelleMoment(moment),
                        modifier = Modifier.width(72.dp).padding(4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )

                    etat.jours.forEach { jour ->
                        Column(modifier = Modifier.width(96.dp).padding(4.dp)) {
                            etat.cellules[jour to moment].orEmpty().forEach { entree ->
                                Text(
                                    "${marqueur(entree.statut)} ${entree.nom}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

private fun marqueur(statut: StatutPrise): String = when (statut) {
    StatutPrise.PRISE -> "✓"
    StatutPrise.OUBLIEE -> "✗"
    StatutPrise.EN_RETARD -> "!"
    StatutPrise.A_VENIR -> "·"
}
```

- [ ] **Step 5: Lancer les tests et compiler**

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug
```

Attendu : les six tests de `ObserverSemaineTest` au vert et la compilation réussie.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(app): vue semaine en grille de pillulier"
```

---

### Task 19 : Préférences, navigation et demande d'autorisations

**Files:**
- Modify: `app/src/main/kotlin/fr/pillulier/app/data/DepotPreferences.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/preferences/PreferencesViewModel.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/preferences/PreferencesEcran.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/autorisations/AutorisationsEcran.kt`
- Create: `app/src/main/kotlin/fr/pillulier/app/ui/Navigation.kt`
- Modify: `app/src/main/kotlin/fr/pillulier/app/MainActivity.kt`
- Test: `app/src/test/kotlin/fr/pillulier/app/ui/preferences/PreferencesViewModelTest.kt`

**Interfaces:**
- Consumes: `DepotPreferences` et `DepotMoments` (tâche 9), `ReArmerRappels` (tâche 11), tous les écrans des tâches 15 à 18
- Produces:
  - `DepotPreferences.autorisationsVues: Flow<Boolean>` et `marquerAutorisationsVues()`
  - `PreferencesViewModel` avec `etat: StateFlow<EtatPreferences>`, `definirHeure(moment, heure)`, `definirDelaiPlusTard(minutes)`, `definirIntervalleRelance(minutes)`, `definirSeuilJours(jours)`
  - `PillulierNavigation()` — l'arbre de navigation complet

- [ ] **Step 1: Écrire le test qui échoue**

`app/src/test/kotlin/fr/pillulier/app/ui/preferences/PreferencesViewModelTest.kt` :

```kotlin
package fr.pillulier.app.ui.preferences

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.ProgrammateurEspion
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.domain.Moment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PreferencesViewModelTest {

    private lateinit var base: PillulierDatabase
    private lateinit var moments: DepotMoments
    private lateinit var preferences: DepotPreferences
    private lateinit var vue: PreferencesViewModel
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        // `viewModelScope` lance sur le dispatcher Main, en pause sous Robolectric :
        // sans ce remplacement, le `join()` des tests ne rendrait jamais la main.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val contexte = ApplicationProvider.getApplicationContext<android.content.Context>()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()

        moments = DepotMoments(base.moments())
        preferences = DepotPreferences(contexte)
        vue = PreferencesViewModel(
            moments = moments,
            preferences = preferences,
            reArmerRappels = ReArmerRappels(
                ordonnances = DepotOrdonnances(base.ordonnances()),
                medicaments = DepotMedicaments(base.medicaments()),
                moments = moments,
                evenements = DepotEvenements(base.evenements()),
                programmateur = programmateur,
                horloge = horloge,
            ),
        )
    }

    @After
    fun fermer() {
        base.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `modifier l heure d un moment la persiste`() = runTest {
        vue.definirHeure(Moment.MATIN, LocalTime.of(7, 15)).join()

        assertEquals(LocalTime.of(7, 15), moments.heures().getValue(Moment.MATIN))
    }

    @Test
    fun `modifier le delai plus tard le persiste`() = runTest {
        vue.definirDelaiPlusTard(20).join()

        assertEquals(20, preferences.instantane().delaiPlusTardMinutes)
    }

    @Test
    fun `modifier l intervalle de relance le persiste`() = runTest {
        vue.definirIntervalleRelance(30).join()

        assertEquals(30, preferences.instantane().intervalleRelanceMinutes)
    }

    @Test
    fun `modifier le seuil d alerte par defaut le persiste`() = runTest {
        vue.definirSeuilJours(10).join()

        assertEquals(10, preferences.instantane().seuilAlerteJoursDefaut)
    }

    @Test
    fun `les autorisations ne sont marquees vues qu une fois demandees`() = runTest {
        assertEquals(false, preferences.autorisationsVues.first())

        preferences.marquerAutorisationsVues()

        assertEquals(true, preferences.autorisationsVues.first())
    }
}
```

- [ ] **Step 2: Lancer le test pour vérifier qu'il échoue**

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.pillulier.app.ui.preferences.PreferencesViewModelTest'
```

Attendu : ÉCHEC de compilation, `Unresolved reference: PreferencesViewModel`

- [ ] **Step 3: Ajouter le drapeau des autorisations aux préférences**

Dans `app/src/main/kotlin/fr/pillulier/app/data/DepotPreferences.kt`, ajouter :

```kotlin
private val CLE_AUTORISATIONS_VUES = booleanPreferencesKey("autorisations_vues")
```

avec l'import `androidx.datastore.preferences.core.booleanPreferencesKey`, puis dans la classe `DepotPreferences` :

```kotlin
    val autorisationsVues: Flow<Boolean> =
        contexte.dataStore.data.map { it[CLE_AUTORISATIONS_VUES] ?: false }

    suspend fun marquerAutorisationsVues() {
        contexte.dataStore.edit { it[CLE_AUTORISATIONS_VUES] = true }
    }
```

- [ ] **Step 4: Écrire le ViewModel des préférences**

`app/src/main/kotlin/fr/pillulier/app/ui/preferences/PreferencesViewModel.kt` :

```kotlin
package fr.pillulier.app.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.PreferencesPillulier
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.domain.Moment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class EtatPreferences(
    val heures: Map<Moment, LocalTime> = emptyMap(),
    val valeurs: PreferencesPillulier = PreferencesPillulier(),
)

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val moments: DepotMoments,
    private val preferences: DepotPreferences,
    private val reArmerRappels: ReArmerRappels,
) : ViewModel() {

    val etat: StateFlow<EtatPreferences> = combine(
        moments.observer(),
        preferences.preferences,
    ) { heures, valeurs -> EtatPreferences(heures, valeurs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatPreferences())

    /** Changer une heure de moment déplace les alarmes : on réarme aussitôt. */
    fun definirHeure(moment: Moment, heure: LocalTime): Job = viewModelScope.launch {
        moments.definir(moment, heure)
        reArmerRappels()
    }

    fun definirDelaiPlusTard(minutes: Int): Job = viewModelScope.launch {
        preferences.definirDelaiPlusTard(minutes)
    }

    fun definirIntervalleRelance(minutes: Int): Job = viewModelScope.launch {
        preferences.definirIntervalleRelance(minutes)
    }

    fun definirSeuilJours(jours: Int): Job = viewModelScope.launch {
        preferences.definirSeuilAlerteJours(jours)
    }
}
```

- [ ] **Step 5: Écrire l'écran des préférences**

`app/src/main/kotlin/fr/pillulier/app/ui/preferences/PreferencesEcran.kt` :

```kotlin
package fr.pillulier.app.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.domain.Moment
import java.time.format.DateTimeFormatter

private val formatHeure = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun PreferencesEcran(vue: PreferencesViewModel = hiltViewModel()) {
    val etat by vue.etat.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Heures des moments", style = MaterialTheme.typography.titleMedium)

        Moment.entries.forEach { moment ->
            val heure = etat.heures[moment] ?: return@forEach

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(libelleMoment(moment), modifier = Modifier.padding(end = 8.dp))
                OutlinedButton(onClick = { vue.definirHeure(moment, heure.minusMinutes(15)) }) {
                    Text("−15 min")
                }
                Text(heure.format(formatHeure), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { vue.definirHeure(moment, heure.plusMinutes(15)) }) {
                    Text("+15 min")
                }
            }
        }

        Text("Rappels", style = MaterialTheme.typography.titleMedium)

        ReglageMinutes(
            libelle = "Délai du « Plus tard »",
            valeur = etat.valeurs.delaiPlusTardMinutes,
            surChangement = vue::definirDelaiPlusTard,
        )
        ReglageMinutes(
            libelle = "Intervalle de relance",
            valeur = etat.valeurs.intervalleRelanceMinutes,
            surChangement = vue::definirIntervalleRelance,
        )

        Text("Renouvellement", style = MaterialTheme.typography.titleMedium)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Alerter par défaut")
            OutlinedButton(
                onClick = { vue.definirSeuilJours((etat.valeurs.seuilAlerteJoursDefaut - 1).coerceAtLeast(1)) },
            ) {
                Text("−")
            }
            Text("${etat.valeurs.seuilAlerteJoursDefaut} jours avant la fin")
            OutlinedButton(onClick = { vue.definirSeuilJours(etat.valeurs.seuilAlerteJoursDefaut + 1) }) {
                Text("+")
            }
        }
    }
}

@Composable
private fun ReglageMinutes(libelle: String, valeur: Int, surChangement: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(libelle)
        OutlinedButton(onClick = { surChangement((valeur - 5).coerceAtLeast(5)) }) { Text("−5") }
        Text("$valeur min")
        OutlinedButton(onClick = { surChangement(valeur + 5) }) { Text("+5") }
    }
}
```

- [ ] **Step 6: Écrire l'écran de demande d'autorisations**

`app/src/main/kotlin/fr/pillulier/app/ui/autorisations/AutorisationsEcran.kt` :

```kotlin
package fr.pillulier.app.ui.autorisations

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Les quatre autorisations, dans l'ordre. Sans l'exemption d'optimisation de
 * batterie, aucune app de rappel de médicaments n'est fiable sur les surcouches
 * constructeur : c'est pour cela qu'elle est présentée ici et pas cachée.
 */
@Composable
fun AutorisationsEcran(surTermine: () -> Unit) {
    val contexte = LocalContext.current

    val demandeNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Autorisations", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pour que les rappels arrivent à l'heure, l'application a besoin de " +
                "quatre autorisations.",
        )

        Button(
            onClick = { demandeNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("1. Autoriser les notifications")
        }

        Button(
            onClick = { contexte.ouvrirAlarmesExactes() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("2. Autoriser les alarmes exactes")
        }

        Button(
            onClick = { contexte.ouvrirPleinEcran() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("3. Autoriser les alarmes plein écran")
        }

        Button(
            onClick = { contexte.ouvrirOptimisationBatterie() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("4. Désactiver l'optimisation de batterie")
        }

        Button(onClick = surTermine, modifier = Modifier.fillMaxWidth()) {
            Text("Terminé")
        }
    }
}

private fun Context.ouvrirAlarmesExactes() {
    val gestionnaire = getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !gestionnaire.canScheduleExactAlarms()) {
        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }
}

private fun Context.ouvrirPleinEcran() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}

private fun Context.ouvrirOptimisationBatterie() {
    val gestionnaire = getSystemService(PowerManager::class.java)
    if (!gestionnaire.isIgnoringBatteryOptimizations(packageName)) {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}
```

- [ ] **Step 7: Écrire la navigation**

`app/src/main/kotlin/fr/pillulier/app/ui/Navigation.kt` :

```kotlin
package fr.pillulier.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.pillulier.app.ui.aujourdhui.AujourdhuiEcran
import fr.pillulier.app.ui.medicaments.EditionEcran
import fr.pillulier.app.ui.medicaments.MedicamentsEcran
import fr.pillulier.app.ui.preferences.PreferencesEcran
import fr.pillulier.app.ui.semaine.SemaineEcran
import fr.pillulier.app.ui.stock.StockEcran

private data class Onglet(val route: String, val titre: String, val icone: ImageVector)

private val onglets = listOf(
    Onglet("aujourdhui", "Aujourd'hui", Icons.Filled.Today),
    Onglet("semaine", "Semaine", Icons.Filled.CalendarMonth),
    Onglet("medicaments", "Médicaments", Icons.Filled.Medication),
    Onglet("stock", "Stock", Icons.Filled.Inventory),
)

@Composable
fun PillulierNavigation(controleur: NavHostController = rememberNavController()) {
    val entree by controleur.currentBackStackEntryAsState()
    val routeCourante = entree?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(onglets.firstOrNull { it.route == routeCourante }?.titre ?: "Pillulier") },
                actions = {
                    IconButton(onClick = { controleur.navigate("preferences") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Préférences")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                onglets.forEach { onglet ->
                    NavigationBarItem(
                        selected = routeCourante == onglet.route,
                        onClick = {
                            controleur.navigate(onglet.route) {
                                popUpTo(onglets.first().route)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(onglet.icone, contentDescription = onglet.titre) },
                        label = { Text(onglet.titre) },
                    )
                }
            }
        },
    ) { rembourrage ->
        NavHost(
            navController = controleur,
            startDestination = "aujourdhui",
            modifier = Modifier.padding(rembourrage),
        ) {
            composable("aujourdhui") { AujourdhuiEcran() }
            composable("semaine") { SemaineEcran() }
            composable("medicaments") {
                MedicamentsEcran(surSelection = { id -> controleur.navigate("medicament/$id") })
            }
            composable("medicament/{id}") { entreeRoute ->
                EditionEcran(
                    medicamentId = entreeRoute.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    surSortie = { controleur.popBackStack() },
                )
            }
            composable("stock") { StockEcran() }
            composable("preferences") { PreferencesEcran() }
        }
    }
}
```

- [ ] **Step 8: Brancher l'activité principale**

`app/src/main/kotlin/fr/pillulier/app/MainActivity.kt` :

```kotlin
package fr.pillulier.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dagger.hilt.android.AndroidEntryPoint
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.ui.PillulierNavigation
import fr.pillulier.app.ui.autorisations.AutorisationsEcran
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: DepotPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vues by preferences.autorisationsVues.collectAsState(initial = true)
            val portee = rememberCoroutineScope()

            MaterialTheme {
                Surface {
                    if (vues) {
                        PillulierNavigation()
                    } else {
                        AutorisationsEcran(
                            surTermine = { portee.launch { preferences.marquerAutorisationsVues() } },
                        )
                    }
                }
            }
        }
    }
}
```

L'état initial du drapeau est `true` pour ne pas faire clignoter l'écran d'autorisations le temps que DataStore réponde.

- [ ] **Step 9: Lancer toute la suite et compiler**

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug
```

Attendu : tous les tests des deux modules au vert et la compilation réussie.

- [ ] **Step 10: Vérifier l'application sur un appareil**

```bash
./gradlew :app:installDebug
```

Parcours de contrôle manuel :

1. accorder les quatre autorisations à l'écran d'accueil ;
2. créer un médicament « Levothyrox 75 µg », comprimé, 30 par boîte, stock 10, tous les jours, ½ le matin ;
3. dans Préférences, régler le Matin à deux minutes dans le futur ;
4. attendre la notification, vérifier qu'elle porte le nom du médicament et les deux actions ;
5. appuyer sur *Pris* et vérifier dans Stock que le reste est passé à 9½ ;
6. créer un second médicament marqué critique et vérifier que son rappel ouvre l'alarme plein écran ;
7. dans Stock, vérifier les jours restants et la date d'alerte ;
8. ouvrir Semaine et vérifier la grille.

S'il n'y a ni appareil ni émulateur disponible, noter le contrôle comme non effectué et le signaler.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat(app): preferences, navigation et demande d autorisations"
```

---

## Notes d'exécution

**Ordre des tâches.** Les tâches 1 à 7 construisent le module `:domain` et ne dépendent d'aucune décision Android : elles peuvent être exécutées d'affilée. Les tâches 8 à 14 sont la couche technique ; les tâches 15 à 19 sont les écrans, et chacune est indépendante des autres écrans — seule la tâche 19 les assemble.

**Fichiers d'attente.** Deux fichiers sont créés vides puis remplis plus tard, pour que chaque tâche compile seule : `RecepteurRappel` (créé en tâche 11, rempli en tâche 12) et `AlarmeActivity` (créée en tâche 12, remplie en tâche 13). Ne pas les oublier.

**Vérifications sur appareil.** Les tâches 13 et 19 comportent un contrôle manuel qui exige un appareil ou un émulateur. Tous les autres tests tournent en JVM via Robolectric : `./gradlew :domain:test :app:testDebugUnitTest` doit suffire à valider le travail.
