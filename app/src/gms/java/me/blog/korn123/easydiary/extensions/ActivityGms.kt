package me.blog.korn123.easydiary.extensions

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

fun Activity.startReviewFlow() {
    config.appExecutionCount = 0
    val manager = ReviewManagerFactory.create(this)
    val request = manager.requestReviewFlow()
    request.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            // We got the ReviewInfo object
            val reviewInfo = task.result
            val flow = manager.launchReviewFlow(this, reviewInfo)
            flow.addOnCompleteListener { _ ->
                if (config.enableDebugMode) makeToast("The flow has finished.")
                // The flow has finished. The API does not indicate whether the user
                // reviewed or not, or even whether the review dialog was shown. Thus, no
                // matter the result, we continue our app flow.
            }
        } else {
            if (config.enableDebugMode) makeToast("There was some problem, log or handle the error code.")
            // There was some problem, log or handle the error code.
        }
    }
}