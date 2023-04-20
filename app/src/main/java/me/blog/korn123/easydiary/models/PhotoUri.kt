package me.blog.korn123.easydiary.models

import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.LinkingObjects
import me.blog.korn123.easydiary.helper.CONTENT_URI_PREFIX
import me.blog.korn123.easydiary.helper.DIARY_PHOTO_DIRECTORY
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils


/**
 * Created by hanjoong on 2017-06-08.
 */

open class PhotoUri : RealmObject {
    @LinkingObjects("photoUris")
    val diary: RealmResults<Diary>? = null
    var photoUri: String? = null
    var mimeType: String? = null

    constructor() 

    constructor(photoUri: String) {
        this.photoUri = photoUri
    }

    constructor(photoUri: String, mimeType: String) {
        this.photoUri = photoUri
        this.mimeType = mimeType
    }
    
    fun isContentUri(): Boolean = StringUtils.startsWith(photoUri, CONTENT_URI_PREFIX)
    
    fun getFilePath(): String {
        return "$DIARY_PHOTO_DIRECTORY${FilenameUtils.getBaseName(photoUri)}"
    }

    fun isEncrypt(): Boolean = photoUri?.isEmpty() ?: false
}
