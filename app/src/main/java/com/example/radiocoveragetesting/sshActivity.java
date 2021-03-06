package com.example.radiocoveragetesting;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

/**
 * Activity that was used to test ssh functionality. Is no longer accessible in the app and is not in use.
 *
 * This activity was made to test and learn about the ssh library before we created testingActivity.
 * It serves no real purpose in the app anymore.
 */
public class sshActivity extends AppCompatActivity {

    ClientChannel channel;
    TextView shellOutput;
    String host, username, password;
    Integer port;
    String command;

    /**
     * Sets up the activity screen, establishes ssh connection, and then sends go command.
     * Automatically runs when starting activity.
     * @param savedInstanceState bundle that can be used to restore the activity to previous state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssh);
        System.out.println("lol");
        // set output field
        shellOutput = findViewById(R.id.textView);

        // Get user credentials from intent
        Intent intent = getIntent();
        host = intent.getStringExtra("host");
        port = Integer.parseInt(intent.getStringExtra("port"));
        username = intent.getStringExtra("username");
        password = intent.getStringExtra("password");

        // Command which will be executed
        command = "go\n";

        // Setting user.com property manually 
        // since isn't set by default in android
        String key = "user.home";
        Context Syscontext;
        Syscontext = getApplicationContext();
        String val = Syscontext.getApplicationInfo().dataDir;
        System.setProperty(key, val);

        // Creating a client instance
        final SshClient client = SshClient.setUpDefaultClient();
        client.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        client.start();

        // Starting new thread because network processes 
        // can interfere with UI if started in main thread
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Connection establishment and authentication
                    try (ClientSession session = client.connect(username, host, port).verify(10000).getSession()) {
                        session.addPasswordIdentity(password);
                        session.auth().verify(50000);
                        System.out.println("Connection established");

                        // Create a channel to communicate
                        channel = session.createChannel(Channel.CHANNEL_SHELL);
                        System.out.println("Starting shell");

                        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
                        channel.setOut(responseStream);

                        // Open channel
                        channel.open().verify(5, TimeUnit.SECONDS);
                        try (OutputStream pipedIn = channel.getInvertedIn()) {
                            pipedIn.write(command.getBytes());
                            pipedIn.flush();
                        }

                        // Close channel
                        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED),
                                TimeUnit.SECONDS.toMillis(5));

                        // Output after converting to string type
                        String responseString = new String(responseStream.toByteArray());
                        System.out.println(responseString);
                        System.out.println("ll");
                        shellOutput.setText(responseString);

                        //lets try looping this to test how quick session closed
                        // Open channel 2nd time
                        // channel.open().verify(5, TimeUnit.SECONDS);
                        command = "go\n";
                        try (OutputStream pipedIn = channel.getInvertedIn()) {
                            pipedIn.write(command.getBytes());
                            pipedIn.flush();
                        }

                        // Close channel
                        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED),
                                TimeUnit.SECONDS.toMillis(5));
                        // Output after converting to string type
                        String responseString2 = new String(responseStream.toByteArray());
                        System.out.println(responseString2);
                        shellOutput.setText(responseString2);



                    } catch (IOException e) {
                        System.out.println("lol1");
                        e.printStackTrace();
                    } finally {
                        System.out.println("lol2");
                        client.stop();
                    }
                } catch (Exception e) {
                    System.out.println("lol3");
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
}