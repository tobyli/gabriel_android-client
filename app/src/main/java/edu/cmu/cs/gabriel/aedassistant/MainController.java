package edu.cmu.cs.gabriel.aedassistant;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.util.Log;
import android.text.format.Time;
import android.widget.TextView;

import java.util.Calendar;

import edu.cmu.cs.gabriel.GabrielClientActivity;
//import edu.cmu.cs.gabriel.R;

/**
 * @author toby
 * @date 10/19/16
 * @time 6:39 PM
 */
public class MainController {
    public boolean active = false;
    public long lastMessageReceivedTimeStamp;
    public boolean isConnected = true;
    private Object messageMutex = new Object();
    private State currentState;
    private TextToSpeech tts;
    private Time lastTtsTime = new Time();
    private Context context;
    private TextView textView;
    //TODO: the prompt of the current state should be read out every Ns

    public MainController(Context context, TextView textView){
        this.context = context;
        this.textView = textView;
        this.lastMessageReceivedTimeStamp = Calendar.getInstance().getTimeInMillis();
        if (tts == null) {
            tts = new TextToSpeech(context, null);
        }
    }

    public State getCurrentState(){
        return currentState;
    }

    //TODO: state message should be priority-queued by time stamp
    public void handleStateMessage(StateMessage message){
        synchronized (messageMutex) {
            if(!message.getStateIdentifier().contentEquals(currentState.getIdentifier())) {
                //wrong identifier received
                Log.w("CONTROLLER", "Wrong identifier received, received " + message.getStateIdentifier() + " expecting " + currentState.getIdentifier());
                //do nothing
            }
            else{
                //mainActivity.screenLog("A: " + getCurrentState().getPrompt(), "#f89ff9");
                //correct identifier received
                try {
                    switch (message.messageType) {
                        case StateMessage.NEXT_STATE:
                            moveToNextState();
                            //tts.stop();
                            //while (tts.isSpeaking()) {/* nothing to do. just wait*/}
                            //tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);

                            readOutCurrentPrompt(false);
                            break;
                        case StateMessage.ERROR_STATE:
                            moveToErrorState();
                            //tts.stop();
                            //while (tts.isSpeaking()) {/* nothing to do. just wait*/}
                            //tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);

                            readOutCurrentPrompt(false);
                            break;
                        case StateMessage.HOLD:
                        default:
                            //do nothing
                    }
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void moveToNextState() throws Exception{
        if(currentState.getNextState() == null)
            throw currentState.stateType == State.ENDING_STATE ? new Exception("Reach the ending state") : new Exception("NULL next state");
        else
            currentState = currentState.getNextState();
    }

    private void moveToErrorState() throws Exception{
        if(currentState.getErrorState() == null)
            throw currentState.stateType == State.ENDING_STATE ? new Exception("Reach the ending state") : new Exception("NULL next state");
        else
            currentState = currentState.getErrorState();
    }

    public void readOutCurrentPrompt(boolean reread) throws Exception{
        if(currentState == null)
            throw new Exception("null current state");
        if(tts == null)
            throw new Exception("null tts");

        if (reread == true) {
            // check how long has it been since assistant last spoke
            Time now = new Time();
            now.setToNow();

            if (now.toMillis(false) - lastTtsTime.toMillis(false) < AEDAssistantConst.VOICE_PROMPT_DELAY) {
                return;
            }
        }
        tts.stop();
        if(isConnected)
            tts.speak(currentState.getPrompt(), TextToSpeech.QUEUE_FLUSH, null);
        else
            tts.speak(currentState.getDisconnectedPrompt(), TextToSpeech.QUEUE_FLUSH, null);
        lastTtsTime.setToNow();
    }

    public void init(){
        //create the states
        State initialState = new State("Initial state", "initialState", "AED Assistant ready. Say yes to continue.", State.INITIAL_STATE);

        State subjectAgeDetectionState = new State("Detecting if the human is an adult", "subjectAgeDetectionState", "Please look at the face of the subject. Detecting the age of the subject", State.NORMAL_STATE);
        //specify a special message to speak if the client is disconneced to the gabriel server
        subjectAgeDetectionState.setDisconnectedPrompt("Is the subject an adult? Say yes or no");

        //State adultDetectedState  = new State("Human detected as an adult", "adultDetectedState", "The subject seems to be an adult. Is this correct?", State.NORMAL_STATE);
        //State childDetectedState = new State("Human detected as a child", "childDetectedState", "The subject seems to be a child. Is this correct?", State.NORMAL_STATE);

        State showAdultPadState = new State("State to detect adult pads", "showAdultPadState", "Subject detected as Adult. Please pick the pads with the red instructions and put them in front of the camera.", State.NORMAL_STATE);
        //specify a special message to speak if the client is disconneced to the gabriel server
        showAdultPadState.setDisconnectedPrompt("Subject detected as Adult. Please pick the pads with the red instructions. Say yes when you are ready");

        State showAdultPadErrorState = new State("Pads shown are not correct", "showAdultPadErrorState", "Those are the child pads. Please pick the pads with red instructions.", State.NORMAL_STATE);

        State showChildPadState = new State("State to detect child pads", "showChildPadState", "Subject detected as Child. Please pick the pads with the blue instructions.", State.NORMAL_STATE);
        State showChildPadErrorState = new State("Pads shown are not correct", "showChildPadErrorState", "Those are the adult pads. Please pick the pads with blue instructions.", State.NORMAL_STATE);

        State peelInstructionsState = new State("State to peel instructions", "peelInstructionsState", "Great. Now peel the instructions from the pads. Say yes when ready.", State.NORMAL_STATE);
        State peelInstructionsErrorState = new State("Pads not peeled off","peelInstructionsErrorState", "The pads have not been peeled off. Please peel off the instructions from the pad. Say yes when done.",State.NORMAL_STATE);

        State putPadRightChestState = new State("State to put pad on right chest.", "putPadRightChestState", "Good. Now, place the pad that goes to the right chest of the subject. Say yes when done.", State.NORMAL_STATE);
        State putPadRightChestErrorState = new State("Right chest pad wrongly placed.", "putPadRightChestErrorState", "Wrong location for the pad! Place the pad on the right chest of the subject. Say yes when ready.", State.NORMAL_STATE);

        State putPadLeftTorsoState =  new State("State to put pad on left torso.", "putPadLeftTorsoState", "Ok. Now, place the pad that goes to the left torso of the subject. Say yes when done.", State.NORMAL_STATE);
        State putPadLeftTorsoErrorState = new State("Left torso pad wrongly placed.", "putPadLeftTorsoErrorState", "Wrong location for the pad! Please put the pad on the left torso of the subject. Say yes when ready", State.NORMAL_STATE);

        State finalState = new State("Final state", "finalState", "Good. You can now operate the AED machine.", State.ENDING_STATE);


        // Initial state
        initialState.setNextState(subjectAgeDetectionState);
        initialState.setErrorState(initialState);

        // Age detection
        subjectAgeDetectionState.setNextState(showAdultPadState);
        subjectAgeDetectionState.setErrorState(showChildPadState);

        //childDetectedState.setNextState(showChildPadState);
        //childDetectedState.setErrorState(subjectAgeDetectionState);

        //adultDetectedState.setNextState(showAdultPadState);
        //adultDetectedState.setErrorState(subjectAgeDetectionState);

        // Pad showing
        showAdultPadState.setNextState(peelInstructionsState);
        showAdultPadState.setErrorState(showAdultPadErrorState);

        showAdultPadErrorState.setNextState(peelInstructionsState);
        showAdultPadErrorState.setErrorState(showAdultPadErrorState);

        showChildPadState.setNextState(peelInstructionsState);
        showChildPadState.setErrorState(showChildPadErrorState);

        showChildPadErrorState.setNextState(peelInstructionsState);
        showChildPadErrorState.setErrorState(showChildPadErrorState);

        // Instructions peeling
        peelInstructionsState.setNextState(putPadRightChestState);
        peelInstructionsState.setErrorState(peelInstructionsState);

        //showChildPadState.setNextState(putPadOnLeftChestState);
        //showChildPadState.setErrorState(showChildPadErrorState);

        //showChildPadErrorState.setNextState(putPadOnLeftChestState);
        //showChildPadErrorState.setErrorState(showChildPadErrorState);

        // Putting pads
        putPadRightChestState.setNextState(putPadLeftTorsoState);
        putPadRightChestState.setErrorState(putPadRightChestErrorState);

        putPadRightChestErrorState.setNextState(putPadLeftTorsoState);
        putPadRightChestErrorState.setErrorState(putPadRightChestErrorState);

        putPadLeftTorsoState.setNextState(finalState);
        putPadLeftTorsoState.setErrorState(putPadLeftTorsoErrorState);

        putPadLeftTorsoErrorState.setNextState(finalState);
        putPadLeftTorsoErrorState.setErrorState(putPadLeftTorsoErrorState);

        //use the first state as the current state
        currentState = initialState;
        try {
            readOutCurrentPrompt(false);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        active = true;
    }

    public void destroy(){
        currentState = null;
    }

    public void screenLog(String log){
        screenLog(log, "#000000");
    }

    public void screenLog(String log, String color) {
        String html = "";
        if(textView.getEditableText() != null)
            html = Html.toHtml(textView.getEditableText());
        textView.setText(Html.fromHtml(html + " " + "<font color = \"" + color.toString() +"\">" + log + "</font>"), TextView.BufferType.EDITABLE);
        textView.invalidate();
    }






}
