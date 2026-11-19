package de.enyaqtube.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.enyaqtube.Prefs
import de.enyaqtube.R

/** Hauptbildschirm im Auto: YouTube auf der ganzen Fläche, Knöpfe am Rand. */
class BrowserScreen(carContext: CarContext) : Screen(carContext) {

    private val browser = CarBrowser(carContext)
    private val drivingLock = DrivingLock(carContext) { locked ->
        browser.setLocked(locked)
        if (locked) CarToast.makeText(carContext, "Fahrsperre aktiv", CarToast.LENGTH_SHORT).show()
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(browser)
                drivingLock.start()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                drivingLock.stop()
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                browser.release()
            }
        })
    }

    private fun text(title: String, onClick: () -> Unit) =
        Action.Builder().setTitle(title).setOnClickListener(onClick).build()

    private fun icon(res: Int, onClick: () -> Unit) =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener(onClick)
            .build()

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(text("Abos") { browser.subscriptions() })
            .addAction(text("Suche") { screenManager.push(SearchScreen(carContext, browser)) })
            .addAction(text("Zurück") { browser.back() })
            .addAction(text("Start") { browser.home() })
            .build()

        val playerActions = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(icon(R.drawable.ic_replay) { browser.seek(-10) })
            .addAction(icon(R.drawable.ic_play_pause) { browser.playPause() })
            .addAction(icon(R.drawable.ic_forward) { browser.seek(10) })
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actions)
            .setMapActionStrip(playerActions)
            .setPanModeListener { }
            .build()
    }
}

/** Suche über die Tastatur von Android Auto (nur im Stand verfügbar). */
class SearchScreen(carContext: CarContext, private val browser: CarBrowser) : Screen(carContext) {

    private val prefs = Prefs(carContext)

    private fun submit(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        prefs.addSearch(q)
        browser.search(q)
        screenManager.pop()
    }

    override fun onGetTemplate(): Template {
        val recent = ItemList.Builder().setNoItemsMessage("Suchbegriff eingeben")
        prefs.recentSearches.forEach { q ->
            recent.addItem(Row.Builder().setTitle(q).setOnClickListener { submit(q) }.build())
        }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) = submit(searchText)
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("YouTube durchsuchen")
            .setShowKeyboardByDefault(true)
            .setItemList(recent.build())
            .build()
    }
}
