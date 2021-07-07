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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.exception.SshChannelOpenException;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class testingActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    TextView snrUp;
    TextView snrDown;
    TextView peakSnrUp;
    TextView peakSnrDown;
    TextView avgPwrUp;
    TextView avgPwrDown;
    TextView peakPwrUp;
    TextView peakPwrDown;
    TextView currentSector;
    Button startStop;
    Button export;
    Button confirmSwitch;
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
    Spinner spinnerBaseStation, spinnerSector;
    Boolean retryFetchStat, retrySwitchSector;
    Boolean updateEnabled;
    String selectedSector;
    Double highest_snr_up = -100000.0, highest_snr_down = -10000.0;
    private final Context thisContext = this;
    private boolean justStarted;


    /**
     * Automatically used on creation
     * @param savedInstanceState record of what state the app was in previously
     */
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("test", "the testingActivity works");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing);
        retryFetchStat = true;
        retrySwitchSector = true;
        spinnerBaseStation = findViewById(R.id.select_sector);
        ArrayAdapter<CharSequence>adapter1 = ArrayAdapter.createFromResource(this, R.array.Base_station_list, android.R.layout.simple_spinner_item);
        adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBaseStation.setAdapter(adapter1);
        spinnerBaseStation.setOnItemSelectedListener(this);

        spinnerSector = findViewById(R.id.select_tower);



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
        currentSector = findViewById(R.id.cur_sector);
        confirmSwitch = findViewById(R.id.confirm_sector);

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
        mainHandler = new Handler(Looper.getMainLooper());


        //activate the said runnable in background
        sshHandler.post(establishSsh);

        confirmSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                switchSector();
                retrySwitchSector = true;
            }
        });



        //Setting onClick Listener for Start/Stop Button
        startStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Checks if button should start/stop ssh session
                if(startStop.getText().equals("Start")) {
                    // Changes State of Button
                    startStop.setText("Stop");
                    startStop.setBackgroundColor(Color.RED);

                    updateEnabled = true;


                    System.out.println("ssh established");
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
                            //sshHandler.postDelayed(updater,1000); //updateStat() does this now
                        }
                    };
                    System.out.println("start update");
                    sshHandler.post(updater);
                }
                else {
                    // Changes State of Button
                    startStop.setText("Start");
                    startStop.setBackgroundColor(Color.GREEN);
                    updateEnabled = false;
                    /*
                    try {
                        sshHandler.removeCallbacks(updater);
                        //sshHandler.removeCallbacks(establishSsh);
                        //client.close();
                        //thread.interrupt();
                        System.out.println("Successfully Closed");
                    }


                    catch(Exception e) {
                        System.out.println("Either failed to close client or client did not exist");
                    }
                    */

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

        if (stat.size() > 7) {

            snrUp.setText("SNR Up \n" + stat.get(0)); //good
            snrDown.setText("SNR Down \n" + stat.get(1)); //good

            if(Double.parseDouble(stat.get(0)) > highest_snr_up) {
                highest_snr_up = Double.parseDouble(stat.get(0));
            }
            if(Double.parseDouble(stat.get(1)) > highest_snr_down) {
                highest_snr_down = Double.parseDouble(stat.get(1));
            }
            peakSnrUp.setText("Peak SNR Up \n" + String.format(Locale.getDefault(), "%f", highest_snr_up));
            peakSnrDown.setText("Peak SNR Down \n" + String.format(Locale.getDefault(), "%f", highest_snr_down));
            avgPwrUp.setText("AVG PWR Up \n" + stat.get(4));
            avgPwrDown.setText("AVG PWR Down \n" + stat.get(5));
            peakPwrUp.setText("Peak PWR Up \n" + stat.get(6) + " (" + stat.get(7) + ")");
            peakPwrDown.setText("Peak PWR Down \n" +stat.get(8) + " (" + stat.get(9) + ")");
        }

        if (updateEnabled){
            sshHandler.postDelayed(updater,1000);
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
                System.out.println("Post-process: " +   Arrays.toString(answerArray));
                //I just did response 4-11 because I don't know the actual format of stats
                double[] pwr_downs = new double[]{Double.parseDouble(answerArray[6]),
                        Double.parseDouble(answerArray[7]),Double.parseDouble(answerArray[8]),
                        Double.parseDouble(answerArray[9])};
                double[] pwr_ups = new double[]{Double.parseDouble(answerArray[21]),
                        Double.parseDouble(answerArray[22]),Double.parseDouble(answerArray[23]),
                        Double.parseDouble(answerArray[24])};
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
                ans.add(answerArray[20]);  //snrUp-good
                ans.add(answerArray[5]);  //snrDown-good
                ans.add(answerArray[20]);  //peakSnrUp-good
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
                if (retryFetchStat) {  //checks that this is not the 2nd time in a row error has happened
                    retryFetchStat = false;
                    fetchStats();
                }

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
     * @param adapterView the spinner
     * @param view I think context?
     * @param position the location in terms of array
     * @param l no idea
     */
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
        Spinner spin = (Spinner) adapterView;
        //String[] baseStationList = getResources().getStringArray(R.array.Base_station_list);
        int sectorListId;
        if(spin.getId() == R.id.select_sector)
        {
            String selectedBaseStation = adapterView.getItemAtPosition(position).toString();
            switch (selectedBaseStation) {
                case "DEA DO":
                    sectorListId = R.array.DEA_DO_sector_list;
                    break;
                case "Edinberg":
                    sectorListId = R.array.Edinberg_sector_list;
                    break;
                case "Mission TX":
                    sectorListId = R.array.Mission_sector_list;
                    break;
                case "Weslaco":
                    sectorListId = R.array.Weslaco_sector_list;
                    break;
                default:
                    sectorListId = R.array.DEA_DO_sector_list; //merely a default. Should never happen
                    System.out.println("problem processing base selection in onItemSelection()");
            }

            ArrayAdapter<CharSequence>adapter2 = ArrayAdapter.createFromResource(this, sectorListId, android.R.layout.simple_spinner_item);
            adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerSector.setAdapter(adapter2);
            spinnerSector.setOnItemSelectedListener(this);

        }
        else if(spin.getId() == R.id.select_tower)
        {
            selectedSector =  adapterView.getItemAtPosition(position).toString();
        }
    }

    /**
     * runs when a view selects nothing
     * @param adapterView the view that selected nothing
     */
    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    /**
     * Runs when you hit the confirm sector switch button, commands pi to change config file
     */
    public void switchSector() {
        String commandArg;
        //TODO: find out what the arguments for switch command should be for each sector
        switch (selectedSector) {
            case "DEA_NW":
                commandArg = "placeholder";
                break;
            case "DEA_NE":
                commandArg = "";
                break;
            case "DEA_SE":
                commandArg = "";
                break;
            case "DEA_SW":
                commandArg = "";
                break;
            case "Edinberg_W":
                commandArg = "";
                break;
            case "Edinberg_E":
                commandArg = "";
                break;
            case "Edinberg_S":
                commandArg = "";
                break;
            case "Mission_W":
                commandArg = "";
                break;
            case "Mission_N":
                commandArg = "";
                break;
            case "Mission_E":
                commandArg = "";
                break;
            case "Weslaco_NW":
                commandArg = "";
                break;
            case "Weslaco_NE":
                commandArg = "";
                break;
            case "Weslaco_SE":
                commandArg = "";
                break;
            case "Weslaco_SW":
                commandArg = "";
                break;
            default:
                commandArg = "";
                System.out.println("This line should not run");
        }

        Runnable switchSector = new Runnable() {
            @Override
            public void run() {
                try {
                    String command = "switch " + commandArg + "\n";

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
                    //TODO: when sending switch command, the output stream's new text can
                    // mess up formatting for next update. Find a way around this

                    retrySwitchSector = true;
                    switchSectorSuccess(selectedSector);

                    /*
                    //You gotta capture the string rather than
                    //scan it with scanner line-by-line because the stream constantly adds more
                    String responseString = new String(responseStream.toByteArray());
                    System.out.println("response string: \n" + responseString);
                    //break down the string into lines
                    String[] response = responseString.trim().split("\n");
                    */

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
    }

    /**
     * Used to make UI reflect successful sector switch
     * @param newSectorName name of the sector you are switching to
     */
    public void switchSectorSuccess(String newSectorName) {
        Runnable updateSector = new Runnable() {
            @Override
            public void run() {
                currentSector.setText("Current sector: " + newSectorName);
                Toast toast = Toast.makeText(thisContext, "Sector changed", Toast.LENGTH_SHORT);
                toast.show();
            }
        };
        mainHandler.post(updateSector);
    }
}
