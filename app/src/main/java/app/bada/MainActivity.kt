package app.bada

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private companion object {
        const val REQ_EXPORT_BACKUP = 1
        const val REQ_IMPORT_BACKUP = 2
        const val REQ_IMPORT_GPX = 3
        const val MENU_EXPORT = 1
        const val MENU_IMPORT = 2
        const val MENU_GPX = 3
        const val MENU_UPDATES = 4
        const val MENU_CHECK = 5
        const val MENU_STATS = 6
        const val MENU_BATTERY = 7
        const val MENU_AUTOPAUSE = 8
        const val MENU_VOICE = 9
        const val MENU_GOALS = 10
        /** Scorciatoie dell'icona (pressione lunga): avviano subito la registrazione. */
        const val ACTION_START_RUN = "app.bada.START_RUN"
        const val ACTION_START_BIKE = "app.bada.START_BIKE"
    }

    private var pendingRun = true
    private var items: List<Workout> = emptyList()
    /** Righe della lista: intestazioni di mese e uscite. */
    private var rows: List<Any> = emptyList()
    /** Filtro della lista: null = tutte, true = corsa, false = bici. Ricordato tra un avvio e l'altro. */
    private var filter: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.run).setOnClickListener { start(true) }
        findViewById<Button>(R.id.bike).setOnClickListener { start(false) }
        findViewById<Button>(R.id.back_to_recording).setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
        findViewById<ListView>(R.id.list).setOnItemClickListener { _, _, pos, _ ->
            val w = rows.getOrNull(pos) as? Workout ?: return@setOnItemClickListener
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_FILE, w.file))
        }
        findViewById<View>(R.id.menu).setOnClickListener(::showMenu)
        findViewById<View>(R.id.week_card).setOnClickListener { openStats() }
        filter = when (getPreferences(MODE_PRIVATE).getString("filter", "all")) { "run" -> true; "bike" -> false; else -> null }
        findViewById<View>(R.id.filter_all).setOnClickListener { setFilter(null) }
        findViewById<View>(R.id.filter_run).setOnClickListener { setFilter(true) }
        findViewById<View>(R.id.filter_bike).setOnClickListener { setFilter(false) }
        stat(R.id.week_distance, "Distanza")
        stat(R.id.week_time, "Tempo")
        stat(R.id.week_count, "Attività")
        Updater.check(this, force = false) { r, _ -> showUpdate(r) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Esito di un aggiornamento, oppure un GPX aperto o condiviso con Offtrack da un'altra app. */
    private fun handleIntent(intent: Intent?) {
        handleInstallStatus(intent)
        when (intent?.action) {
            ACTION_START_RUN, ACTION_START_BIKE -> {
                val run = intent.action == ACTION_START_RUN
                setIntent(Intent(this, MainActivity::class.java))
                if (TrackingService.file == null) start(run) else startActivity(Intent(this, RecordActivity::class.java))
                return
            }
        }
        @Suppress("DEPRECATION")
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> return
        }
        if (uris.isNotEmpty()) importGpx(uris)
        setIntent(Intent(this, MainActivity::class.java)) // una rotazione non ripete l'import
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setFilter(f: Boolean?) {
        filter = f
        getPreferences(MODE_PRIVATE).edit().putString("filter", when (f) { true -> "run"; false -> "bike"; null -> "all" }).apply()
        refresh()
    }

    private fun openStats() = startActivity(Intent(this, StatsActivity::class.java).putExtra(StatsActivity.EXTRA_RUN, filter != false))

    /** I riepiloghi si calcolano in background: la prima volta, con molte uscite, ci vuole qualche secondo. */
    private fun refresh() {
        val f = filter
        thread {
            val all = Workouts.all(this)
            val records = Stats.recordCounts(all)
            runOnUiThread { if (!isFinishing && f == filter) show(all.filter { f == null || it.run == f }, all, records) }
        }
    }

    private fun show(list: List<Workout>, all: List<Workout>, records: Map<String, Int>) {
        val recording = TrackingService.file
        items = list
        rows = withMonths(list)
        StatsActivity.selectable(findViewById(R.id.filter_all), filter == null)
        StatsActivity.selectable(findViewById(R.id.filter_run), filter == true)
        StatsActivity.selectable(findViewById(R.id.filter_bike), filter == false)
        findViewById<ListView>(R.id.list).adapter = object : ArrayAdapter<Any>(this, 0, rows) {
            override fun getViewTypeCount() = 2
            override fun getItemViewType(pos: Int) = if (rows[pos] is Month) 0 else 1
            override fun isEnabled(pos: Int) = rows[pos] is Workout

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val row = rows[pos]
                if (row is Month) {
                    val h = convertView ?: layoutInflater.inflate(R.layout.item_month, parent, false)
                    h.findViewById<TextView>(R.id.month).text = row.label
                    h.findViewById<TextView>(R.id.totals).text =
                        "${if (row.count == 1) "1 attività" else "${row.count} attività"}  ·  ${Format.distance(row.m)}"
                    return h
                }
                val v = convertView ?: layoutInflater.inflate(R.layout.item_activity, parent, false)
                val it = row as Workout
                val n = records[it.file] ?: 0
                v.findViewById<TextView>(R.id.badge).apply {
                    visibility = if (n > 0) View.VISIBLE else View.GONE
                    text = if (n == 1) "RECORD" else "$n RECORD"
                }
                v.findViewById<TextView>(R.id.title).text = "${kind(it.run)}  ·  ${date(it.start)}"
                v.findViewById<TextView>(R.id.name).text = it.title
                v.findViewById<TextView>(R.id.distance).text = Format.distance(it.m)
                v.findViewById<TextView>(R.id.stats).text =
                    "${Format.duration(it.ms)}   ${Format.average(it.run, it.m, it.ms)}"
                v.findViewById<RouteView>(R.id.route).setRoute(it.thumb)
                return v
            }
        }
        val empty = findViewById<TextView>(R.id.empty)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (filter == null) "Nessuna attività registrata.\nI dati restano solo su questo telefono."
        else "Nessuna attività di questo tipo."
        findViewById<View>(R.id.start_buttons).visibility = if (recording == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.back_to_recording).visibility = if (recording != null) View.VISIBLE else View.GONE

        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val week = items.filter { it.start >= monday }
        findViewById<TextView>(R.id.week_title).text = "Questa settimana" + when (filter) { true -> " · Corsa"; false -> " · Bici"; null -> "" }
        stat(R.id.week_distance, null, Format.distance(week.sumOf { it.m }))
        stat(R.id.week_time, null, Format.duration(week.sumOf { it.ms }))
        stat(R.id.week_count, null, week.size.toString())
        showGoals(all.filter { it.start >= monday })
    }

    /** Raggruppa per mese, dal più recente: ogni gruppo ha un'intestazione con numero di uscite e km. */
    private fun withMonths(list: List<Workout>): List<Any> {
        val fmt = SimpleDateFormat("LLLL yyyy", Locale.ITALY)
        val out = mutableListOf<Any>()
        for ((label, group) in list.groupBy { fmt.format(Date(it.start)).replaceFirstChar(Char::uppercaseChar) }) {
            out.add(Month(label, group.size, group.sumOf { it.m }))
            out.addAll(group)
        }
        return out
    }

    private class Month(val label: String, val count: Int, val m: Double)

    /** Barre di avanzamento verso gli obiettivi della settimana; se non ce ne sono, l'invito a impostarli. */
    private fun showGoals(week: List<Workout>) {
        val box = findViewById<LinearLayout>(R.id.goals)
        box.removeAllViews()
        val types = listOf(true, false).filter { filter == null || filter == it }
        val withGoal = types.filter { Prefs.goalKm(this, it) > 0 }
        if (withGoal.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "Imposta un obiettivo settimanale  ›"
                setTextColor(getColor(R.color.muted))
                textSize = 13f
                setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
                setOnClickListener { editGoals() }
            })
            return
        }
        for (run in withGoal) {
            val goal = Prefs.goalKm(this, run)
            val km = week.filter { it.run == run }.sumOf { it.m } / 1000
            val v = layoutInflater.inflate(R.layout.item_goal, box, false)
            v.findViewById<TextView>(R.id.goal_label).text =
                if (km >= goal) "Obiettivo ${kind(run).lowercase()} raggiunto" else "Obiettivo ${kind(run).lowercase()}: mancano ${String.format(Locale.ITALY, "%.1f", goal - km)} km"
            v.findViewById<TextView>(R.id.goal_value).text = String.format(Locale.ITALY, "%.1f / %d km", km, goal)
            v.findViewById<ProgressBar>(R.id.goal_bar).progress = (km / goal * 1000).toInt().coerceIn(0, 1000)
            v.setOnClickListener { editGoals() }
            box.addView(v)
        }
    }

    private fun editGoals() {
        val dp = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
        }
        fun field(run: Boolean) = EditText(this).apply {
            hint = "${kind(run)}: km a settimana (vuoto = nessuno)"
            inputType = InputType.TYPE_CLASS_NUMBER
            Prefs.goalKm(this@MainActivity, run).takeIf { it > 0 }?.let { setText(it.toString()) }
        }
        val run = field(true); val bike = field(false)
        box.addView(run); box.addView(bike)
        AlertDialog.Builder(this)
            .setTitle("Obiettivi settimanali")
            .setView(box)
            .setPositiveButton("Salva") { _, _ ->
                Prefs.setGoalKm(this, true, run.text.toString().toIntOrNull() ?: 0)
                Prefs.setGoalKm(this, false, bike.text.toString().toIntOrNull() ?: 0)
                refresh()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun stat(id: Int, label: String?, value: String? = null) {
        val v = findViewById<View>(id)
        label?.let { v.findViewById<TextView>(R.id.label).text = it }
        value?.let { v.findViewById<TextView>(R.id.value).text = it }
    }

    private fun kind(run: Boolean) = if (run) "Corsa" else "Bici"
    private fun date(t: Long) = SimpleDateFormat("EEE d MMM, HH:mm", Locale.ITALY).format(Date(t))

    // --- Menu: backup, import, aggiornamenti ---

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, MENU_STATS, 0, "Statistiche e record")
        menu.menu.add(0, MENU_GOALS, 0, "Obiettivi settimanali")
        menu.menu.add(0, MENU_EXPORT, 1, "Esporta backup")
        menu.menu.add(0, MENU_IMPORT, 2, "Importa backup")
        menu.menu.add(0, MENU_GPX, 3, "Importa GPX")
        menu.menu.add(0, MENU_AUTOPAUSE, 4, "Pausa automatica").setCheckable(true).isChecked = Prefs.autoPause(this)
        menu.menu.add(0, MENU_VOICE, 4, "Avvisi vocali").setCheckable(true).isChecked = Prefs.voice(this)
        menu.menu.add(0, MENU_BATTERY, 4, "Ottimizzazione batteria")
        menu.menu.add(0, MENU_UPDATES, 5, "Aggiornamenti automatici").setCheckable(true).isChecked = Updater.isEnabled(this)
        menu.menu.add(0, MENU_CHECK, 6, "Cerca aggiornamenti  (versione ${BuildConfig.VERSION_NAME})")
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                MENU_EXPORT -> startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                        .putExtra(Intent.EXTRA_TITLE, "offtrack-backup-${LocalDate.now()}.zip"),
                    REQ_EXPORT_BACKUP,
                )
                MENU_IMPORT -> startActivityForResult(openDocument(multiple = false), REQ_IMPORT_BACKUP)
                MENU_GPX -> startActivityForResult(openDocument(multiple = true), REQ_IMPORT_GPX)
                MENU_STATS -> openStats()
                MENU_GOALS -> editGoals()
                MENU_AUTOPAUSE -> Prefs.setAutoPause(this, !Prefs.autoPause(this)).also {
                    toast(if (Prefs.autoPause(this)) "Pausa automatica attiva: si ferma quando ti fermi" else "Pausa automatica disattivata")
                }
                MENU_VOICE -> Prefs.setVoice(this, !Prefs.voice(this)).also {
                    toast(if (Prefs.voice(this)) "Avvisi vocali attivi: ogni km in corsa, ogni 5 km in bici" else "Avvisi vocali disattivati")
                }
                MENU_BATTERY -> if (Battery.isExempt(this)) toast("Offtrack è già esclusa dall'ottimizzazione batteria")
                    else Battery.request(this)
                MENU_UPDATES -> Updater.setEnabled(this, !Updater.isEnabled(this))
                MENU_CHECK -> Updater.check(this, force = true) { r, err ->
                    showUpdate(r)
                    toast(err ?: if (r == null) "Hai già l'ultima versione" else "Disponibile la versione ${r.name}")
                }
            }
            true
        }
        menu.show()
    }

    // "*/*": molti gestori file non riconoscono i tipi MIME di zip e gpx.
    private fun openDocument(multiple: Boolean) = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)

    @Deprecated("API di piattaforma; senza AndroidX non c'è l'alternativa")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null) return
        val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        if (uris.isEmpty()) return
        if (requestCode == REQ_IMPORT_GPX) { importGpx(uris); return }
        thread {
            val msg = try {
                when (requestCode) {
                    REQ_EXPORT_BACKUP -> {
                        contentResolver.openOutputStream(uris.first())!!.use { TrackStore.exportZip(this, it) }
                        "Backup salvato"
                    }
                    REQ_IMPORT_BACKUP -> {
                        val n = contentResolver.openInputStream(uris.first())!!.use { TrackStore.importZip(this, it) }
                        "Ripristinate $n attività"
                    }
                    else -> null
                }
            } catch (e: Exception) {
                "Operazione non riuscita: ${e.message}"
            }
            runOnUiThread { msg?.let(::toast); refresh() }
        }
    }

    private fun importGpx(uris: List<Uri>) = thread {
        var added = 0
        var skipped = 0
        for (u in uris) {
            try {
                val text = contentResolver.openInputStream(u)!!.use { it.bufferedReader().readText() }
                val g = Gpx.read(text)
                if (g != null && TrackStore.save(this, g.events, g.run)) added++ else skipped++
            } catch (e: Exception) {
                skipped++
            }
        }
        val msg = "Importate $added attività" + if (skipped > 0) ", $skipped saltate (già presenti o non valide)" else ""
        runOnUiThread { toast(msg); refresh() }
    }

    // --- Aggiornamenti ---

    private fun showUpdate(r: Updater.Release?) {
        val card = findViewById<View>(R.id.update_card)
        card.visibility = if (r != null) View.VISIBLE else View.GONE
        if (r == null) return
        findViewById<TextView>(R.id.update_text).text = "Aggiornamento disponibile\n${r.name}"
        findViewById<Button>(R.id.update_install).setOnClickListener {
            if (!packageManager.canRequestPackageInstalls()) {
                toast("Consenti a Offtrack di installare app, poi tocca di nuovo Installa")
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return@setOnClickListener
            }
            toast("Download in corso")
            Updater.install(this, r, ::toast)
        }
    }

    private fun handleInstallStatus(intent: Intent?) {
        if (intent?.action != Updater.ACTION_INSTALL_STATUS) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let(::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> toast("Aggiornamento non installato: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "errore"}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // --- Avvio registrazione ---

    private fun start(run: Boolean) {
        pendingRun = run
        // Da Android 12 la posizione precisa va chiesta insieme a quella approssimativa.
        val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) begin() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) begin()
        else toast("Serve il permesso di posizione precisa")
    }

    private fun begin() {
        if (!getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            toast("Attiva la posizione (GPS)")
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        TrackingService.start(this, pendingRun)
        startActivity(Intent(this, RecordActivity::class.java))
    }
}
