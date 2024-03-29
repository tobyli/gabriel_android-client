package edu.cmu.cs.gabriel;

import java.io.File;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Html;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.glass.view.WindowUtils;


import edu.cmu.cs.gabriel.aedassistant.AEDAssistantConst;
import edu.cmu.cs.gabriel.aedassistant.MainController;
import edu.cmu.cs.gabriel.aedassistant.State;
import edu.cmu.cs.gabriel.aedassistant.StateMessage;
import edu.cmu.cs.gabriel.network.AccStreamingThread;
import edu.cmu.cs.gabriel.network.NetworkProtocol;
import edu.cmu.cs.gabriel.network.ResultReceivingThread;
import edu.cmu.cs.gabriel.network.VideoStreamingThread;
import edu.cmu.cs.gabriel.token.ReceivedPacketInfo;
import edu.cmu.cs.gabriel.token.TokenController;

public class GabrielClientActivity extends Activity implements TextToSpeech.OnInitListener, SensorEventListener{

    private static final String LOG_TAG = "Main";

    // major components for streaming sensor data and receiving information
    private VideoStreamingThread videoStreamingThread = null;
    private AccStreamingThread accStreamingThread = null;
    private ResultReceivingThread resultThread = null;
    private TokenController tokenController = null;

    private boolean isRunning = false;
    private boolean isFirstExperiment = true;
    private CameraPreview preview = null;

    private SensorManager sensorManager = null;
    private Sensor sensorAcc = null;
    private TextToSpeech tts = null;

    private ReceivedPacketInfo receivedPacketInfo = null;

    private MainController mainController;
    private Context context;



