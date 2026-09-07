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
