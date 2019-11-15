package com.syabonbubble.imagerec

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.firebase.ui.database.SnapshotParser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.syabonbubble.imagerec.data.MyMessage
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var mUsername: String = ANONYMOUS
    private var mPhotoUrl: String? = null

    //    private var mGoogleApiClient: GoogleApiClient? = null
    private lateinit var googleSignInClient: GoogleSignInClient

    private var mLinearLayoutManager: LinearLayoutManager? = null

    // Firebase instance variables
    private val mFirebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private var currentUser: FirebaseUser? = null
    private var mFirebaseDatabaseReference: DatabaseReference? = null
    private var mFirebaseAdapter: FirebaseRecyclerAdapter<MyMessage?, MessageViewHolder?>? =
        null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        currentUser = mFirebaseAuth.currentUser

        mLinearLayoutManager = LinearLayoutManager(this)
        mLinearLayoutManager!!.stackFromEnd = true

        message_recyclerview.layoutManager = mLinearLayoutManager

        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().reference
        val parser: SnapshotParser<MyMessage> = SnapshotParser { dataSnapshot ->
            val myMessage: MyMessage =
                dataSnapshot.getValue<MyMessage>(
                    MyMessage::class.java
                )!!
            myMessage.id = dataSnapshot.key
            myMessage
        }

        val messagesRef =
            mFirebaseDatabaseReference!!.child(MESSAGES_CHILD)

        val options: FirebaseRecyclerOptions<MyMessage?> =
            FirebaseRecyclerOptions.Builder<MyMessage>()
                .setQuery(messagesRef, parser)
                .build()

        mFirebaseAdapter = object :
            FirebaseRecyclerAdapter<MyMessage?, MessageViewHolder?>(options) {
            override fun onCreateViewHolder(
                viewGroup: ViewGroup,
                i: Int
            ): MessageViewHolder {
                val inflater: LayoutInflater =
                    LayoutInflater.from(viewGroup.context)
                return MessageViewHolder(
                    inflater.inflate(R.layout.item_message, viewGroup, false)
                )
            }

            override fun onBindViewHolder(
                viewHolder: MessageViewHolder,
                position: Int,
                myMessage: MyMessage
            ) {

                progress_bar.visibility = ProgressBar.INVISIBLE
                viewHolder.messageTextView.text = myMessage.text
                val imageUrl = myMessage.imageUrl
                if (imageUrl!!.startsWith("gs://")) {
                    val storageReference =
                        FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)

                    storageReference.downloadUrl.addOnCompleteListener { taskUri ->

                        if (taskUri.isSuccessful) {
                            val downloadUrl = taskUri.result.toString()
                            Glide.with(viewHolder.messageImageView.context)
                                .load(downloadUrl)
                                .into(viewHolder.messageImageView)
                        } else {
                            Log.w(
                                TAG,
                                "Getting download url was not successful.",
                                taskUri.exception
                            )
                        }
                    }
                } else {
                    Glide.with(viewHolder.messageImageView.context)
                        .load(myMessage.imageUrl)
                        .into(viewHolder.messageImageView)
                }
                
                if (myMessage.photoUrl == null) {
                    viewHolder.messengerImageView.setImageDrawable(
                        ContextCompat.getDrawable(
                            this@MainActivity,
                            R.drawable.baseline_account_circle_black_36dp
                        )
                    )
                } else {
                    Glide.with(this@MainActivity)
                        .load(myMessage.photoUrl)
                        .into(viewHolder.messengerImageView)
                }
            }
        }
        mFirebaseAdapter!!.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)

                message_recyclerview.scrollToPosition(positionStart)
            }
        })
        message_recyclerview.adapter = mFirebaseAdapter

//        sendButton.setOnClickListener {
//            val myMessage =
//                MyMessage("message", mUsername, mPhotoUrl, null)
//            mFirebaseDatabaseReference!!.child(MESSAGES_CHILD).push().setValue(friendlyMessage)
//            messageEditText.setText("")
//        }

        add_image_fab.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }
    }

    public override fun onStart() {
        super.onStart()
        if (currentUser == null) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        } else {
            mUsername = currentUser!!.displayName!!
            if (currentUser!!.photoUrl != null) {
                mPhotoUrl = currentUser!!.photoUrl.toString()
            }
        }
    }

    public override fun onPause() {
        mFirebaseAdapter!!.stopListening()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        mFirebaseAdapter!!.startListening()
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                mFirebaseAuth.signOut()
                googleSignInClient.signOut().addOnCompleteListener(this) {}
                mUsername = ANONYMOUS
                startActivity(Intent(this, SignInActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

//    override fun onConnectionFailed(connectionResult: ConnectionResult) {
//        Log.d(TAG, "onConnectionFailed:$connectionResult")
//        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show()
//    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(
            TAG,
            "onActivityResult: requestCode=$requestCode, resultCode=$resultCode"
        )
        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    val uri: Uri = data.data!!
                    Log.d(TAG, "Uri: $uri")
                    val tempMessage = MyMessage(
                        null,
                        mPhotoUrl,
                        LOADING_IMAGE_URL
                    )
                    mFirebaseDatabaseReference!!.child(MESSAGES_CHILD).push()
                        .setValue(tempMessage) { databaseError, databaseReference ->
                            if (databaseError == null) {
                                val key = databaseReference.key
                                val storageReference =
                                    FirebaseStorage.getInstance().getReference(currentUser!!.uid)
                                        .child(key!!).child(uri.lastPathSegment!!)
                                putImageInStorage(storageReference, uri, key)
                            } else {
                                Log.w(
                                    TAG,
                                    "Unable to write message to database.",
                                    databaseError.toException()
                                )
                            }
                        }
                }
            }
        }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri, key: String?) {
        storageReference.putFile(uri).addOnCompleteListener(this@MainActivity) { taskSnapShot ->
            if (taskSnapShot.isSuccessful) {
                taskSnapShot.result!!.metadata!!.reference!!.downloadUrl.addOnCompleteListener(this@MainActivity) { taskUri ->
                    if (taskUri.isSuccessful) {
                        val myMessage =
                            MyMessage("mytext", mPhotoUrl, taskUri.result.toString())
                        mFirebaseDatabaseReference!!.child(MESSAGES_CHILD)
                            .child(key!!)
                            .setValue(myMessage)
                    }
                }
            } else {
                Log.w(
                    TAG, "Image upload task was not successful.",
                    taskSnapShot.exception
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val MESSAGES_CHILD = "messages"
        private const val REQUEST_IMAGE = 2
        private const val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"
        const val ANONYMOUS = "anonymous"
    }

    class MessageViewHolder(v: View?) :
        RecyclerView.ViewHolder(v!!) {
        val messageTextView: TextView =
            itemView.findViewById<View>(R.id.message_text) as TextView
        val messageImageView: ImageView =
            itemView.findViewById<View>(R.id.message_imageview) as ImageView
        val messengerImageView: CircleImageView =
            itemView.findViewById<View>(R.id.messenger_imageview) as CircleImageView

    }
}