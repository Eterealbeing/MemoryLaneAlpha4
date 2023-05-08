package me.blog.korn123.easydiary.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.DriveScopes
import me.blog.korn123.commons.utils.DateUtils
import me.blog.korn123.commons.utils.EasyDiaryUtils
import me.blog.korn123.easydiary.R
import me.blog.korn123.easydiary.activities.BaseDevActivity
import me.blog.korn123.easydiary.activities.DiaryMainActivity
import me.blog.korn123.easydiary.extensions.config
import me.blog.korn123.easydiary.extensions.createBackupContentText
import me.blog.korn123.easydiary.extensions.pendingIntentFlag
import me.blog.korn123.easydiary.extensions.reExecuteGmsBackup
import me.blog.korn123.easydiary.fragments.SettingsScheduleFragment
import me.blog.korn123.easydiary.helper.*
import me.blog.korn123.easydiary.models.ActionLog
import me.blog.korn123.easydiary.models.Alarm
import java.io.File
import java.util.*

class FullBackupService : Service() {
    private lateinit var mPhotoPath: String
    private lateinit var mWorkingFolderId: String
    private lateinit var mNotificationBuilder: NotificationCompat.Builder
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mDriveServiceHelper: DriveServiceHelper
    private var mInProcessJob = true
    private var workStatusList = arrayListOf<WorkStatus>()

    data class WorkStatus(
            var localDeviceFileCount: Int = 0, var duplicateFileCount: Int = 0, var successCount: Int = 0, var failCount: Int = 0,
            var targetFilenamesCursor: Int = 0, var remoteDriveFileNames: ArrayList<String> = arrayListOf(), var targetFilenames: ArrayList<String> = arrayListOf(),
            var isDone: Boolean = false
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "onCreate", "INFO", "start"), this)
        val googleSignInAccount: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(this)
        val credential: GoogleAccountCredential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = googleSignInAccount?.account
        val googleDriveService: com.google.api.services.drive.Drive = com.google.api.services.drive.Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
                .setApplicationName(getString(R.string.app_name))
                .build()
        mDriveServiceHelper = DriveServiceHelper(applicationContext, googleDriveService)
        mNotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationBuilder = NotificationCompat.Builder(applicationContext, "${NOTIFICATION_CHANNEL_ID}_alarm")
        mPhotoPath = "${EasyDiaryUtils.getApplicationDataDirectory(this)}$DIARY_PHOTO_DIRECTORY"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel("${NOTIFICATION_CHANNEL_ID}_alarm", getString(R.string.notification_channel_name_alarm), importance)
            mChannel.description = NOTIFICATION_CHANNEL_DESCRIPTION
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "onCreate", "INFO", "end"), this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "onStartCommand", "INFO", "start"), this)
        mWorkingFolderId = intent?.getStringExtra(DriveServiceHelper.WORKING_FOLDER_ID) ?: ""

        // test alarm sequence is 5
        val alarmId = intent?.getIntExtra(SettingsScheduleFragment.ALARM_ID, 5) ?: 5
        EasyDiaryDbHelper.findAlarmBy(alarmId)?.let {
            val workStatus = WorkStatus()
            workStatusList.add(workStatus)
            backupPhoto(it, workStatus)
        }
        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "onStartCommand", "INFO", "end"), this)
        return super.onStartCommand(intent, flags, startId)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mInProcessJob = false
    }

    @SuppressLint("RestrictedApi")
    private fun backupPhoto(alarm: Alarm, workStatus: WorkStatus) {
        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "backupPhoto", "INFO", "start"), this)
        mNotificationBuilder.mActions.clear()
        mNotificationBuilder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(NotificationCompat.InboxStyle())
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_easydiary)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_googledrive_upload))
                .setOnlyAlertOnce(true)
                .setContentTitle(if (config.enableDebugOptionVisibleAlarmSequence) "[$NOTIFICATION_FOREGROUND_FULL_BACKUP_GMS_ID] ${getString(R.string.task_progress_message)}" else getString(R.string.task_progress_message))
