# Séparation de l'édition — traitement et stock

L'écran d'édition d'un médicament mélange deux sujets qui n'ont ni le même rythme ni le
même lecteur : ce qu'on prescrit, et ce qu'il reste dans le placard. On les sépare en
deux écrans, chacun ouvert depuis l'onglet qui le concerne.

## Le problème

`EditionEcran` porte aujourd'hui quatorze contrôles d'un seul tenant : nom, dosage,
forme, unités par boîte, stock actuel, prise critique, type d'ordonnance, rythme,
intervalle, jours de semaine, début et fin de traitement, date d'effet, doses par
moment, seuil d'alerte. Un seul bouton « Enregistrer » écrit l'ensemble.

Deux conséquences.

**Le formulaire ne dit pas ce qu'il fait.** Changer le nombre d'unités par boîte et
changer le rythme d'une prescription sont deux gestes sans rapport, à des fréquences
sans rapport — l'un quand la pharmacie change de conditionnement, l'autre quand le
médecin change l'ordonnance. Le formulaire les présente sur la même colonne.

**L'écriture est indivisible.** `EnregistrerMedicament` écrit la ligne `medicament`
*et* une version d'ordonnance, dans cet ordre, à chaque appel. Corriger un stock
recompté passe donc par le chemin qui versionne les prescriptions. Rien ne casse
aujourd'hui — `enregistrerVersion` compare par `prescritLaMemeChose` et n'écrit pas une
version identique — mais la propreté de l'historique repose sur une comparaison
d'égalité qu'il faut penser à tenir à jour.

### Ce qui n'est pas le problème

Les deux onglets du bas, « Traitement » et « Stock », existent déjà et ne changent pas.
Ce chantier ne touche que les écrans qu'ils ouvrent.

La liste de l'onglet Traitement affiche sous chaque nom « 24 comprimés en stock ·
critique ». Elle reste telle quelle : séparer l'édition n'oblige pas à séparer les
listes, et le stock reste une information utile en un coup d'œil. Hors périmètre.

## Le découpage

Le partage demandé tombe sur la frontière du modèle. `Medicament` porte nom, dosage,
forme, unités par boîte, stock et seuils d'alerte — exactement le versant stock.
`Ordonnance` porte type, rythme, dates et doses — exactement le versant traitement.

| Écran | Champs |
|---|---|
| Traitement | prise critique, type d'ordonnance, rythme, intervalle, jours de semaine, fin du traitement, date d'effet, doses par moment, archivage |
| Stock | nom, dosage, forme, unités par boîte, stock actuel, seuil d'alerte |

Un seul champ ne suit pas la frontière du modèle : `critique` est porté par
`Medicament` mais commande l'alarme plein écran, donc le traitement. Il reste côté
traitement à l'écran, et l'écran Stock le reporte sans le montrer (voir « Le piège du
`@Update` »).

Le seuil d'alerte reste conditionné au type de prescription comme aujourd'hui : en
jours si l'ordonnance est planifiée, en unités restantes si elle est à la demande.
L'écran Stock lit donc l'ordonnance en vigueur, sans l'éditer.

« Archiver » reste côté traitement : c'est le traitement qu'on arrête.

## La création reste d'un bloc

Le « + » de l'onglet Traitement continue d'ouvrir un formulaire complet — identité,
stock et prescription — et d'écrire le tout d'un coup. Seule la **modification** se
scinde.

Scinder aussi la création obligerait à laisser naître un médicament sans prescription,
ou une prescription sans médicament. `EnregistrerMedicament` exige aujourd'hui un nom
non vide et au moins une unité par boîte ; une ordonnance planifiée exige au moins une
dose. Ces invariants ne tiennent que si la création est atomique.

À l'écran, les champs stock disparaissent de `EditionEcran` quand `id != 0L`, selon
l'idiome déjà en place dans ce fichier pour « Début du traitement » et « S'applique à
partir du ».

## Approche retenue : scinder les cas d'usage

Trois classes dans `app/usecase/EnregistrerMedicament.kt`, là où vit déjà
`EnregistrerMedicament`.

