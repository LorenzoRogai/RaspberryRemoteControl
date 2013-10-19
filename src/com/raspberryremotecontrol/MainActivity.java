package com.raspberryremotecontrol;

import com.jcraft.jsch.*;
import android.view.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.*;
import android.content.*;
import android.net.*;
import java.util.*;
import java.io.*;
import java.text.*;

public class MainActivity extends Activity {

    SharedPreferences prefs = null;
    ChannelExec channel;
    Session session;
    BufferedReader in;
    InfoAdapter adapter;
    private ListView listView;
    Info infos[] = new Info[]{
        new Info(R.drawable.hostname, "Hostname", "", -1),
        new Info(R.drawable.distribution, "Distribution", "", -1),
        new Info(R.drawable.kernel, "Kernel", "", -1),
        new Info(R.drawable.firmware, "Firmware", "", -1),
        new Info(R.drawable.cpuheat, "Cpu Heat", "", -1),
        new Info(R.drawable.uptime, "Uptime", "", -1),
        new Info(R.drawable.ram, "Ram Info", "", -1)
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        prefs = getSharedPreferences("com.raspberryremotecontrol", MODE_PRIVATE);

        if (prefs.getBoolean("firstrun", true)) {
            ShowSetupDialog();
            prefs.edit().putBoolean("firstrun", false).commit();
        } else {
            ConnectSSH();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.showsettings:
                ShowSetupDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.menu, menu);
        return true;
    }

    public void ShowSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialog_layout = getLayoutInflater().inflate(R.layout.settings_dialog_layout, null);

        builder.setTitle("Connection Settings");

        final EditText IpAddress = (EditText) dialog_layout.findViewById(R.id.ipaddress);
        final EditText username = (EditText) dialog_layout.findViewById(R.id.sshusername);
        final EditText password = (EditText) dialog_layout.findViewById(R.id.sshpassword);

        String currIpAddress = prefs.getString("ipaddress", null);
        String currusername = prefs.getString("sshusername", null);
        String currpassword = prefs.getString("sshpassword", null);

        IpAddress.setText(currIpAddress);
        username.setText(currusername);
        password.setText(currpassword);

        builder.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                prefs.edit().putString("ipaddress", IpAddress.getText().toString()).commit();
                prefs.edit().putString("sshusername", username.getText().toString()).commit();
                prefs.edit().putString("sshpassword", password.getText().toString()).commit();