//                .setContentText(getString(R.string.task_progress_message))
                .setProgress(0, 0, true)
                .addAction(
                        R.drawable.ic_easydiary,
                        getString(R.string.cancel),
                        PendingIntent.getService(this, alarm.id, Intent(this, NotificationService::class.java).apply {
                            action = NotificationService.ACTION_FULL_BACKUP_GMS_CANCEL
                        }, pendingIntentFlag())
                )
        startForeground(NOTIFICATION_FOREGROUND_FULL_BACKUP_GMS_ID, mNotificationBuilder.build())

        determineRemoteDrivePhotos(null, alarm, workStatus)
        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "backupPhoto", "INFO", "end"), this)
    }

    private fun determineRemoteDrivePhotos(nextPageToken: String?, alarm: Alarm, workStatus: WorkStatus) {
        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "determineRemoteDrivePhotos", "INFO", "start"), this)
        mDriveServiceHelper.queryFiles("mimeType = '${DriveServiceHelper.MIME_TYPE_AAF_EASY_DIARY_PHOTO}' and trashed = false",  1000, nextPageToken).run {
            addOnSuccessListener { result ->
                EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "determineRemoteDrivePhotos", "INFO", "progress-1"), this@FullBackupService)
                result.files.map { photoFile ->
                    workStatus.remoteDriveFileNames.add(photoFile.name)
                }
                EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "determineRemoteDrivePhotos", "INFO", "progress-2"), this@FullBackupService)
                when (result.nextPageToken == null) {
                    true -> {
                        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "determineRemoteDrivePhotos", "INFO", "progress-3"), this@FullBackupService)
                        val localPhotos = File(mPhotoPath).listFiles()
                        localPhotos.map { photo ->
                            if (!workStatus.remoteDriveFileNames.contains(photo.name)) {
                                workStatus.targetFilenames.add(photo.name)
                            }
                        }
                        workStatus.localDeviceFileCount = localPhotos.size
                        workStatus.duplicateFileCount = workStatus.localDeviceFileCount - workStatus.targetFilenames.size
                        if (workStatus.targetFilenames.size == 0) {
                            updateNotification(alarm, workStatus)
                        } else {
                            uploadDiaryPhoto(alarm, workStatus)
                        }
                    }
                    false -> {
                        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "determineRemoteDrivePhotos", "INFO", "progress-4"), this@FullBackupService)
                        determineRemoteDrivePhotos(result.nextPageToken, alarm, workStatus)
                    }
                }
            }
            addOnFailureListener { exception ->
                reExecuteGmsBackup(alarm, "${exception.message ?: ""} (determineRemoteDrivePhotos)", FullBackupService::class.java.name)
                stopSelf()
            }
        }
        EasyDiaryDbHelper.insertActionLog(ActionLog(this::class.java.name, "determineRemoteDrivePhotos", "INFO", "end"), this)
    }

    private fun uploadDiaryPhoto(alarm: Alarm, workStatus: WorkStatus) {
        val fileName =  workStatus.targetFilenames[workStatus.targetFilenamesCursor]
        mDriveServiceHelper.createFile(mWorkingFolderId, mPhotoPath + fileName, fileName, DriveServiceHelper.MIME_TYPE_AAF_EASY_DIARY_PHOTO).run {
            addOnSuccessListener { _ ->
                workStatus.targetFilenamesCursor++
                workStatus.successCount++
                updateNotification(alarm, workStatus)
            }
            addOnFailureListener { exception ->
                workStatus.targetFilenamesCursor++
                workStatus.failCount++
                updateNotification(alarm, workStatus)
                reExecuteGmsBackup(alarm, "${exception.message ?: ""} (uploadDiaryPhoto)", FullBackupService::class.java.name)
                stopSelf()
            }
        }
    }

    private fun updateNotification(alarm: Alarm, workStatus: WorkStatus) {
        if (mInProcessJob) {
            if (workStatus.targetFilenames.size == 0) {
                backupDiaryRealm(alarm, workStatus)
            } else {
                val stringBuilder = createBackupContentText(workStatus.localDeviceFileCount, workStatus.duplicateFileCount, workStatus.successCount, workStatus.failCount)
                mNotificationBuilder
                        .setStyle(NotificationCompat.BigTextStyle()
                                .bigText(HtmlCompat.fromHtml(stringBuilder.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)).setSummaryText(alarm.label)
                        )
                        .setContentTitle(if (config.enableDebugOptionVisibleAlarmSequence) "[${alarm.id}] ${getString(R.string.notification_msg_upload_progress)} ${workStatus.successCount + workStatus.failCount}/${workStatus.targetFilenames.size}" else "${getString(R.string.notification_msg_upload_progress)} ${workStatus.successCount + workStatus.failCount}/${workStatus.targetFilenames.size}")
                        .setProgress(workStatus.targetFilenames.size, workStatus.successCount + workStatus.failCount, false)
                mNotificationManager.notify(alarm.id, mNotificationBuilder.build())

                if (workStatus.successCount + workStatus.failCount < workStatus.targetFilenames.size) {
                    if (mInProcessJob) uploadDiaryPhoto(alarm, workStatus)
                } else {
                    config.photoBackupGoogle = System.currentTimeMillis()
                    backupDiaryRealm(alarm, workStatus)
                }
            }
        }
    }

    private fun backupDiaryRealm(alarm: Alarm, workStatus: WorkStatus) {
        GoogleOAuthHelper.getGoogleSignAccount(applicationContext)?.account?.let { account ->
            DriveServiceHelper(applicationContext, account).run {
                initDriveWorkingDirectory(DriveServiceHelper.AAF_EASY_DIARY_REALM_FOLDER_NAME) { realmFolderId ->
                    val dbFileName = DIARY_DB_NAME + "_" + DateUtils.getCurrentDateTime("yyyyMMdd_HHmmss")
                    if (realmFolderId != null) {
                        createFile(
                                realmFolderId, EasyDiaryDbHelper.getRealmPath(),
                                dbFileName,
                                EasyDiaryUtils.easyDiaryMimeType
                        ).addOnSuccessListener {
                            config.diaryBackupGoogle = System.currentTimeMillis()
                            launchCompleteNotification(alarm, dbFileName, workStatus)
                        }.addOnFailureListener { exception ->
                            reExecuteGmsBackup(alarm, "${exception.message ?: ""} (backupDiaryRealm)", FullBackupService::class.java.name)
                            stopSelf()
                        }
                    } else {
                        EasyDiaryDbHelper.insertActionLog(ActionLog("FullBackupService", "backupDiaryRealm", "ERROR", "realmFolderId is null"), applicationContext)
                    }
                }
            }
        }
    }

    private fun launchCompleteNotification(alarm: Alarm, savedFileName: String, workStatus: WorkStatus) {
        if (mInProcessJob) {
//            stopForeground(true)

            val stringBuilder = createBackupContentText(workStatus.localDeviceFileCount, workStatus.duplicateFileCount, workStatus.successCount, workStatus.failCount)
                    .insert(0, getString(R.string.schedule_backup_gms_complete, "<br>"))
                    .append("<b>\uD83D\uDCC1 Database</b><br>")
                    .append("* Saved file name: $savedFileName")

            val resultNotificationBuilder = NotificationCompat.Builder(applicationContext, "${NOTIFICATION_CHANNEL_ID}_alarm")
            resultNotificationBuilder
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_easydiary)
                    .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_googledrive_upload))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentTitle(if (config.enableDebugOptionVisibleAlarmSequence) "[$NOTIFICATION_FOREGROUND_PHOTO_BACKUP_GMS_ID] ${alarm.label}" else alarm.label)
//                .setContentText(getString(R.string.schedule_backup_gms_complete))
                    .setStyle(NotificationCompat.BigTextStyle()
                            .bigText(HtmlCompat.fromHtml(stringBuilder.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY))
                    )
                    .setContentIntent(
                            PendingIntent.getActivity(this, alarm.id, Intent(this, DiaryMainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra(BaseDevActivity.NOTIFICATION_INFO, this@FullBackupService::class.java.name)
                            }, pendingIntentFlag())
                    )
                    .addAction(
                            R.drawable.ic_easydiary,
                            getString(R.string.dismiss),
                            PendingIntent.getService(this, alarm.id, Intent(this, NotificationService::class.java).apply {
                                action = NotificationService.ACTION_FULL_BACKUP_GMS_DISMISS
                                putExtra(SettingsScheduleFragment.ALARM_ID, alarm.id)
                            }, pendingIntentFlag())
                    )
            mNotificationManager.notify(alarm.id, resultNotificationBuilder.build())
            workStatus.isDone = true
            if (workStatusList.filter { item -> item.isDone }.size == workStatusList.size) stopSelf()
        }
    }
}