package com.stone.player.vrlib;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import com.stone.player.vrlib.constant.GravityMode;
import com.stone.player.vrlib.render.VideoTextureRenderer;

import java.io.IOException;

public class SurfaceActivity extends Activity implements TextureView.SurfaceTextureListener,View.OnClickListener
{
   private static final String TAG = "SurfaceActivity";
    private TextureView surface;
    private MediaPlayer player;
    private VideoTextureRenderer renderer;

    private int surfaceWidth;
    private int surfaceHeight;
    boolean isMultiScreen = false;
    boolean is3dTo2d = false;
    GravityMode gravity = GravityMode.GRAVITY_RESIZE;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

        surface = (TextureView) findViewById(R.id.surface);
        surface.setSurfaceTextureListener(this);
        Button dualScreenBtn = findViewById(R.id.dualscreen);
        dualScreenBtn.setOnClickListener(this);

        Button convert3dto2dBtn = findViewById(R.id.convert3dto2d);
        convert3dto2dBtn.setOnClickListener(this);
        Button gravityBtn = findViewById(R.id.changegravity);
        gravityBtn.setOnClickListener(this);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (surface.isAvailable())
            startPlaying();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (player != null)
            player.release();
        if (renderer != null)
            renderer.onPause();
    }

    private void startPlaying()
    {
        renderer = new VideoTextureRenderer(this, surface.getSurfaceTexture(), surfaceWidth, surfaceHeight);
        player = new MediaPlayer();

        try
        {
            //for 3d/2d
          //  AssetFileDescriptor afd = getAssets().openFd("3d.mp4");
            //for 2d/3d
            AssetFileDescriptor afd = getAssets().openFd("big_buck_bunny.mp4");
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.setSurface(new Surface(renderer.getVideoTexture()));
            player.setLooping(true);
            player.prepare();
            renderer.setVideoSize(player.getVideoWidth(), player.getVideoHeight());
            player.start();

        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not open input video!");
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
    {
        Log.i(TAG, "onSurfaceTextureAvailable: "+ " width: " + width +" height: "+ height);
        surfaceWidth = width;
        surfaceHeight = height;
        startPlaying();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {
        Log.i(TAG, "onSurfaceTextureSizeChanged: " + " width: " + width +" height: "+ height);
        //To change body of implemented methods use File | Settings | File Templates.
        renderer.setSurfaceSize(width,height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
        Log.i(TAG, "onSurfaceTextureDestroyed: ");
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()){

            case R.id.dualscreen:
                if(!isMultiScreen) {
                    renderer.convert2DTo3D(true);
                    isMultiScreen = true;
                }else {
                    isMultiScreen = false;
                    renderer.convert2DTo3D(false);
                }
                break;
            case R.id.convert3dto2d:
                if(!is3dTo2d){
                    is3dTo2d = true;
                    renderer.convert3DTo2D(true);
                }else {
                    is3dTo2d = false;
                    renderer.convert3DTo2D(false);
                }
                break;
            case R.id.changegravity:

                Toast.makeText(this, " gravity： " + gravity.name(), Toast.LENGTH_SHORT).show();

                renderer.setGravity(gravity);

                gravity = GravityMode.values()[(gravity.ordinal() + 1)%3];



        }

    }
}
