package app.bada

import android.Manifest
import android.app.Activity
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
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private class Item(
        val file: File, val run: Boolean, val start: Long, val m: Double, val ms: Long, val route: List<List<Fix>>,
    )

    private companion object {
        const val REQ_EXPORT_BACKUP = 1
        const val REQ_IMPORT_BACKUP = 2
        const val REQ_IMPORT_GPX = 3
        const val MENU_EXPORT = 1
        const val MENU_IMPORT = 2
        const val MENU_GPX = 3
        const val MENU_UPDATES = 4
        const val MENU_CHECK = 5
    }

    private var pendingRun = true
    private var items: List<Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.run).setOnClickListener { start(true) }
        findViewById<Button>(R.id.bike).setOnClickListener { start(false) }
        findViewById<Button>(R.id.back_to_recording).setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
        findViewById<ListView>(R.id.list).setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_FILE, items[pos].file.path))
        }
        findViewById<View>(R.id.menu).setOnClickListener(::showMenu)
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

    private fun refresh() {
        val recording = TrackingService.file
        items = TrackStore.list(this).filter { it != recording }.map(::item)
        findViewById<ListView>(R.id.list).adapter = object : ArrayAdapter<Item>(this, 0, items) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_activity, parent, false)
                val it = items[pos]
                v.findViewById<TextView>(R.id.title).text = "${kind(it.run)}  ·  ${date(it.start)}"
                v.findViewById<TextView>(R.id.distance).text = Format.distance(it.m)
                v.findViewById<TextView>(R.id.stats).text =
                    "${Format.duration(it.ms)}   ${Format.average(it.run, it.m, it.ms)}"
                v.findViewById<RouteView>(R.id.route).setRoute(it.route)
                return v
            }
        }
        findViewById<TextView>(R.id.empty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.start_buttons).visibility = if (recording == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.back_to_recording).visibility = if (recording != null) View.VISIBLE else View.GONE

        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val week = items.filter { it.start >= monday }
        stat(R.id.week_distance, null, Format.distance(week.sumOf { it.m }))
        stat(R.id.week_time, null, Format.duration(week.sumOf { it.ms }))
        stat(R.id.week_count, null, week.size.toString())
    }

    private fun stat(id: Int, label: String?, value: String? = null) {
        val v = findViewById<View>(id)
        label?.let { v.findViewById<TextView>(R.id.label).text = it }
        value?.let { v.findViewById<TextView>(R.id.value).text = it }
    }

    private fun item(f: File): Item {
        val ev = TrackStore.read(f)
        // Per la miniatura bastano ~100 punti a segmento: tiene bassa la memoria con molte uscite.
        val route = Track.segments(ev).map { seg ->
            val step = seg.size / 100 + 1
            seg.filterIndexed { i, _ -> i % step == 0 || i == seg.lastIndex }
        }
        return Item(
            f, TrackStore.isRun(f), TrackStore.startMillis(f), Track.distanceM(ev),
            if (ev.isEmpty()) 0L else Track.activeMs(ev), route,
        )
    }

    private fun kind(run: Boolean) = if (run) "Corsa" else "Bici"
    private fun date(t: Long) = SimpleDateFormat("EEE d MMM, HH:mm", Locale.ITALY).format(Date(t))

    // --- Menu: backup, import, aggiornamenti ---

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, MENU_EXPORT, 0, "Esporta backup")
        menu.menu.add(0, MENU_IMPORT, 1, "Importa backup")
        menu.menu.add(0, MENU_GPX, 2, "Importa GPX")
        menu.menu.add(0, MENU_UPDATES, 3, "Aggiornamenti automatici").setCheckable(true).isChecked = Updater.isEnabled(this)
        menu.menu.add(0, MENU_CHECK, 4, "Cerca aggiornamenti  (versione ${BuildConfig.VERSION_NAME})")
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                MENU_EXPORT -> startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                        .putExtra(Intent.EXTRA_TITLE, "offtrack-backup-${LocalDate.now()}.zip"),
                    REQ_EXPORT_BACKUP,
                )
                MENU_IMPORT -> startActivityForResult(openDocument(multiple = false), REQ_IMPORT_BACKUP)
                MENU_GPX -> startActivityForResult(openDocument(multiple = true), REQ_IMPORT_GPX)
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
