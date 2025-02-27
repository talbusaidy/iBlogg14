import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.example.iblogg.data.AuthViewModel
import com.example.iblogg.model.Topics
import com.example.iblogg.navigation.ROUTE_HOME
import com.example.iblogg.navigation.ROUTE_LOGIN
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class TopicsViewModel(
    private val navController: NavController,
    private val context: Context
) : ViewModel() {

    private val _topics = MutableStateFlow<List<Topics>>(emptyList())
    val topics: StateFlow<List<Topics>> = _topics

    private val authRepository: AuthViewModel = AuthViewModel(navController, context)

    init {
        if (!authRepository.isloggedin()) {
            navController.navigate(ROUTE_LOGIN)
        }
        fetchTopics()
    }

    private fun fetchTopics() {
        val ref = FirebaseDatabase.getInstance().getReference("Topics")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val topicList = snapshot.children.mapNotNull { it.getValue(Topics::class.java) }
                _topics.update { topicList }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun saveTopics(filePath: Uri, topic: String, discuss: String) {
        val id = System.currentTimeMillis().toString()
        val storageReference = FirebaseStorage.getInstance().getReference("Passport/$id")

        storageReference.putFile(filePath).addOnCompleteListener {
            if (it.isSuccessful) {
                storageReference.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    val newTopic = Topics(imageUrl, topic, discuss, id)
                    val dbRef = FirebaseDatabase.getInstance().getReference("Topics/$id")
                    dbRef.setValue(newTopic)
                    Toast.makeText(context, "Topic added successfully", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Error: ${it.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun viewTopics(
        Topic: MutableState<Topics>,
        topics: SnapshotStateList<Topics>
    ): SnapshotStateList<Topics> {
        var ref = FirebaseDatabase.getInstance().getReference().child("Topics")


        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                topics.clear()
                for (snap in snapshot.children) {
                    val value = snap.getValue(Topics::class.java)
                    Topic.value = value!!
                    topics.add(value)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            }
        })
        return topics
    }

    fun deleteTopic(id: String) {
        val dbRef = FirebaseDatabase.getInstance().getReference("Topics/$id")
        dbRef.removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(context, "Topic deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun updateTopic(
        context: Context,  // Pass context from the Composable or ViewModel
        navController: NavController,  // Pass navController from the Composable or ViewModel
        filePath: Uri,
        topic: String,
        discuss: String,
        id: String,
        currentImageUrl: String // Pass the current image URL from the database
    ) {
        val databaseReference = FirebaseDatabase.getInstance().getReference("Topics/$id")

        if (filePath != Uri.EMPTY) {
            val storageReference = FirebaseStorage.getInstance().reference
            val imageRef = storageReference.child("Passport/${UUID.randomUUID()}.jpg")

            imageRef.putFile(filePath)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        val imageUrl = uri.toString()
                        val updatedTopics = Topics(imageUrl, topic, discuss, id)

                        databaseReference.setValue(updatedTopics)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "Update successful", Toast.LENGTH_SHORT)
                                        .show()
                                    navController.navigate(ROUTE_HOME)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Update failed: ${task.exception?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(
                        context,
                        "Image upload failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            // Keep the current image URL if no new image is selected
            val updatedTopics = Topics(currentImageUrl, topic, discuss, id)
            databaseReference.setValue(updatedTopics)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(context, "Update successful", Toast.LENGTH_SHORT).show()
                        navController.navigate(ROUTE_HOME)
                    } else {
                        Toast.makeText(
                            context,
                            "Update failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }
}
