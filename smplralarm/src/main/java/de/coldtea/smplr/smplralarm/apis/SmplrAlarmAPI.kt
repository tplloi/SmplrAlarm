package de.coldtea.smplr.smplralarm.apis

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.coldtea.smplr.smplralarm.extensions.getTimeExactForAlarmInMilliseconds
import de.coldtea.smplr.smplralarm.models.*
import de.coldtea.smplr.smplralarm.receivers.AlarmNotification
import de.coldtea.smplr.smplralarm.receivers.AlarmReceiver
import de.coldtea.smplr.smplralarm.receivers.SmplrAlarmReceiverObjects.Companion.SMPLR_ALARM_RECEIVER_INTENT_ID
import de.coldtea.smplr.smplralarm.receivers.SmplrAlarmReceiverObjects.Companion.alarmNotification
import de.coldtea.smplr.smplralarm.repository.AlarmNotificationRepository
import de.coldtea.smplr.smplralarm.services.AlarmService
import kotlinx.coroutines.*
import timber.log.Timber
import java.lang.IllegalArgumentException
import java.util.Calendar
import kotlin.math.absoluteValue

typealias AlarmRingEvent = (Int) -> Unit

class SmplrAlarmAPI(val context: Context) {

    //region properties

    private var hour = -1
    private var min = -1
    private var weekdays: List<WeekDays> = listOf()
    private var isActive: Boolean = true

    private var requestCode = -1
    private var intent: Intent? = null
    private var fullScreenIntent: Intent? = null

    private var notificationChannel: NotificationChannelItem? = null
    private var notification: NotificationItem? = null

    private var alarmRingEvent: AlarmRingEvent? = null

    private var requestAPI: SmplrAlarmListRequestAPI? = null

    private val alarmService by lazy { AlarmService(context) }

    //endregion

    // region computed properties

    private val alarmNotificationRepository: AlarmNotificationRepository by lazy {
        AlarmNotificationRepository(context)
    }

    // endregion

    // region DSL setters
    fun hour(hour: () -> Int) {
        this.hour = hour()
    }

    fun min(min: () -> Int) {
        this.min = min()
    }

    fun requestCode(requestCode: () -> Int) {
        this.requestCode = requestCode()
    }

    fun intent(intent: () -> Intent) {
        this.intent = intent()
    }

    fun fullScreenIntent(fullScreenIntent: () -> Intent) {
        this.fullScreenIntent = fullScreenIntent()
    }

    fun notificationChannel(notificationChannel: () -> NotificationChannelItem) {
        this.notificationChannel = notificationChannel()
    }

    fun notification(notification: () -> NotificationItem) {
        this.notification = notification()
    }

    fun onAlarmRings(alarmRingEvent: AlarmRingEvent) {
        this.alarmRingEvent = alarmRingEvent
    }

    fun weekdays(lambda: WeekDaysAPI.() -> Unit) {
        weekdays = WeekDaysAPI().apply(lambda).getWeekDays()
    }

    fun isActive(isActive: () -> Boolean) {
        this.isActive = isActive()
    }

    fun requestAPI(requestAPI: () -> SmplrAlarmListRequestAPI) {
        this.requestAPI = requestAPI()
    }

    // endregion

    // region functionality

    internal fun setAlarm(): Int {

        requestCode = getUniqueIdBasedNow()
        Timber.v("SmplrAlarm.AlarmManager.setAlarm: $requestCode -- $hour:$min")

        val notifiactionBuilderItem = createAlarmNotification()

        CoroutineScope(Dispatchers.IO).launch {
            saveAlarmNotificationToDatabase(notifiactionBuilderItem)
            requestAPI?.requestAlarmList()
        }

        alarmService.setAlarm(requestCode, hour, min, weekdays)

        return requestCode
    }

    internal fun renewMissingAlarms() = CoroutineScope(Dispatchers.IO).launch {
        val notificationRepository = AlarmNotificationRepository(context)
        try {
            val alarmNotifications = notificationRepository.getAllAlarmNotifications()

            alarmNotifications
                .filter { it.isActive && !alarmService.alarmExist(it.alarmNotificationId)}
                .map {
                    alarmService.renewAlarm(it)
                }
        } catch (ex: Exception) {
            Timber.e("SmplrAlarmApp.SmplrAlarmManager.updateRepeatingAlarm: $ex ")
        }
    }

