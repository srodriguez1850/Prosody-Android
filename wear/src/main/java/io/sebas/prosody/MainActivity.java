package io.sebas.prosody;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.input.RotaryEncoder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends WearableActivity implements SensorEventListener {

    //region Activity Members
    // Define minimum and maximum bpm (heart rate will be between these)
    public static final int MAXIMUM_BPM = 300;
    public static final int MINIMUM_BPM = 1;
    // Display progress bar for bpm (false, circular pb isn't compatible with square/roundchin)
    public static final boolean DISPLAY_BPM_PROGRESSBAR = false;
    // Factor of motion events when changing bpm
    public static final float ROTARYHANDLER_BPM_SCALE = 0.25f;
    public static final float GESTUREEVENT_BPM_SCALE = 0.25f;

    // System variables
    SharedPreferences preferences;
    SensorManager sensorManager;
    Sensor heartRateSensor;
    Vibrator vibrator;
    GestureDetector gestureDetector;

    // UI variables
    ConstraintLayout mContainerView;
    FloatingActionButton heartButton;
    FloatingActionButton bpmButton;
    CircleProgressBar bpmProgressBar;
    TextView bpmText;
    ProgressBar hrLoading;
    ImageButton buttonFaster;

    // Activity vars
    boolean hasHeartRateSensor = false;     // Is here a heart rate sensor?
    boolean heartActive = false;            // Metronome (heart rate) active
    boolean bpmActive = false;              // Metronome (manual) active
    boolean bpmMultiplied = false;          // Is the BPM multiplied?
    boolean vibrating = false;              // Is the device vibrating
    int bpmActual;                          // Actual BPM for metronome
    long[] pattern = { 0, 100, 100 };       // Vibration pattern (API <25)
    //endregion

    //region Activity Lifecycle Methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get preferences, ensure we're at least 1 BPM; 30 BPM on first launch
        preferences = getPreferences(0);
        bpmActual = preferences.getInt("SavedBpm", 30);
        if (bpmActual <  MINIMUM_BPM) { bpmActual = MINIMUM_BPM; }

        // Get sensor manager and try to open the heart rate sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        // Get vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Set gesture detector
        gestureDetector = new GestureDetector(this, new BpmGestureListener());

        // Load layout depending if we find a heart rate sensor
        if (heartRateSensor == null) setContentView(R.layout.activity_main_noheart);
        else {
            setContentView(R.layout.activity_main);
            hasHeartRateSensor = true;
        }

        // Get views from layout
        mContainerView = (ConstraintLayout) findViewById(R.id.mcontainer);
        mContainerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return MainActivity.this.gestureDetector.onTouchEvent(motionEvent); // Set gesture listener on background
            }
        });
        heartButton = (FloatingActionButton) findViewById(R.id.buttonHeart);
        bpmButton = (FloatingActionButton) findViewById(R.id.buttonTime);
        buttonFaster = (ImageButton) findViewById(R.id.buttonFaster);
        bpmProgressBar = (CircleProgressBar) findViewById(R.id.bpmProgressBar);
        if (DISPLAY_BPM_PROGRESSBAR) ((LinearLayout) findViewById(R.id.llayout)).setVisibility(View.VISIBLE);
        bpmText = (TextView) findViewById(R.id.bpmText);
        bpmText.setText(getString(R.string.bpm, bpmActual));        // Set BPM text on load
        hrLoading = (ProgressBar) findViewById(R.id.hrLoading);

        setProgressBar(bpmActual);      // Set progress bar

        if (hasHeartRateSensor) {
            // Add click listener to heart rate button if we have a heart rate sensor (else it won't be there)
            heartButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (heartActive) {
                        // Turn off current button
                        heartActive = false;
                        heartButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_inactive)));
                        // Turn off measurement and metronome
                        stopHeartMeasure();
                        stopMetronome();
                        // Remove faster and loading views
                        buttonFaster.setVisibility(View.INVISIBLE);
                        hrLoading.setVisibility(View.INVISIBLE);
                        // Restore bpm text
                        bpmText.setVisibility(View.VISIBLE);
                        // Add circle progress bar
                        bpmProgressBar.setVisibility(View.VISIBLE);
                    } else {
                        // Turn off other button
                        bpmActive = false;
                        bpmButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_inactive)));
                        // Turn on current button
                        heartActive = true;
                        heartButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_active)));
                        // Remove progress bar and text
                        bpmText.setVisibility(View.INVISIBLE);
                        bpmProgressBar.setVisibility(View.INVISIBLE);
                        // Turn off metronome
                        stopMetronome();
                        // Start measuring the heart
                        startHeartMeasure();
                    }
                }
            });
            // Add click listener to manual bpm button
            bpmButton.setOnClickListener(new BpmButtonListener());
        }
        else {
            // Add click listener to manual bpm button (no interactions with heart rate button)
            bpmButton.setOnClickListener(new BpmButtonListenerNoHeart());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Stop metronome, heart rate sensor and unregister
        stopHeartMeasure();
        stopMetronome();

        // Store last BPM for next load
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("SavedBpm", bpmActual);
        editor.apply();
    }
    //endregion

    //region BpmButton Click Listeners
    private class BpmButtonListener implements View.OnClickListener {
        // When there is a heart rate sensor
        @Override
        public void onClick(View view) {
            if (bpmActive) {
                // Turn off current button
                bpmActive = false;
                bpmButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_inactive)));
                // Stop metronome
                stopMetronome();
            } else {
                // Turn off other button
                heartActive = false;
                heartButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_inactive)));
                bpmMultiplied = false;
                buttonFaster.setVisibility(View.INVISIBLE);
                // Stop metronome
                stopMetronome();
                // Turn on current button
                bpmActive = true;
                bpmButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_active)));
                // Set progress bar and bpm visibility
                bpmProgressBar.setVisibility(View.VISIBLE);
                bpmText.setVisibility(View.VISIBLE);
                // Start metronome
                startMetronome();
            }
        }
    }

    private class BpmButtonListenerNoHeart implements View.OnClickListener {
        // When there is no heart rate sensor
        @Override
        public void onClick(View view) {
            if (bpmActive) {
                // Turn off button
                bpmActive = false;
                bpmButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_inactive)));
                // Turn off metronome
                stopMetronome();
            } else {
                // Turn on button
                bpmActive = true;
                bpmButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_active)));
                // Start metronome
                startMetronome();
            }
        }
    }
    //endregion

    //region MotionEvent Listeners
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event) && !heartActive && !bpmActive) {
            // Negate the encoder, divide by 4 to scale
            float delta = -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(getApplicationContext());
            int dInt = Math.round(delta * ROTARYHANDLER_BPM_SCALE);

            // Ensure we're within bounds
            bpmActual += dInt;
            if (bpmActual > MAXIMUM_BPM) { bpmActual = MAXIMUM_BPM; }
            else if (bpmActual < MINIMUM_BPM) { bpmActual = MINIMUM_BPM; }

            // Update UI
            setProgressBar(bpmActual);
            bpmText.setText(getString(R.string.bpm, bpmActual));

            // Consume event
            return true;
        }
        return super.onGenericMotionEvent(event);
    }
    //endregion

    //region Sensor Listeners
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            float heartRateF = sensorEvent.values[0];
            int heartRate = Math.round(heartRateF);
            bpmActual = heartRate;
            sensorManager.unregisterListener(this);
            hrLoading.setVisibility(View.INVISIBLE);
            buttonFaster.setVisibility(View.VISIBLE);
            bpmText.setVisibility(View.VISIBLE);
            bpmText.setText(getString(R.string.bpm, heartRate));
            setProgressBar(bpmActual);
            startMetronome();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
    //endregion

    //region Gesture Listeners
    private class BpmGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (BuildConfig.DEBUG) Log.d("bpmGestureListener", "onScroll: X-" + String.valueOf(distanceX) + " Y-" + String.valueOf(distanceY));
            if (!heartActive && !bpmActive) {
                bpmActual += Math.round(distanceY * GESTUREEVENT_BPM_SCALE);
                if (bpmActual > MAXIMUM_BPM) { bpmActual = MAXIMUM_BPM; }
                else if (bpmActual < MINIMUM_BPM) { bpmActual = MINIMUM_BPM; }
                bpmText.setText(getString(R.string.bpm, bpmActual));
                return true;
            }
            return false;
        }
    }
    //endregion

    //region Public Methods
    public void multiplyBpm(View v)
    {
        if (bpmMultiplied) {
            bpmMultiplied = false;
            stopMetronome();
            bpmActual /= 2;
            buttonFaster.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_inactive)));
            setProgressBar(bpmActual);
            bpmText.setText(getString(R.string.bpm, bpmActual));
            startMetronome();
        }
        else {
            bpmMultiplied = true;
            stopMetronome();
            bpmActual *= 2;
            buttonFaster.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.button_active)));
            setProgressBar(bpmActual);
            bpmText.setText(getString(R.string.bpm, bpmActual));
            startMetronome();
        }
    }
    //endregion

    //region Private Methods
    private void startHeartMeasure()
    {
        // Prevent screen from turning off while we get the heart rate
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Show loading progress bar in UI
        hrLoading.setVisibility(View.VISIBLE);
        // Register listener for heart rate events
        boolean sensorRegistered = sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST);
        if (BuildConfig.DEBUG) Log.d("Sensor Status", " Sensor registered: " + (sensorRegistered ? "yes" : "no"));
    }

    private void stopHeartMeasure()
    {
        // Allow screen to turn off
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Unregister listener
        sensorManager.unregisterListener(this);
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

    private void setProgressBar(int bpm)
    {
        bpmProgressBar.setProgress((bpm / (float)MAXIMUM_BPM) * 100);
    }
    //endregion
}
