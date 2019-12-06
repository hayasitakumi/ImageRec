package com.syabonbubble.imagerec

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
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
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.syabonbubble.imagerec.data.MyMessage
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    private var userName: String = ANONYMOUS
    private var photoUrl: String? = null

    private lateinit var googleSignInClient: GoogleSignInClient

    private var linearLayoutManager: LinearLayoutManager? = null

    // Firebase instance variables
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val currentUser: FirebaseUser? = firebaseAuth.currentUser
    private val firebaseDatabaseReference: DatabaseReference? =
        FirebaseDatabase.getInstance().reference
    private var firebaseAdapter: FirebaseRecyclerAdapter<MyMessage, MessageViewHolder>? = null

    private var mLabelList: List<String>? = null
    private var mDataOptions: FirebaseModelInputOutputOptions? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (currentUser == null) {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        } else {
            userName = currentUser.displayName!!
            if (currentUser.photoUrl != null) {
                photoUrl = currentUser.photoUrl.toString()
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setFirebaseAdapter()

        image_fab.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }

        camera_fab.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, REQUEST_CAMERA)
        }

    }

    public override fun onStart() {
        super.onStart()
    }

    public override fun onPause() {
        super.onPause()
        firebaseAdapter!!.stopListening()
    }

    public override fun onResume() {
        super.onResume()
        firebaseAdapter!!.startListening()
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
                firebaseAuth.signOut()
                googleSignInClient.signOut().addOnCompleteListener(this) {}
                userName = ANONYMOUS
                startActivity(Intent(this, SignInActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    val uri: Uri = data.data!!
                    Log.d(TAG, "Uri: $uri")
                    val tempMessage = MyMessage(
                        null,
                        photoUrl,
                        LOADING_IMAGE_URL
                    )
                    firebaseDatabaseReference!!.child(MESSAGES_CHILD).child(currentUser!!.uid)
                        .push()
                        .setValue(tempMessage) { databaseError, databaseReference ->
                            if (databaseError == null) {
                                val key = databaseReference.key
                                val storageReference =
                                    FirebaseStorage.getInstance().getReference(currentUser.uid)
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

        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    Log.d("TAG", data.toString())
//                    val bitmap = data.extras!!.get("data") as Bitmap
                }
            }
        }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri, key: String?) {

        storageReference.putFile(uri).addOnCompleteListener(this@MainActivity) { taskSnapShot ->
            if (taskSnapShot.isSuccessful) {
                taskSnapShot.result!!.metadata!!.reference!!.downloadUrl.addOnCompleteListener(this@MainActivity) { taskUri ->
                    Log.d("TAG", taskUri.result.toString())



                    if (taskUri.isSuccessful) {
                        val myMessage =
                            MyMessage("mytext", photoUrl, taskUri.result.toString())
                        firebaseDatabaseReference!!.child(MESSAGES_CHILD).child(currentUser!!.uid)
                            .child(key!!).setValue(myMessage)
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

    private fun loadLabelList(activity: Activity): List<String> {
        val labelList: MutableList<String> = ArrayList()
        try {
            BufferedReader(InputStreamReader(activity.assets.open(LABEL_PATH))).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    labelList.add(line!!)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read label list.", e)
        }
        return labelList
    }

    private fun setFirebaseAdapter() {

        linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager!!.stackFromEnd = true

        message_recyclerview.layoutManager = linearLayoutManager


        val parser: SnapshotParser<MyMessage> = SnapshotParser { dataSnapshot ->
            val myMessage: MyMessage =
                dataSnapshot.getValue<MyMessage>(
                    MyMessage::class.java
                )!!
            myMessage.id = dataSnapshot.key
            myMessage
        }

        val messagesRef =
            firebaseDatabaseReference!!.child(MESSAGES_CHILD).child(currentUser!!.uid)

        val options: FirebaseRecyclerOptions<MyMessage?> =
            FirebaseRecyclerOptions.Builder<MyMessage>()
                .setQuery(messagesRef, parser)
                .build()


        firebaseAdapter = object : FirebaseRecyclerAdapter<MyMessage, MessageViewHolder>(options) {
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
        firebaseAdapter!!.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)

                message_recyclerview.scrollToPosition(positionStart)
            }
        })
        message_recyclerview.adapter = firebaseAdapter

    }

    companion object {
        private const val TAG = "MainActivity"
        private const val MESSAGES_CHILD = "messages"
        private const val REQUEST_IMAGE = 1001
        private const val REQUEST_CAMERA = 1002
        private const val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"
        private const val ANONYMOUS = "anonymous"
        private const val LABEL_PATH = "labels.txt"
        private const val DIM_BATCH_SIZE = 1
        private const val DIM_PIXEL_SIZE = 3
        private const val DIM_IMG_SIZE_X = 224
        private const val DIM_IMG_SIZE_Y = 224
        private const val HOSTED_MODEL_NAME = "cloud_model_1"
        private const val LOCAL_MODEL_ASSET = "mobilenet_v1_1.0_224_quant.tflite"
    }

    class MessageViewHolder(v: View?) : RecyclerView.ViewHolder(v!!) {
        val messageTextView: TextView =
            itemView.findViewById<View>(R.id.message_text) as TextView
        val messageImageView: ImageView =
            itemView.findViewById<View>(R.id.message_imageview) as ImageView
        val messengerImageView: CircleImageView =
            itemView.findViewById<View>(R.id.messenger_imageview) as CircleImageView

    }
}