```
EnregistrerFicheMedicament(medicament): Long
    require(medicament.nom.isNotBlank())
    require(medicament.unitesParBoite > 0)
    id = medicaments.enregistrer(medicament)
    reArmerRappels() ; rafraichirWidget()
    return id

EnregistrerPrescription(
    medicamentId, type, rythme, dateDebut, dateFin, doses, dateEffet)
    require(dateFin == null || !dateFin.isBefore(dateDebut))
    si PLANIFIEE :
        require(doses.isNotEmpty())
        require(doses.all { it.dose > 0.0 })
        require(moments distincts)
    ordonnances.enregistrerVersion(medicamentId, ordonnance, doses, dateEffet)
    reArmerRappels() ; rafraichirWidget()

EnregistrerMedicament(medicament, type, rythme, dateDebut, dateFin, doses, dateEffet): Long
    require(!dateEffet.isBefore(dateDebut))
    id = ficheMedicament(medicament)
    prescription(id, type, rythme, dateDebut, dateFin, doses, dateEffet)
    return id
```

Chaque écran écrit exactement ce qu'il édite. Deux écrans ouverts en parallèle ne
peuvent plus s'écraser l'un l'autre, et renommer un médicament ne passe plus par le
code qui versionne les prescriptions.

`EnregistrerMedicament` garde sa signature publique : la création l'appelle comme
avant, et son test existant reste le filet qui dit que la composition n'a rien perdu.

### Le garde de date d'effet remonte dans la composition

`require(!dateEffet.isBefore(dateDebut))` ne vaut aujourd'hui qu'à la création, testé
par `medicament.id == 0L`. Le commentaire en place explique pourquoi : à la
modification, `dateDebut` porte le début de la version en vigueur, et l'opposer à la
date d'effet interdirait la correction rétroactive que `supprimerVersionsDepuis` existe
précisément pour servir.

`EnregistrerPrescription` ne reçoit plus de `Medicament`, donc plus de `id == 0L` à
tester. Plutôt que de lui passer un `creation: Boolean` — un paramètre dont l'oubli
rendrait le garde silencieusement inopérant — le garde remonte dans
`EnregistrerMedicament`, seul chemin de création. `EnregistrerPrescription` n'a alors
qu'une règle de date, celle qui vaut toujours.

### Le double réarmement à la création est assumé

À la création, `EnregistrerMedicament` appelle les deux feuilles, donc `reArmerRappels()`
et `rafraichirWidget()` deux fois. C'est correct — le réarmement est idempotent, il
reconstruit la fenêtre depuis `medicaments.tous()` — mais redondant.

L'alternative, un paramètre `rearmer: Boolean` sur les feuilles, rend celles-ci
conditionnellement sûres : un appelant qui oublie le drapeau laisse des alarmes
périmées. Un appel en trop sur une action manuelle rare coûte moins qu'une classe de
bug silencieux.

### Approche écartée : garder un seul cas d'usage

L'écran Stock chargerait aussi l'ordonnance en vigueur et la repasserait inchangée à
`EnregistrerMedicament` ; `prescritLaMemeChose` éviterait d'écrire une version inutile.

Moins de code déplacé, aucun test touché. Mais l'écran Stock se mettrait à dépendre
d'une prescription qu'il n'édite pas, et la propreté de l'historique reposerait sur une
comparaison d'égalité : le jour où un champ s'ajoute à `Ordonnance` sans entrer dans
`prescritLaMemeChose`, renommer un médicament se mettrait silencieusement à créer des
versions fantômes. Le découpage demandé à l'écran est déjà celui du modèle ; le faire
descendre dans les cas d'usage supprime la classe de bug au lieu de la contourner.

## Écrans et navigation

| Route | Écran | Ouvert depuis | Contenu |
|---|---|---|---|
| `medicament/{id}` | `EditionEcran` | onglet Traitement : ligne de liste, et « + » | `id == 0` : formulaire complet. Sinon : versant traitement seul. |
| `stock/{id}` | `EditionStockEcran` | onglet Stock : carte cliquable | versant stock |

`EditionEcran` garde son nom : il porte la création complète, et l'appeler
`EditionTraitementEcran` mentirait sur la moitié de son travail. Le nouvel écran vit
dans `ui/stock/`, à côté du `StockViewModel` qu'il prolonge.

`StockEcran` change sur trois points :

- la `Card` devient cliquable et appelle `surSelection(ligne.medicamentId)` ;
- le champ « Recompté » et son bouton « OK » disparaissent — le stock recompté se
  saisit désormais dans le formulaire, un seul endroit pour une même valeur ;
