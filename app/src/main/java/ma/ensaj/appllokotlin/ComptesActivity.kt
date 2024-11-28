package ma.ensaj.appllokotlin

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import kotlinx.coroutines.launch
import ma.ensaj.AllComptesQuery
import ma.ensaj.DeleteCompteMutation
import ma.ensaj.SaveCompteMutation
import ma.ensaj.appllokotlin.adapter.ComptesAdapter
import ma.ensaj.type.CompteRequest
import ma.ensaj.type.TypeCompte
import com.apollographql.apollo.api.Optional

class ComptesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ComptesAdapter
    private lateinit var apolloClient: ApolloClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comptes)

        // Initialize Apollo Client
        apolloClient = ApolloClient.Builder()
            .serverUrl("http://10.0.2.2:8082/graphql")
            .build()

        // Set up RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize Create Button and handle clicks
        val createButton: Button = findViewById(R.id.btn_create_compte)
        createButton.setOnClickListener {
            val soldeInput: EditText = findViewById(R.id.input_solde)
            val dateCreationInput: EditText = findViewById(R.id.input_date_creation)
            val typeSpinner: Spinner = findViewById(R.id.input_type)

            val solde = soldeInput.text.toString().toFloatOrNull()
            val dateCreation = dateCreationInput.text.toString()
            val type = typeSpinner.selectedItem.toString()

            if (solde != null && dateCreation.isNotBlank() && type.isNotBlank()) {
                createCompte(solde, dateCreation, type)
            } else {
                Toast.makeText(this, "Please fill in all fields correctly", Toast.LENGTH_SHORT).show()
            }
        }

        // Fetch data
        fetchComptes()
    }

    private fun fetchComptes() {
        val query = AllComptesQuery()

        lifecycleScope.launch {
            try {
                val response = apolloClient.query(query).execute()
                val fetchedComptes = response.data?.allComptes?.toMutableList() ?: mutableListOf()

                // Update adapter or initialize it
                if (::adapter.isInitialized) {
                    adapter.updateData(fetchedComptes)
                } else {
                    adapter = ComptesAdapter(fetchedComptes, { id -> deleteCompte(id) }, { compte -> updateCompte(compte) })

                    recyclerView.adapter = adapter
                }
            } catch (e: ApolloException) {
                e.printStackTrace()
            }
        }
    }

    private fun createCompte(solde: Float, dateCreation: String, type: String) {
        lifecycleScope.launch {
            try {
                val compteRequest = CompteRequest(
                    solde = Optional.Present(solde.toDouble()),
                    dateCreation = Optional.Present(dateCreation),
                    type = Optional.Present(TypeCompte.valueOf(type.uppercase()))
                )

                val response = apolloClient.mutation(SaveCompteMutation(compteRequest)).execute()

                if (response.data?.saveCompte != null) {
                    Toast.makeText(this@ComptesActivity, "Compte created successfully!", Toast.LENGTH_SHORT).show()

                    // Clear input fields after success
                    clearInputFields()

                    // Refresh the list after creation
                    fetchComptes()
                } else {
                    Toast.makeText(this@ComptesActivity, "Failed to create compte", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApolloException) {
                e.printStackTrace()
                Toast.makeText(this@ComptesActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun clearInputFields() {
        val soldeInput: EditText = findViewById(R.id.input_solde)
        val dateCreationInput: EditText = findViewById(R.id.input_date_creation)
        val typeSpinner: Spinner = findViewById(R.id.input_type)

        soldeInput.text.clear()  // Clear the solde input field
        dateCreationInput.text.clear()  // Clear the date input field
        typeSpinner.setSelection(0)  // Reset spinner to first item (default)
    }


    private fun deleteCompte(id: String) {
        lifecycleScope.launch {
            try {
                val response = apolloClient.mutation(DeleteCompteMutation(id)).execute()

                if (response.data?.deleteCompte?.contains("deleted successfully") == true) {
                    runOnUiThread {
                        adapter.removeItem(id)
                        Toast.makeText(this@ComptesActivity, "Compte deleted successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@ComptesActivity, "Failed to delete compte", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApolloException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@ComptesActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun updateCompte(compte: AllComptesQuery.AllCompte) {
        // Remplir les champs avec les données existantes
        val soldeInput: EditText = findViewById(R.id.input_solde)
        val dateCreationInput: EditText = findViewById(R.id.input_date_creation)
        val typeSpinner: Spinner = findViewById(R.id.input_type)

        soldeInput.setText(compte.solde.toString())
        dateCreationInput.setText(compte.dateCreation)

        // Sélectionner le type dans le Spinner
        val typeArray = resources.getStringArray(R.array.type_compte_array)
        val typeIndex = compte.type?.name?.let { typeName ->
            typeArray.indexOf(typeName)
        } ?: -1

        if (typeIndex != -1) {
            typeSpinner.setSelection(typeIndex)
        }


        // Modifier le texte du bouton pour indiquer la mise à jour
        val updateButton: Button = findViewById(R.id.btn_create_compte)
        updateButton.text = "Mettre à jour"
        updateButton.setOnClickListener {
            val solde = soldeInput.text.toString().toFloatOrNull()
            val dateCreation = dateCreationInput.text.toString()
            val type = typeSpinner.selectedItem.toString()

            if (solde != null && dateCreation.isNotBlank() && type.isNotBlank()) {
                lifecycleScope.launch {
                    try {
                        // Construire la requête avec les valeurs mises à jour
                        val compteRequest = CompteRequest(
                            id = Optional.Present(compte.id),  // Utiliser l'ID existant pour la mise à jour
                            solde = Optional.Present(solde.toDouble()),
                            dateCreation = Optional.Present(dateCreation),
                            type = Optional.Present(TypeCompte.valueOf(type.uppercase()))
                        )

                        // Exécuter la mutation GraphQL
                        val mutation = SaveCompteMutation(compteRequest)
                        val response = apolloClient.mutation(mutation).execute()

                        // Vérifier la réponse
                        if (response.data?.saveCompte != null) {
                            Toast.makeText(
                                this@ComptesActivity,
                                "Compte mis à jour avec succès!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Rafraîchir la liste et réinitialiser les champs
                            fetchComptes()
                            resetInputs()
                        } else {
                            Log.d("error", "Échec de la mise à jour : ${response.errors?.joinToString(", ") { it.message }}")
                            Toast.makeText(
                                this@ComptesActivity,
                                "Échec de la mise à jour : ${response.errors?.joinToString()}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: ApolloException) {
                        e.printStackTrace()
                        Toast.makeText(
                            this@ComptesActivity,
                            "Erreur : ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(this, "Remplissez tous les champs correctement", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetInputs() {
        val soldeInput: EditText = findViewById(R.id.input_solde)
        val dateCreationInput: EditText = findViewById(R.id.input_date_creation)
        val typeSpinner: Spinner = findViewById(R.id.input_type)

        soldeInput.text.clear()
        dateCreationInput.text.clear()
        typeSpinner.setSelection(0)

        val createButton: Button = findViewById(R.id.btn_create_compte)
        createButton.text = "Créer Compte" // Rétablir le texte du bouton
    }


}
