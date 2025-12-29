package com.example.crevolutionattendance;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class FirebaseHelper {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    // Converts milliseconds to hours.
    public double millisToHrs(double x) {
        return (x / (1000.0 * 60.0 * 60.0));
    }

    // Returns today's date key in YYYY-MM-DD format.
    public String getDate() {
        Calendar calendar = Calendar.getInstance();
        // Refresh calendar to current time each call (prevents stale date if app stays open).
        calendar.setTime(new Date());

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1; // Calendar months are 0-based
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        // Zero-padded so sorting works correctly
        return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
    }

    // Returns a date offset from today (0=today, -1=yesterday, +1=tomorrow)
    private String getDateOffset(int dayOffset) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, dayOffset);

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
    }

    // Returns the local midnight (00:00:00.000) in milliseconds.
    // This method is for handled cross-midnight shifts.
    private long startOfDayMillis(long whenMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(whenMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // Initialize Firebase Auth, Firestore, and Calendar handles once.
    public FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    // Convenience method to point to "today's" document for a user.
    private DocumentReference dayDoc(String uid) {
        return db.collection("LoggedHours")
                .document(uid)
                .collection("Days")
                .document(getDate());
    }

    // Convenience method to point to a specific date's document for a user.
    private DocumentReference dayDoc(String uid, String dateKey) {
        return db.collection("LoggedHours")
                .document(uid)
                .collection("Days")
                .document(dateKey);
    }

    /*
     * Register a new user.
     * Creates the user in Firebase Auth, and also stores a simple profile doc in Firestore:
     * LoggedHours/{uid} with { username }
     * NOTE: This does NOT create a Days subcollection yet.
     * Firestore only shows subcollections after at least one document exists within them.
     * The first clockIn() call creates the first daily doc under Days.
     */
    public void registerUser(String email, String password, String username, FirebaseCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            String uid = user.getUid();

                            // Profile data stored on the user's root document.
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("username", username);

                            db.collection("LoggedHours").document(uid)
                                    .set(userData)
                                    .addOnSuccessListener(aVoid -> callback.onSuccess())
                                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                        }
                    } else {
                        callback.onFailure(Objects.requireNonNull(task.getException()).getMessage());
                    }
                });
    }

    // Signs an existing user into Firebase Auth.
    public void signInUser(String email, String password, FirebaseCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        callback.onFailure(Objects.requireNonNull(task.getException()).getMessage());
                    }
                });
    }

    /*
     * Clock In:
     * - Writes to today's daily doc (dayDoc(uid))
     * - Appends a new shift object to the "shifts" array:
     *      { inMillis: <timestamp> }
     * - Prevents double clock-in by ensuring the last shift has an outMillis value before adding another.
     */
    public void clockIn(FirebaseCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onFailure("No user signed in");
            return;
        }
        String uid = user.getUid();
        DocumentReference docRef = dayDoc(uid);

        long inMillis = System.currentTimeMillis();

        db.runTransaction(transaction -> {
                    DocumentSnapshot snapshot = transaction.get(docRef);

                    // Read today's shifts array. If the document is new, "shifts" will be null.
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> shifts =
                            (List<Map<String, Object>>) snapshot.get("shifts");
                    if (shifts == null) shifts = new ArrayList<>();

                    // Prevent repeated clock-ins:
                    // If the last shift does not have outMillis yet, they are already clocked in.
                    if (!shifts.isEmpty()) {
                        Map<String, Object> last = shifts.get(shifts.size() - 1);
                        if (last.get("outMillis") == null) {
                            throw new IllegalStateException("Already clocked in. Please clock out first.");
                        }
                    }

                    // Create a new shift and append it.
                    // Later, clockOut() will fill in outMillis for this same shift object.
                    Map<String, Object> newShift = new HashMap<>();
                    newShift.put("inMillis", inMillis);
                    shifts.add(newShift);

                    // Update the doc:
                    // - date
                    // - shifts: the updated shift array
                    // SetOptions.merge() ensures we don't wipe other fields (like totalShiftTime).
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("date", getDate());
                    updates.put("shifts", shifts);

                    transaction.set(docRef, updates, SetOptions.merge());
                    return null;
                })
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /*
     * Clock Out:
     * - Reads today's "shifts" array
     * - Finds the last shift and verifies it is "open" (has inMillis but no outMillis)
     * - Sets outMillis on that last shift
     * - Recomputes totalShiftTime for the entire day by summing all closed shifts
     * - Stores totalShiftTime rounded to nearest tenth
     */
    public void clockOut(FirebaseCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onFailure("No user signed in");
            return;
        }

        String uid = user.getUid();
        long outMillis = System.currentTimeMillis();

        String todayKey = getDate();
        String yesterdayKey = getDateOffset(-1);

        DocumentReference todayRef = dayDoc(uid, todayKey);
        DocumentReference yesterdayRef = dayDoc(uid, yesterdayKey);

        db.runTransaction(transaction -> {
            // Look for an open shift to close.
            DocumentSnapshot todaySnap = transaction.get(todayRef);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> todayShifts =
                    (List<Map<String, Object>>) todaySnap.get("shifts");
            if (todayShifts == null) todayShifts = new ArrayList<>();

            Map<String, Object> openToday = null;
            for (int i = todayShifts.size() - 1; i >= 0; i--) {
                Map<String, Object> s = todayShifts.get(i);
                if (s.get("inMillis") != null && s.get("outMillis") == null) {
                    openToday = s;
                    break;
                }
            }

            if (openToday != null) {
                // Close today's open shift
                openToday.put("outMillis", outMillis);

                double totalShiftTime = 0.0;

                for (Map<String, Object> s : todayShifts) {
                    Object in = s.get("inMillis");
                    Object out = s.get("outMillis");

                    if (in != null && out != null) {
                        long inM = ((Number) in).longValue();
                        long outM = ((Number) out).longValue();
                        totalShiftTime += millisToHrs(Math.max(0, outM - inM));
                    }
                }
                totalShiftTime = Math.round(totalShiftTime * 10.0) / 10.0;

                Map<String, Object> updates = new HashMap<>();
                updates.put("date", todayKey);
                updates.put("shifts", todayShifts);
                updates.put("totalShiftTime", totalShiftTime);

                transaction.set(todayRef, updates, SetOptions.merge());
                return null;
            }

            // If no open shift today, check yesterday for an open shift.
            DocumentSnapshot ySnap = transaction.get(yesterdayRef);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> yShifts =
                    (List<Map<String, Object>>) ySnap.get("shifts");
            if (yShifts == null || yShifts.isEmpty()) {
                throw new IllegalStateException("No active shift found to clock out from. Please clock in first.");
            }

            Map<String, Object> openYesterday = null;
            for (int i = yShifts.size() - 1; i >= 0; i--) {
                Map<String, Object> s = yShifts.get(i);
                if (s.get("inMillis") != null && s.get("outMillis") == null) {
                    openYesterday = s;
                    break;
                }
            }

            if (openYesterday == null) {
                throw new IllegalStateException("No active shift found to clock out from. Please clock in first.");
            }

            // Split the shift across midnight
            long startToday = startOfDayMillis(outMillis); // today 00:00:00.000
            long endYesterday = startToday - 1; // yesterday 23:59:59.999 (displays as 23:59)

            // Close yesterday at 23:59-ish
            openYesterday.put("outMillis", endYesterday);

            // Create a new shift on TODAY from 00:00 to the real outMillis
            Map<String, Object> spillShift = new HashMap<>();
            spillShift.put("inMillis", startToday);
            spillShift.put("outMillis", outMillis);
            todayShifts.add(spillShift);

            // Compute shift totals for both days
            double yTotal = 0.0;
            for (Map<String, Object> s : yShifts) {
                Object in = s.get("inMillis");
                Object out = s.get("outMillis");
                if (in != null && out != null) {
                    long inM = ((Number) in).longValue();
                    long outM = ((Number) out).longValue();
                    yTotal += millisToHrs(Math.max(0, outM - inM));
                }
            }
            yTotal = Math.round(yTotal * 10.0) / 10.0;

            double tTotal = 0.0;
            for (Map<String, Object> s : todayShifts) {
                Object in = s.get("inMillis");
                Object out = s.get("outMillis");
                if (in != null && out != null) {
                    long inM = ((Number) in).longValue();
                    long outM = ((Number) out).longValue();
                    tTotal += millisToHrs(Math.max(0, outM - inM));
                }
            }
            tTotal = Math.round(tTotal * 10.0) / 10.0;

            // Update both documents
            Map<String, Object> yUpdates = new HashMap<>();
            yUpdates.put("date", yesterdayKey);
            yUpdates.put("shifts", yShifts);
            yUpdates.put("totalShiftTime", yTotal);

            Map<String, Object> tUpdates = new HashMap<>();
            tUpdates.put("date", todayKey);
            tUpdates.put("shifts", todayShifts);
            tUpdates.put("totalShiftTime", tTotal);

            transaction.set(yesterdayRef, yUpdates, SetOptions.merge());
            transaction.set(todayRef, tUpdates, SetOptions.merge());

            return null;
        })
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }


    // Simple callback interface so Activities/Fragments can react to async operations.
    public interface FirebaseCallback {
        void onSuccess();
        void onFailure(String error);
    }
}