package com.raspberryremotecontrol;

import com.jcraft.jsch.*;
import android.view.*;
import android.app.*;
import android.app.Dialog;
import android.widget.*;
import android.content.*;
import android.graphics.Color;
import android.net.*;
import android.os.*;
import android.widget.AdapterView.OnItemClickListener;
import java.util.*;
import java.io.*;
import java.text.*;
import java.lang.reflect.*;

public class MainActivity extends Activity {

    SharedPreferences prefs = null;
    ChannelExec channel;
    Session session;
    BufferedReader in;
    InfoAdapter adapter;
    private ListView listView;
    Integer refreshrate = 5000;
    List<Profile> Profiles = new ArrayList<Profile>();
    int CurrProfile = -1;
    Boolean paused = false;
    Info infos[] = new Info[]{
        new Info(R.drawable.hostname, "Hostname", "", -1),
        new Info(R.drawable.distribution, "Distribution", "", -1),
        new Info(R.drawable.kernel, "Kernel", "", -1),
        new Info(R.drawable.firmware, "Firmware", "", -1),
        new Info(R.drawable.cpuheat, "Cpu Heat", "", -1),
        new Info(R.drawable.uptime, "Uptime", "", -1),
        new Info(R.drawable.ram, "Ram Info", "", -1),
        new Info(R.drawable.cpu, "Cpu", "", -1),
        new Info(R.drawable.storage, "Storage", "", -1)
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getOverflowMenu();

        prefs = getSharedPreferences("com.raspberryremotecontrol", MODE_PRIVATE);

        if (prefs.getString("refreshrate", null) != null) {
            refreshrate = Integer.parseInt(prefs.getString("refreshrate", null));
        } else {
            prefs.edit().putString("refreshrate", "5000").commit();
        }

        if (prefs.getBoolean("firstrun", true)) {
            CreateNewProfile();
            prefs.edit().putBoolean("firstrun", false).commit();
        } else {
            SelectProfile();
        }
    }

    private void FetchProfiles() {
        String profiles = prefs.getString("profiles", null);
        for (String profile : profiles.split("\\|\\|")) {
            String[] data = profile.split("\\|");
            String Name = data[0];
            String IpAddress = data[1];
            String Username = data[2];
            String Password = data[3];
            Profiles.add(new Profile(Name, IpAddress, Username, Password));
        }
    }

    private void SaveProfiles() {
        String profiles = "";
        for (Profile p : Profiles) {
            profiles += p.Name + "|" + p.IpAddress + "|" + p.Username + "|" + p.Password + "||";
        }

        prefs.edit().putString("profiles", profiles).commit();
    }
    int lastChecked = 0;