    private Handler promptReadingHandler = null, fakeStateMessageHandler = null;
    private Handler checkConnectionHandler = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(LOG_TAG, "++onCreate");
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(WindowUtils.FEATURE_VOICE_COMMANDS);
        setContentView(R.layout.activity_main);
        context = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON +
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }

    @Override
    protected void onResume() {
        Log.v(LOG_TAG, "++onResume");
        super.onResume();

        initOnce();
        if (Const.IS_EXPERIMENT) { // experiment mode
            runExperiments();
        } else { // demo mode
            initPerRun(Const.SERVER_IP, Const.TOKEN_SIZE, null);
        }
    }

    @Override
    protected void onPause() {
        Log.v(LOG_TAG, "++onPause");

        this.terminate();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.v(LOG_TAG, "++onDestroy");

        super.onDestroy();
    }

    @Override
    public boolean onCreatePanelMenu (int featureId, Menu menu){
        if(featureId == WindowUtils.FEATURE_VOICE_COMMANDS){
            menu.add("Yes");
            menu.add("No");
            return true;
        }
        return super.onCreatePanelMenu(featureId, menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if(featureId == WindowUtils.FEATURE_VOICE_COMMANDS){
            if(tts != null)
                //tts.speak(item.getTitle().toString(), TextToSpeech.QUEUE_ADD, null);
            if(item.getTitle().toString().contentEquals("Yes")){
                YesButtonOnClick(null);
            }
            else if(item.getTitle().toString().contentEquals("No")){
                NoButtonOnClick(null);
            }
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    /**
     * Does initialization for the entire application. Called only once even for multiple experiments.
     */
    private void initOnce() {
        Log.v(LOG_TAG, "++initOnce");
        preview = (CameraPreview) findViewById(R.id.camera_preview);
        preview.checkCamera();
        preview.setPreviewCallback(previewCallback);
        
        Const.ROOT_DIR.mkdirs();
        Const.EXP_DIR.mkdirs();
        
        // TextToSpeech.OnInitListener
        if (tts == null) {
            tts = new TextToSpeech(this, this);
        }

        // IMU sensors
        if (sensorManager == null) {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, sensorAcc, SensorManager.SENSOR_DELAY_NORMAL);
        }
        initAEDAssistant();
        isRunning = true;
    }

    private void initAEDAssistant() {
        // initiate the main controller
        if (mainController == null) {
            TextView textView = (TextView) findViewById(R.id.gabriel_log);
            mainController = new MainController(this, textView);
            // initiate the main controller
            if (mainController != null && mainController.active == false) {
                mainController.init();
                screenLog("Initial State");

                //add a new thread to read out the prompt every Ns
                promptReadingHandler = new Handler();
                promptReadingHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (mainController != null && mainController.getCurrentState() != null && mainController.getCurrentState().stateType != State.ENDING_STATE)
                                mainController.readOutCurrentPrompt(true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        promptReadingHandler.postDelayed(this, AEDAssistantConst.VOICE_PROMPT_DELAY);
                    }
                }, AEDAssistantConst.VOICE_PROMPT_DELAY);
            }
            //print the initial message
            screenLog("A: " + mainController.spokenSentence, "#f89ff9");

            checkConnectionHandler = new Handler();
            checkConnectionHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    long currentTime = Calendar.getInstance().getTimeInMillis();
                    if(mainController.isConnected == true && (currentTime - mainController.lastMessageReceivedTimeStamp > AEDAssistantConst.CHECK_CONNECTION_THRESHOLD)){
                      //  mainController.isConnected = false;
                      //  screenLog("Disconnected to the Gabriel Server", "#ffffff");
                    }
                    checkConnectionHandler.postDelayed(this, AEDAssistantConst.CHECK_CONNECTION_DELAY);
                }
            }, AEDAssistantConst.CHECK_CONNECTION_DELAY);
        /*
        //add a new thread that fakes the StateMessage
        fakeStateMessageHandler = new Handler();
        fakeStateMessageHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mainController != null && mainController.getCurrentState() != null && mainController.getCurrentState().stateType != State.ENDING_STATE) {
                    StateMessage stateMessage = new StateMessage(mainController.getCurrentState().getIdentifier(), StateMessage.NEXT_STATE);
                    mainController.handleStateMessage(stateMessage);
                    screenLog("SENT A FAKE \"NEXT\" MESSAGE");
                    fakeStateMessageHandler.postDelayed(this, AEDAssistantConst.FAKE_MESSAGE_DELAY);
                }
                else if(mainController != null && mainController.getCurrentState() != null && mainController.getCurrentState().stateType == State.ENDING_STATE) {
                    screenLog("REACH THE ENDING STATE");
                }
            }
        }, AEDAssistantConst.FAKE_MESSAGE_DELAY);
        */
        }
    }

    public void YesButtonOnClick(View view){
        if(mainController != null && mainController.getCurrentState() != null && mainController.getCurrentState().stateType != State.ENDING_STATE) {
            StateMessage stateMessage = new StateMessage(mainController.getCurrentState().getIdentifier(), StateMessage.NEXT_STATE);
            screenLog("U: " + "Yes", "#42f4f4");
            mainController.handleStateMessage(stateMessage);
            screenLog("A: " + mainController.spokenSentence, "#f89ff9");
            //screenLog("SENT A \"NEXT\" MESSAGE");
            //show the ending state
            if(mainController.getCurrentState().stateType == State.ENDING_STATE)
                YesButtonOnClick(view);

        }
        else if(mainController != null && mainController.getCurrentState() != null && mainController.getCurrentState().stateType == State.ENDING_STATE) {
            screenLog("REACH THE ENDING STATE", "#ffffff");
        }
    }

    public void NoButtonOnClick(View view){
        if(mainController != null && mainController.getCurrentState() != null && mainController.getCurrentState().stateType != State.ENDING_STATE) {
            StateMessage stateMessage = new StateMessage(mainController.getCurrentState().getIdentifier(), StateMessage.ERROR_STATE);
            screenLog("U: " + "No", "#42f4f4");
            mainController.handleStateMessage(stateMessage);
            screenLog("A: " + mainController.spokenSentence, "#f89ff9");
        }
        else if(mainController != null && mainController.getCurrentState() != null && mainController.getCurrentState().stateType == State.ENDING_STATE) {
            screenLog("REACH THE ENDING STATE", "#ffffff");
        }
    }
    
    /**
     * Does initialization before each run (connecting to a specific server).
     * Called once before each experiment.
     */
    private void initPerRun(String serverIP, int tokenSize, File latencyFile) {
        Log.v(LOG_TAG, "++initPerRun");
        if (tokenController != null){
            tokenController.close();
        }
        if ((videoStreamingThread != null) && (videoStreamingThread.isAlive())) {
            videoStreamingThread.stopStreaming();
            videoStreamingThread = null;
        }
        if ((accStreamingThread != null) && (accStreamingThread.isAlive())) {
            accStreamingThread.stopStreaming();
            accStreamingThread = null;
        }
        if ((resultThread != null) && (resultThread.isAlive())) {
            resultThread.close();
            resultThread = null;
        }

        if (Const.IS_EXPERIMENT) {
            if (isFirstExperiment) {
                isFirstExperiment = false;
            } else {
                try {
                    Thread.sleep(20 * 1000);
                } catch (InterruptedException e) {}
            }
        }

        tokenController = new TokenController(tokenSize, latencyFile);
        resultThread = new ResultReceivingThread(serverIP, Const.RESULT_RECEIVING_PORT, returnMsgHandler, mainController, this);
        resultThread.start();

        videoStreamingThread = new VideoStreamingThread(serverIP, Const.VIDEO_STREAM_PORT, returnMsgHandler, tokenController, mainController);
        videoStreamingThread.start();





    }

    /**
     * Runs a set of experiments with different server IPs and token numbers.
     * IP list and token sizes are defined in the Const file.
     */
    private void runExperiments(){
        final Timer startTimer = new Timer();
        TimerTask autoStart = new TimerTask(){
            int ipIndex = 0;
            int tokenIndex = 0;
            @Override
            public void run() {
                GabrielClientActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // end condition
                        if ((ipIndex == Const.SERVER_IP_LIST.length) || (tokenIndex == Const.TOKEN_SIZE_LIST.length)) {
                            Log.d(LOG_TAG, "Finish all experiemets");
                            startTimer.cancel();
                            terminate();
                            return;
                        }

                        // make a new configuration
                        String serverIP = Const.SERVER_IP_LIST[ipIndex];
                        int tokenSize = Const.TOKEN_SIZE_LIST[tokenIndex];
                        File latencyFile = new File (Const.EXP_DIR.getAbsolutePath() + File.separator + 
                                "latency-" + serverIP + "-" + tokenSize + ".txt");
                        Log.i(LOG_TAG, "Start new experiment - IP: " + serverIP +"\tToken: " + tokenSize);

                        // run the experiment
                        initPerRun(serverIP, tokenSize, latencyFile);

                        // move to the next experiment
                        tokenIndex++;
                        if (tokenIndex == Const.TOKEN_SIZE_LIST.length){
                            tokenIndex = 0;
                            ipIndex++;
                        }
                    }
                });
            }
        };

        // run 5 minutes for each experiment
        startTimer.schedule(autoStart, 1000, 5*60*1000);
    }

    private PreviewCallback previewCallback = new PreviewCallback() {
        // called whenever a new frame is captured
        public void onPreviewFrame(byte[] frame, Camera mCamera) {
            if (isRunning) {
                Camera.Parameters parameters = mCamera.getParameters();
                if (videoStreamingThread != null){
                    videoStreamingThread.push(frame, parameters);
                }
            }
        }
    };

    /**
     * Notifies token controller that some response is back
     */
    private void notifyToken() {
        Message msg = Message.obtain();
        msg.what = NetworkProtocol.NETWORK_RET_TOKEN;
        if(receivedPacketInfo != null){
            receivedPacketInfo.setGuidanceDoneTime(System.currentTimeMillis());
            msg.obj = receivedPacketInfo;
            try {
                tokenController.tokenHandler.sendMessage(msg);
            } catch (NullPointerException e) {
                // might happen because token controller might have been terminated
            }
        }
        else{
            Log.d(LOG_TAG, "NULL VALUE");
        }
    }
    
    /**
     * Handles messages passed from streaming threads and result receiving threads.
     */
    private Handler returnMsgHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == NetworkProtocol.NETWORK_RET_FAILED) {
                terminate();
            }
            if (msg.what == NetworkProtocol.NETWORK_RET_MESSAGE) {
                receivedPacketInfo = (ReceivedPacketInfo) msg.obj;
                receivedPacketInfo.setMsgRecvTime(System.currentTimeMillis());
            }

            if (msg.what == NetworkProtocol.NETWORK_RET_SPEECH) {

                String message = (String) msg.obj;
                System.out.println(message);
                System.out.println("\n" + msg + "\n");

                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                //this is the part used to speat out the instruction

                /*
                if (tts != null && !tts.isSpeaking()){
                    Log.d(LOG_TAG, "tts to be played: " + ttsMessage);
                    //tts.setSpeechRate(1.5f);
                    String[] splitMSGs = ttsMessage.split("\\.");
                    HashMap<String, String> map = new HashMap<String, String>();
                    map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "unique");

                    if (splitMSGs.length == 1) {
                        //tts.speak(splitMSGs[0].toString().trim(), TextToSpeech.QUEUE_FLUSH, map); // the only sentence
                    }
                    else {
                        //tts.speak(splitMSGs[0].toString().trim(), TextToSpeech.QUEUE_FLUSH, null); // the first sentence
                        for (int i = 1; i < splitMSGs.length - 1; i++) {
                            //tts.playSilence(350, TextToSpeech.QUEUE_ADD, null); // add pause for every period
                            //tts.speak(splitMSGs[i].toString().trim(),TextToSpeech.QUEUE_ADD, null);
                        }
                        //tts.playSilence(350, TextToSpeech.QUEUE_ADD, null);
                        //tts.speak(splitMSGs[splitMSGs.length - 1].toString().trim(),TextToSpeech.QUEUE_ADD, map); // the last sentence
                    }

                }
                */
            }
            //this is the part used to show the guidance image
            if (msg.what == NetworkProtocol.NETWORK_RET_IMAGE || msg.what == NetworkProtocol.NETWORK_RET_ANIMATION) {
                Bitmap feedbackImg = (Bitmap) msg.obj;
                //ImageView img = (ImageView) findViewById(R.id.guidance_image);
                //img.setImageBitmap(feedbackImg);
            }
            if (msg.what == NetworkProtocol.NETWORK_RET_DONE) {
                notifyToken();
            }

        }
    };

    public void screenLog(String log){
        screenLog(log, "#000000");
    }

    public void screenLog(String log, String color) {
        TextView textView = (TextView) findViewById(R.id.gabriel_log);
        String html = "";
        if(textView.getText().toString().length() > 160)
            textView.setText("");
        if(textView.getEditableText() != null)
            html = Html.toHtml(textView.getEditableText());
        textView.setText(Html.fromHtml(html + " " + "<font color = \"" + color.toString() +"\">" + log + "</font>"), TextView.BufferType.EDITABLE);
        textView.invalidate();
    }

        /**
         * Terminates all services.
         */
    private void terminate() {
        Log.v(LOG_TAG, "++terminate");
        
        isRunning = false;

        if ((resultThread != null) && (resultThread.isAlive())) {
            resultThread.close();
            resultThread = null;
        }
        if ((videoStreamingThread != null) && (videoStreamingThread.isAlive())) {
            videoStreamingThread.stopStreaming();
            videoStreamingThread = null;
        }
        if ((accStreamingThread != null) && (accStreamingThread.isAlive())) {
            accStreamingThread.stopStreaming();
            accStreamingThread = null;
        }
        if (tokenController != null){
            tokenController.close();
            tokenController = null;
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (preview != null) {
            preview.setPreviewCallback(null);
            preview.close();
            preview = null;
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            sensorManager = null;
            sensorAcc = null;
        }

        if(fakeStateMessageHandler != null)
            fakeStateMessageHandler.removeCallbacksAndMessages(null);
        if(promptReadingHandler != null)
            promptReadingHandler.removeCallbacksAndMessages(null);
    }



    /**************** SensorEventListener ***********************/
    // TODO: test accelerometer streaming
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER)
            return;
        if (accStreamingThread != null) {
//          accStreamingThread.push(event.values);
        }
        // Log.d(LOG_TAG, "acc_x : " + mSensorX + "\tacc_y : " + mSensorY);
    }
    /**************** End of SensorEventListener ****************/
    
    /**************** TextToSpeech.OnInitListener ***************/
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            if (tts == null) {
                tts = new TextToSpeech(this, this);
            }
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(LOG_TAG, "Language is not available.");
            }
            int listenerResult = tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onDone(String utteranceId) {
                    Log.v(LOG_TAG,"progress on Done " + utteranceId);
//                  notifyToken();
                }
                @Override
                public void onError(String utteranceId) {
                    Log.v(LOG_TAG,"progress on Error " + utteranceId);
                }
                @Override
                public void onStart(String utteranceId) {
                    Log.v(LOG_TAG,"progress on Start " + utteranceId);
                }
            });
            if (listenerResult != TextToSpeech.SUCCESS) {
                Log.e(LOG_TAG, "failed to add utterance progress listener");
            }
        } else {
            // Initialization failed.
            Log.e(LOG_TAG, "Could not initialize TextToSpeech.");
        }
    }
    /**************** End of TextToSpeech.OnInitListener ********/

}
