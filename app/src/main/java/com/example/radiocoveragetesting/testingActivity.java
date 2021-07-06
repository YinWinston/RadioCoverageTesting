package com.example.radiocoveragetesting;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.exception.SshChannelOpenException;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class testingActivity extends AppCompatActivity {

    TextView snrUp;
    TextView snrDown;
    TextView peakSnrUp;
    TextView peakSnrDown;
    TextView avgPwrUp;
    TextView avgPwrDown;
    TextView peakPwrUp;
    TextView peakPwrDown;
    Button startStop;
    Button export;
    String host, username, password;
    Integer port;
    Handler sshHandler;
    ClientSession sshSession;
    ClientChannel sshChannel;
    ByteArrayOutputStream responseStream;
    Runnable updater, establishSsh;
    SshClient client;
    Handler mainHandler;
    HandlerThread thread;
    ByteArrayOutputStream errStream;
    private boolean justStarted;



    protected void onCreate(Bundle savedInstanceState) {
        Log.d("test", "the testingActivity works");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing);

        Intent intent = getIntent();

        snrUp = findViewById(R.id.SNR_Up);
        snrDown = findViewById(R.id.SNR_Down);
        peakSnrUp = findViewById(R.id.peak_SNR_up);
        peakSnrDown = findViewById(R.id.peak_SNR_down);
        avgPwrUp = findViewById(R.id.avg_PWR_up);
        avgPwrDown = findViewById(R.id.avg_PWR_down);
        peakPwrUp = findViewById(R.id.peak_PWR_up);
        peakPwrDown = findViewById(R.id.peak_PWR_down);
        startStop = findViewById(R.id.start_stop);
        export = findViewById(R.id.export);

        //get login cred from intent
        host = intent.getStringExtra("host");
        port = Integer.parseInt(intent.getStringExtra("port"));
        username = intent.getStringExtra("username");
        password = intent.getStringExtra("password");

        //I don't get what this does, but the code breaks without it
        // Setting user.com property manually
        // since isn't set by default in android
        String key = "user.home";
        Context sysContext;
        sysContext = getApplicationContext();
        String val = sysContext.getApplicationInfo().dataDir;
        System.setProperty(key, val);



        //Setting onClick Listener for Start/Stop Button
        startStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Checks if button should start/stop ssh session
                if(startStop.getText().equals("Start")) {
                    // Changes State of Button
                    startStop.setText("Stop");
                    startStop.setBackgroundColor(Color.RED);

                    // Creating a client instance
                    client = SshClient.setUpDefaultClient();
                    client.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
                    client.start();
                    //mainHandler allows a background thread to access main thread and update ui
                    //mainHandler = new Handler();



                    thread = new HandlerThread("MyHandlerThread");
                    thread.start();
                    //sshHandler allows main thread to post runnable to background thread
                    sshHandler = new Handler(thread.getLooper());

                    //make a runnable to establish ssh session in background
                    establishSsh = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                establishSshSession();
                            }
                            catch (Exception e) {
                                System.out.println("issue 1");
                                e.printStackTrace();
                            }
                        }
                    };

                    //activate the said runnable in background
                    sshHandler.post(establishSsh);

                    System.out.println("ssh established");
                    mainHandler = new Handler(Looper.getMainLooper());

                    //create another runnable, for updates
                    updater = new Runnable() {
                        @Override
                        public void run() {
                            ArrayList<String> stat = fetchStats();

                            Runnable myRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    updateStat(stat);
                                    System.out.println("myRunnable running");
                                }
                            };

                            mainHandler.post(myRunnable);
                            sshHandler.postDelayed(updater,1000);
                        }
                    };
                    System.out.println("start update");
                    sshHandler.post(updater);
                }
                else {
                    // Changes State of Button
                    startStop.setText("Start");
                    startStop.setBackgroundColor(Color.GREEN);
                    try {
                        sshHandler.removeCallbacks(updater);
                        sshHandler.removeCallbacks(establishSsh);
                        client.close();
                        thread.interrupt();
                        System.out.println("Successfully Closed");
                    }
                    catch(Exception e) {
                        System.out.println("Either failed to close client or client did not exist");
                    }
                }
            }
        });
    }

    /**
     * Updates stat TextViews
     * Make sure to post it to mainHandler if using it in background
     * @param stat ArrayList of stats
     */
    private void updateStat(ArrayList<String> stat) {

        System.out.println("updateStat() running");

        if (stat.size() > 7) {

            snrUp.setText(stat.get(0));
            snrDown.setText(stat.get(1));
            peakSnrUp.setText(stat.get(2));
            peakSnrDown.setText(stat.get(3));
            avgPwrUp.setText(stat.get(4));
            avgPwrDown.setText(stat.get(5));
            peakPwrUp.setText(stat.get(6));
            peakPwrDown.setText(stat.get(7));
        }

    }


    /**
     * Obtains the current stats and returns it
     * currently uses the command "go"
     * @return Array of list of strings that contain signal stat
     */
    private ArrayList<String> fetchStats() {
        //change the command to new command that returns current stats later
        String command = "go\n";
        System.out.println("fetchStats() running)");
        ArrayList<String> ans = new ArrayList<>();
        try {

            // Open channel
            sshChannel.open().verify(5, TimeUnit.SECONDS);
            sshSession.resetIdleTimeout();

            try {
                OutputStream pipedIn = sshChannel.getInvertedIn();
                pipedIn.write(command.getBytes());
                System.out.println("sending command");
                pipedIn.flush();
                pipedIn.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            //You gotta capture the string rather than
            //scan it with scanner line-by-line because the stream constantly adds more
            String responseString = new String(responseStream.toByteArray());
            System.out.println("response string: \n" + responseString);
            //break down the string into lines
            String[] response = responseString.trim().split("\n");

            //if this is first time the method is running since ssh connection
            //was established, the output format will be wrong, so skip it once
            if(!justStarted) {
                //find the line containing the info we want
                String answerLine = response[response.length - 2];
                System.out.println("Testing line: " + answerLine);//show it to log to manually check
                //formatting the string
                String[] answerArray = answerLine.split(",");

                //I just did response 4-11 because I don't know the actual format of stats
                ans.add(answerArray[4]);  //snrUp
                ans.add(answerArray[5]);  //snrDown
                ans.add(answerArray[6]);  //peakSnrUp
                ans.add(answerArray[7]);  //peakSnrDown
                ans.add(answerArray[8]);  //avgPwrUp
                ans.add(answerArray[9]);  //avgPwrDown
                ans.add(answerArray[10]); //peakPwrUp
                ans.add(answerArray[11]); //peakPwrDown
            }
            justStarted = false;
        }
        catch (Exception e) {
            System.out.println("error in opening channel or getting response at fetchStat()");
            if(e instanceof SshChannelOpenException && Objects.requireNonNull(e.getMessage()).trim().equals(
                    "open failed")) {
                sshChannel.close(true);
                sshSession.close(true);
                try {
                    responseStream.close();
                }
                catch (IOException e2) {
                    e2.printStackTrace();
                }
                try {
                    errStream.close();
                }
                catch (IOException e2) {
                    e2.printStackTrace();
                }
                establishSshSession();
                fetchStats();

            }
            System.out.println("stacktrace for why first attempt failed");
            e.printStackTrace();
        }
        return ans;
    }

    /**
     * Establishes sshSession
     * Pretty sure that you need to call it in background thread.
     * use sshHandler.post() to run it in background
     * @return true if session is successfully established, else false
     */
    private boolean establishSshSession(){

        try {
            sshSession = client.connect(username, host, port).verify(10000).getSession();
            sshSession.addPasswordIdentity(password);
            sshSession.auth().verify(50000);
            sshChannel = sshSession.createChannel(Channel.CHANNEL_SHELL);
            responseStream = new ByteArrayOutputStream();
            sshChannel.setOut(responseStream);
            //set error stream
            errStream = new ByteArrayOutputStream();
            sshChannel.setErr(errStream);
            justStarted = true;
            return true;
        }
        catch (Exception e) {
            System.out.println("failed to establish session");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sshHandler.removeCallbacksAndMessages(null);
        sshHandler.getLooper().quit();
    }
}
