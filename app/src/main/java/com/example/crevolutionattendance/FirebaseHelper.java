package com.example.crevolutionattendance;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FirebaseHelper {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    /** Register a new user and create Firestore document */
    public void registerUser(String email, String password, String username, FirebaseCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();

                            // Initialize Firestore document for this user
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("username", username);
                            userData.put("hours", 0);
                            userData.put("clockInTimes", new ArrayList<>());
                            userData.put("clockOutTimes", new ArrayList<>());

                            db.collection("LoggedHours").document(uid)
                                    .set(userData)
                                    .addOnSuccessListener(aVoid -> callback.onSuccess())
                                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                        }
                    } else {
                        callback.onFailure(task.getException().getMessage());
                    }
                });
    }

    /** Sign in user */
    public void signInUser(String email, String password, FirebaseCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        callback.onFailure(task.getException().getMessage());
                    }
                });
    }

    /** Clock in: add timestamp to clockInTimes */
    public void clockIn(FirebaseCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onFailure("No user signed in");
            return;
        }
        String uid = user.getUid();
        DocumentReference docRef = db.collection("LoggedHours").document(uid);
        docRef.update("clockInTimes", com.google.firebase.firestore.FieldValue.arrayUnion(System.currentTimeMillis()))
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /** Clock out: add timestamp to clockOutTimes and update total hours */
    public void clockOut(FirebaseCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onFailure("No user signed in");
            return;
        }
        String uid = user.getUid();
        DocumentReference docRef = db.collection("LoggedHours").document(uid);

        // Add clock out time
        docRef.update("clockOutTimes", com.google.firebase.firestore.FieldValue.arrayUnion(System.currentTimeMillis()))
                .addOnSuccessListener(aVoid -> {
                    // Optionally, calculate hours worked (simplified example)
                    docRef.get().addOnSuccessListener(snapshot -> {
                        ArrayList<Long> inTimes = (ArrayList<Long>) snapshot.get("clockInTimes");
                        ArrayList<Long> outTimes = (ArrayList<Long>) snapshot.get("clockOutTimes");

                        long totalMillis = 0;
                        if (inTimes != null && outTimes != null) {
                            int size = Math.min(inTimes.size(), outTimes.size());
                            for (int i = 0; i < size; i++) {
                                totalMillis += outTimes.get(i) - inTimes.get(i);
                            }
                        }
                        double totalHours = totalMillis / (1000.0 * 60.0 * 60.0);

                        docRef.update("hours", totalHours)
                                .addOnSuccessListener(aVoid1 -> callback.onSuccess())
                                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                    }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /** Callback interface to handle async responses */
    public interface FirebaseCallback {
        void onSuccess();
        void onFailure(String error);
    }
}
