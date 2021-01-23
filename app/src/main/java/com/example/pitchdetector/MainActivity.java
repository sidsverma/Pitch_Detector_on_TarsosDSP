package com.example.pitchdetector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.util.PitchConverter;

import static java.lang.Math.log;
import static java.lang.Math.pow;
import static java.lang.Math.round;

public class MainActivity extends AppCompatActivity
{

    SilenceDetector silenceDetector;
    double threshold;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1234);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initializePlayerAndStartRecording();

    }

    public void initializePlayerAndStartRecording() {
        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050,1024,0);
        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult result, AudioEvent e) {
                final float pitchInHz = result.getPitch();
                final double spl = silenceDetector.currentSPL();
                if(pitchInHz == -1 || spl<threshold)
                    return;
                final String nearestNote = hertzToNearestNote(pitchInHz);
                final double absoluteCent = PitchConverter.hertzToAbsoluteCent(pitchInHz);
                final double midiCent = PitchConverter.hertzToMidiCent(pitchInHz);
                final int midiKey = PitchConverter.hertzToMidiKey((double) pitchInHz);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView text = (TextView) findViewById(R.id.textView1);
                        text.setText(pitchInHz+" Hz");
                        TextView text2 = (TextView) findViewById(R.id.textView2);
                        text2.setText(""+nearestNote);
                        TextView text3 = (TextView) findViewById(R.id.textView3);
                        text3.setText("spl "+spl);
                        TextView text4 = (TextView) findViewById(R.id.textView4);
                        text4.setText("diff "+ (midiCent - midiKey)*100);
                        TextView text5 = (TextView) findViewById(R.id.textView5);
                        text5.setText("midiCent "+midiCent);
                        TextView text6 = (TextView) findViewById(R.id.textView6);
                        text6.setText("midiKey "+midiKey);
                    }
                });
            }
        };
        AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pdh);
        dispatcher.addAudioProcessor(p);
        threshold = SilenceDetector.DEFAULT_SILENCE_THRESHOLD;
        silenceDetector = new SilenceDetector(threshold,false);
        dispatcher.addAudioProcessor(silenceDetector);
        new Thread(dispatcher,"Audio Dispatcher").start();
//        startPitchShifting();
    }

//    This function is going to be used to do pitch shifting. Currently, experiencing some errors around ffmpeg.
//    java.lang.Error: Decoding via a pipe will not work: Could not find an ffmpeg binary for your system.

//    private void startPitchShifting()
//    {
//        double rate = 1.0;
//        RateTransposer rateTransposer;
//        AudioDispatcher dispatcher;
//        WaveformSimilarityBasedOverlapAdd wsola;
//
//        File downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
//
//        String mAudiopath = downloadFolder.getPath() + "/test.wav";
//        dispatcher = AudioDispatcherFactory.fromPipe(mAudiopath, 44100, 5000, 2500);
////        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050,1024,0);
//        rateTransposer = new RateTransposer(rate);
//        wsola = new WaveformSimilarityBasedOverlapAdd(WaveformSimilarityBasedOverlapAdd.Parameters.musicDefaults(rate, 44100));
//        File f = new File(getFilesDir(), "ttttest.mp3");
//        RandomAccessFile raf = null;
//        try
//        {
//            raf = new RandomAccessFile(f, "rw");
//        } catch (FileNotFoundException e)
//        {
//            e.printStackTrace();
//        }
//        WriterProcessor writer = new WriterProcessor((TarsosDSPAudioFormat) dispatcher.getFormat(), raf);
//
//        wsola.setDispatcher(dispatcher);
//        dispatcher.addAudioProcessor(wsola);
//        dispatcher.addAudioProcessor(rateTransposer);
//        dispatcher.addAudioProcessor(new AndroidAudioPlayer(dispatcher.getFormat()));
//        dispatcher.setZeroPadFirstBuffer(true);
//        dispatcher.setZeroPadLastBuffer(true);
//        dispatcher.addAudioProcessor(writer);
//    }

    public static String hertzToNearestNote(float freq) {
        int A4 = 440;
        double C0 = A4 * pow(2, -4.75);
        String[] name = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        long h = round(12*log(freq/C0)/log(2));
        long octave = h/12;
        int note = (int) (h % 12);
        return name[note] + octave;
    }
//    public static String hertzToSaRe(float freq) {
//        int A4 = 440;
//        double C0 = A4 * pow(2, -4.75);
//        String mainScaleNote = "D#";
//        int mainScaleOctave = 3;
//        String[] name = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
//        String[] sare = {"S", "r", "R", "g", "G", "m", "M", "P", "d", "D", "n", "N"};
//        long h = round(12*log(freq/C0)/log(2));
//        long octave = h/12;
//        int note = (int) (h % 12);
//
//    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
//        switch (requestCode) {
//            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializePlayerAndStartRecording();

                } else {
                    Log.d("TAG", "permission denied by user");
                }
                return;
//            }
//        }
    }

}
