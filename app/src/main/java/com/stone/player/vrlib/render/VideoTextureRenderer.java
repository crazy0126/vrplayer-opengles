package com.stone.player.vrlib.render;


import android.content.Context;
import android.graphics.*;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import com.stone.player.vrlib.constant.GravityMode;


public class VideoTextureRenderer extends TextureBaseRenderer implements SurfaceTexture.OnFrameAvailableListener
{
    private static final String TAG = "VideoTextureRenderer";
    private static final String vertexShaderCode =
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vTexCoordinate;" +
                    "uniform mat4 textureTransform;" +
                    "varying vec2 v_TexCoordinate;" +
                    "void main() {" +
                    "   v_TexCoordinate = (textureTransform * vTexCoordinate).xy;" +
                    "   gl_Position = vPosition;" +
                    "}";

    private static final String fragmentShaderCode =
                    "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "uniform samplerExternalOES texture;" +
                    "varying vec2 v_TexCoordinate;" +
                    "void main () {" +
                    "    vec4 color = texture2D(texture, v_TexCoordinate);" +
                    "    gl_FragColor = color;" +
                    "}";


    private static float squareSize = 1.0f;
    private static float squareCoords[] = { -squareSize,  squareSize, 0.0f,   // top left
                                            -squareSize, -squareSize, 0.0f,     // bottom left
                                             squareSize, -squareSize, 0.0f,     // bottom right
                                             squareSize,  squareSize, 0.0f };   // top right

    private static short drawOrder[] = { 0, 1, 2, 0, 2, 3};

    private Context ctx;

    // Texture to be shown in backgrund
    private FloatBuffer textureBuffer;
    private float textureCoords[] = { 0.0f, 1.0f, 0.0f, 1.0f,       //左上角
                                      0.0f, 0.0f, 0.0f, 1.0f,           //左下角
                                      1.0f, 0.0f, 0.0f, 1.0f,           //右下角
                                      1.0f, 1.0f, 0.0f, 1.0f };         //右上角
    private int[] textures = new int[1];

    private int vertexShaderHandle;
    private int fragmentShaderHandle;
    private int shaderProgram;
    private FloatBuffer vertexBuffer;
    private ShortBuffer drawListBuffer;

    private SurfaceTexture videoTexture;
    private float[] videoTextureTransform;
    private boolean frameAvailable = false;

    private int videoWidth;
    private int videoHeight;
    private boolean adjustViewport = false;
    //zhaojiangang
    private  int screenNum = 1;
    private float crop_right = 0.0f;
    private boolean isCropRight = false;
    private GravityMode gravity = GravityMode.GRAVITY_RESIZE_ASPECT;
    private boolean gravityChanged = true;
    private boolean videoSizeChagned = false;
    private boolean surfaceSizeChanged = false;

    public VideoTextureRenderer(Context context, SurfaceTexture texture, int width, int height)
    {
        super(texture, width, height);
        this.ctx = context;
        videoTextureTransform = new float[16];
        setupVertexBuffer();
        setupTexture(ctx);
    }

    private void loadShaders()
    {
        vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShaderHandle, vertexShaderCode);
        GLES20.glCompileShader(vertexShaderHandle);
        checkGlError("Vertex shader compile");

        fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShaderHandle, fragmentShaderCode);
        GLES20.glCompileShader(fragmentShaderHandle);
        checkGlError("Pixel shader compile");

        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertexShaderHandle);
        GLES20.glAttachShader(shaderProgram, fragmentShaderHandle);
        GLES20.glLinkProgram(shaderProgram);
        checkGlError("Shader program compile");

        int[] status = new int[1];
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetProgramInfoLog(shaderProgram);
            Log.e(TAG, "Error while linking program:\n" + error);
        }

    }


    private void setupVertexBuffer()
    {
        // Draw list buffer
        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder. length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        // Initialize the texture holder
        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());

        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);
    }


    private void setupTexture(Context context)
    {
        ByteBuffer texturebb = ByteBuffer.allocateDirect(textureCoords.length * 4);
        texturebb.order(ByteOrder.nativeOrder());

        textureBuffer = texturebb.asFloatBuffer();
        textureBuffer.put(textureCoords);
        textureBuffer.position(0);


        // Generate the actual texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(1, textures, 0);
        checkGlError("Texture generate");

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        checkGlError("Texture bind");

        videoTexture = new SurfaceTexture(textures[0]);
        videoTexture.setOnFrameAvailableListener(this);
    }

    @Override
    protected boolean draw()
    {
        synchronized (this)
        {
            if (frameAvailable)
            {
                videoTexture.updateTexImage();

                videoTexture.getTransformMatrix(videoTextureTransform);
                frameAvailable = false;
            }
            else
            {
                return false;
            }

        }


        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);


        // Draw texture
        GLES20.glUseProgram(shaderProgram);
        int textureParamHandle = GLES20.glGetUniformLocation(shaderProgram, "texture");
        int textureCoordinateHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoordinate");
        int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
        int textureTranformHandle = GLES20.glGetUniformLocation(shaderProgram, "textureTransform");

        //adjust display aspect.
        resolveScale(videoWidth,videoHeight,surfaceWidth,surfaceHeight,gravity);

        //3D to 2D
        if(isCropRight) {
            resetTextureBuffer();
            setCropRight();
            isCropRight = false;
        }

        for(int i = 0; i < screenNum; i++) {
            GLES20.glViewport(i * surfaceWidth/screenNum, 0, surfaceWidth/screenNum, surfaceHeight);
            GLES20.glEnableVertexAttribArray(positionHandle);

            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 4 * 3, vertexBuffer);

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glUniform1i(textureParamHandle, 0);


            GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
            GLES20.glVertexAttribPointer(textureCoordinateHandle, 4, GLES20.GL_FLOAT, false, 0, textureBuffer);

            GLES20.glUniformMatrix4fv(textureTranformHandle, 1, false, videoTextureTransform, 0);

            GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
        }

        return true;
    }


    @Override
    protected void initGLComponents()
    {

        loadShaders();
    }

    @Override
    protected void deinitGLComponents()
    {
        GLES20.glDeleteTextures(1, textures, 0);
        GLES20.glDeleteProgram(shaderProgram);
        videoTexture.release();
        videoTexture.setOnFrameAvailableListener(null);
    }

    public void setVideoSize(int width, int height)
    {
        this.videoWidth = width;
        this.videoHeight = height;
        videoSizeChagned = true;
    }
    public void setSurfaceSize(int surfaceWidth, int surfaceHeight)
    {
        this.surfaceWidth = surfaceWidth;
        this.surfaceHeight = surfaceHeight;
        surfaceSizeChanged = true;
    }

    public void checkGlError(String op)
    {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + GLUtils.getEGLErrorString(error));
        }
    }

    public SurfaceTexture getVideoTexture()
    {
        return videoTexture;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture)
    {

        synchronized (this)
        {
            frameAvailable = true;

        }
    }


    public int convert2DTo3D(boolean enable)
    {

        if(enable){
            screenNum = 2;
        }else{
            screenNum = 1;
        }
        return 0;
    }
    public int convert3DTo2D(boolean enable)
    {
        resetTextureBuffer();
        if(enable){
            crop_right = 0.5f;
        }else{
            crop_right = 0.0f;
        }
        isCropRight = true;
        return 0;
    }

    public void setGravity(GravityMode gravity)
    {
        if(this.gravity != gravity ){
            this.gravity = gravity;
            gravityChanged = true;
        }
    }
    private void resetTextureCoords()
    {
        //左上角
        textureCoords[0] =0.0f;
        textureCoords[1] =1.0f;
        textureCoords[2] =0.0f;
        textureCoords[3] =1.0f;
        //左下角
        textureCoords[4] =0.0f;
        textureCoords[5] =0.0f;
        textureCoords[6] =0.0f;
        textureCoords[7] =1.0f;
        //右下角
        textureCoords[8] =1.0f;
        textureCoords[9] =0.0f;
        textureCoords[10] =0.0f;
        textureCoords[11] =1.0f;
        //右上角
        textureCoords[12] =1.0f;
        textureCoords[13] =1.0f;
        textureCoords[14] =0.0f;
        textureCoords[15] =1.0f;
    }
    private void resetTextureBuffer()
    {

        resetTextureCoords();
        textureBuffer.put(textureCoords);
        textureBuffer.position(0);

    }
    private void resetVertexCoords()
    {
        //top left
        squareCoords[0] = -squareSize;
        squareCoords[1] = squareSize;
        squareCoords[2] = 0.0f;
        //bottom left
        squareCoords[3] = -squareSize;
        squareCoords[4] = -squareSize;
        squareCoords[5] = 0.0f;
        //bottom right
        squareCoords[6] = squareSize;
        squareCoords[7] = -squareSize;
        squareCoords[8] = 0.0f;
        //top right
        squareCoords[9] = squareSize;
        squareCoords[10] = squareSize;
        squareCoords[11] = 0.0f;
    }
    private void resetVertexBuffer()
    {
        //TODO
        resetVertexCoords();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);
    }
    private void resetDrawBuffer()
    {
        //TODO
    }
    private  void  setCropRight(){
            textureBuffer.put(8, textureBuffer.get(8) - crop_right);                    //crop right, change 右下角x坐标
            textureBuffer.put(12, textureBuffer.get(12) - crop_right);                 // crop right, change 右上角x坐标

    }
    private void resolveScale(int videoWidth, int videoHeight, int surfaceWidth, int surfaceHeight, GravityMode gravity)
    {
            surfaceWidth = surfaceWidth / screenNum;
            switch(gravity){

                case GRAVITY_RESIZE:                                      //normal stretch 变形
                   resetVertexBuffer();
                    return;
                case GRAVITY_RESIZE_ASPECT:                             //ASPECT_FIT
                    break;
                case GRAVITY_RESIZE_ASPECT_FILL:                        //ASPECT_FILL
                    break;
                default:
                    Log.e(TAG, "Invalid gravity  " + gravity);
                    return;
            }
        float videoAspect = videoWidth / (float) videoHeight;
        float surfaceAspect = surfaceWidth / (float) surfaceHeight;
        //Log.i(TAG, "resolveScale: wideoWidth: " + videoWidth + " videoHeight: " + videoHeight + " surfaceWidth: " + surfaceWidth + " surfaceHeight: " + surfaceHeight);
       // Log.i(TAG, "resolveScale:  videoAspect: " + videoAspect + " surfaceAspect: "+ surfaceAspect);
        float width = videoWidth *((float)1.0 - crop_right);
        float height = videoHeight;

        float dW = (float)surfaceWidth / width;
        float dH = (float)surfaceHeight / height;
        float dd = 1.0f;
        float nW = 1.0f;
        float nH = 1.0f;

        switch(gravity){
            case GRAVITY_RESIZE_ASPECT_FILL:
                dd = dW > dH ? dW:dH;
                break;
            case GRAVITY_RESIZE_ASPECT:
                dd = dW < dH ? dW : dH;
                break;
        }

        nW = (width * dd ) / (float)surfaceWidth;
        nH = (height * dd ) / (float)surfaceHeight;
        //Log.i(TAG, "resolveScale: dw: " + dW + " dH: "+ dH + "nW: "+ nW +" nH: " +nH);
        squareCoords[0] = - nW;
        squareCoords[1] =  nH;
        squareCoords[2] =  0.0f;

        squareCoords[3] = - nW;
        squareCoords[4] = - nH;
        squareCoords[5] =  0.0f;

        squareCoords[6] =  nW;
        squareCoords[7] = - nH;
        squareCoords[8] =  0.0f;

        squareCoords[9] =  nW;
        squareCoords[10] =  nH;
        squareCoords[11] =  0.0f;

        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);
    }

}