    internal fun updateRepeatingAlarm() {
        if (requestCode == -1) return

        alarmService.cancelAlarm(requestCode)
        val updatedActivation = isActive

        CoroutineScope(Dispatchers.IO).launch {
            val notificationRepository = AlarmNotificationRepository(context)
            try {
                val alarmNotification = notificationRepository.getAlarmNotification(requestCode)

                val updatedHour = if (hour == -1) alarmNotification.hour else hour
                val updatedMinute = if (min == -1) alarmNotification.min else min
                val updatedWeekdays =
                    if (weekdays.isEmpty()) alarmNotification.weekDays else weekdays

                updateRepeatingAlarmNotification(
                    requestCode,
                    updatedHour,
                    updatedMinute,
                    updatedWeekdays,
                    updatedActivation
                )
                if (updatedActivation) alarmService.setAlarm(
                    requestCode,
                    updatedHour,
                    updatedMinute,
                    updatedWeekdays
                )

                requestAPI?.requestAlarmList()
            } catch (ex: IllegalArgumentException) {
                Timber.e("SmplrAlarmApp.SmplrAlarmManager.updateRepeatingAlarm: The alarm intended to be removed does not exist! ")
            } catch (ex: Exception) {
                Timber.e("SmplrAlarmApp.SmplrAlarmManager.updateRepeatingAlarm: $ex ")
            }
        }
    }

    internal fun updateSingleAlarm() {
        if (requestCode == -1) return

        alarmService.cancelAlarm(requestCode)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notificationRepository = AlarmNotificationRepository(context)
                val alarmNotification = notificationRepository.getAlarmNotification(requestCode)
                val updatedHour = if (hour == -1) alarmNotification.hour else hour
                val updatedMinute = if (min == -1) alarmNotification.min else min
                val daysToSkip =
                    if (alarmNotification.hour == updatedHour && alarmNotification.min == updatedMinute) 1 else 0

                updateSingleAlarmNotification(requestCode, updatedHour, updatedMinute, isActive)
                if (isActive) alarmService.setAlarm(
                    requestCode,
                    updatedHour,
                    updatedMinute,
                    listOf(),
                    daysToSkip
                )

                requestAPI?.requestAlarmList()
            } catch (ex: IllegalArgumentException) {
                Timber.e("SmplrAlarmApp.SmplrAlarmManager.updateRepeatingAlarm: The alarm intended to be removed does not exist! ")
            } catch (ex: Exception) {
                Timber.e("SmplrAlarmApp.SmplrAlarmManager.updateRepeatingAlarm: $ex ")
            }
        }
    }

    internal fun removeAlarm() {
        alarmService.cancelAlarm(requestCode)

        CoroutineScope(Dispatchers.IO).launch {
            deleteAlarmNotificationFromDatabase()
            requestAPI?.requestAlarmList()
        }
    }

    private fun createAlarmNotification() = AlarmNotification(
        requestCode,
        hour,
        min,
        weekdays,
        notificationChannel
            ?: ChannelManagerAPI().build(),
        notification
            ?: AlarmNotificationAPI().build(),
        intent,
        fullScreenIntent,
        true,
        alarmRingEvent
    )

    private suspend fun saveAlarmNotificationToDatabase(notificationBuilderItem: AlarmNotification) {
        try {
            alarmNotificationRepository.insertAlarmNotification(notificationBuilderItem)
            alarmNotification.add(notificationBuilderItem)
        } catch (exception: Exception) {
            Timber.e("SmplrAlarm.AlarmManager.saveAlarmNotificationToDatabase: Alarm Notification could not be saved to the database --> $exception")
        }
    }

    private suspend fun updateSingleAlarmNotification(
        alarmNotificationId: Int,
        hour: Int?,
        min: Int?,
        isActive: Boolean?
    ) {
        try {
            alarmNotificationRepository.updateSingleAlarmNotification(
                alarmNotificationId,
                hour,
                min,
                isActive
            )
        } catch (exception: Exception) {
            Timber.e("SmplrAlarm.AlarmManager.saveAlarmNotificationToDatabase: Alarm Notification could not be updated to the database --> $exception")
        }
    }

    private suspend fun updateRepeatingAlarmNotification(
        alarmNotificationId: Int,
        hour: Int?,
        min: Int?,
        weekDays: List<WeekDays>?,
        isActive: Boolean?
    ) {

        try {
            alarmNotificationRepository.updateRepeatingAlarmNotification(
                alarmNotificationId,
                hour,
                min,
                weekDays,
                isActive
            )
        } catch (exception: Exception) {
            Timber.e("SmplrAlarm.AlarmManager.saveAlarmNotificationToDatabase: Alarm Notification could not be updated to the database --> $exception")
        }
    }

    private suspend fun deleteAlarmNotificationFromDatabase() {
        try {
            alarmNotificationRepository.deleteAlarmNotification(requestCode)
        } catch (exception: Exception) {
            Timber.e("SmplrAlarm.AlarmManager.saveAlarmNotificationToDatabase: Alarm Notification with id $requestCode could not be removed from the database --> $exception")
        }
    }

    private fun getUniqueIdBasedNow() = System.currentTimeMillis().toInt().absoluteValue


    // endregion
}
