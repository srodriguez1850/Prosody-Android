package com.ssrodriguez.speechrhythm;

import android.content.Context;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity {

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
    }

    private void startMetronome()
    {
        vibrating = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pattern[2] = Math.round(((float)60000 / (float)bpmActual)) - pattern[1];
        vibrator.vibrate(pattern, 0);
    }
    private void stopMetronome()
    {
        vibrating = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        vibrator.cancel();
    }
}