- « +1 boîte » reste : c'est un geste d'une touche, sans saisie, qui n'a rien à gagner
  à passer par un écran.

### `CorrigerStock` perd son dernier appelant

La liste était le seul endroit qui appelait `CorrigerStock`. L'écran Stock ne le
reprend pas : il édite la fiche entière, donc il écrit par `EnregistrerFicheMedicament`,
dont le `@Update` porte déjà `stockUnites`. Passer en plus par `definirStock` ferait
deux écritures concurrentes de la même colonne dans le même geste.

`CorrigerStock` et son test deviennent donc du code mort. Deux lectures défendables :

- **Les retirer**, avec `MedicamentDao.definirStock` si plus personne ne l'appelle. Le
  dépôt ne garde que les chemins qui servent.
- **Les garder**, en pariant sur un futur geste de recomptage rapide qui voudra une
  écriture ciblée plutôt qu'un `@Update` de toute la ligne.

À trancher avant l'implémentation. `AjouterBoite` n'est pas concerné : « +1 boîte »
reste sur la carte et continue d'appeler `ajusterStock`.

## ViewModels

### `EditionViewModel`

`EtatEdition` garde tous ses champs : la création en a besoin. Seul le chemin
d'écriture se dédouble.

```
enregistrer()
    si id == 0L  -> EnregistrerMedicament(...)        // inchange
    sinon        -> EnregistrerPrescription(id, ...)  // le stock n est plus ecrit
```

Les deux gardes de saisie — « Unités par boîte : nombre illisible », « Stock : nombre
illisible » — passent sous `id == 0L`. En modification, ces champs viennent de la base
et ne sont plus exposés au clavier.

Tout le reste ne bouge pas : confirmation d'effacement de versions, comparaison par
`prescritLaMemeChose`, date d'effet, archivage, garde `dejaCharge`.

### `EditionStockViewModel`

Nouveau, dans `ui/stock/`.

```
data class EtatEditionStock(
    id, nom, dosage, forme,
    unitesParBoite, stockUnites,
    seuilAlerteJours, seuilAlerteUnites,
    aLaDemande,              // lu sur l ordonnance en vigueur : decide quel seuil s affiche
    critique, archiveLe,     // jamais edites ici, reportes tels quels
    erreur, enregistre,
)
```

Il charge le médicament par `DepotMedicaments.parId` et l'ordonnance en vigueur par
`DepotOrdonnances.enVigueur`, dont il ne retient que le type. Même garde `dejaCharge`
que `EditionViewModel`, pour la même raison : la rotation relance le `LaunchedEffect`
et écraserait la saisie en cours.

L'enregistrement valide les deux champs numériques — un champ non vide qui ne se lit
pas est une erreur de saisie, pas un zéro — puis appelle `EnregistrerFicheMedicament`.

### Le piège du `@Update`

`DepotMedicaments.enregistrer` fait un `@Update` qui réécrit la ligne entière. Tout
champ absent de l'objet `Medicament` transmis repart à sa valeur par défaut.

`EtatEdition` porte déjà `archiveLe` pour cette seule raison, commentaire à l'appui.
Séparer l'édition double le nombre d'endroits où le piège se tend : `EtatEditionStock`
doit porter `critique` **et** `archiveLe` bien qu'aucun des deux ne soit éditable sur
cet écran. Un écran Stock qui les oublierait désarchiverait un médicament et éteindrait
son alarme critique à chaque correction de stock.

## Tests

En TDD, dans cet ordre.

1. **`EnregistrerFicheMedicamentTest`** et **`EnregistrerPrescriptionTest`** — les
   validations déménagées, vérifiées là où elles vivent désormais.
2. **Les deux non-régressions qui sont la raison d'être du chantier** : renommer depuis
   l'écran Stock ne crée aucune nouvelle version d'ordonnance ; modifier la
   prescription ne touche pas `stockUnites`.
3. **`EditionStockViewModelTest`** — chargement, report de `critique` et `archiveLe`,
   choix du seuil selon `aLaDemande`, erreurs de saisie numérique.
4. **`EditionViewModelTest`** — à reprendre : en modification il n'écrit plus la ligne
   `medicament`.
5. **`EnregistrerMedicamentTest`** — doit passer inchangé. C'est le filet qui dit que la
   composition n'a rien perdu.
