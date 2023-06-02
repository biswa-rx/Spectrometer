package com.example.spectrometer;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        GraphView graph = (GraphView) findViewById(R.id.graph);

        int audioSource = MediaRecorder.AudioSource.DEFAULT;
        int sampleRateInHz = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
//      int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        int bufferSizeInBytes = 4096;
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
        // Start recording and playing audio
        byte[] audioData = new byte[bufferSizeInBytes];
        audioRecord.startRecording();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int numBytesRead = audioRecord.read(audioData, 0, bufferSizeInBytes);
//                    System.out.println("Read audio sample"+ numBytesRead);
                    double[] audioSamples = new double[audioData.length / 2];
                    for (int i = 0; i < audioSamples.length; i++) {
                        int sample = (audioData[2 * i + 1] << 8) | (audioData[2 * i] & 0xff);
                        audioSamples[i] = sample / 32767.0; // normalize to [-1, 1]
                    }
                    int length = audioSamples.length;
                    for (int i = 0; i < length; i++) {
                        audioSamples[i] *= 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (length - 1));
                    }
                    Complex[] complexSamples = new Complex[audioSamples.length];
                    for (int i = 0; i < audioSamples.length; i++) {
                        complexSamples[i] = new Complex(audioSamples[i], 0);
                    }
                    fft(complexSamples, false);
                    updateSpectrumGraph(complexSamples,graph);
                    SystemClock.sleep(1);
                }
            }
        }).start();
//        BarGraphSeries<DataPoint> series = new BarGraphSeries<DataPoint>(new DataPoint[] {
//                new DataPoint(0, 1),
//                new DataPoint(1, 5),
//                new DataPoint(2, 3),
//                new DataPoint(3, 2),
//                new DataPoint(4, 6)
//        });
//        graph.addSeries(series);
    }
    private void updateSpectrumGraph(Complex[] complexSamples, GraphView graphView) {
        //AVERAGE FILTER
        int dataPointCounter = 0;
        int maxFacter = 10,max = 0;
        DataPoint[] frequencyDomain = new DataPoint[(complexSamples.length/(2*maxFacter))+1];
        for (int i = 0; i < complexSamples.length/2; i+=maxFacter) {
            for(int j=0;j<maxFacter;j++) {
                short sample = (short) (complexSamples[i+j].re * Short.MAX_VALUE);
                if (sample < 0) {
                    max += (-1*sample);
                }else {
                    max += sample;
                }
            }
            frequencyDomain[i/maxFacter] = new DataPoint(dataPointCounter,max/(float)maxFacter);
            max = 0;
            dataPointCounter++;
        }
        frequencyDomain[0] = new DataPoint(0,Short.MAX_VALUE);
        BarGraphSeries<DataPoint> series = new BarGraphSeries<DataPoint>(frequencyDomain);
        graphView.removeAllSeries();
        graphView.addSeries(series);
//        for(int i=0;i<frequencyDomain.length;i++){
//            System.out.println(frequencyDomain[i]);
//        }
        System.out.println(frequencyDomain.length);
    }
    static class Complex {
        double re, im;
        public static final Complex ZERO = new Complex(0, 0);

        Complex(double x, double y) {
            re = x;
            im = y;
        }

        Complex() {
            this(0, 0);
        }

        Complex add(Complex b) {
            return new Complex(re + b.re, im + b.im);
        }

        Complex sub(Complex b) {
            return new Complex(re - b.re, im - b.im);
        }

        Complex mul(Complex b) {
            return new Complex(re * b.re - im * b.im, re * b.im + im * b.re);
        }

        Complex div(double x) {
            return new Complex(re / x, im / x);
        }
    }

    static final double PI = Math.acos(-1);

    static void fft(Complex[] a, boolean invert) {
        int n = a.length;
        if (n == 1)
            return;

        Complex[] a0 = new Complex[n / 2];
        Complex[] a1 = new Complex[n / 2];
        for (int i = 0; 2 * i < n; i++) {
            a0[i] = a[2 * i];
            a1[i] = a[2 * i + 1];
        }
        fft(a0, invert);
        fft(a1, invert);

        double ang = 2 * PI / n * (invert ? -1 : 1);
        Complex w = new Complex(1, 0);
        Complex wn = new Complex(Math.cos(ang), Math.sin(ang));
        for (int i = 0; 2 * i < n; i++) {
            a[i] = a0[i].add(w.mul(a1[i]));
            a[i + n / 2] = a0[i].sub(w.mul(a1[i]));
            if (invert) {
                a[i] = a[i].div(2);
                a[i + n / 2] = a[i + n / 2].div(2);
            }
            w = w.mul(wn);
        }
    }
}