package io.sebas.prosody;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    SensorManager sensorManager;
    Sensor heartRateSensor;
    //boolean hasHeartRateSensor = false;

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

        bpmSeekbar = (SeekBar) findViewById(R.id.bpmSeekbar);
        bpmText = (TextView) findViewById(R.id.bpmText);
        toggleVibrationButton = (ToggleButton) findViewById(R.id.toggleButton);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        /*
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (Build.VERSION.SDK_INT >= 20) {
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            //Log.d("heartRateSensor", heartRateSensor.toString());
            Log.d("SensorTypes", sensorManager.getSensorList(Sensor.TYPE_HEART_BEAT).toString());
            hasHeartRateSensor = true;
        }
        */

        toggleVibrationButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                {
                    //startHeartMeasure();
                    startMetronome();
                }
                else
                {
                    //stopHeartMeasure();
                    stopMetronome();
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
        //sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void startHeartMeasure()
    {
        boolean sensorRegistered = sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST);
        Log.d("Sensor Status", " Sensor registered: " + (sensorRegistered ? "yes" : "no"));
    }

    private void stopHeartMeasure()
    {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            float heartRateF = sensorEvent.values[0];
            int heartRate = Math.round(heartRateF);
            bpmText.setText(getString(R.string.bpm, heartRate));
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
