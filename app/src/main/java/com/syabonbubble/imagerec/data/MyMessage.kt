package com.syabonbubble.imagerec.data

@Suppress("unused")
class MyMessage {
    var id: String? = null
    var text: String? = null
    var photoUrl: String? = null
    var imageUrl: String? = null

    constructor()

    constructor(
        text: String?,
        photoUrl: String?,
        imageUrl: String?
    ) {
        this.text = text
        this.photoUrl = photoUrl
        this.imageUrl = imageUrl
    }

}