package com.example.radiocoveragetesting;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.scp.ScpClient;
import org.apache.sshd.client.scp.ScpClientCreator;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.exception.SshChannelOpenException;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.text.DecimalFormat;

/**
 * The activity screen where the stats can be monitored, data collected, and sectors switched.
 * All the primary features of the app is here.
 */
public class testingActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private final String configFileAddress = "bin/ConfigList.csv";
    TextView snrUp, snrDown, peakSnrUp, peakSnrDown, avgPwrUp, avgPwrDown, peakPwrUp; //the display boxes for the stats of corresponding name
    TextView peakPwrDown, currentSector; //The display boxes for peak power down and current sector
    Button startStop, record, confirmSwitch; //poll star/stop button, record button and the ok button
    String host, username, password; //used to store the pi's ip address, username and password
    Integer port; //port number for ssh. Usually is 22
    Handler sshHandler; //Handler for the background thread used for network connection.
    ClientSession sshSession;
    ClientChannel sshChannel;
    ByteArrayOutputStream responseStream; //used to receive output from pi
    Runnable updater, establishSsh; //Runnable objects that update stats and establish ssh. They should be sent to ssh handler when using
    SshClient client;
    Handler mainHandler; //Handler for the main thread, aka UI thread. Used when you want to contact main thread from background thread.
    HandlerThread thread; //background thread used for network connection
    ByteArrayOutputStream errStream; //stream output for errors in network. It exists just in case, but doesn't see any use.
    Spinner sectorSpinner; //spinner is drop-down menu. This one is used for the sector selection
    Boolean retryFetchStat, retrySwitchSector; //used to indicate if a method should retry something in case of failure
    Boolean updateEnabled; //If true, updateStat() will call itself to keep on updating stats.
    Boolean isLoginAttempt, sectorsSet, firstRun;
    ArrayList<String> configFileTranscript; //copy of config file
    String selectedSector; //current sector
    Double highest_snr_up = -100000.0, highest_snr_down = -100000.0;
    ArrayList<String> coverageData = new ArrayList<>(); //array List of coverage data.
    Map<String, ArrayList<String>> config_order = new HashMap<String, ArrayList<String>>();
    ArrayList<String> AreaCombos = new ArrayList<String>();
    testingActivity thisReference = this; //this class sometimes needs to be passed as argument in a situation where 'this' doesn't work.
    Toast toastMsg; //used to show toast messages. Is in field to prevent spamming toasts.
    String updateCommand = "poll \n"; //the command to use to update stats
    String curBaseStation = ""; //current base station
    Boolean shouldSwitchConf = false;

    CoverageData cur_coverage;
    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseReference;

    private final Context thisContext = this; //this object in form of context
    private boolean justStarted; //used to let the app know to ignore first output from pi


    /**
     * Automatically used on creation
     * @param savedInstanceState record of what state the app was in previously
     */
    protected void onCreate(Bundle savedInstanceState) {
        sectorsSet = false;
        firstRun = true;
        retryFetchStat = true;
        retrySwitchSector = true;
        isLoginAttempt = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing);

        Intent intent = getIntent();
        //get login cred from intent
        host = intent.getStringExtra("host");
        port = Integer.parseInt(intent.getStringExtra("port"));
        username = intent.getStringExtra("username");
        password = intent.getStringExtra("password");

        snrUp = findViewById(R.id.SNR_Up);
        snrDown = findViewById(R.id.SNR_Down);
        peakSnrUp = findViewById(R.id.peak_SNR_up);
        peakSnrDown = findViewById(R.id.peak_SNR_down);
        avgPwrUp = findViewById(R.id.avg_PWR_up);
        avgPwrDown = findViewById(R.id.avg_PWR_down);
        peakPwrUp = findViewById(R.id.peak_PWR_up);
        peakPwrDown = findViewById(R.id.peak_PWR_down);
        startStop = findViewById(R.id.start_stop);
        record = findViewById(R.id.record);
        currentSector = findViewById(R.id.cur_sector);
        sectorSpinner = (Spinner) findViewById(R.id.select_area);
        confirmSwitch = findViewById(R.id.confirm_area);

        //code for what the record button should do on being clicked
        //When the button is clicked, it makes the fetchStat() use 'go' command rather than poll, so that the pi records the data.
        //When you click start record when polling button is stopped, it will start as well
        //When you click stop polling when the record is still running, record will stop
        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Following black of code/comment is for firebase, which is still in the works
                //Crashes if you're connected to the Drive-5 Wifi, gotta connect to a diff one
                /*Method doesn't work properly right now for some reason. Code seems to run w/o
                error but the database doesn't update. Don't know how to fix this. */
                //to do: Use try catch statements here
                /*
                System.out.println("Database Upload");
                firebaseDatabase = FirebaseDatabase.getInstance();
                databaseReference = firebaseDatabase.getReference("CoverageData").push();
                for(int i = 0; i < coverageData.size(); i++){
                    cur_coverage = new CoverageData();
                    System.out.println(coverageData.get(i));
                    addDataToFirebase(coverageData.get(i));
                }
                */

                if (record.getText().toString().equals("Start record") || record.getText().toString().equals("Resume record")){
                    updateCommand = "go \n";
                    record.setText(R.string.record_stop);
                    if (startStop.getText().toString().equals("Start poll")){
                        startStop.callOnClick();
                    }
                }
                else { //record button text is currently in stop
                    updateCommand = "poll \n";
                    if(startStop.getText().toString().equals("Start poll")){
                        record.setText(R.string.record_start);
                    }
                    else {
                        record.setText(R.string.record_resume);
                    }

                }
            }
        });


        // Setting user.com property manually
        // since isn't set by default in android
        String key = "user.home";
        Context sysContext;
        sysContext = getApplicationContext();
        String val = sysContext.getApplicationInfo().dataDir;
        System.setProperty(key, val);

        // Creating a ssh client instance
        client = SshClient.setUpDefaultClient();
        client.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        client.start();

        //mainHandler allows a background thread to access main thread and update ui
        thread = new HandlerThread("Network Thread");
        thread.start();
        //sshHandler allows main thread to post runnable to background thread
        sshHandler = new Handler(thread.getLooper());

        //make a runnable to establish ssh session in background
        establishSsh = new Runnable() {
            @Override
            public void run() {
                try {
                    establishSshSession();

                    System.out.println("SSH session established");
                }
                catch (Exception e) {
                    System.out.println("issue 1");
                    e.printStackTrace();
                }
            }
        };
        mainHandler = new Handler(Looper.getMainLooper());


        //activate the said runnable in background
        sshHandler.post(establishSsh);

        //runnable to download config file via ssh
        Runnable goFetchConfig = new Runnable() {
            @Override
            public void run() {
                try {
                    fetchConfig();
                    System.out.println("Config File Fetched");
                    /* deletes dummy test index */
                    AreaCombos.remove("temp");


                }
                catch (Exception e) {
                    System.out.println("issue fetching config");
                    e.printStackTrace();
                }
            }
        };

        //run the above runnable
        sshHandler.post(goFetchConfig);

        //code for what the ok button should do
        confirmSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                switchSector();
                retrySwitchSector = true;
            }
        });


        //Setting onClick Listener for poll Button
        startStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Checks if button should start/stop ssh session
                if(startStop.getText().toString().equals("Start poll")) {
                    // Changes State of Button
                    startStop.setText(R.string.poll_stop);
                    startStop.setBackgroundColor(Color.RED);
                    highest_snr_up = -100000.0;
                    highest_snr_down = -100000.0;
                    updateEnabled = true;


                    System.out.println("ssh established");
                    //create runnable for updates
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

                            //have the
                            mainHandler.post(myRunnable);
                        }
                    };
                    System.out.println("start update");
                    sshHandler.post(updater);
                }
                else {
                    // Changes State of Button
                    startStop.setText(R.string.poll_start);
                    startStop.setBackgroundColor(Color.GREEN);
                    updateEnabled = false;
                    if (record.getText().toString().equals("Pause record")){
                        record.callOnClick();
                    }

                }
            }
        });
    }

    /**
     * Updates stat TextViews and also determines if update should continue
     * Make sure to post it to mainHandler if using it in background
     * @param stat ArrayList of stats
     */
    private void updateStat(ArrayList<String> stat) {
        //TODO: need to make sure the titles are included and formatting is done correctly
        System.out.println("updateStat() running");

        //Checks that correct output line has been selected.
        if (stat.size() > 7) {

            snrUp.setText(getString(R.string.Snr_up_value, stat.get(0))); //good
            snrDown.setText(getString(R.string.Snr_down_value, stat.get(1))); //good

            //determines highest snr up & down
            if(!stat.get(0).equals("") && Double.parseDouble(stat.get(0)) > highest_snr_up) {
                highest_snr_up = Double.parseDouble(stat.get(0));
            }
            if(!stat.get(1).equals("") && Double.parseDouble(stat.get(1)) > highest_snr_down) {
                highest_snr_down = Double.parseDouble(stat.get(1));
            }
            //formats the numbers, then sets te numbers to the display
            DecimalFormat decimalFormat = new DecimalFormat("#.#");
            peakSnrUp.setText(getString(R.string.Peak_snr_up_value, decimalFormat.format(highest_snr_up)));
            peakSnrDown.setText(getString(R.string.Peak_snr_down_value, decimalFormat.format(highest_snr_down)));
            avgPwrUp.setText(getString(R.string.Avg_pwr_up_value, stat.get(4)));
            avgPwrDown.setText(getString(R.string.Avg_pwr_down_value, stat.get(5)));
            peakPwrUp.setText(getString(R.string.Peak_pwr_up_value, stat.get(6), stat.get(7)));
            peakPwrDown.setText(getString(R.string.Peak_pwr_down_value, stat.get(8), stat.get(9)));
            if (stat.get(0).equals("") || stat.get(1).equals("") || stat.get(4).equals("") || stat.get(5).equals("")){
                System.out.println("Radio likely not connected.");
                showToastMsg("Pi returned no data on one or more stat fields. Radio has most likely lost signal.");
            }
        }

        //Checks if it should continue updating stat screen
        if (updateEnabled){
            sshHandler.postDelayed(updater,1000);
        }

    }

    /**
     * Shows a toast with a message, handles case when a toast is already present
     * @param msg message you want to show in String format
     */
    private void showToastMsg(String msg){
        Runnable msgToast = new Runnable() {
            @Override
            public void run() {
                //If a toast message is already showing on screen, erase it and show new toast.
                //Doing this prevents app from creating a massive backlog of toast that persist for minutes
                if(toastMsg != null) {
                    toastMsg.cancel();
                }
                toastMsg = Toast.makeText(thisContext, msg, Toast.LENGTH_SHORT);
                toastMsg.show();

            }
        };
        mainHandler.post(msgToast);
    }

    /**
     * Obtains the current stats and returns it
     * currently uses the command "go"
     * @return Array of list of strings that contain signal stat
     */
    private ArrayList<String> fetchStats() {
        //change the command to new command that returns current stats later
        System.out.println("fetchStats() running)");
        ArrayList<String> ans = new ArrayList<>();
        String[] answerArray = {""};
        try {

            // Open channel
            sshChannel.open().verify(5, TimeUnit.SECONDS);
            sshSession.resetIdleTimeout();

            try {
                OutputStream pipedIn = sshChannel.getInvertedIn();
                pipedIn.write(updateCommand.getBytes());
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
                coverageData.add(answerLine);
                System.out.println("Testing line: " + answerLine);//show it to log to manually check
                //formatting the string
                answerArray = answerLine.split(",");
                System.out.println("Post-process: " +   Arrays.toString(answerArray));
                double[] pwr_downs = new double[]{Double.parseDouble(answerArray[6]),
                        Double.parseDouble(answerArray[7]), Double.parseDouble(answerArray[8]),
                        Double.parseDouble(answerArray[9])};
                double[] pwr_ups = new double[]{Double.parseDouble(answerArray[23]),
                        Double.parseDouble(answerArray[24]), Double.parseDouble(answerArray[25]),
                        Double.parseDouble(answerArray[26])};// handle empty string cases
                double avg_p_downs = (pwr_downs[0] + pwr_downs[1] + pwr_downs[2]+ pwr_downs[3])/4.0;
                double avg_p_ups = (pwr_ups[0] + pwr_ups[1] + pwr_ups[2]+ pwr_ups[3])/4.0;
                double peak_p_down = 10000; double peak_p_up = -10000;
                String p_sector_down = "A"; String p_sector_up = "A";
                String[] sector_conv = new String[]{"A", "B", "C", "D"};
                for(int i = 0; i < 4; i++) {
                    if(pwr_downs[i] < peak_p_down) {
                        peak_p_down = pwr_downs[i];
                        p_sector_down = sector_conv[i];
                    }
                    if(pwr_ups[i] > peak_p_up) {
                        peak_p_up = pwr_ups[i];
                        p_sector_up = sector_conv[i];
                    }
                }
                ans.add(answerArray[22]);  //snrUp-good
                ans.add(answerArray[5]);  //snrDown-good
                ans.add(answerArray[22]);  //peakSnrUp-good
                ans.add(answerArray[5]);  //peakSnrDown-good
                ans.add(Double.toString(avg_p_ups));  //avgPwrUp-good
                ans.add(Double.toString(avg_p_downs));  //avgPwrDown-good
                ans.add(Double.toString(peak_p_up)); //peakPwrUp
                ans.add(p_sector_up); //peakPwrUp Sector
                ans.add(Double.toString(peak_p_down)); //peakPwrDown
                ans.add(p_sector_down); //peakPwrDown Sector
            }
            justStarted = false;
            retryFetchStat = true;
        }
        catch (Exception e) {
            System.out.println("error in opening channel or getting response at fetchStat()");
            //Currently, there is a bug with the library(?) that causes the sshSession to die every ten seconds. In order to get around
            // this issue, the app is set to try to re-establish ssh session once when ssh Session errors out. The two errors below
            // usually indicate the above bug messing up the update attempt.

            //This block of code detects if ssh Session was closed by the bug and then attempts to re-establish ssh session.
            if((e instanceof SshChannelOpenException || e instanceof SshException) && (Objects.requireNonNull(e.getMessage()).trim().equals(
                    "open failed") || Objects.requireNonNull(e.getMessage()).trim().equals(
                    "Session has been closed"))) {
                sshChannel.close(true);
                sshSession.close(true);
                System.out.println("open failed");
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
                if (retryFetchStat) {  //checks that this is not the 2nd time in a row this error has happened
                    retryFetchStat = false;
                    fetchStats();
                }

            }
            //This error usually indicates radios not communicating as it should be
            if(e instanceof ArrayIndexOutOfBoundsException && Objects.requireNonNull(e.getMessage()).trim().equals(
                    "length=1; index=6")){
                if(!answerArray[0].contains("mkdir: cannot create directory"))
                showToastMsg("Radio may be offline. Check that both radios are online and communicating properly.");
            }
            //This error usually happens when wrong sector has been selected
            else if (e instanceof NumberFormatException && Objects.requireNonNull(e.getMessage()).trim().equals(
                    "empty String")){
                showToastMsg("One or more antennae are sending or receiving no signals. Check that the right sector has been selected.");
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
     */
    private void establishSshSession(){
        try {
            sshSession = client.connect(username, host, port).verify(5000).getSession();
            sshSession.addPasswordIdentity(password);
            sshSession.auth().verify(5000);
            sshChannel = sshSession.createChannel(Channel.CHANNEL_SHELL);
            responseStream = new ByteArrayOutputStream();
            sshChannel.setOut(responseStream);
            //set error stream
            errStream = new ByteArrayOutputStream();
            sshChannel.setErr(errStream);
            justStarted = true;


        }
        catch (Exception e) {
            if (isLoginAttempt){
                isLoginAttempt = false;
                Intent loginFailIntent = new Intent(this, MainActivity.class);
                if (e instanceof UnresolvedAddressException ){
                    System.out.println("error with address");
                    loginFailIntent.putExtra("reason", "Invalid address");
                }
                else if (e.getMessage() == null){
                    System.out.println("unknown error during login attempt");
                    loginFailIntent.putExtra("reason", "Unknown");
                }
                else if (e instanceof SshException && e.getMessage().contains("timeout")){
                    System.out.println("timeout error");
                    loginFailIntent.putExtra("reason", "No response from login server");
                }
                else if (e instanceof SshException && e.getMessage().contains("No more authentication methods available")){
                    System.out.println("verification");
                    loginFailIntent.putExtra("reason", "Wrong username or password");
                }
                else {
                    System.out.println("unknown error during login attempt");
                    loginFailIntent.putExtra("reason", "Unknown");
                }
                startActivity(loginFailIntent);
            }

            System.out.println("failed to establish session");
            e.printStackTrace();
        }
    }

    /**
     * Automatically called when the app is to be closed
     * Need to end the network thread or else it will consume resources after app's death
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        sshHandler.removeCallbacksAndMessages(null);
        sshHandler.getLooper().quit();
    }

    /**
     * Determines what action to take when user chooses something on spinner
     * @param adapterView the spinner where selection happened
     * @param view the view that was clicked
     * @param position the location in terms of array
     * @param l row id of the item selected
     */
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
        System.out.println("onItemSelected Activated");
        selectedSector = adapterView.getItemAtPosition(position).toString();
    }

    /**
     * Runs when a view selects nothing
     * @param adapterView the view that selected nothing
     */
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    /**
     * Runs when you hit the confirm sector switch button, commands pi to change config file
     */
    public void switchSector() {
        shouldSwitchConf = false;
        String switchCommandArg;
        String switchSecCommandArg;
        String pushCommandArg;
        //TODO: find out what the arguments for switch command should be for each sector
        if(selectedSector == null){
            showToastMsg("Retry after ssh connection is established.");
            return;
        }
        String[] selectedSectorSplit = selectedSector.split(" - ");
        String baseStation = selectedSectorSplit[0];
        String sector = selectedSectorSplit[1];
        if(!baseStation.equals(curBaseStation)) {
            shouldSwitchConf = true;
        }
        curBaseStation = baseStation;
        String selectedRadioTab = "";
        String configFilePath = "";
        Boolean matchNotFound = true;
        for(String s: configFileTranscript){
            String[] temp = s.split(",");
            if (baseStation.equals(temp[0]) && sector.equals(temp[1])){
                selectedRadioTab = temp[2];
                configFilePath = temp[3];
                matchNotFound = false;
            }

        }
        if(matchNotFound){
            System.out.println("matching base station/sector not found.");
            //If this line runs, double check the config file
            showToastMsg("Double check that the config file is correct.");
        }
        //everything from here on will need adjustment later
        switchCommandArg = baseStation + " " + sector;
        switchSecCommandArg = selectedRadioTab;
        System.out.println("switchSecCommandArg" + switchSecCommandArg);
        pushCommandArg = configFilePath;
        System.out.println("pushCommandArg" + pushCommandArg);
        highest_snr_up = -100000.0;
        highest_snr_down = -100000.0;

        Runnable switchSector = new Runnable() {
            @Override
            public void run() {
                try {
                    String switchCommand = "switch " + switchCommandArg + "\n";
                    String switchSecCommand = "./bin/switch_sec " + switchSecCommandArg + "\n";
                    String pushCommand = "./bin/pushConfig " + pushCommandArg + "\n";

                    // Open channel
                    sshChannel.open().verify(5, TimeUnit.SECONDS);
                    sshSession.resetIdleTimeout();

                    try {
                        OutputStream pipedIn = sshChannel.getInvertedIn();
                        pipedIn.write(switchCommand.getBytes());
                        System.out.println("sending command");
                        pipedIn.flush();

                        pipedIn.write(switchSecCommand.getBytes());
                        pipedIn.flush();
                        if(shouldSwitchConf) {
                            pipedIn.write(pushCommand.getBytes());
                            pipedIn.flush();
                        }

                        pipedIn.close();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    //TODO: when sending switch command, the output stream's new text can
                    // mess up formatting for next stat display update. Find a way around this.

                    retrySwitchSector = true;

                    switchSectorSuccess(selectedSector);
                    System.out.println("selected sector: " + selectedSector);

                }
                catch (Exception e) {
                    System.out.println("error in opening channel or getting response at switchSector()");
                    if((e instanceof SshChannelOpenException || e instanceof SshException) && (Objects.requireNonNull(e.getMessage()).trim().equals(
                            "open failed") || Objects.requireNonNull(e.getMessage()).trim().equals(
                            "Session has been closed"))) {
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
                        if (retrySwitchSector) {  //checks that this is not the 2nd time in a row error has happened
                            retrySwitchSector = false;
                            switchSector();
                        }

                    }
                    System.out.println("stacktrace for why first attempt failed");
                    e.printStackTrace();
                }
            }
        };

        sshHandler.post(switchSector);
//        sector_switched_before = true;
    }

    /**
     * Used to make UI reflect successful sector switch
     * @param newSectorName name of the sector you are switching to
     */
    public void switchSectorSuccess(String newSectorName) {
        Runnable updateSector = new Runnable() {
            @Override
            public void run() {
                System.out.println("new sector name: " + newSectorName);
                currentSector.setText(getString(R.string.Current_sector, newSectorName));
            }
        };

        mainHandler.post(updateSector);
        showToastMsg("Sector changed");
    }

    /**
     * Adds line of data to the database. Still in development.
     * @param wholeString the string containing data to add to the database
     */

    //TODO: Change to parse all of the different statistics independently
    private void addDataToFirebase(String wholeString) {
        // below are the lines of code is used to set the data in our object class.
        cur_coverage.setWholeString(wholeString);
        //databaseReference.child("CoverageData").setValue(cur_coverage);
        // we are use add value event listener method
        // which is called with database reference.
//        databaseReference.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                // inside the method of on Data change we are setting
//                // our object class to our database reference.
//                // data base reference will sends data to firebase.
//
//
//                // after adding this data we are showing toast message.
//               Toast.makeText(MainActivity.this, "data added", Toast.LENGTH_SHORT).show();
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                // if the data is not added or it is cancelled then
//                // we are displaying a failure toast message.
////                Toast.makeText(MainActivity.this, "Fail to add data " + error, Toast.LENGTH_SHORT).show();
//            }
//        });
    }

    /**
     * Uses Secure copy protocol to download base station config file,
     * then calls processConfig() to process the file
     */
    private void fetchConfig() {
        System.out.println("fetchConfig() running)");
        ArrayList<String> ans = new ArrayList<>();
        try {
            // Open channel
            sshChannel.open().verify(5, TimeUnit.SECONDS);
            sshSession.resetIdleTimeout();

            //Example use of scp creator: https://stackoverflow.com/questions/62692515/how-to-upload-download-files-using-apache-sshd-scpclient
            File configFile = new File(getFilesDir() + "/config.csv");
            FileOutputStream fileOutputStream = new FileOutputStream(configFile);
            ScpClientCreator creator = ScpClientCreator.instance();
            ScpClient scpClient = creator.createScpClient(sshSession);
            scpClient.download(configFileAddress, fileOutputStream);
            ArrayList<String> config = new ArrayList<>();
            try {
                Scanner scanner = new Scanner(new File(getFilesDir() + "/config.csv"));
                while(scanner.hasNextLine()) {
                    String cur_line = scanner.nextLine();
                    config.add(cur_line);
                }
                System.out.println("Sample config file data: line 1 & 2: " + config.get(0) + config.get(1));
                processConfigs(config);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
        catch (Exception e) {
            System.out.println("error in opening channel or getting response at fetchConfig()");

            if((e instanceof SshChannelOpenException || e instanceof SshException) && (Objects.requireNonNull(e.getMessage()).trim().equals(
                    "open failed") || Objects.requireNonNull(e.getMessage()).trim().equals(
                    "Session has been closed"))) {
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
            }
            System.out.println("stacktrace for fetchConfig() failing");
            e.printStackTrace();
        }
    }

    /**
     * Method to process string extracted from config csv file
     * @param items  string ArrayList containing info from the config file
     */
    void processConfigs(ArrayList<String> items) {
        configFileTranscript = items;
        //Order is as follows: Area name, Sector label, Radio tab, config file directory
        //Order will be subject to change
        //BaseStations arraylist stores list of base stations
        for(int i = 0; i < items.size(); i++) {
            String[] temp = items.get(i).split(",");
            System.out.println(i + ": " + config_order.get(temp[0]));
            List<String> itemsList = config_order.get(temp[0]);

            // if list does not exist create it
            if(itemsList == null) {
                itemsList = new ArrayList<String>();
                itemsList.add(temp[1]);
                config_order.put(temp[0], (ArrayList<String>) itemsList);

            }
            else {
                // add if item is not already in list
                if(!itemsList.contains(temp[1])) {
                    itemsList.add(temp[1]);
                }
            }
        }
        String[] temp = items.get(0).split(",");
        System.out.println(config_order.get(temp[0]));
        temp = items.get(1).split(",");
        System.out.println(config_order.get(temp[0]));
        temp = items.get(2).split(",");
        System.out.println(config_order.get(temp[0]));
        Iterator hmIterator = config_order.entrySet().iterator();
        while (hmIterator.hasNext()) {
            Map.Entry mapElement = (Map.Entry)hmIterator.next();
            ArrayList<String> marks = (ArrayList<String>) mapElement.getValue();
            for(int i = 0; i < marks.size(); i++) {
                AreaCombos.add(mapElement.getKey() + " - " + marks.get(i));
            }
        }

        //Makes main thread set up the spinner now that the arraylist is populated
        Runnable setUpSpinner = new Runnable() {
            @Override
            public void run() {
                ArrayAdapter<String> adapter1 = new ArrayAdapter<> (thisContext, android.R.layout.simple_spinner_item, AreaCombos);
                adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                sectorSpinner.setAdapter(adapter1);
                //sectorSpinner.setSelection(0,false);
                adapter1.notifyDataSetChanged();
                sectorSpinner.setOnItemSelectedListener(thisReference);
                sectorSpinner.setSelection(1);
            }
        };
        mainHandler.post(setUpSpinner);
    }
}
