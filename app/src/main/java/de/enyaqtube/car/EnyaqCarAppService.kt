package de.enyaqtube.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class EnyaqCarAppService : CarAppService() {

    // Die App wird selbst installiert (nicht über Play Store), daher jeden Host zulassen.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = BrowserScreen(carContext)
    }
}
