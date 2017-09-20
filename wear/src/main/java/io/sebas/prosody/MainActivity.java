package io.sebas.prosody;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.input.RotaryEncoder;
import android.support.wearable.view.BoxInsetLayout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends WearableActivity implements CounterHandler.CounterListener {

    private BoxInsetLayout mContainerView;

    CounterHandler counterHandler;
    CircleProgressBar bpmProgressBar;
    TextView bpmText;
    Vibrator vibrator;

    ToggleButton toggleVibrationButton;
    Button plusButton;
    Button minusButton;

    int bpmActual = 30;
    long[] pattern = { 0, 100, 100 };
    boolean vibrating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        mContainerView = (BoxInsetLayout) findViewById(R.id.container);

        bpmProgressBar = (CircleProgressBar) findViewById(R.id.bpmProgressBar);
        bpmText = (TextView) findViewById(R.id.bpmText);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        toggleVibrationButton = (ToggleButton) findViewById(R.id.toggleButton);
        plusButton = (Button) findViewById(R.id.plusButton);
        minusButton = (Button) findViewById(R.id.minusButton);

        toggleVibrationButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                {
                    startMetronome();
                }
                else
                {
                    stopMetronome();
                }
            }
        });

        counterHandler = new CounterHandler.Builder()
                .incrementalView(plusButton)
                .decrementalView(minusButton)
                .startNumber(0)
                .minRange(0) // cant go any less than -50
                .maxRange(210) // cant go any further than 50
                .isCycle(false) // 49,50,-50,-49 and so on
                .counterDelay(25) // speed of counter
                .counterStep(1)  // steps e.g. 0,2,4,6...
                .listener(this) // to listen counter results and show them in app
                .build();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {
            // Don't forget the negation here
            float delta = -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(getApplicationContext());
            int dInt = Math.round(delta / 2);
            //Log.d("CircleProgressBar", "delta: " + String.valueOf(dInt));
            bpmActual += dInt;
            if (bpmActual > 240) { bpmActual = 240; }
            else if (bpmActual < 30) { bpmActual = 30; }

            toggleVibrationButton.setChecked(false);
            counterHandler.startNumber = bpmActual - 30;
            bpmProgressBar.setProgress(((float)counterHandler.startNumber / 210) * 100);
            bpmText.setText(getString(R.string.bpm, bpmActual));

            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bpmProgressBar.requestFocus();
    }

    @Override
    public void onIncrement(View view, long number) {
        toggleVibrationButton.setChecked(false);
        bpmActual = (int) number + 30;
        bpmProgressBar.setProgress(((float)number / 210) * 100);
        bpmText.setText(getString(R.string.bpm, bpmActual));
    }

    @Override
    public void onDecrement(View view, long number) {
        toggleVibrationButton.setChecked(false);
        bpmActual = (int) number + 30;
        bpmProgressBar.setProgress(((float)number / 210) * 100);
        bpmText.setText(getString(R.string.bpm, bpmActual));
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    private void updateDisplay() {
        if (isAmbient()) {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
        } else {
            mContainerView.setBackground(null);
        }
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
