package com.example.chaticalmusic;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

public class SplashActivity extends AppCompatActivity {

    private TextView mStatusText;
    private android.widget.ProgressBar mProgressBar;
    private View mSignInContainer;
    private androidx.cardview.widget.CardView mGoogleBtn;
    private TextView mGuestBtn;
    private FirebaseAuth mAuth;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mStatusText = findViewById(R.id.status_text);
        mProgressBar = findViewById(R.id.progress_bar);
        mSignInContainer = findViewById(R.id.sign_in_container);
        mGoogleBtn = findViewById(R.id.btn_google_sign_in);
        mGuestBtn = findViewById(R.id.btn_guest_sign_in);

        mAuth = FirebaseAuth.getInstance();

        // Check if user is already authenticated
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            SharedPreferences prefs = getSharedPreferences("MusicalChatPrefs", MODE_PRIVATE);
            if (prefs.contains("display_name")) {
                mStatusText.setText("Authenticated successfully!");
                mHandler.postDelayed(() -> {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    finish();
                }, 500);
            } else {
                mProgressBar.setVisibility(View.GONE);
                mStatusText.setVisibility(View.GONE);
                String defaultName = "User_" + (uid.length() >= 4 ? uid.substring(0, 4) : uid);
                showDisplayNameDialog(uid, defaultName, prefs);
            }
        } else {
            showSignInOptions();
        }
    }

    private void performSignIn() {
        mStatusText.setText("Connecting to Firebase...");
        mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    String uid = user.getUid();
                    SharedPreferences prefs = getSharedPreferences("MusicalChatPrefs", MODE_PRIVATE);

                    if (prefs.contains("display_name")) {
                        // Already exists, just make sure uid is saved and go
                        prefs.edit().putString("user_uid", uid).apply();
                        mStatusText.setText("Authenticated successfully!");
                        mHandler.postDelayed(() -> {
                            startActivity(new Intent(SplashActivity.this, MainActivity.class));
                            finish();
                        }, 500);
                    } else {
                        // Show BottomSheetDialog to ask for display name
                        String defaultName = "User_" + (uid.length() >= 4 ? uid.substring(0, 4) : uid);
                        showDisplayNameDialog(uid, defaultName, prefs);
                    }
                } else {
                    handleSignInFailure();
                }
            } else {
                handleSignInFailure();
            }
        });
    }

    private void showDisplayNameDialog(String uid, String defaultName, SharedPreferences prefs) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.dialog_display_name, null);
        bottomSheet.setContentView(sheetView);
        bottomSheet.setCancelable(false);

        EditText input = sheetView.findViewById(R.id.display_name_input);
        Button confirmBtn = sheetView.findViewById(R.id.btn_confirm);

        input.setText(defaultName);
        input.setSelection(defaultName.length());

        confirmBtn.setOnClickListener(v -> {
            String enteredName = input.getText().toString().trim();
            if (enteredName.isEmpty()) {
                input.setError("Name cannot be empty");
                return;
            }

            // Save to SharedPreferences
            prefs.edit()
                    .putString("user_uid", uid)
                    .putString("display_name", enteredName)
                    .apply();

            // Write to /users/{uid}/display_name in Firebase
            com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(uid)
                    .child("display_name")
                    .setValue(enteredName)
                    .addOnCompleteListener(dbTask -> {
                        bottomSheet.dismiss();
                        mStatusText.setText("Authenticated successfully!");
                        startActivity(new Intent(SplashActivity.this, MainActivity.class));
                        finish();
                    });
        });

        bottomSheet.show();
    }

    private void showSignInOptions() {
        mProgressBar.setVisibility(View.GONE);
        mStatusText.setVisibility(View.GONE);
        mSignInContainer.setVisibility(View.VISIBLE);

        mGuestBtn.setOnClickListener(v -> {
            mSignInContainer.setVisibility(View.GONE);
            mProgressBar.setVisibility(View.VISIBLE);
            mStatusText.setVisibility(View.VISIBLE);
            performSignIn();
        });

        mGoogleBtn.setOnClickListener(v -> {
            int clientIdResId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
            if (clientIdResId == 0) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Google Sign-In Setup")
                        .setMessage("To enable Google Sign-In, please add your SHA-1 fingerprint in the Firebase Console under Project Settings, then download and replace the google-services.json file in your project.\n\nAfter replacing, rebuild and try again.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            String webClientId = getString(clientIdResId);
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account.getIdToken());
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        mSignInContainer.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mStatusText.setVisibility(View.VISIBLE);
        mStatusText.setText("Authenticating with Firebase...");

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    String uid = user.getUid();
                    SharedPreferences prefs = getSharedPreferences("MusicalChatPrefs", MODE_PRIVATE);

                    if (prefs.contains("display_name")) {
                        prefs.edit().putString("user_uid", uid).apply();
                        mStatusText.setText("Authenticated successfully!");
                        startActivity(new Intent(SplashActivity.this, MainActivity.class));
                        finish();
                    } else {
                        String googleName = user.getDisplayName();
                        String defaultName = (googleName != null && !googleName.trim().isEmpty())
                                ? googleName : "User_" + (uid.length() >= 4 ? uid.substring(0, 4) : uid);
                        showDisplayNameDialog(uid, defaultName, prefs);
                    }
                } else {
                    handleSignInFailure();
                }
            } else {
                Toast.makeText(this, "Firebase Google Auth failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
                mSignInContainer.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                mStatusText.setVisibility(View.GONE);
            }
        });
    }

    private void handleSignInFailure() {
        mStatusText.setText("Connection error — retrying");
        // Retry after 3 seconds
        mHandler.postDelayed(this::performSignIn, 3000);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
