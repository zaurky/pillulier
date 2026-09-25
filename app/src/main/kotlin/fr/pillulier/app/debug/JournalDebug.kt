package fr.pillulier.app.debug

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * TEMPORAIRE — journal de diagnostic, a retirer une fois les deux bugs
 * confirmes corriges. Tous ses points d'appel portent le marqueur
 * `JOURNAL-DEBUG` : `grep -rn JOURNAL-DEBUG app/src` les liste tous.
 *
 * Pourquoi un fichier et pas logcat : les deux bugs traques se manifestent le
 * matin, apres une nuit pendant laquelle le telephone n'est pas branche. Le
 * tampon circulaire de logcat aurait deja ecrase la soiree et le passage de
 * minuit — precisement les lignes qui nous interessent.
 *
 * Pourquoi un `object` et pas une dependance injectee : une instrumentation
 * jetable ne doit pas s'inscrire dans les constructeurs. Passer par Hilt
 * obligeait a toucher onze fichiers de test qu'il aurait fallu defaire ensuite ;
 * ici un point d'appel est une ligne, et son retrait aussi. Le prix est un
 * singleton de processus, non injectable et donc non substituable en test — a
 * ne pas imiter dans du code destine a rester.
 *
 * Le fichier vit dans le stockage interne de l'app : aucune permission, ni au
 * manifeste ni a l'execution. Seul le partage ajoute un `FileProvider`.
 */
object JournalDebug {

    private val horodatage = DateTimeFormatter
        .ofPattern("dd/MM HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    /** Nul tant que l'application n'a pas demarre : les tests JVM n'ecrivent rien. */
    @Volatile
    private var contexte: Context? = null

    fun initialiser(contexte: Context) {
        this.contexte = contexte.applicationContext
    }

    private fun fichier(): File? = contexte?.let { File(it.filesDir, NOM) }

    private fun precedent(): File? = contexte?.let { File(it.filesDir, "$NOM.1") }

    /**
     * Synchronise : les rappels ecrivent depuis un `BroadcastReceiver` sur
     * `Dispatchers.IO`, le widget depuis sa propre coroutine et l'ecran depuis
     * le thread principal. Sans verrou, deux lignes s'entrelaceraient.
     */
    @Synchronized
    fun ecrire(categorie: String, message: String) {
        val courant = fichier() ?: return
        runCatching {
            if (courant.length() > TAILLE_MAX) {
                precedent()?.delete()
                courant.renameTo(precedent())
            }
            courant.appendText("${horodatage.format(Instant.now())} [$categorie] $message\n")
        }
    }

    /** Les deux tranches bout a bout, la plus ancienne d'abord. */
    @Synchronized
    fun contenu(): String = buildString {
        precedent()?.takeIf { it.exists() }?.let { append(it.readText()) }
        fichier()?.takeIf { it.exists() }?.let { append(it.readText()) }
    }.ifEmpty { "Journal vide." }

    @Synchronized
    fun effacer() {
        precedent()?.delete()
        fichier()?.delete()
    }

    /**
     * Une copie dans le cache, seul endroit que `chemins_partage.xml` expose :
     * le stockage interne reste inaccessible aux autres applications.
     */
    @Synchronized
    fun uriDePartage(): Uri {
        val app = requireNotNull(contexte) { "journal non initialise" }
        val dossier = File(app.cacheDir, "journal").apply { mkdirs() }
        val copie = File(dossier, NOM).apply { writeText(contenu()) }
        return FileProvider.getUriForFile(app, "${app.packageName}.fichiers", copie)
    }

    private const val NOM = "journal-debug.log"
    private const val TAILLE_MAX = 512L * 1024
}
