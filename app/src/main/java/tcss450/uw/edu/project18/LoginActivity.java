package tcss450.uw.edu.project18;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 * There is also a button to handle registering a new account.
 * @author Melinda Robertson
 * @version 20160531
 */
public class LoginActivity extends AppCompatActivity
        implements LoaderCallbacks<Cursor>,
        EditProfileFragment.EditProfileListener{

    /**
     * The shared preferences for the profile.
     * Generally contains the login state, username, birthday and id
     * for the picture gallery.
     */
    private SharedPreferences mShared;

    /**
     * Sent to the edit profile fragment if creating a new
     * profile. The main activity has a corresponding String
     * for when editing the profile.git
     */
    public static final String PROFILE_NEW = "newProfile";

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    // UI references.
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        this.setTitle(R.string.title_activity_login);
        mShared = getSharedPreferences(getString(R.string.LOGIN_PREFS),
                Context.MODE_PRIVATE);
        if (mShared.getBoolean(getString(R.string.LOGGEDIN), false)) {
            startMain();
            return;
        }
        // Set up the login form.
        mEmailView = (AutoCompleteTextView) findViewById(R.id.email);
        if(Driver.DEBUG) Log.i("LoginActivity:onCreate", "EmailView:" + mEmailView.toString());
        populateAutoComplete();
        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        if (mEmailSignInButton != null) {
            mEmailSignInButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    attemptLogin();
                }
            });
        }

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
    }

    /**
     * Begins the main activity for the bulk of the app.
     * This will initialize to the event list if the user
     * successfully logs in.
     */
    private void startMain() {
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
        finish();
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                populateAutoComplete();
            }
        }
    }


    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid email.
        if (!Driver.isValidEmail(email)) {
            Toast.makeText(this, "Could not log in, please check email and password.",
                    Toast.LENGTH_LONG).show();
            focusView = mEmailView;
            cancel = true;
        }
        // Check for a valid password, if the user entered one.
        String error = Driver.isValidPassword("login", password, null);
        if (!error.equals("success")) {
            Toast.makeText(this, "Could not log in," +
                    "please check email and password.", Toast.LENGTH_LONG).show();
            if (focusView == null) focusView = mPasswordView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            if (focusView != null) focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(email, password);
            mAuthTask.execute((Void) null);
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(this,
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE +
                        " = ?", new String[]{ContactsContract.CommonDataKinds.Email
                .CONTENT_ITEM_TYPE},

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
    }

    /**
     * This would be for autocomplete.
     * @param cursorLoader not sure.
     * @param cursor don't know.
     *               @see {LoaderCallbacks}
     */
    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        List<String> emails = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS));
            cursor.moveToNext();
        }

        addEmailsToAutoComplete(emails);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        //required blank method
    }

    /**
     * Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
     * @param emailAddressCollection is the list of emails previously saved.
     */
    private void addEmailsToAutoComplete(List<String> emailAddressCollection) {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(LoginActivity.this,
                        android.R.layout.simple_dropdown_item_1line, emailAddressCollection);

        mEmailView.setAdapter(adapter);
    }

    /**
     * Open the edit profile fragment to register a new user.
     * @param view is the view that was clicked on.
     */
    public void onClick_Register(View view) {
        EditProfileFragment epf = new EditProfileFragment();
        Bundle args = new Bundle();
        args.putCharSequence(PROFILE_NEW, "none");
        epf.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.login_container, epf, EditProfileFragment.TAG)
                .addToBackStack(null)
                .commit();
        mLoginFormView.setVisibility(View.GONE);
    }

    /**
     * Need this for displaying the login screen again
     * after registering a new account.
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mLoginFormView.setVisibility(View.VISIBLE);
    }

    /**
     * Begins an edit profile task that will insert a new
     * record into the database.
     * @param url is the URL for the database.
     */
    @Override
    public void editProfile(String url) {
        EditProfileTask ept = new EditProfileTask(this);
        ept.execute(url);
    }

    /**
     * This is the editProfileCallback function for the edit profile task
     * that lets the calling object know if it was successful.
     * If it is, it returns to the previous fragment which in this
     * case should be the login form.
     * @param success is true if the task successfully inserted a new record,
     *                false otherwise.
     * @param message is a message to display to the user about the task.
     */
    @Override
    public void editProfileCallback(boolean success, String message) {
        Toast.makeText(getApplicationContext(), message,
                Toast.LENGTH_LONG).show();
        if (success) {
            getSupportFragmentManager().popBackStackImmediate();
            mLoginFormView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * An interface used in the login task. Auto-created by
     * android studio.
     */
    private interface ProfileQuery {
        String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY,
        };

        int ADDRESS = 0;
    }


    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        /**
         * The URL for the database query.
         */
        public static final String url = "http://cssgate.insttech.washington.edu/~_450atm18/login.php";
        /**
         * The strings for the table attributes; also found in string.xml
         */
        public final static String RESULT = "result";
        public final static String USER = "email";
        public final static String SUCCESS = "success";
        public final static String BDAY = "bday";
        public final static String UID = "uid";
        /**
         * The email to send to the database.
         */
        private final String mEmail;
        /**
         * The password to send to the database.
         */
        private final String mPassword;

        /**
         * The response from the database.
         */
        private String response;

        /**
         * Takes the user's email and password to authenticate
         * it against the database.
         * @param email is the user's email.
         * @param password is the user's password.
         */
        UserLoginTask(String email, String password) {
            mEmail = email;
            mPassword = password;
        }

        @Override
        protected Boolean doInBackground(Void... urls) {
            response = "";
            HttpURLConnection urlConnection;
            //some way to add the email and pswd to request...
            String loginurl = url + "?email=" + this.mEmail + "&pwd=" + this.mPassword;
                try {
                    URL urlObject = new URL(loginurl);
                    urlConnection = (HttpURLConnection) urlObject.openConnection();
                    InputStream content = urlConnection.getInputStream();
                    BufferedReader buffer = new BufferedReader(new InputStreamReader(content));
                    String s;
                    while ((s = buffer.readLine()) != null)
                        response += s;
                } catch (MalformedURLException e) {
                    Log.d("Login:do", "MalformedURL; cannot update user data.");
                } catch (IOException e) {
                    Log.d("Login:do", "IOException; could not open URL.");
                    if (Driver.DEBUG) Log.i("Login:do", loginurl);
                } catch (Exception e) {
                    if (Driver.DEBUG) Log.i("Login:do", e.getMessage());
                }
            return response.contains(SUCCESS);
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            showProgress(false);
            if (success) {
                try {
                    JSONObject jo = new JSONObject(response);
                    String status = (String) jo.get(RESULT);
                    //Store user data into shared preferences.
                    if (status.equals(SUCCESS)) {
                        mShared.edit().putBoolean(getString(R.string.LOGGEDIN), true).apply();
                        mShared.edit().putString(getString(R.string.USER), (String) jo.get(USER)).apply();
                        mShared.edit().putString(getString(R.string.BDAY), (String) jo.get(BDAY)).apply();
                        mShared.edit().putString(getString(R.string.UID), (String) jo.get(UID)).apply();
                        startMain();
                    } else if (Driver.DEBUG) {
                        Log.i("Login:post", "Status:" + status);
                    }
                } catch (JSONException e) {
                    if (Driver.DEBUG){
                        Log.d("Login:post", "Could not parse JSON response.");
                        Log.i("Login:post", response);
                        Log.i("Login:post", e.toString());
                    }
                    mShared.edit().putBoolean(getString(R.string.LOGGEDIN), false).apply();
                }
            } else {
                if(Driver.DEBUG) {
                    Log.i("Login:post", "Not successful.");
                    Log.i("Login:post", response);
                }
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }

    /**
     * Id to identity READ_CONTACTS permission request.
     */
    private static final int REQUEST_READ_CONTACTS = 0;

    /**
     * More auto-code.
     */
    private void populateAutoComplete() {
        if (!mayRequestContacts()) {
            return;
        }

        getLoaderManager().initLoader(0, null, this);
    }

    /**
     * This is part of the auto-complete thing for the email field.
     * @return true if permission is granted to read contacts, false otherwise.
     */
    private boolean mayRequestContacts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
            Snackbar.make(mEmailView, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        @TargetApi(Build.VERSION_CODES.M)
                        public void onClick(View v) {
                            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
                        }
                    });
        } else {
            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
        }
        return false;
    }
}

