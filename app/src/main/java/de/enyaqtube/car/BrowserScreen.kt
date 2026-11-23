package de.enyaqtube.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.enyaqtube.Prefs

/**
 * Hauptbildschirm im Auto: YouTube auf der ganzen Fläche. Die Bedienknöpfe zeichnet
 * EnyaqTube selbst oben links; von Android Auto bleiben nur "Suche" (braucht die
 * Android-Auto-Tastatur) und der Verschiebe-Knopf.
 */
class BrowserScreen(carContext: CarContext) : Screen(carContext) {

    private val browser: CarBrowser = CarBrowser(carContext) { openSearch() }

    private fun openSearch() = screenManager.push(SearchScreen(carContext, browser))

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(browser)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                browser.release()
            }
        })
    }

    private fun text(title: String, onClick: () -> Unit) =
        Action.Builder().setTitle(title).setOnClickListener(onClick).build()

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(text("Suche") { openSearch() })
            .build()

        val playerActions = ActionStrip.Builder()
            .addAction(Action.PAN)
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
