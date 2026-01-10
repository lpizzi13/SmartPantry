package it.sapienza.smartpantry.ui // <-- ATTENTO: Lascia il tuo package qui!

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.smartpantry.R

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // 1. Colleghiamo il codice ai componenti grafici
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        // 2. Cosa succede quando clicchi "Accedi"
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                // Errore: campi vuoti
                Toast.makeText(this, "Inserisci email e password!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Login riuscito!
                        navigateToMain()
                    } else {
                        // Errore (es. password sbagliata, utente non trovato)
                        Toast.makeText(
                            this,
                            "Errore: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        // 3. Cosa succede quando clicchi "Registrati"
        tvRegister.setOnClickListener {
            Toast.makeText(this, "Funzione registrazione da implementare", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        // Passiamo alla schermata principale e chiudiamo questa
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Importante: così se fai "Indietro" l'app si chiude invece di tornare al login
    }
}