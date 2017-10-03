package io.sebas.prosody;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int PERMISSION_HEART_RATE_MONITOR = 1;
    boolean hrmPermission = false;

    SensorManager sensorManager;
    Sensor heartRateSensor;
    boolean gotHeartRate = false;

    SeekBar bpmSeekbar;
    TextView bpmText;
    ToggleButton toggleVibrationButton;
    Vibrator vibrator;

    int bpmActual = 30;
    long[] pattern = { 0, 100, 100 };
    boolean vibrating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BODY_SENSORS}, PERMISSION_HEART_RATE_MONITOR);
        }
        else {
            hrmPermission = true;
            initializeSensorManager();
        }

        bpmSeekbar = (SeekBar) findViewById(R.id.bpmSeekbar);
        bpmText = (TextView) findViewById(R.id.bpmText);
        toggleVibrationButton = (ToggleButton) findViewById(R.id.toggleButton);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        toggleVibrationButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                {
                    startHeartMeasure();
                    //startMetronome();
                }
                else
                {
                    stopHeartMeasure();
                    //stopMetronome();
                }
            }
        });

        bpmSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                bpmActual = progress + 30;
                bpmText.setText(getString(R.string.bpm, bpmActual));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                toggleVibrationButton.setChecked(false);
                stopMetronome();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        bpmText.setText(getString(R.string.bpm, bpmActual));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && hrmPermission) { sensorManager.unregisterListener(this); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && hrmPermission) { sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL); }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_HEART_RATE_MONITOR) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                hrmPermission = true;
                initializeSensorManager();
            }
        }
    }

    private void initializeSensorManager()
    {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        //Log.d("SensorTypes", sensorManager.getSensorList(Sensor.TYPE_HEART_RATE).toString());
    }

    private void startHeartMeasure()
    {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                confirmListener();
            }
        });
        t.start();
    }

    private void confirmListener()
    {
        sensorManager.unregisterListener(this);
        boolean sensorRegistered;
        do {
            try { Thread.sleep(1000); }
            catch (Exception e) { Log.e("confirmListener", e.toString()); }
            sensorRegistered = sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d("Sensor Status", " Sensor registered: " + (sensorRegistered ? "yes" : "no"));
        } while (!sensorRegistered);
    }

    private void stopHeartMeasure()
    {
        sensorManager.unregisterListener(this);
        stopMetronome();
        gotHeartRate = false;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_HEART_RATE && !gotHeartRate) {
            float heartRateF = sensorEvent.values[0];
            int heartRate = Math.round(heartRateF);
            if (heartRate > 0) {
                bpmActual = heartRate;
                bpmText.setText(getString(R.string.bpm, heartRate));
                gotHeartRate = true;
                sensorManager.unregisterListener(this);
                startMetronome();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private void startMetronome()
    {
        vibrating = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pattern[2] = Math.round(((float)60000 / (float)bpmActual)) - pattern[1];
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{100, 100}, new int[]{255, 0}, 0));
        }
        else {
            vibrator.vibrate(pattern, 0);
        }
    }
    private void stopMetronome()
    {
        vibrating = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        vibrator.cancel();
    }
}
