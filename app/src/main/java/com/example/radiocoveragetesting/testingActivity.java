package com.example.radiocoveragetesting;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Spinner;
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

public class testingActivity extends AppCompatActivity {
    TextView displayTitle, stat_title, snr_up, snr_down, peak_snr_up, peak_snr_down;
    TextView avg_pwr_up, avg_pwr_down, peak_pwr_up, peak_pwr_down, switch_config, cur_sector;
    Button start_stop, export, set_sector, set_tower;
    Spinner select_sector, select_tower;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing);
        displayTitle = findViewById(R.id.displayTitle);
        stat_title = findViewById(R.id.Stat_title);
        snr_up = findViewById(R.id.SNR_Up);
        snr_down = findViewById(R.id.SNR_Down);
        peak_snr_up = findViewById(R.id.peak_SNR_up);
        peak_snr_down = findViewById(R.id.peak_SNR_down);
        avg_pwr_up = findViewById(R.id.avg_PWR_up);
        avg_pwr_down = findViewById(R.id.avg_PWR_down);
        peak_pwr_up = findViewById(R.id.peak_PWR_up);
        peak_pwr_down = findViewById(R.id.peak_PWR_down);
        switch_config = findViewById(R.id.switch_config);
        cur_sector = findViewById(R.id.cur_sector);
        start_stop = findViewById(R.id.start_stop);
        export = findViewById(R.id.export);
        set_sector = findViewById(R.id.ok_sector);
        set_tower = findViewById(R.id.ok_tower);
        select_sector = findViewById(R.id.select_sector);
        select_tower = findViewById(R.id.select_tower);
    }
}
