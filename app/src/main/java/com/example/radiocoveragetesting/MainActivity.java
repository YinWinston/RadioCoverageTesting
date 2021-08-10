package com.example.radiocoveragetesting;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * The main screen of the app where you can initiate ssh session by logging in
 */
public class MainActivity extends AppCompatActivity {

    /**
     * Sets up the activity screen for use. Automatically runs when starting activity.
     * Sets up the login button and if the user was sent here due to issue logging in,
     * shows the error message
     *
     * @param savedInstanceState bundle used to restore itself to previous state in certain cases
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button login = findViewById(R.id.button);
        Intent intent = this.getIntent();
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                test(v);
            }
        });

        //Checks if the user was redirected to this screen due to error on login
        //Then give a toast message as to why it may have happened
        if(intent.getExtras() != null){
            String reason = intent.getStringExtra("reason");

            Toast toast;
            if (reason.equals("Invalid address")){
                toast = Toast.makeText(this, "Invalid address", Toast.LENGTH_LONG);
            }
            else if (reason.equals("No response from login server")){
                toast = Toast.makeText(this, "Login attempt timed out", Toast.LENGTH_LONG);
            }
            else if (reason.equals("Wrong username or password")){
                toast = Toast.makeText(this, "Wrong username or password", Toast.LENGTH_LONG);
            }
            else {
                toast = Toast.makeText(this, "Login failed", Toast.LENGTH_LONG);
            }

            toast.show();
        }
    }

    /**
     * Creates and starts intent to begin sshActivity screen.
     * Currently not in use.
     * @param view the view that was clicked
     */
    public void authenticate(View view) {

        // Create an intent for sshActivity
        Intent intent = new Intent(this, sshActivity.class);

        // Declare fields
        EditText editText = (EditText) findViewById(R.id.editText);
        EditText portField = (EditText) findViewById(R.id.portField);
        EditText usernameField = (EditText) findViewById(R.id.usernameField);
        EditText passwordField = (EditText) findViewById(R.id.passwordField);

        // Get input data from fields
        String host = editText.getText().toString();
        String port = portField.getText().toString();
        String username = usernameField.getText().toString();
        String password = passwordField.getText().toString();

        // Pass on data to sshActivity via intent
        intent.putExtra("host", host);
        intent.putExtra("port", port);
        intent.putExtra("username", username);
        intent.putExtra("password", password);
        startActivity(intent);
        finish();
    }

    /**
     * Creates and starts intent to begin testingActivity screen
     * @param view the view that was clicked
     */
    public void test(View view) {

        // Create an intent for sshActivity
        Intent intent = new Intent(this, testingActivity.class);

        // Declare fields
        EditText editText = (EditText) findViewById(R.id.editText);
        EditText portField = (EditText) findViewById(R.id.portField);
        EditText usernameField = (EditText) findViewById(R.id.usernameField);
        EditText passwordField = (EditText) findViewById(R.id.passwordField);

        // Get input data from fields
        String host = editText.getText().toString();
        String port = portField.getText().toString();
        String username = usernameField.getText().toString();
        String password = passwordField.getText().toString();

        // Pass on data to sshActivity via intent
        intent.putExtra("host", host);
        intent.putExtra("port", port);
        intent.putExtra("username", username);
        intent.putExtra("password", password);
        startActivity(intent);
        finish();
    }
}
