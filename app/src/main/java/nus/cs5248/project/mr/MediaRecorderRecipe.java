package nus.cs5248.project.mr;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.StatusLine;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.mime.HttpMultipartMode;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntityBuilder;
import ch.boye.httpclientandroidlib.entity.mime.content.FileBody;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;


@SuppressWarnings("deprecation")
public class MediaRecorderRecipe extends Activity implements SurfaceHolder.Callback {
    private static String VIDEO_PATH_NAME = "/Pictures/test";
    int frequency = 9;
    Boolean isRecording;
    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private View mToggleButton, newtogglebutton;
    private boolean mInitSuccesful;
    File file;
    boolean looperCalled = false;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private int index = 0;

    final int BUFFER_SIZE = 1;
    LocalServerSocket server = null;
    LocalSocket receiver, sender;

    Timer timer;
    static int filecounter = 1;
    static int uploadcounter = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_recorder_recipe);
        isRecording = false;
        //preprocessdata();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        MyApplication my = (MyApplication) getApplicationContext();
        my.setFilenumber(index);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        final Button recordButton = (Button) findViewById(R.id.recordButton);
        recordButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isRecording) {
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    mMediaRecorder.release();
                    mCamera.stopPreview();
                    mCamera.release();
                    recordButton.setText("Start");
                    shutdown();
                } else {
                    mMediaRecorder.start();
                    isRecording = true;
                    recordButton.setText("Stop");
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {

                            runOnUiThread(new Runnable() {
                                              public void run() {
                                                  Toast.makeText(getApplicationContext(), frequency + " seconds read", Toast.LENGTH_SHORT).show();
                                              }
                                          }
                            );
                            restart();
                            //insert code here for upload.
                            if (looperCalled) {
                            } else {
                                Looper.prepare();
                                looperCalled = true;
                            }
                            MyApplication my = (MyApplication) getApplicationContext();
                            Integer currentindex = my.getFilenumber();
                            //((MyApplication)getApplicationContext()).setFilenumber(1);
                            int t = 0;

                            new EncodeVideo().execute(new String(Environment.getExternalStorageDirectory().getAbsolutePath() + VIDEO_PATH_NAME + uploadcounter++ + ".mp4"));

                            currentindex = my.getFilenumber();
                            my.setFilenumber(currentindex + t);

                        }
                    }, frequency * 1000, frequency * 1000);
                }

            }
        });

    }


    void restart() {

        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            //mMediaRecorder.reset();
        }
        mCamera.release();
        try {
            initRecorder(mHolder.getSurface());
            mMediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initRecorder(Surface surface) throws IOException {

        mCamera = Camera.open();
        mCamera.setPreviewDisplay(mHolder);
        mCamera.startPreview();
        mCamera.unlock();

        if (mMediaRecorder == null) mMediaRecorder = new MediaRecorder();

        mMediaRecorder.setCamera(mCamera);

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + VIDEO_PATH_NAME + filecounter++ + ".mp4");
        mMediaRecorder.setOutputFile(file.getAbsolutePath());
        mMediaRecorder.setPreviewDisplay(surface);

        mMediaRecorder.setVideoSize(720, 480);
        mMediaRecorder.setVideoEncodingBitRate(3000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setMaxDuration(3600000);
        mMediaRecorder.setMaxFileSize(2000000000);

        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        mInitSuccesful = true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (!mInitSuccesful)
                initRecorder(mHolder.getSurface());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        shutdown();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }


    private void shutdown() {
        // Release MediaRecorder and especially the Camera as it's a shared
        // object that can be used by other applications

        // once the objects have been released they can't be reused
        mMediaRecorder = null;
        mCamera = null;
        finish();
    }

    /**
     * Create a file Uri for saving an image or video
     */
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    void preprocessdata() {
        boolean f = true;
        while (f) {
            ch.boye.httpclientandroidlib.client.HttpClient httpClient = HttpClientBuilder.create().build();
            HttpPost postRequest = new HttpPost("http://pilatus.d1.comp.nus.edu.sg/~team06/preprocessing.php");
            HttpResponse response=null;
            Log.i("DASH", "Executing the httpClient execute");
            try {
                response = httpClient.execute(postRequest);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (response!=null && response.getStatusLine().getStatusCode() == 200) {
                f = false;
            }

        }
    }
}