                ConnectSSH();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        });

        AlertDialog Dialog = builder.create();
        Dialog.setView(dialog_layout);
        Dialog.show();
    }

    public boolean isOnline() {
        ConnectivityManager conMgr = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conMgr.getActiveNetworkInfo();

        if (netInfo == null || !netInfo.isConnected() || !netInfo.isAvailable()) {
            return false;
        }
        return true;
    }

    public void BuildList() {
        if (adapter == null) {
            adapter = new InfoAdapter(this,
                    R.layout.listview_item_row, infos);

            listView = (ListView) findViewById(R.id.listView);
            listView.setAdapter(adapter);
        } else {
            adapter.Update(infos);
        }
    }

    public void StartUpdateLoop() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                DecimalFormat df;
                try {
                    while (isOnline() && session.isConnected()) {
                        try {
                            if (infos[0].Description.equals("")) {
                                String hostname = ExecuteCommand("hostname -f");
                                infos[0].Description = hostname;
                            }
                            if (infos[1].Description.equals("")) {
                                String distribution = ExecuteCommand("cat /etc/*-release | grep PRETTY_NAME=");
                                distribution = distribution.replace("PRETTY_NAME=\"", "");
                                distribution = distribution.replace("\"", "");
                                infos[1].Description = distribution;
                            }
                            if (infos[2].Description.equals("")) {
                                String kernel = ExecuteCommand("uname -mrs");
                                infos[2].Description = kernel;
                            }
                            if (infos[3].Description.equals("")) {
                                String firmware = ExecuteCommand("uname -v");
                                infos[3].Description = firmware;
                            }
                            df = new DecimalFormat("0.0");
                            String cputemp = df.format(Float.parseFloat(ExecuteCommand("cat /sys/class/thermal/thermal_zone0/temp")) / 1000) + "'C";
                            infos[4].Description = cputemp;

                            Double d = Double.parseDouble(ExecuteCommand("cat /proc/uptime").split(" ")[0]);
                            Integer uptimeseconds = d.intValue();
                            String uptime = convertMS(uptimeseconds * 1000);
                            infos[5].Description = uptime;

                            String info = ExecuteCommand("cat /proc/meminfo");
                            info = info.replaceAll(" ", "");
                            info = info.replaceAll("kB", "");
                            String[] lines = info.split(System.getProperty("line.separator"));
                            df = new DecimalFormat("0");
                            Integer MemTot = Integer.parseInt(df.format(Integer.parseInt(lines[0].substring(lines[0].indexOf(":") + 1)) / 1024.0f));
                            Integer MemFree = Integer.parseInt(df.format(Integer.parseInt(lines[1].substring(lines[1].indexOf(":") + 1)) / 1024.0f));
                            Integer Buffers = Integer.parseInt(df.format(Integer.parseInt(lines[2].substring(lines[2].indexOf(":") + 1)) / 1024.0f));
                            Integer Cached = Integer.parseInt(df.format(Integer.parseInt(lines[3].substring(lines[3].indexOf(":") + 1)) / 1024.0f));
                            Integer Used = MemTot - MemFree;
                            Integer fMemFree = MemFree + Buffers + Cached;
                            Integer MemUsed = Used - Buffers - Cached;
                            Integer Percentage = Integer.parseInt(df.format((float) ((float) MemUsed / (float) MemTot) * 100.0f));

                            infos[6].Description = "Used: " + MemUsed + "Mb\nFree: " + fMemFree + "Mb\nTot: " + MemTot + "Mb";
                            infos[6].ProgressBarProgress = Percentage;

                            runOnUiThread(new Runnable() {
                                public void run() {
                                    BuildList();
                                }
                            });

                            Thread.sleep(5000);

                        } catch (Exception e) {
                            ThrowException(e.getMessage());
                        }
                    }

                    DisconnectSSH();
                    ThrowException("Can't communicate with the Raspberry Pi through SSH");
                } catch (Exception e) {
                    ThrowException(e.getMessage());
                }
            }
        }).start();
    }

    public void ConnectSSH() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSch jsch = new JSch();
                    String IpAddress = prefs.getString("ipaddress", null);
                    String username = prefs.getString("sshusername", null);
                    String password = prefs.getString("sshpassword", null);
                    session = jsch.getSession(username, IpAddress, 22);
                    session.setPassword(password);
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    session.connect();

                    StartUpdateLoop();
                } catch (final Exception e) {
                    ThrowException(e.getMessage());
                }
            }
        }).start();
    }

    public void DisconnectSSH() {
        channel.disconnect();
        session.disconnect();
    }

    public String ExecuteCommand(String command) {
        try {
            if (session.isConnected()) {
                channel = (ChannelExec) session.openChannel("exec");
                in = new BufferedReader(new InputStreamReader(channel.getInputStream()));
                channel.setCommand(command);
                channel.connect();

                StringBuilder builder = new StringBuilder();

                String line = null;
                while ((line = in.readLine()) != null) {
                    builder.append(line).append(System.getProperty("line.separator"));
                }

                String output = builder.toString();
                if (output.lastIndexOf("\n") > 0) {
                    return output.substring(0, output.lastIndexOf("\n"));
                } else {
                    return output;
                }
            }
        } catch (Exception e) {
            ThrowException(e.getMessage());
        }

        return "";
    }

    public void ThrowException(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Error");
                builder.setMessage(msg);
                builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
                builder.setPositiveButton("Change settings", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ShowSetupDialog();
                    }
                });
                builder.show();
            }
        });
    }

    public String convertMS(int ms) {
        int seconds = (int) ((ms / 1000) % 60);
        int minutes = (int) (((ms / 1000) / 60) % 60);
        int hours = (int) ((((ms / 1000) / 60) / 60) % 24);

        String sec, min, hrs;
        if (seconds < 10) {
            sec = "0" + seconds;
        } else {
            sec = "" + seconds;
        }
        if (minutes < 10) {
            min = "0" + minutes;
        } else {
            min = "" + minutes;
        }
        if (hours < 10) {
            hrs = "0" + hours;
        } else {
            hrs = "" + hours;
        }

        if (hours == 0) {
            return min + ":" + sec;
        } else {
            return hrs + ":" + min + ":" + sec;
        }
    }

    public void shutdown(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage("Are you sure you want to shutdown your Raspberry Pi?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ExecuteCommand("shutdown -h now");
                DisconnectSSH();
            }
        })
                .setNegativeButton("No", null)
                .show();

    }

    public void reboot(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage("Are you sure you want to reboot your Raspberry Pi?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ExecuteCommand("shutdown -r now");
                DisconnectSSH();
            }
        })
                .setNegativeButton("No", null)
                .show();

    }
}