    private void SelectProfile() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Profiles.isEmpty()) {
                    FetchProfiles();
                }

                final String[] ProfilesName = new String[Profiles.size()];

                for (int i = 0; i < Profiles.size(); i++) {
                    ProfilesName[i] = Profiles.get(i).Name;
                }

                final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                final View dialog_layout = getLayoutInflater().inflate(R.layout.select_profile_dialog_layout, null);
                final ListView lv = (ListView) dialog_layout.findViewById(R.id.profiles);
                ArrayAdapter adapter1 = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_single_choice, ProfilesName);
                lv.setAdapter(adapter1);
                lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

                lv.setItemChecked(0, true);

                lv.setOnItemClickListener(new OnItemClickListener() {
                    boolean somethingChecked = false;

                    public void onItemClick(AdapterView arg0, View arg1, int arg2,
                            long arg3) {
                        if (somethingChecked) {
                            ListView lv = (ListView) arg0;
                            TextView tv = (TextView) lv.getChildAt(lastChecked);
                            CheckedTextView cv = (CheckedTextView) tv;
                            cv.setChecked(false);
                        }
                        ListView lv = (ListView) arg0;
                        TextView tv = (TextView) lv.getChildAt(arg2);
                        CheckedTextView cv = (CheckedTextView) tv;
                        if (!cv.isChecked()) {
                            cv.setChecked(true);
                        }
                        lastChecked = arg2;
                        somethingChecked = true;
                    }
                });

                builder.setTitle("Profiles");
                builder.setPositiveButton("Select", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        CurrProfile = lastChecked;

                        for (int i = 0; i < infos.length; i++) {
                            infos[i].Description = "";
                            if (infos[i].ProgressBarProgress != -1) {
                                infos[i].ProgressBarProgress = 0;
                            }
                        }

                        ConnectSSH();
                    }
                });
                builder.setNeutralButton("New", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        CreateNewProfile();
                    }
                });

                builder.setNegativeButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

                final AlertDialog Dialog = builder.create();

                Dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(final DialogInterface dialog) {
                        Button deletebutton = Dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                        deletebutton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                if (Profiles.size() == 1) {
                                    Toast.makeText(getApplicationContext(), "Can't delete the only profile available", Toast.LENGTH_SHORT).show();
                                } else {
                                    Profiles.remove(lastChecked);
                                    SaveProfiles();
                                    dialog.dismiss();
                                    SelectProfile();
                                }
                            }
                        });
                    }
                });

                Dialog.setView(dialog_layout);
                Dialog.show();
            }
        });
    }

    private void getOverflowMenu() {

        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.showprofiles:
                SelectProfile();
                return true;
            case R.id.changerefreshrate:
                ShowChangeRefreshRateDialog();
                return true;
            case R.id.customcommand:
                final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                final View dialog_layout = getLayoutInflater().inflate(R.layout.sendcustomcommand_dialog_layout, null);
                builder.setTitle("Send custom command");

                final EditText et = (EditText) dialog_layout.findViewById(R.id.customcommand);

                builder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.setPositiveButton("Send", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String output = ExecuteCommand(et.getText().toString());

                        AlertDialog outDialog = new AlertDialog.Builder(MainActivity.this)
                                .setMessage(output)
                                .setTitle("Output")
                                .setCancelable(true)
                                .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        })
                                .show();
                        TextView textView = (TextView) outDialog.findViewById(android.R.id.message);
                        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
                    }
                });


                final AlertDialog Dialog = builder.create();

                Dialog.setView(dialog_layout);
                Dialog.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.menu, menu);
        return true;
    }

    public void ShowChangeRefreshRateDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                final View dialog_layout = getLayoutInflater().inflate(R.layout.refreshrate_dialog_layout, null);
                final NumberPicker np = (NumberPicker) dialog_layout.findViewById(R.id.numberPicker1);
                np.setMaxValue(30);
                np.setMinValue(1);
                np.setWrapSelectorWheel(false);

                np.setValue(Integer.parseInt(prefs.getString("refreshrate", null)) / 1000);

                builder.setTitle("Change refresh rate");
                builder.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        prefs.edit().putString("refreshrate", Integer.toString(np.getValue() * 1000)).commit();
                        refreshrate = np.getValue() * 1000;
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

                AlertDialog Dialog = builder.create();
                Dialog.setView(dialog_layout);
                Dialog.show();
            }
        });
    }

    public void CreateNewProfile() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                final View dialog_layout = getLayoutInflater().inflate(R.layout.profile_dialog_layout, null);

                builder.setTitle("Create new profile");

                final EditText ProfileName = (EditText) dialog_layout.findViewById(R.id.profilename);
                final EditText IpAddress = (EditText) dialog_layout.findViewById(R.id.ipaddress);
                final EditText username = (EditText) dialog_layout.findViewById(R.id.sshusername);
                final EditText password = (EditText) dialog_layout.findViewById(R.id.sshpassword);

                builder.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Profiles.add(new Profile(ProfileName.getText().toString(), IpAddress.getText().toString(), username.getText().toString(), password.getText().toString()));

                        SaveProfiles();

                        SelectProfile();
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
        });
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
                        while (!paused) {

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
                                String cputemp_str = ExecuteCommand("cat /sys/class/thermal/thermal_zone0/temp");

                                if (!cputemp_str.isEmpty()) {
                                    String cputemp = df.format(Float
                                            .parseFloat(cputemp_str) / 1000) + "'C";
                                    infos[4].Description = cputemp;
                                } else {
                                    infos[4].Description = "* not available *";
                                }

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

                                df = new DecimalFormat("0.0");
                                String[] loadavg = ExecuteCommand(
                                        "cat /proc/loadavg").split(" ");
                                String cpuCurFreq_cmd = ExecuteCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");

                                String cpuCurFreq = "*N/A*";
                                if (!cpuCurFreq_cmd.isEmpty()) {
                                    cpuCurFreq = df.format(Float
                                            .parseFloat(cpuCurFreq_cmd) / 1000) + "Mhz";
                                }
                                String cpuMinFreq_cmd = ExecuteCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq");

                                String cpuMinFreq = "*N/A*";
                                if (!cpuMinFreq_cmd.isEmpty()) {
                                    cpuMinFreq = df.format(Float
                                            .parseFloat(cpuMinFreq_cmd) / 1000) + "Mhz";
                                }
                                String cpuMaxFreq_cmd = ExecuteCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");

                                String cpuMaxFreq = "*N/A*";
                                if (!cpuMaxFreq_cmd.isEmpty()) {
                                    cpuMaxFreq = df.format(Float
                                            .parseFloat(cpuMaxFreq_cmd) / 1000) + "Mhz";
                                }

                                infos[7].Description = "Loads\n" + loadavg[0] + " [1 min] · " + loadavg[1] + " [5 min] · " + loadavg[2] + " [15 min]\nRunning at " + cpuCurFreq + "\n(min: " + cpuMinFreq + " · max: " + cpuMaxFreq + ")";

                                String Drives = ExecuteCommand("df -T | grep -vE \"tmpfs|rootfs|Filesystem|File system\"");
                                lines = Drives.split(System.getProperty("line.separator"));

                                infos[8].Description = "";

                                Integer totalSize = 0;
                                Integer usedSize = 0;
                                Integer partSize = 0;
                                Integer partUsed = 0;
                                for (int i = 0; i < lines.length; i++) {
                                    String line = lines[i];
                                    line = line.replaceAll("\\s+", "|");
                                    String[] DriveInfos = line.split("\\|");
                                    String name = DriveInfos[6];
                                    partSize = Integer.parseInt(DriveInfos[2]);
                                    String total = kConv(partSize);
                                    String free = kConv(Integer.parseInt(DriveInfos[4]));
                                    partUsed = Integer.parseInt(DriveInfos[3]);
                                    String used = kConv(partUsed);
                                    String format = DriveInfos[1];
                                    totalSize += partSize;
                                    usedSize += partUsed;
                                    infos[8].Description += name + "\n" + "Free: " + free + " · used: " + used + "\nTotal: " + total + " · format: " + format + ((i == (lines.length - 1)) ? "" : "\n\n");
                                }

                                Integer percentage = usedSize * 100 / totalSize;
                                infos[8].ProgressBarProgress = percentage;

                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        BuildList();
                                    }
                                });

                                Thread.sleep(refreshrate);

                            } catch (Exception e) {
                                ThrowException(e.getMessage());
                            }
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
                    Profile p = Profiles.get(CurrProfile);
                    session = jsch.getSession(p.Username, p.IpAddress, 22);
                    session.setPassword(p.Password);
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

                Profile p = Profiles.get(CurrProfile);
                String username = p.Username;
                if (!username.equals("root")) {
                    command = "sudo " + command;
                }

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
                builder.setPositiveButton("Change profile", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        SelectProfile();
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

    public static String kConv(Integer kSize) {
        char[] unit = {'K', 'M', 'G', 'T'};
        Integer i = 0;
        Float fSize = (float) (kSize * 1.0);
        while (i < 3 && fSize > 1024) {
            i++;
            fSize = fSize / 1024;
        }
        DecimalFormat df = new DecimalFormat("0.00");
        return df.format(fSize) + unit[i];
    }

    protected void onPause() {
        paused = true;
        super.onPause();
    }

    protected void onResume() {
        paused = false;
        super.onResume();
    }
}
