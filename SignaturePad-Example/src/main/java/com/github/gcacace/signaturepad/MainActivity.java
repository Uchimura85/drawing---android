package com.github.gcacace.signaturepad;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.gcacace.signaturepad.utils.Point;
import com.github.gcacace.signaturepad.views.SignaturePad;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import it.gcacace.signaturepad.R;

public class MainActivity extends Activity{

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private SignaturePad mSignaturePad;
    private Button mClearButton;

    private Button mChangePlotTypeButton;
    private Button mZoomInButton;
    private Button mZoomOutButton;

    int MAX_LENGTH = 22;
    float [] arr =new float[MAX_LENGTH];
    int [] grid_lines =new int[MAX_LENGTH];

    List<String> strTime = new ArrayList<>();
    float sinX = 0f;
    void drawUpdate(){
        float [] ttt = new float[]{130, 17, 120, 12,34,0, 140, 160, 130, 140, 160, 130, 140, 160, 130, 140, 160, 130, 140, 160, 170, 200};
        for(int i=0;i<ttt.length-1;i++){
            arr[i] = ttt[i];
            grid_lines[i] = 0;
            if(i % 50 == 0)
            {
                grid_lines[i] = 0;
                strTime.add("k"+i);
            }else
            {
                strTime.add("");
            }
        }
        strTime.add("");

//        arr[arr.length-1]=1500 + (float)Math.sin(sinX)*1500;
        grid_lines[0] = 2;
        strTime.set(0,"11:00 PM");
        grid_lines[arr.length-2] = 2;
        strTime.set(arr.length-2,"8:00 PM");

        sinX +=0.5f;
        mSignaturePad.setMaxHeight(200);
        mSignaturePad.setPenColor(3);
        mSignaturePad.setPts(arr, grid_lines,strTime);

//        mSignaturePad.update();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        verifyStoragePermissions(this);
        setContentView(R.layout.activity_main);
        for(int i = 0 ; i < MAX_LENGTH ;i  ++ )
            arr[i] = (float)Math.random()*200;

        mSignaturePad = (SignaturePad) findViewById(R.id.signature_pad);


        Timer timer = new Timer();

        mSignaturePad.setOnSignedListener(new SignaturePad.OnSignedListener() {
            @Override
            public void onStartSigning() {
                Toast.makeText(MainActivity.this, "OnStartSigning", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSigned() {

                mClearButton.setEnabled(true);
            }

            @Override
            public void onClear() {
                mClearButton.setEnabled(false);
            }
        });

        mClearButton = (Button) findViewById(R.id.Start);
        this.mChangePlotTypeButton= (Button) findViewById(R.id.save_button);
        this.mZoomInButton= (Button) findViewById(R.id.zoom_in);
        this.mZoomOutButton= (Button) findViewById(R.id.zoom_out);

        mClearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread thread;
                final Runnable runnableDraw = new Runnable() {
                    @Override
                    public void run() {
                        drawUpdate();
                    }
                };
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (;;) {
                            runOnUiThread(runnableDraw);
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                                break;
                            }
                        }
                    }
                });
                thread.start();
            }
        });


        mChangePlotTypeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSignaturePad.setPenColor(plotType);
            }
        });
        mZoomInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSignaturePad.zoomIn();
            }
        });
        mZoomOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSignaturePad.zoomOut();
            }
        });
        new android.os.Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        Log.i("tag", "This'll run 300 milliseconds later");
                        drawUpdate();
                    }
                },
                300);

    }
    int plotType = 0;
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0
                        || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "Cannot write images to external storage", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public File getAlbumStorageDir(String albumName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), albumName);
        if (!file.mkdirs()) {
            Log.e("SignaturePad", "Directory not created");
        }
        return file;
    }

    public void saveBitmapToJPG(Bitmap bitmap, File photo) throws IOException {
        Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bitmap, 0, 0, null);
        OutputStream stream = new FileOutputStream(photo);
        newBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
        stream.close();
    }

    public boolean addJpgSignatureToGallery(Bitmap signature) {
        boolean result = false;
        try {
            File photo = new File(getAlbumStorageDir("SignaturePad"), String.format("Signature_%d.jpg", System.currentTimeMillis()));
            saveBitmapToJPG(signature, photo);
            scanMediaFile(photo);
            result = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void scanMediaFile(File photo) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(photo);
        mediaScanIntent.setData(contentUri);
        MainActivity.this.sendBroadcast(mediaScanIntent);
    }

    public boolean addSvgSignatureToGallery(String signatureSvg) {
        boolean result = false;
        try {
            File svgFile = new File(getAlbumStorageDir("SignaturePad"), String.format("Signature_%d.svg", System.currentTimeMillis()));
            OutputStream stream = new FileOutputStream(svgFile);
            OutputStreamWriter writer = new OutputStreamWriter(stream);
            writer.write(signatureSvg);
            writer.close();
            stream.flush();
            stream.close();
            scanMediaFile(svgFile);
            result = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Checks if the app has permission to write to device storage
     * <p/>
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity the activity from which permissions are checked
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
}
