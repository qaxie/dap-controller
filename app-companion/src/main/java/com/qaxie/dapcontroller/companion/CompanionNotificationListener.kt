package com.qaxie.dapcontroller.companion

import android.service.notification.NotificationListenerService

// Sole purpose: granting getActiveSessions() access once the user enables
// Notification Access for this app in system settings.
class CompanionNotificationListener : NotificationListenerService()
